(ns top.signal
  (:require [uix.core.alpha :as uix]
            [top.store :refer [store]]))

(defonce ^:private -genkey-counter (atom 0))

(defn genkey []
  (swap! -genkey-counter inc))

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
               {:get-current-value #(-value signal)
                :subscribe (fn [callback]
                             (let [k (genkey)]
                               (-add-listener signal k callback)
                               #(-remove-listener signal k)))})
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
(deftype Signal [key inputs compute-fn on-dispose
                 ^:mutable value ^:mutable listeners]
  Object
  (equiv [this other]
    (-equiv this other))

  (update! [this]
    (let [new-val (compute-fn (map -value inputs))]
      (when (not= value new-val)
        (set! (.-value this) new-val)
        (doseq [[_ f] listeners]
          (f)))))

  IEquiv
  (-equiv [o other] (identical? o other))

  IHash
  (-hash [o] (goog/getUid o))

  IDeref
  (-deref [_] value)

  ISignal
  (-value [_] value)
  (-add-listener [this k f]
    (.info js/console "Added listener" key k)
    (set! (.-listeners this) (assoc listeners k f)))
  (-remove-listener [this k]
    (.info js/console "Removed listener" key k)
    (set! (.-listeners this) (dissoc listeners k))
    (when (empty? listeners)
      (.info js/console "Disposing signal" key)
      (doseq [s inputs]
        (-remove-listener s key))
      (on-dispose))))

(defn- create-signal [inputs compute-fn on-dispose]
  (let [key    (genkey)
        value  (compute-fn (map -value inputs))
        signal (Signal. key inputs compute-fn on-dispose value nil)
        f      #(.update! signal)]
    (doseq [s inputs]
      (-add-listener s key f))
    signal))

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
  (swap! signal-cache dissoc query-v)
  (.log js/console "Removed" (str query-v) "Cache size:" (count @signal-cache)))

(defn- get-signal [query-v]
  (if-let [s (get @signal-cache query-v)]
    s
    (let [s (create-sig query-v #(remove-from-cache query-v))]
      (swap! signal-cache assoc query-v s)
      (.log js/console "Added" (str query-v) (.-key s) "Cache size:" (count @signal-cache))
      s)))

(defn subscribe [query-v]
  (uix/subscribe
   (uix/memo (fn []
               (let [k (genkey)]
                 {:get-current-value (fn []
                                       (-value (get-signal query-v)))
                  :subscribe (fn [callback]
                               (let [signal (get-signal query-v)]
                                 (-add-listener signal k callback)
                                 #(-remove-listener signal k)))}))
             [query-v])))

;; ----

(defonce handlers (atom {}))

(defn register [id handler])
