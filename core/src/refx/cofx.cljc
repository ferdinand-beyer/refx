(ns refx.cofx
  (:require [refx.interceptor :refer [->interceptor]]
            [refx.log :as log]
            [refx.registry :as registry]
            [refx.store :refer [store]]))

(def kind :cofx)

(defn register
  [id handler]
  (registry/add! kind id handler))

;; -- Interceptor -------------------------------------------------------------

(defn inject-cofx
  ([id]
   (->interceptor
    :id      :coeffects
    :before  (fn coeffects-before
               [context]
               (if-let [handler (registry/lookup kind id)]
                 (update context :coeffects handler)
                 (log/error "No cofx handler registered for" id)))))
  ([id value]
   (->interceptor
    :id     :coeffects
    :before  (fn coeffects-before
               [context]
               (if-let [handler (registry/lookup kind id)]
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

