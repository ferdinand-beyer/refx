(ns refx.interop
  (:require [goog.async.nextTick]))

(def empty-queue #queue [])

(def next-tick goog.async.nextTick)

(def after-render next-tick)  ;; is there an equivalent in plain React?

;; Make sure the Google Closure compiler sees this as a boolean constant,
;; otherwise Dead Code Elimination won't happen in `:advanced` builds.
;; Type hints have been liberally sprinkled.
;; https://developers.google.com/closure/compiler/docs/js-for-compiler
(def ^boolean debug-enabled? "@define {boolean}" ^boolean goog/DEBUG)

(defn set-timeout! [f ms]
  (js/setTimeout f ms))

(def loggers
  {:debug (.bind js/console.debug js/console)
   :info  (.bind js/console.log js/console)
   :warn  (.bind js/console.warn js/console)
   :error (.bind js/console.error js/console)})

(defn log [level args]
  (apply (loggers level) args))
