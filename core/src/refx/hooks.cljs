(ns refx.hooks
  (:require ["react" :as react]
            ["use-sync-external-store/shim" :refer [useSyncExternalStore]]
            [refx.subs :as subs]))

(defn- deps [deps]
  (let [ref (react/useRef deps)]
    (when (not= (.-current ref) deps)
      (set! (.-current ref) deps))
    #js [(.-current ref)]))

(defn use-sub
  "React hook to subscribe to signals."
  [query-v]
  (let [[subscribe snapshot]
        (react/useMemo
         (fn []
           (let [sub (subs/sub query-v)]
             [(fn [callback]
                (let [key (str "use-sub-" (goog/getUid callback))]
                  (subs/-add-listener sub key callback)
                  (fn [] (subs/-remove-listener sub key))))
              (fn [] (subs/-value sub))]))
         (deps query-v))]
    (useSyncExternalStore subscribe snapshot)))
