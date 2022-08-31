(ns todomvc.core
  (:require-macros [secretary.core :refer [defroute]])
  (:require [goog.events :as events]
            [secretary.core :as secretary]
            [todomvc.events] ;; These two are only required to make the compiler
            [todomvc.subs] ;; load them (see docs/App-Structure.md)
            [top.alpha :as top :refer [dispatch dispatch-sync]])
  (:import [goog History]
           [goog.history EventType]))


;; -- Debugging aids ----------------------------------------------------------
(enable-console-print!)   ;; so that println writes to `console.log`


;; Put an initial value into app-db.
;; The event handler for `:initialise-db` can be found in `events.cljs`
;; Using the sync version of dispatch means that value is in
;; place before we go onto the next step.
(dispatch-sync [:initialise-db])

;; -- Routes and History ------------------------------------------------------
;; Although we use the secretary library below, that's mostly a historical
;; accident. You might also consider using:
;;   - https://github.com/DomKM/silk
;;   - https://github.com/juxt/bidi
;; We don't have a strong opinion.
;;
(defroute "/" [] (dispatch [:set-showing :all]))
(defroute "/:filter" [filter] (dispatch [:set-showing (keyword filter)]))

(defonce history
  (doto (History.)
    (events/listen EventType.NAVIGATE
                   (fn [^js/goog.History.Event event] (secretary/dispatch! (.-token event))))
    (.setEnabled true)))
