(ns top.events
  (:require [top.interceptor :as interceptor]
            [top.interop :refer [debug-enabled?]]
            [top.log :as log]
            [top.utils :refer [first-in-vector]]))

(defonce registry (atom {}))

(defn- flatten-and-remove-nils
  "`interceptors` might have nested collections, and contain nil elements.
  return a flat collection, with all nils removed.
  This function is 9/10 about giving good error messages."
  [id interceptors]
  (let [make-chain  #(->> % flatten (remove nil?))]
    (if-not debug-enabled?
      (make-chain interceptors)
      (do
        (when-not (coll? interceptors)
          (log/error "when registering" id ", expected a collection of interceptors, got:" interceptors))
        (let [chain (make-chain interceptors)]
          (when (empty? chain)
            (log/error "when registering" id ", given an empty interceptor chain"))
          (when-let [not-i (first (remove interceptor/interceptor? chain))]
            (log/error "when registering" id ", expected interceptors, but got:" not-i))
          chain)))))

(defn register
  "Associate the given event `id` with the given collection of `interceptors`.

   `interceptors` may contain nested collections and there may be nils
   at any level,so process this structure into a simple, nil-less vector
   before registration.

   Typically, an `event handler` will be at the end of the chain (wrapped
   in an interceptor)."
  [id interceptors]
  (swap! registry assoc id (flatten-and-remove-nils id interceptors)))

(defn unregister
  ([]
   (reset! registry {}))
  ([id]
   (swap! registry dissoc id)))

;; -- handle event ------------------------------------------------------------

(def ^:dynamic *handling* nil)

(defn handle
  "Given an event vector `event-v`, look up the associated interceptor chain,
   and execute it."
  [event-v]
  (let [event-id (first-in-vector event-v)]
    (when-let [interceptors (get @registry event-id true)]
      (if *handling*
        (log/error "while handling" *handling* ", dispatch-sync was called for" event-v
                   ". You can't call dispatch-sync within an event handler.")
        (binding [*handling*  event-v]
          (interceptor/execute event-v interceptors))))))
