(ns top.log
  (:require [top.interop :as interop]))

(defn debug [& args]
  (interop/log :debug "top:" args))

(defn info [& args]
  (interop/log :info "top:" args))

(defn warn [& args]
  (interop/log :warn "top:" args))

(defn error [& args]
  (interop/log :error "top:" args))
