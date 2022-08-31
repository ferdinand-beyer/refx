(ns top.alpha
  (:refer-clojure :exclude [->])
  (:require [top.builtins :as builtins]
            [top.cofx :as cofx]
            [top.dispatch :as dispatch]
            [top.effects :as effects]
            [top.events :as events]
            [top.store :refer [store]]
            [top.subs :as subs]))

;; --- dispatch ---------------------------------------------------------------

;; re-frame: dispatch event
;; redux: dispatch action
;; elm: messages

(defn dispatch
  [event]
  (dispatch/dispatch event))

(defn dispatch-sync
  [event]
  (dispatch/dispatch-sync event))

;; --- events -----------------------------------------------------------------

;; TODO: Provide a registry of interceptors as well, so they can be referenced by ID.

(def base-interceptors
  [cofx/inject-db effects/do-fx builtins/inject-global-interceptors])

(defn- -reg-event [id interceptors handler-interceptor]
  (events/register id (conj base-interceptors interceptors handler-interceptor)))

(defn reg-event-db
  ([id handler]
   (reg-event-db id nil handler))
  ([id interceptors handler]
   (-reg-event id interceptors (events/db-handler->interceptor handler))))

(defn reg-event-fx
  ([id handler]
   (reg-event-fx id nil handler))
  ([id interceptors handler]
   (-reg-event id interceptors (events/fx-handler->interceptor handler))))

(defn reg-event-ctx
  ([id handler]
   (reg-event-ctx id nil handler))
  ([id interceptors handler]
   (-reg-event id interceptors (events/ctx-handler->interceptor handler))))

(defn clear-event
  ([]
   (events/unregister))
  ([id]
   (events/unregister id)))

;; --- subscriptions ----------------------------------------------------------

(defn sub
  "Return a subscription signal to be used as an input in `reg-sub`."
  [query-v]
  (subs/sub query-v))

(defn <-
  "Like re-frame's `:<-` sugar, returns an `input-fn` for `reg-sub` that
   subscribes to one or more query vectors."
  ([query-v]
   (fn [_] (subs/sub query-v)))
  ([query-v & qs]
   (let [qs (cons query-v qs)]
     (fn [_] (mapv subs/sub qs)))))

(defn ->
  "Like re-frame's `:->` sugar, wraps a handler function that ignores the
   query vector."
  [f]
  (fn [input _]
    (f input)))

(defn =>
  "Like re-frame's `:->` sugar, wraps a handler function that takes the
   \"arguments\" of a query vector, without the query ID."
  [f]
  (fn [input [_ & qs]]
    (apply f input qs)))

;; TODO: Provide means to compose input functions?
;; ??? Can we make use of transducers?
(defn reg-sub
  ([query-id compute-fn]
   (reg-sub query-id (constantly store) compute-fn))
  ([query-id input-fn compute-fn]
   (subs/register query-id input-fn compute-fn))
  ;; re-frame compat
  ([query-id a b c & more]
   (let [[qs compute-fn]
         (reduce (fn [[qs cfn] [m arg]]
                   (case m
                     :<- [(conj qs arg) cfn]
                     :-> [qs (-> arg)]
                     :=> [qs (=> arg)]
                     [qs m]))
                 [[] nil]
                 (->> (concat [a b c] more)
                      (partition-all 2)))]
     (if (seq qs)
       (reg-sub query-id (apply <- qs) compute-fn)
       (reg-sub query-id compute-fn)))))

(defn subscribe [query-v]
  (subs/subscribe query-v))

(defn clear-sub
  ([]
   (subs/unregister))
  ([id]
   (subs/unregister id)))

(defn clear-subscription-cache! []
  (subs/clear-subscription-cache!))

;; --- effects ----------------------------------------------------------------

(defn reg-fx
  [id handler]
  (effects/register id handler))

(defn clear-fx
  ([]
   (effects/unregister))
  ([id]
   (effects/unregister id)))

;; --- coeffects --------------------------------------------------------------

(defn reg-cofx
  [id handler]
  (cofx/register id handler))

(defn clear-cofx
  ([]
   (cofx/unregister))
  ([id]
   (cofx/unregister id)))

(defn inject-cofx
  ([id]
   (cofx/inject-cofx id))
  ([id value]
   (cofx/inject-cofx id value)))
