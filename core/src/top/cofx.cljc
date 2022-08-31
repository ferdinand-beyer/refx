(ns top.cofx
  (:require [top.interceptor :refer [->interceptor]]
            [top.log :as log]
            [top.store :refer [store]]))

(defonce registry (atom {}))

(defn register
  [id handler]
  (swap! registry assoc id handler))

(defn unregister
  ([]
   (reset! registry {}))
  ([id]
   (swap! registry dissoc id)))

;; -- Interceptor -------------------------------------------------------------

(defn inject-cofx
  ([id]
   (->interceptor
    :id      :coeffects
    :before  (fn coeffects-before
               [context]
               (if-let [handler (get @registry id)]
                 (update context :coeffects handler)
                 (log/error "No cofx handler registered for" id)))))
  ([id value]
   (->interceptor
    :id     :coeffects
    :before  (fn coeffects-before
               [context]
               (if-let [handler (get @registry id)]
                 (update context :coeffects handler value)
                 (log/error "No cofx handler registered for" id))))))

;; -- Builtin CoEffects Handlers  ---------------------------------------------

;; :db
;;
;; Adds to coeffects the value in `app-db`, under the key `:db`
(register
 :db
 (fn db-coeffects-handler
   [coeffects]
   (assoc coeffects :db @store)))

;; Because this interceptor is used so much, we reify it
(def inject-db (inject-cofx :db))

