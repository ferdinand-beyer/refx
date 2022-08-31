(ns top.alpha
  (:refer-clojure :exclude [->])
  (:require [top.builtins :as builtins]
            [top.cofx :as cofx]
            [top.dispatch :as dispatch]
            [top.effects :as effects]
            [top.events :as events]
            [top.hooks :as hooks]
            [top.registry :as registry]
            [top.store :refer [store]]
            [top.subs :as subs]
            [top.utils :as utils]))

;; --- dispatch ---------------------------------------------------------------

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
   (registry/clear! events/kind))
  ([id]
   (registry/remove! events/kind id)))

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

(defn- parse-reg-sub-sugar [args]
  (let [[qs f] (reduce (fn [[qs f] [op arg]]
                         (case op
                           :<- [(conj qs arg) f]
                           :-> [qs (-> arg)]
                           :=> [qs (=> arg)]
                           [qs op]))
                       [[] nil]
                       (partition-all 2 args))]
    [(when (seq qs) (apply <- qs)) f]))

(defn reg-sub
  ([query-id compute-fn]
   (reg-sub query-id (constantly store) compute-fn))
  ([query-id input-fn compute-fn]
   (subs/register query-id input-fn compute-fn))
  ;; re-frame compat
  ([query-id x y z & args]
   (let [[input-fn compute-fn] (parse-reg-sub-sugar (concat [x y z] args))]
     (if input-fn
       (reg-sub query-id input-fn compute-fn)
       (reg-sub query-id compute-fn)))))

(defn subscribe [query-v]
  (hooks/use-sub query-v))

(defn clear-sub
  ([]
   (registry/clear! subs/kind))
  ([id]
   (registry/remove! subs/kind id)))

(defn clear-subscription-cache! []
  (subs/clear-subscription-cache!))

;; --- effects ----------------------------------------------------------------

(defn reg-fx
  [id handler]
  (effects/register id handler))

(defn clear-fx
  ([]
   (registry/clear! effects/kind))
  ([id]
   (registry/remove! effects/kind id)))

;; --- coeffects --------------------------------------------------------------

(defn reg-cofx
  [id handler]
  (cofx/register id handler))

(defn clear-cofx
  ([]
   (registry/clear! cofx/kind))
  ([id]
   (registry/remove! cofx/kind id)))

(defn inject-cofx
  ([id]
   (cofx/inject-cofx id))
  ([id value]
   (cofx/inject-cofx id value)))

(defn ->interceptor
  [& {:as m :keys [id before after]}]
  (utils/apply-kw interceptor/->interceptor m))
