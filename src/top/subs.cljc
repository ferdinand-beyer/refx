(ns top.subs
  (:require [top.log :as log]
            [uix.core.alpha :as uix]))

#?(:cljs
   (defn uid
     ([] (goog/getUid #js {}))
     ([obj] (goog/getUid obj)))

   :clj
   (let [counter (atom 0)
         cache   (atom {})]
     (defn uid
       ([] (swap! counter inc))
       ([obj]
        (or (get @cache obj)
            (let [x (uid)]
              (swap! cache assoc obj x)
              x))))))

(defprotocol ISignal
  "Protocol for signal types.

   Very similar to IDeref + IWatchable, but other than Clojure watches, our
   listeners do not get values, as they are expected to be computed on demand."
  (-value [this])
  (-add-listener [this k f]
    "Register a listener that will be called like `(f k signal)`.")
  (-remove-listener [this k]))

(defn signal? [x]
  (satisfies? ISignal x))

(extend-protocol ISignal
  nil
  (-value [_] nil)
  (-add-listener [_ _ _])
  (-remove-listener [_ _])

  #?(:cljs Atom
     :clj clojure.lang.Atom)
  (-value [a] @a)
  (-add-listener [a k f] (add-watch a k f))
  (-remove-listener [a k] (remove-watch a k)))

;; TODO: Only (when (signal? %))?
(defn- map-inputs
  "Apply `f` to a node input value."
  [f inputs]
  (cond
    (sequential? inputs) (mapv f inputs)
    (map? inputs) (update-vals inputs f)
    (signal? inputs) (f inputs)
    :else nil))

(defn- run-inputs! [f inputs]
  (map-inputs f inputs)
  nil)

(def ^:private INVALID ::invalid)

(deftype Node [inputs compute-fn ^:mutable value ^:mutable listeners]
  Object
  (invalidate! [this]
    (when-not (identical? value INVALID)
      (set! value INVALID)
      (doseq [[key f] listeners]
        (f key this))))

  #?@(:cljs
      [(equiv [this other]
              (-equiv this other))

       IEquiv
       (-equiv [this other] (identical? this other))

       IHash
       (-hash [this] (uid this))])

  ISignal
  (-value [_]
    (when (identical? value INVALID)
      (set! value (compute-fn (map-inputs -value inputs))))
    value)
  (-add-listener [this k f]
    (when (empty? listeners)
      (let [k* (uid this)
            f* #(.invalidate! this)]
        (run-inputs! #(-add-listener % k* f*) inputs)))
    (set! listeners (assoc listeners k f)))
  (-remove-listener [this k]
    (set! listeners (dissoc listeners k))
    (when (empty? listeners)
      (let [k* (uid this)]
        (run-inputs! #(-remove-listener % k*) inputs)))))

(defn make-node
  "Make a computational node, which implements ISignal."
  [inputs compute-fn]
  (Node. inputs compute-fn INVALID nil))

(deftype Subscription [signal dispose-fn ^:mutable listener-count]
  ISignal
  (-value [_] (-value signal))
  (-add-listener [this key f]
    (-add-listener signal key f)
    (set! listener-count (inc listener-count))
    this)
  (-remove-listener [this key]
    (-remove-listener signal key)
    (set! listener-count (dec listener-count))
    (when (zero? listener-count)
      (dispose-fn this))))

(defn- make-sub [signal dispose-fn]
  (Subscription. signal dispose-fn 0))

(defonce cache (atom {}))

(defn- cache-lookup [query-v]
  (get @cache query-v))

(defn- cache-add! [query-v sub]
  (swap! cache assoc query-v sub))

(defn- cache-remove! [query-v sub]
  (swap! cache (fn [cache]
                 (if (identical? sub (get cache query-v))
                   (dissoc cache query-v)
                   cache))))

(defonce registry (atom {}))

(defn- create-sub [query-v]
  (let [query-id (first query-v)
        handler  (get @registry query-id)]
    (if (nil? handler)
      (do (log/error "no subscription handler registered for:" (str query-id))
          (make-sub nil #()))
      (let [signal ((:compile handler) query-v)
            sub    (make-sub signal (partial cache-remove! query-v))]
        (cache-add! query-v sub)
        sub))))

;; TODO: Provide a `sub-value` function that will only compute the value,
;; optionally making use of the cache.  This can be used in event handlers.
;; This could be done by storing subscription handlers as data.

(defn sub
  "Returns a subscription to `query-v`.

   Callers must make sure that the returned object is eventually used, or it
   will leak memory.  This is designed to construct custom subscriptions in
   handlers, React components should use `subscribe` instead."
  [query-v]
  (or (cache-lookup query-v)
      (create-sub query-v)))

(defn subscribe
  "React hook to subscribe to signals."
  [query-v]
  (uix/subscribe
   (uix/memo (fn []
               (let [k (uid)]
                 {:get-current-value (fn [] (-value (sub query-v)))
                  :subscribe (fn [callback]
                               (let [s (sub query-v)]
                                 (-add-listener s k callback)
                                 #(-remove-listener s k)))}))
             [query-v])))

(defn- make-handler [query-id inputs-fn compute-fn]
  {:id      query-id
   :compile (fn [query-v]
              (make-node (inputs-fn query-v) compute-fn))})

(defn register
  [query-id inputs-fn compute-fn]
  (swap! registry assoc query-id (make-handler query-id inputs-fn compute-fn)))

(defn unregister
  ([]
   (reset! registry {}))
  ([id]
   (swap! registry dissoc id)))
