(ns top.subs
  (:require [top.interop :as interop]
            [top.log :as log]
            [top.registry :as registry]
            [top.utils :as utils]))

(def kind :sub)

;; --- signals ----------------------------------------------------------------

(defprotocol ISignal
  "Protocol for signal types.

   Very similar to IDeref + IWatchable, but other than Clojure watches, our
   listeners do not get values, as they are expected to be computed on demand."
  (-value [this])
  (-add-listener [this k f]
    "Register a listener that will be called without arguments.")
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

(defprotocol ISub
  (-query-v [this])
  (-dispose! [this]))

(defn- dispose! [sub]
  (cache-remove! (-query-v sub) sub)
  (-dispose! sub))

;; TODO This could be changed to support "garbage collection": Don't dispose
;; right away, but keep subscriptions around for a while in case they are
;; requested again.
;; E.g. we could trigger background jobs using window.requestIdleCallback()
(defn- sub-orphaned [sub]
  (dispose! sub))

(defn clear-subscription-cache! []
  (doseq [[_ sub] @sub-cache]
    (dispose! sub))
  (when (not-empty @sub-cache)
    (log/warn "The subscription cache isn't empty after being cleared")))

;; TODO: Use pure JavaScript for speed?
(deftype Listeners [^:mutable listeners]
  Object
  (empty? [_] (empty? listeners))
  (add [_ k f]
    (set! listeners (assoc listeners k f)))
  (remove [_ k]
    (set! listeners (dissoc listeners k)))
  (notify [_]
    (doseq [[_ f] listeners]
      (f))))

(defn- make-listeners []
  (Listeners. nil))

(def object-uid #?(:cljs goog/getUid
                   :clj  System/identityHashCode))

(defn object-key [o]
  (str "__obj_" (object-uid o)))

(defn- map-signals
  "Apply `f` to a node input value."
  [f input]
  (cond
    (signal? input) (f input)
    (sequential? input) (mapv f input)
    (map? input) (update-vals input f)
    :else input))

(defn- run-signals! [f input]
  (map-signals f input)
  nil)

(defn- compute-sub [query-v input compute-fn]
  (compute-fn (map-signals -value input) query-v))

(deftype Sub [query-v input compute-fn
              ^Listeners listeners
              ^:mutable value
              ^:mutable dirty?]
  Object
  (init! [this]
    (let [key (object-key this)
          cb  #(.invalidate! this)]
      (run-signals! #(-add-listener % key cb) input)))

  (invalidate! [this]
    (when-not dirty?
      (set! dirty? true)
      (interop/next-tick #(.update! this))))

  (update! [_]
    (let [new-value (compute-sub query-v input compute-fn)]
      (set! dirty? false)
      (when (not= value new-value)
        (set! value new-value)
        (.notify listeners))))

  #?@(:cljs
      [IEquiv
       (-equiv [this other] (identical? this other))

       IHash
       (-hash [this] (object-uid this))

       IDeref
       (-deref [this] (-value this))])

  ISub
  (-query-v [_] query-v)
  (-dispose! [this]
    (let [key (object-key this)]
      (run-signals! #(-remove-listener % key) input)))

  ISignal
  (-value [_] value)
  (-add-listener [_ k f]
    (.add listeners k f))
  (-remove-listener [this k]
    (.remove listeners k)
    (when (.empty? listeners)
      (sub-orphaned this))))

(defn- make-sub
  [query-v input compute-fn]
  (let [value (compute-sub query-v input compute-fn)
        sub   (Sub. query-v input compute-fn (make-listeners) value false)]
    (.init! sub)
    sub))

;; --- register ---------------------------------------------------------------

(defn register
  [query-id input-fn compute-fn]
  (letfn [(handler-fn [query-v]
            (make-sub query-v (input-fn query-v) compute-fn))]
    (registry/add! kind query-id handler-fn)))

;; --- dynamic ----------------------------------------------------------------
;;
;; Dynamic subscriptions allow callers to place signals in query vectors:
;; (subscribe [:dynamic (sub [:param1]) (sub [:param2])])
;;
;; This is not very useful in views, as these should be composed in such a way
;; that child components take parameters for their subscriptions as props.
;;
;; However, it can be useful to create more powerful named subscriptions with
;; `reg-sub`, without needing to change how views are organised.
;;
;; Dynamic subs wrap a special "query-sub" that computes the dynamic query
;; vector, and a mutable "value-sub" that is updated whenever the query sub
;; changes.
(deftype DynamicSub [query-v query-sub handler-fn
                     ^Listeners listeners
                     ^:mutable value-sub]
  Object
  (init! [this]
    (.update! this)
    (-add-listener query-sub (object-key this) #(.update! this)))

  (update! [this]
    (let [qv  (-value query-sub)
          key (object-key this)]
      (when value-sub
        (-remove-listener value-sub key))
      (set! value-sub (or (cache-lookup qv)
                          (handler-fn qv)))
      (-add-listener value-sub key #(.notify listeners))
      (.notify listeners)))

  #?@(:cljs
      [IEquiv
       (-equiv [this other] (identical? this other))

       IHash
       (-hash [this] (object-uid this))

       IDeref
       (-deref [this] (-value this))])

  ISub
  (-query-v [_] query-v)
  (-dispose! [this]
    (let [key (object-key this)]
      (-remove-listener query-sub key)
      (when value-sub
        (-remove-listener value-sub key))))

  ISignal
  (-value [_]
    (-value value-sub))
  (-add-listener [_ k f]
    (.add listeners k f))
  (-remove-listener [this k]
    (.remove listeners k)
    (when (.empty? listeners)
      (sub-orphaned this))))

;; Don't consider nil dynamic, even though it is a valid signal.
(def ^:private some-signal?
  (every-pred some? signal?))

(defn- dynamic?
  "Return true if a query vector contains signals."
  [query-v]
  (some some-signal? query-v))

(defn- dynamic-input
  "Input function for dynamic subscriptions, where the query vector contains
   signals.  Returns a map of vector indexes to signals."
  [query-v]
  (into {}
        (keep-indexed (fn [i x]
                        (when (some-signal? x)
                          [i x])))
        query-v))

(defn- dynamic-compute
  "Computation function for dynamic subscriptions.  Returns a query vector
   where signals have been replaced with their current values.  `input` must
   be a map of vector index to value, as returned by `dynamic-input`."
  [input [_ query-v]]
  (reduce-kv assoc query-v input))

(defn- make-dynamic
  "Make a dynamic subscription."
  [query-v handler-fn]
  (let [query-sub (make-sub [::query query-v] (dynamic-input query-v) dynamic-compute)
        dynamic   (DynamicSub. query-v query-sub handler-fn (make-listeners) nil)]
    (.init! dynamic)
    dynamic))

;; --- sub --------------------------------------------------------------------

(defn- create-sub [query-v]
  (let [query-id (utils/first-in-vector query-v)]
    ;; Note that nil is a valid signal!
    (when-let [handler-fn (registry/lookup kind query-id)]
      (let [sub (if (dynamic? query-v)
                  (make-dynamic query-v handler-fn)
                  (handler-fn query-v))]
        (cache-add! query-v sub)
        sub))))

(defn sub
  "Returns a subscription to `query-v`.

   Callers must make sure that the returned object is eventually used, or it
   will leak memory.  This is designed to construct custom subscriptions in
   handlers, React components should use `subscribe` instead."
  [query-v]
  (or (cache-lookup query-v)
      (create-sub query-v)))
