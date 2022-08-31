(ns top-demo.core
  (:require [top.alpha :as top]
            [uix.core.alpha :as uix]
            [uix.dom.alpha :as uix.dom]))

(top/reg-sub
 :counter
 (fn [db _]
   (:counter db 0)))

(top/reg-sub
 :countdown
 (top/<- [:counter])
 (fn [n _]
   (- 100 n)))

(top/reg-sub
 :strange-counter
 (top/<- [:counter] [:countdown])
 (fn [[n m] _]
   (if (odd? n) m n)))

(top/reg-sub
 :toggle
 (top/-> :toggle))

(top/reg-event-db
 :inc
 (fn [db _]
   (update db :counter inc)))

(top/reg-event-db
 :toggle
 (fn [db _]
   (update db :toggle not)))

(top/reg-sub
 :dynamic-target
 (top/<- [:toggle])
 (fn [on? _]
   (if on? :counter :countdown)))

(top/reg-sub
 :dynamic-data
 (fn [[_ id]]
   (top/sub [id]))
 (fn [v [_ id & extra]]
   {:id id
    :data v
    :extra extra}))

(top/reg-sub
 :dynamic
 (fn [_]
   (top/sub [:dynamic-data (top/sub [:dynamic-target]) :extra]))
 (fn [data _]
   (str data)))

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

(top/reg-event-db
 :text-change
 (fn [db [_ text]]
   (assoc db :text text)))

(top/reg-sub
 :text
 (top/-> :text))

(defn sandbox []
  [:div
   [:p "Here is a text field:"]
   [:input {:label "Sandbox"
            :value (or (top/subscribe [:text]) "")
            :on-change #(top/dispatch [:text-change (.. % -target -value)])}]
   [:p "And that's all, folks!"]]
  )

(defn dynamic []
  [:p "Dynamic:" [:b (top/subscribe [:dynamic])]])

(top/reg-event-db
 :super-toggle
 (fn [db _]
   (update db :super-toggle not)))

(top/reg-sub
 :super-toggle?
 (top/-> :super-toggle))

(defn app-main []
  [:<>
   [:h1 "Hello!"]
   [:button {:on-click #(top/dispatch [:toggle])} "Toggle!"]
   (when (top/subscribe [:toggle])
     [the-label])
   [the-button]
   [:hr]
   [dynamic]
   [:hr]
   [sandbox]])

(defn app []
  [:<>
   [:button {:on-click #(top/dispatch [:super-toggle])} "Super Toggle!"]
   (when (top/subscribe [:super-toggle?])
     [app-main])])

(defn render []
  (uix.dom/render (uix/strict-mode [app]) (.getElementById js/document "app")))

(defn ^:dev/after-load refresh! []
  (top/clear-subscription-cache!)
  (render))

(defn init []
  (render))
