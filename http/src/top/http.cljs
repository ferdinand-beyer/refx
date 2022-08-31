(ns top.http
  (:require [top.alpha :refer [reg-fx]]
            [top.http.impl :as impl]))

(def simple-http-effect
  "Simple HTTP effect, similar to re-frame's `:http-xhrio`."
  (impl/make-http-effect))

(reg-fx :http simple-http-effect)
(reg-fx :http-xhrio simple-http-effect)
