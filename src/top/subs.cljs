(ns top.subs
  (:require [top.signal :as signal]
            [top.log :as log]
            [uix.core.alpha :as uix]))

(defonce handlers (atom {}))

(defonce signal-cache (atom {}))

(defn- remove-from-cache! [query-v signal]
  (swap! signal-cache (fn [cache]
                        (if (identical? signal (get cache query-v))
                          (dissoc cache query-v)
                          cache))))

(defn- cache-signal! [query-v signal]
  (signal/on-dispose signal #(remove-from-cache! query-v signal))
  (swap! signal-cache assoc query-v signal))

(defn- create-signal [query-v]
  (let [query-id   (first query-v)
        handler-fn (get @handlers query-id)]
    (if (nil? handler-fn)
      (log/error "no subscription handler registered for:" (str query-id))
      (handler-fn query-v))))

(defn signal [query-v]
  (if-let [signal (get @signal-cache query-v)]
    signal
    (when-let [signal (create-signal query-v)]
      (cache-signal! query-v signal)
      signal)))

(defn subscribe
  "React hook to subscribe to signals."
  [query-v]
  (uix/subscribe
   (uix/memo (fn []
               (let [k (signal/uid)]
                 {:get-current-value (fn [] (signal/-value (signal query-v)))
                  :subscribe (fn [callback]
                               (let [signal (signal query-v)]
                                 (signal/-add-listener signal k callback)
                                 #(signal/-remove-listener signal k)))}))
             [query-v])))

;; TODO: Support dynamic signals like this:
;;   [(signal [:selected-id]) (fn [id] (signal [:data id]))]
;; E.g. Support a chain of handlers
;; - fn: [query-v] -> signals
;; - fn: [vals query-v] -> signals
;; - ...
;; - fn: [vals query-v] -> value
;;
;; Syntactic sugar like re-frame's :<-, :-> and :=> are somewhat confusing
;; and maybe better provided with helper functions?
(defn register
  [query-id & fns]

  )
