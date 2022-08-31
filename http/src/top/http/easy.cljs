(ns top.http.easy
  (:require [ajax.easy :as easy]
            [top.alpha :refer [reg-fx]]
            [top.http.impl :as impl]))

(def easy-http-effect
  "An HTTP effect that allows for more convenient request options, at the
   expense of pulling in more dependencies, such as Transit.

   See the difference between `ajax.simple` and `ajax.easy`."
  (impl/make-http-effect easy/transform-opts))

(reg-fx :http easy-http-effect)
(reg-fx :http-easy easy-http-effect)
