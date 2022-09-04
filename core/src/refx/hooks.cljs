(ns refx.hooks
  (:require ["react" :as react]
            ["use-sync-external-store/shim" :refer [useSyncExternalStore]]
            [refx.subs :as subs]))

(defn- cljs-deps
  "Uses a Ref to track ClojureScript values as dependencies, yielding
   a new JavaScript array whenever the dependencies change.  This is
   useful as React does not know about ClojureScript's equality semantics,
   but will check for identical objects instead, leading to false
   re-renders and possible infinite loops."
  [deps]
  (let [ref (react/useRef deps)]
    (when (not= (.-current ref) deps)
      (set! (.-current ref) deps))
    #js [(.-current ref)]))

(defonce ^:private use-sub-counter (atom 0))

(defn use-sub
  "React hook to subscribe to signals."
  [query-v]
  ;; Subs are cached, so no need to memoize them again.  Retrieving them
  ;; on every render allows us to pick up changes in dev workflows, where
  ;; we use clear-subscription-cache! on refresh.
  (let [sub (subs/sub query-v)
        [subscribe snapshot]
        (react/useMemo
         (fn []
           [(fn [callback]
              (let [key (str "use-sub-" (swap! use-sub-counter inc))]
                (subs/-add-listener sub key callback)
                #(subs/-remove-listener sub key)))
            (fn []
              (subs/-value sub))])
         (cljs-deps sub))]
    ;; Expose the query vector in React DevTools.  The current value will
    ;; be inspectable by `useSyncExternalStore` already.
    (react/useDebugValue query-v str)
    (useSyncExternalStore subscribe snapshot)))
