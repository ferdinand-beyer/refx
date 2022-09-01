(ns refx.events
  (:require [refx.interceptor :as interceptor :refer [->interceptor
                                                     assoc-effect get-coeffect]]
            [refx.interop :refer [debug-enabled?]]
            [refx.log :as log]
            [refx.registry :as registry]
            [refx.utils :refer [first-in-vector]]))

(def kind :event)

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
  (registry/add! kind id (flatten-and-remove-nils id interceptors)))

;; --- handle event -----------------------------------------------------------

(def ^:dynamic *handling* nil)

(defn handle
  "Given an event vector `event-v`, look up the associated interceptor chain,
   and execute it."
  [event-v]
  (let [event-id (first-in-vector event-v)]
    (when-let [interceptors (registry/lookup kind event-id)]
      (if *handling*
        (log/error "while handling" *handling* ", dispatch-sync was called for" event-v
                   ". You can't call dispatch-sync within an event handler.")
        (binding [*handling*  event-v]
          (interceptor/execute event-v interceptors))))))

;; --- handler->interceptor ---------------------------------------------------

(defn db-handler->interceptor
  "Returns an interceptor which wraps the kind of event handler given to `reg-event-db`.

  These handlers take two arguments;  `db` and `event`, and they return `db`.

      (fn [db event]
         ....)

  So, the interceptor wraps the given handler:
     1. extracts two `:coeffects` keys: db and event
     2. calls handler-fn
     3. stores the db result back into context's `:effects`"
  [handler-fn]
  (->interceptor
   :id     :db-handler
   :before (fn db-handler-before
             [context]
             (let [{:keys [db event]} (get-coeffect context)]
               (->> (handler-fn db event)
                    (assoc-effect context :db))))))

(defn fx-handler->interceptor
  "Returns an interceptor which wraps the kind of event handler given to `reg-event-fx`.

  These handlers take two arguments;  `coeffects` and `event`, and they return `effects`.

      (fn [coeffects event]
         {:db ...
          :fx ...})

   Wrap handler in an interceptor so it can be added to (the RHS) of a chain:
     1. extracts `:coeffects`
     2. call handler-fn giving coeffects
     3. stores the result back into the `:effects`"
  [handler-fn]
  (->interceptor
   :id     :fx-handler
   :before (fn fx-handler-before
             [context]
             (let [{:keys [event] :as coeffects} (get-coeffect context)]
               (->> (handler-fn coeffects event)
                    (assoc context :effects))))))

(defn ctx-handler->interceptor
  "Returns an interceptor which wraps the kind of event handler given to `reg-event-ctx`.
  These advanced handlers take one argument: `context` and they return a modified `context`.
  Example:

      (fn [context]
         (enqueue context [more interceptors]))"
  [handler-fn]
  (->interceptor
   :id     :ctx-handler
   :before (fn ctx-handler-before
             [context]
             (handler-fn context))))
