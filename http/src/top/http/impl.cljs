(ns top.http.impl
  "Port of day8/re-frame-http-fx."
  (:require [ajax.simple :as simple]
            [ajax.xhrio]
            [top.alpha :refer [dispatch]])
  (:import [goog.net ErrorCode XhrIo]))

; see http://docs.closure-library.googlecode.com/git/class_goog_net_XhrIo.html
(defn- failure-details [^XhrIo xhrio response]
  (merge {:uri             (.getLastUri xhrio)
          :last-method     (.-lastMethod_ xhrio)
          :last-error      (.getLastError xhrio)
          :last-error-code (.getLastErrorCode xhrio)
          :debug-message   (-> xhrio .getLastErrorCode (ErrorCode/getDebugMessage))}
         response))

(defn- handle-response
  [xhrio on-success on-failure [success? response]]
  (if success?
    (dispatch (conj on-success response))
    (dispatch (conj on-failure (failure-details xhrio response)))))

(defn- prepare-request [{:keys [on-success on-failure]
                         :or   {on-success [:http-success]
                                on-failure [:http-failure]}
                         :as   request}]
  (let [xhrio (XhrIo.)]
    (-> request
        (assoc :api     xhrio
               :handler (partial handle-response xhrio on-success on-failure))
        (dissoc :on-success :on-failure :on-request))))

(defn- send-request! [transform-fn request]
  (let [xhrio (-> request prepare-request transform-fn simple/ajax-request)]
    (when-let [on-request (:on-request request)]
      (dispatch (conj on-request xhrio)))))

(defn make-http-effect
  "Makes an effect handler for HTTP requests cia `cljs-ajax`.  The optional
   `transform-fn` will be applied to the request opts map right before passing
   it to `ajax-request`.  This can for example be used to allow more convenient
   defaults, such as `ajax.easy/transform-opts` (see `top.http.easy`)."
  ([]
   (make-http-effect identity))
  ([transform-fn]
   (fn [request]
     (if (sequential? request)
       (doseq [r request]
         (send-request! transform-fn r))
       (send-request! transform-fn request)))))
