(ns top.signal
  (:require [uix.core.alpha :as uix]
            [top.store :refer [store]]))

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

;; TODO: Is this a "subscription"?
(deftype Signal [inputs compute-fn on-dispose ^:mutable value ^:mutable listeners]
  Object
  (equiv [this other]
    (-equiv this other))

  (init! [this]
    (let [k (uid this)
          f #(.update! this)]
      (doseq [s inputs]
        (-add-listener s k f))))

  (dispose! [this]
    (let [k (uid this)]
      (doseq [s inputs]
        (-remove-listener s k))
      (on-dispose)))

  (update! [this]
    (let [new-val (compute-fn (map -value inputs))]
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

(defn- create-signal [inputs compute-fn on-dispose]
  (let [value (compute-fn (map -value inputs))]
    (doto (Signal. inputs compute-fn on-dispose value nil)
      (.init!))))

;; ----

(defonce signal-cache (atom {}))

(declare get-signal)

;; TODO
(defn- create-sig [query-v on-dispose]
  (case (first query-v)
    :store   store
    :raw     (create-signal [store] (comp :counter first) on-dispose)
    :counter (create-signal [(get-signal [:raw])] #(or (first %) 0) on-dispose)
    :toggle  (create-signal [store] (fn [[store]]
                                      (:toggle store))
                            on-dispose)
    ))

(defn- remove-from-cache [query-v]
  (swap! signal-cache dissoc query-v))

(defn- get-signal [query-v]
  (if-let [s (get @signal-cache query-v)]
    s
    (let [s (create-sig query-v #(remove-from-cache query-v))]
      (swap! signal-cache assoc query-v s)
      s)))

(defn subscribe [query-v]
  (uix/subscribe
   (uix/memo (fn []
               (let [k (uid)]
                 {:get-current-value (fn [] (-value (get-signal query-v)))
                  :subscribe (fn [callback]
                               (let [signal (get-signal query-v)]
                                 (-add-listener signal k callback)
                                 #(-remove-listener signal k)))}))
             [query-v])))

;; ----

(defonce handlers (atom {}))

(defn register [id handler])
