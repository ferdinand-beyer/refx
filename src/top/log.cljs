(ns top.log)

(def loggers
  {:debug (.bind js/console.debug js/console)
   :log   (.bind js/console.log js/console)
   :warn  (.bind js/console.warn js/console)
   :error (.bind js/console.error js/console)})

(defn debug [& args]
  (apply (:debug loggers) "top:" args))

(defn warn [& args]
  (apply (:warn loggers) "top:" args))

(defn error [& args]
  (apply (:error loggers) "top:" args))
