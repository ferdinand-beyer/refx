(ns todomvc.uix.views
  (:require [uix.core.alpha :as uix]
            [refx.alpha :refer [use-sub dispatch]]
            [clojure.string :as str]))


(defn todo-input [{:keys [title on-save on-stop] :as props}]
  (let [val  (uix/state title)
        stop #(do (reset! val "")
                  (when on-stop (on-stop)))
        save #(let [v (-> @val str str/trim)]
                (on-save v)
                (stop))]
    [:input (merge (dissoc props :on-save :on-stop :title)
                   {:type        "text"
                    :value       (or @val "")
                    :auto-focus  true
                    :on-blur     save
                    :on-change   #(reset! val (-> % .-target .-value))
                    :on-key-down #(case (.-which %)
                                    13 (save)
                                    27 (stop)
                                    nil)})]))


(defn todo-item
  [{:keys [id done title]}]
  (let [editing (uix/state false)]
    [:li {:class (str (when done "completed ")
                      (when @editing "editing"))}
     [:div.view
      [:input.toggle
       {:type "checkbox"
        :checked done
        :on-change #(dispatch [:toggle-done id])}]
      [:label
       {:on-double-click #(reset! editing true)}
       title]
      [:button.destroy
       {:on-click #(dispatch [:delete-todo id])}]]
     (when @editing
       [todo-input
        {:class "edit"
         :title title
         :on-save #(if (seq %)
                     (dispatch [:save id %])
                     (dispatch [:delete-todo id]))
         :on-stop #(reset! editing false)}])]))


(defn task-list
  []
  (let [visible-todos (use-sub [:visible-todos])
        all-complete? (use-sub [:all-complete?])]
      [:section#main
        [:input#toggle-all
          {:type "checkbox"
           :checked all-complete?
           :on-change #(dispatch [:complete-all-toggle])}]
        [:label
          {:for "toggle-all"}
          "Mark all as complete"]
        [:ul#todo-list
          (for [todo  visible-todos]
            ^{:key (:id todo)} [todo-item todo])]]))


(defn footer-controls
  []
  (let [[active done] (use-sub [:footer-counts])
        showing       (use-sub [:showing])
        a-fn          (fn [filter-kw txt]
                        [:a {:class (when (= filter-kw showing) "selected")
                             :href (str "#/" (name filter-kw))} txt])]
    [:footer#footer
     [:span#todo-count
      [:strong active] " " (case active 1 "item" "items") " left"]
     [:ul#filters
      [:li (a-fn :all    "All")]
      [:li (a-fn :active "Active")]
      [:li (a-fn :done   "Completed")]]
     (when (pos? done)
       [:button#clear-completed {:on-click #(dispatch [:clear-completed])}
        "Clear completed"])]))


(defn task-entry
  []
  [:header#header
    [:h1 "todos"]
    [todo-input
      {:id "new-todo"
       :placeholder "What needs to be done?"
       :on-save #(when (seq %)
                    (dispatch [:add-todo %]))}]])


(defn todo-app
  []
  [:<>
   [:section#todoapp
    [task-entry]
    (when (seq (use-sub [:todos]))
      [task-list])
    [footer-controls]]
   [:footer#info
    [:p "Double-click to edit a todo"]]])
