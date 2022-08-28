(ns top.alpha
  (:require [uix.core.alpha :as uix]
            [top.store :refer [store]]
            [top.signal :as signal]))

(defn subscribe [query-v]
  (signal/subscribe query-v))

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

;; re-frame: subscription
;; Generalized: Some value that changes over time
;; Atoms are signals
;; Store/App DB: Just an atom
;; Other signals: JavaScript intervals, ...
;; Derived: Memoized version of input signals
;; Reactive: Can be used with hooks in react, to update the UI when they change
;; Context: Can read signals


