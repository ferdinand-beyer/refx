(ns top.alpha
  (:require [top.store :refer [store]]
            [top.subs :as subs]))

(defn subscribe [query-v]
  (subs/subscribe query-v))

;; --- post -------------------------------------------------------------------

;; re-frame: dispatch event
;; redux: dispatch action
;; elm: messages

;; TODO
(defn post
  "Post a message."
  [msg]
  (case (first msg)
    :inc    (swap! store update :counter inc)
    :toggle (swap! store update :toggle not)))

(defn post-sync
  [msg])

;; --- message handlers -------------------------------------------------------

;; --- signals ----------------------------------------------------------------

;; TODO: Provide means to compose input functions?
(defn reg-sub
  ([query-id compute-fn]
   (reg-sub query-id (constantly store) compute-fn))
  ([query-id inputs-fn compute-fn]
   (subs/register query-id inputs-fn compute-fn)))
