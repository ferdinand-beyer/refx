(ns top-demo.core
  (:require [uix.core.alpha :as uix]
            [uix.dom.alpha :as uix.dom]))

(defn button [{:keys [on-click]} text]
  [:button.btn {:on-click on-click}
   text])

(defn app []
  (let [state* (uix/state 0)]
    [:<>
     [button {:on-click #(swap! state* dec)} "-"]
     [:span @state*]
     [button {:on-click #(swap! state* inc)} "+"]]))

(defn render []
  (uix.dom/render [app] (.getElementById js/document "app")))

(defn ^:dev/after-load refresh! []
  (render))

(defn init []
  (render))
