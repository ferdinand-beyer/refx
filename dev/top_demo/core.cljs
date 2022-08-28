(ns top-demo.core
  (:require [uix.core.alpha :as uix]
            [uix.dom.alpha :as uix.dom]
            [top.alpha :as top]))

(def n "???")

(defn the-label []
  (let [n (top/subscribe [:counter])
        ;
        ]
    [:p "You clicked " n " times."]))

(defn the-button []
  (let [n (top/subscribe [:counter])
        ;
        ]
    [:button {:on-click #(top/post [:inc])}
     "Click #" n]))

(defn app []
  [:<>
   [:h1 "Hello!"]
   [:button {:on-click #(top/post [:toggle])} "Toggle!"]
   (when (top/subscribe [:toggle])
     [the-label])
   [the-button]])

(defn render []
  (uix.dom/render (uix/strict-mode [app]) (.getElementById js/document "app")))

(defn ^:dev/after-load refresh! []
  (render))

(defn init []
  (render))
