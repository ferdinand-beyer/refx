(ns top.utils
  (:require [top.log :as log]))

(defn first-in-vector
  [v]
  (if (vector? v)
    (first v)
    (log/error "expected a vector, but got:" v)))
