(ns top.interop
  (:require [clojure.tools.logging :as log])
  (:import [java.util.concurrent Executor Executors]))

(defonce ^:private executor (Executors/newSingleThreadExecutor))

(defn next-tick [f]
  (let [bound-f (bound-fn [& args] (apply f args))]
    (.execute ^Executor executor bound-f))
  nil)

(def empty-queue clojure.lang.PersistentQueue/EMPTY)

(def after-render next-tick)

(def debug-enabled? true)

(defn set-timeout! [f _ms]
  (next-tick f))

(defmacro log [level args]
  `(log/log ~level ~@args))
