(ns refx.log
  (:require [refx.interop :as interop]))

(defn debug [& args]
  (interop/log :debug (cons "refx:" args)))

(defn info [& args]
  (interop/log :info (cons "refx:" args)))

(defn warn [& args]
  (interop/log :warn (cons "refx:" args)))

(defn error [& args]
  (interop/log :error (cons "refx:" args)))
