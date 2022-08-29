(ns top.effects
  (:require [top.dispatch :as dispatch]
            [top.events :as events]
            [top.interceptor :refer [->interceptor]]
            [top.interop :as interop]
            [top.log :as log]
            [top.store :refer [store]]))

;; -- Registration ------------------------------------------------------------

(defonce registry (atom {}))

(defn register
  [id handler]
  (swap! registry assoc id handler))

(defn unregister
  ([]
   (reset! registry {}))
  ([id]
   (swap! registry dissoc id)))

;; -- Interceptor -------------------------------------------------------------

(defn- db-effect [db]
  (when-not (identical? @store db)
    (reset! store db)))

(def do-fx
  "An interceptor whose `:after` actions the contents of `:effects`. As a result,
  this interceptor is Domino 3.

  This interceptor is silently added (by reg-event-db etc) to the front of
  interceptor chains for all events.

  For each key in `:effects` (a map), it calls the registered `effects handler`
  (see `reg-fx` for registration of effect handlers).

  So, if `:effects` was:
      {:dispatch  [:hello 42]
       :db        {...}
       :undo      \"set flag\"}

  it will call the registered effect handlers for each of the map's keys:
  `:dispatch`, `:undo` and `:db`. When calling each handler, provides the map
  value for that key - so in the example above the effect handler for :dispatch
  will be given one arg `[:hello 42]`.

  You cannot rely on the ordering in which effects are executed, other than that
  `:db` is guaranteed to be executed first."
  (->interceptor
   :id :do-fx
   :after (fn do-fx-after
            [context]
            (let [effects            (:effects context)
                  effects-without-db (dissoc effects :db)]
                 ;; :db effect is guaranteed to be handled before all other effects.
              (when-let [new-db (:db effects)]
                (db-effect new-db))
              (doseq [[effect-key effect-value] effects-without-db]
                (if-let [effect-fn (get @registry effect-key false)]
                  (effect-fn effect-value)
                  (log/warn "no handler registered for effect:" effect-key ". Ignoring.")))))))

;; -- Builtin Effect Handlers  ------------------------------------------------

;; :dispatch-later
;;
;; `dispatch` one or more events after given delays. Expects a collection
;; of maps with two keys:  :`ms` and `:dispatch`
;;
;; usage:
;;
;;    {:dispatch-later [{:ms 200 :dispatch [:event-id "param"]}    ;;  in 200ms do this: (dispatch [:event-id "param"])
;;                      {:ms 100 :dispatch [:also :this :in :100ms]}]}
;;
;; Note: nil entries in the collection are ignored which means events can be added
;; conditionally:
;;    {:dispatch-later [ (when (> 3 5) {:ms 200 :dispatch [:conditioned-out]})
;;                       {:ms 100 :dispatch [:another-one]}]}
;;
(defn dispatch-later
  [{:keys [ms dispatch] :as effect}]
  (if (or (empty? dispatch) (not (number? ms)))
    (log/error "ignoring bad :dispatch-later value:" effect)
    (interop/set-timeout! #(dispatch/dispatch dispatch) ms)))

(register
 :dispatch-later
 (fn [value]
   (if (map? value)
     (dispatch-later value)
     (doseq [effect (remove nil? value)]
       (dispatch-later effect)))))

;; :fx
;;
;; Handle one or more effects. Expects a collection of vectors (tuples) of the
;; form [effect-key effect-value]. `nil` entries in the collection are ignored
;; so effects can be added conditionally.
;;
;; usage:
;;
;; {:fx [[:dispatch [:event-id "param"]]
;;       nil
;;       [:http-xhrio {:method :post
;;                     ...}]]}
;;
(register
 :fx
 (fn [seq-of-effects]
   (if-not (sequential? seq-of-effects)
     (log/warn "\":fx\" effect expects a seq, but was given " (type seq-of-effects))
     (doseq [[effect-key effect-value] (remove nil? seq-of-effects)]
       (when (= :db effect-key)
         (log/warn "\":fx\" effect should not contain a :db effect"))
       (if-let [effect-fn (get @registry effect-key false)]
         (effect-fn effect-value)
         (log/warn "in \":fx\" effect found " effect-key " which has no associated handler. Ignoring."))))))

;; :dispatch
;;
;; `dispatch` one event. Expects a single vector.
;;
;; usage:
;;   {:dispatch [:event-id "param"] }
(register
 :dispatch
 (fn [value]
   (if-not (vector? value)
     (log/error "ignoring bad :dispatch value. Expected a vector, but got:" value)
     (dispatch/dispatch value))))

;; :dispatch-n
;;
;; `dispatch` more than one event. Expects a list or vector of events. Something for which
;; sequential? returns true.
;;
;; usage:
;;   {:dispatch-n (list [:do :all] [:three :of] [:these])}
;;
;; Note: nil events are ignored which means events can be added
;; conditionally:
;;    {:dispatch-n (list (when (> 3 5) [:conditioned-out])
;;                       [:another-one])}
;;
(register
 :dispatch-n
 (fn [value]
   (if-not (sequential? value)
     (log/error "ignoring bad :dispatch-n value. Expected a collection, but got:" value)
     (doseq [event (remove nil? value)] (dispatch/dispatch event)))))


;; :deregister-event-handler
;;
;; removes a previously registered event handler. Expects either a single id (
;; typically a namespaced keyword), or a seq of ids.
;;
;; usage:
;;   {:deregister-event-handler :my-id)}
;; or:
;;   {:deregister-event-handler [:one-id :another-id]}
;;
(register
 :deregister-event-handler
 (fn [value]
   (if (sequential? value)
     (doseq [event-id value] (events/unregister event-id))
     (events/unregister))))

;; :db
;;
;; reset! app-db with a new value. `value` is expected to be a map.
;;
;; usage:
;;   {:db  {:key1 value1 key2 value2}}
;;
(register
 :db
  db-effect)
