(ns top.log
  (:require [top.interop :as interop]))

(defn debug [& args]
  (interop/log :debug (cons "top:" args)))

(defn info [& args]
  (interop/log :info (cons "top:" args)))

(defn warn [& args]
  (interop/log :warn (cons "top:" args)))

(defn error [& args]
  (interop/log :error (cons "top:" args)))
