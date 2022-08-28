(ns top.signal
  (:require [uix.core.alpha :as uix]))

;; TODO: utils?
(defn uid
  ([] (goog/getUid #js {}))
  ([obj] (goog/getUid obj)))

(defprotocol ISignal
  "Protocol for reactive values that we provide a React hook for."
  (-value [this])
  (-add-listener [this k f])
  (-remove-listener [this k]))

(defn listen
  "Hook to listen to a signal."
  [signal]
  (uix/subscribe
   (uix/memo (fn []
               (let [k (uid)]
                 {:get-current-value #(-value signal)
                  :subscribe (fn [callback]
                               (-add-listener signal k callback)
                               #(-remove-listener signal k))}))
             [signal])))

(extend-protocol ISignal
  nil
  (-value [_] nil)
  (-add-listener [_ _ _])
  (-remove-listener [_ _])

  Atom
  (-value [a] @a)
  (-add-listener [a k f]
    (add-watch a k (fn [_ _ _ _] (f))))
  (-remove-listener [a k]
    (remove-watch a k)))

(defn signal? [x]
  (satisfies? ISignal x))

(defn- map-signals [f signals]
  (cond
    (sequential? signals) (mapv f signals)
    (map? signals) (update-vals signals f)
    (signal? signals) (f signals)
    :else nil))

(defn- run-signals! [f signals]
  (map-signals f signals)
  nil)

(defn- compute-signal-value [inputs compute-fn]
  (compute-fn (map-signals -value inputs)))

;; TODO: Is this a "subscription"?
(deftype Signal [inputs compute-fn ^:mutable on-dispose ^:mutable value ^:mutable listeners]
  Object
  (equiv [this other]
    (-equiv this other))

  (init! [this]
    (let [k (uid this)
          f #(.update! this)]
      (run-signals! #(-add-listener % k f) inputs)))

  (dispose! [this]
    (let [k (uid this)]
      (run-signals! #(-remove-listener % k) inputs)
      (when on-dispose
        (on-dispose this))))

  (update! [this]
    (let [new-val (compute-signal-value inputs compute-fn)]
      (when (not= value new-val)
        (set! (.-value this) new-val)
        (doseq [[_ f] listeners]
          (f)))))

  IEquiv
  (-equiv [this other] (identical? this other))

  IHash
  (-hash [this] (uid this))

  IDeref
  (-deref [_] value)

  ISignal
  (-value [_] value)
  (-add-listener [this k f]
    (set! (.-listeners this) (assoc listeners k f)))
  (-remove-listener [this k]
    (let [ls (dissoc listeners k)]
      (set! (.-listeners this) ls)
      (when (empty? ls)
        (.dispose! this)))))

(defn make-signal [inputs compute-fn on-dispose]
  (let [value (compute-signal-value inputs compute-fn)]
    (doto (Signal. inputs compute-fn on-dispose value nil)
      (.init!))))

;; TODO: This does not work on atoms, split protocols?
(defn on-dispose [^Signal signal f]
  (set! (.-on-dispose signal) f))
