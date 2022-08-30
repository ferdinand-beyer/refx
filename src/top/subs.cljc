(ns top.subs
  (:require [top.log :as log]
            [uix.core.alpha :as uix]))

;; --- signals ----------------------------------------------------------------

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

;; --- subscription cache -----------------------------------------------------

(defonce sub-cache (atom {}))

(defn- cache-lookup [query-v]
  (get @sub-cache query-v))

(defn- cache-add! [query-v sub]
  (swap! sub-cache assoc query-v sub))

(defn- cache-remove! [query-v sub]
  (swap! sub-cache (fn [cache]
                     (if (identical? sub (get cache query-v))
                       (dissoc cache query-v)
                       cache))))

;; --- subscriptions ----------------------------------------------------------

(def object-uid #?(:cljs goog/getUid
                   :clj  System/identityHashCode))

(defn object-key [o]
  (str "__obj_" (object-uid o)))

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

(deftype Sub [query-v inputs compute-fn ^:mutable value ^:mutable listeners]
  Object
  (init! [this]
    (let [key (object-key this)
          cb  #(.invalidate! this)]
      (run-inputs! #(-add-listener % key cb) inputs)
      (cache-add! query-v this)))

  (dispose! [this]
    (let [key (object-key this)]
      (run-inputs! #(-remove-listener % key) inputs))
    (cache-remove! query-v this))

  (invalidate! [this]
    (when-not (identical? value INVALID)
      (set! value INVALID)
      (doseq [[key f] listeners]
        (f key this))))

  ISignal
  (-value [_]
    (when (identical? value INVALID)
      (set! value (compute-fn (map-inputs -value inputs))))
    value)

  (-add-listener [_ k f]
    (set! listeners (assoc listeners k f)))

  (-remove-listener [this k]
    (set! listeners (dissoc listeners k))
    ;; ??? Instead of disposing immeditately, we could use a GC scheme: Mark
    ;; this sub unused, and periodically remove unused subs.  Maybe with an
    ;; intermediate "flag for removal" stage.
    ;; To use subs in event handlers, we could also "reset" the mark every time
    ;; we access the value.
    (when (empty? listeners)
      (.dispose! this))))

(defn make-sub
  [query-v inputs compute-fn]
  (let [sub (Sub. query-v inputs compute-fn INVALID nil)]
    (.init! sub)
    sub))

;; --- registry ---------------------------------------------------------------

(defonce registry (atom {}))

(defn- create-sub [query-v]
  (let [query-id (first query-v)
        handler  (get @registry query-id)]
    (if (nil? handler)
      ;; Note that nil is a valid signal.
      (log/error "no subscription handler registered for:" (str query-id))
      (let [compile-fn (:compile handler)]
        (compile-fn query-v)))))

(defn sub
  "Returns a subscription to `query-v`.

   Callers must make sure that the returned object is eventually used, or it
   will leak memory.  This is designed to construct custom subscriptions in
   handlers, React components should use `subscribe` instead."
  [query-v]
  (or (cache-lookup query-v)
      (create-sub query-v)))

(defn- make-handler [query-id inputs-fn compute-fn]
  {:id      query-id
   :compile (fn [query-v]
              (make-sub query-v (inputs-fn query-v) compute-fn))})

(defn register
  [query-id inputs-fn compute-fn]
  (swap! registry assoc query-id (make-handler query-id inputs-fn compute-fn)))

(defn unregister
  ([]
   (reset! registry {}))
  ([id]
   (swap! registry dissoc id)))

;; --- subscribe --------------------------------------------------------------

(let [counter (atom 0)]
  (defn- sub-key []
    (str "__sub_" (swap! counter inc))))

(defn subscribe
  "React hook to subscribe to signals."
  [query-v]
  (uix/subscribe
   (uix/memo (fn []
               (let [k (sub-key)
                     s (volatile! (delay (sub query-v)))]
                 {:get-current-value (fn [] (-value @@s))
                  :subscribe (fn [callback]
                               (-add-listener @@s k callback)
                               (fn []
                                 (-remove-listener @@s k)
                                 (vreset! s (delay (sub query-v)))))}))
             [query-v])))
