(ns top-demo.core
  (:require [uix.core.alpha :as uix]
            [uix.dom.alpha :as uix.dom]
            [top.alpha :as top]))

(top/reg-sub
 :counter
 (fn [db _]
   (:counter db 0)))

(top/reg-sub
 :strange-counter
 (fn [] {:n (top/sub [:counter])})
 (fn [{:keys [n]} _]
   (- 100 n)))

(top/reg-sub
 :toggle
 (fn [db _]
   (:toggle db)))

(top/reg-event-db
 :inc
 (fn [db _]
   (update db :counter inc)))

(top/reg-event-db
 :toggle
 (fn [db _]
   (update db :toggle not)))

;; ----------------------------------------------------------------------------

(def n "???")

(defn the-label []
  (let [n (top/subscribe [:strange-counter])
        ;
        ]
    [:p "You clicked " n " times."]))

(defn the-button []
  (let [n (top/subscribe [:counter])
        ;
        ]
    [:button {:on-click #(top/dispatch [:inc])}
     "Click #" n]))

(defn app []
  [:<>
   [:h1 "Hello!"]
   [:button {:on-click #(top/dispatch [:toggle])} "Toggle!"]
   (when (top/subscribe [:toggle])
     [the-label])
   [the-button]])

(defn render []
  (uix.dom/render (uix/strict-mode [app]) (.getElementById js/document "app")))

(defn ^:dev/after-load refresh! []
  (render))

(defn init []
  (render))
