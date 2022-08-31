(ns top.interceptors
  (:require [clojure.data :as data]
            [top.interceptor :refer [->interceptor assoc-coeffect assoc-effect
                                     get-coeffect get-effect update-coeffect]]
            [top.log :as log]))

(def debug
  (->interceptor
   :id     :debug
   :before (fn debug-before
             [context]
             (log/info "Handling event:" (get-coeffect context :event))
             context)
   :after  (fn debug-after
             [context]
             (let [event   (get-coeffect context :event)
                   orig-db (get-coeffect context :db)
                   new-db  (get-effect   context :db ::not-found)]
               (if (= new-db ::not-found)
                 (log/info "No app-db changes in:" event)
                 (let [[only-before only-after] (data/diff orig-db new-db)
                       db-changed?    (or (some? only-before) (some? only-after))]
                   (if db-changed?
                     (do (log/info "only before:" only-before)
                         (log/info "only after :" only-after))
                     (log/info "No app-db changes resulted from:" event))))
               context))))

(def unwrap
  (->interceptor
   :id      :unwrap
   :before  (fn unwrap-before
              [context]
              (let [[_ payload :as event] (get-coeffect context :event)]
                (if-not (and (= 2 (count event))
                             (map? payload))
                  (do
                    (log/warn "\"unwrap\" interceptor requires event to be a 2-vector of [event-id payload-map]. Got " event)
                    context)
                  (assoc-coeffect context :event payload))))
   :after   (fn unwrap-after
              [context]
              (assoc-coeffect context :event (get-coeffect context :original-event)))))

(def trim-v
  (->interceptor
   :id      :trim-v
   :before  (fn trim-v-before
              [context]
              (if-not (vector? (get-coeffect context :event))
                (do
                  (log/warn "\"trim-v\" interceptor expected event to be a vector. Got a " (type (get-coeffect context :event)))
                  context)
                (update-coeffect context :event subvec 1)))
   :after   (fn trim-v-after
              [context]
              (assoc-coeffect context :event (get-coeffect context :original-event)))))

(defn path
  [& args]
  (let [path (flatten args)]
    (when (empty? path)
      (log/error "\"path\" interceptor given no params"))
    (->interceptor
     :id      :path
     :before  (fn
                [context]
                (let [original-db (get-coeffect context :db)]
                  (-> context
                      (update ::orig-db conj original-db)
                      (assoc-coeffect :db (get-in original-db path)))))
     :after   (fn [context]
                (let [db-store     (::orig-db context)
                      original-db  (peek db-store)
                      new-db-store (pop db-store)
                      context'     (-> (assoc context ::orig-db new-db-store)
                                       (assoc-coeffect :db original-db))     ;; put the original db back so that things like debug work later on
                      db           (get-effect context :db ::not-found)]
                  (if (= db ::not-found)
                    context'
                    (->> (assoc-in original-db path db)
                         (assoc-effect context' :db))))))))

(defn enrich
  [f]
  (->interceptor
   :id :enrich
   :after (fn enrich-after
            [context]
            (let [event   (get-coeffect context :event)
                  prev-db (if (contains? (get-effect context) :db)
                            (get-effect context :db) ;; If no db effect is returned, we provide the original coeffect.
                            (get-coeffect context :db))
                  new-db  (f prev-db event)]
              (assoc-effect context :db (or new-db prev-db)))))) ;; If the enriched db is nil, use the last known good db

(defn after
  [f]
  (->interceptor
   :id :after
   :after (fn after-after
            [context]
            (let [db    (if (contains? (get-effect context) :db)
                          (get-effect context :db)
                          (get-coeffect context :db))
                  event (get-coeffect context :event)]
              (f db event) ;; call f for side effects
              context)))) ;; context is unchanged

(defn  on-changes
  [f out-path & in-paths]
  (->interceptor
   :id    :on-changes
   :after (fn on-change-after
            [context]
            (let [new-db   (get-effect context :db)
                  old-db   (get-coeffect context :db)

                   ;; work out if any "inputs" have changed
                  new-ins      (map #(get-in new-db %) in-paths)
                  old-ins      (map #(get-in old-db %) in-paths)
                   ;; make sure the db is actually set in the effect
                  changed-ins? (and (contains? (get-effect context) :db)
                                    (some false? (map identical? new-ins old-ins)))]

               ;; if one of the inputs has changed, then run 'f'
              (if changed-ins?
                (->> (apply f new-ins)
                     (assoc-in new-db out-path)
                     (assoc-effect context :db))
                context)))))
