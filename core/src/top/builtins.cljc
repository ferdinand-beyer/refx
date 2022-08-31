(ns top.builtins
  (:require [top.interceptor :refer [->interceptor]]
            [top.interop :as interop]))

;; TODO: What's the bext place for this?
(defonce global-interceptors (atom interop/empty-queue))

(def inject-global-interceptors
  "An interceptor which adds registered global interceptors to the context's queue.

   NOTE: :queue is a Clojure.lang.PersistentQueue and not a vector."
  (->interceptor
   :id     :inject-global-interceptors
   :before (fn inject-global-interceptors-before
             [context]
             (update context :queue #(into @global-interceptors %)))))
