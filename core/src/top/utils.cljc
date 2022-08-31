(ns top.utils
  (:require [top.log :as log]))

(defn first-in-vector
  [v]
  (if (vector? v)
    (first v)
    (log/error "expected a vector, but got:" v)))

(defn apply-kw
  "Like apply, but f takes keyword arguments and the last argument is
  not a seq but a map with the arguments for f"
  [f & args]
  {:pre [(map? (last args))]}
  (apply f (apply concat
                  (butlast args) (last args))))
