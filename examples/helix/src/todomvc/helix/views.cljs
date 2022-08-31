(ns todomvc.helix.views
  (:require [helix.core :refer [defnc $ <>]]
            [helix.hooks :refer [use-state]]
            [helix.dom :as d]
            [top.alpha :refer [subscribe dispatch]]
            [clojure.string :as str]))


(defnc todo-input [{:keys [title on-save on-stop] :as props}]
  (let [[val set-val!] (use-state title)
        stop #(do (set-val! "")
                  (when on-stop (on-stop)))
        save #(let [v (-> val str str/trim)]
                (on-save v)
                (stop))]
    (d/input {:type        "text"
              :value       (or val "")
              :auto-focus  true
              :on-blur     save
              :on-change   #(set-val! (-> % .-target .-value))
              :on-key-down #(case (.-which %)
                              13 (save)
                              27 (stop)
                              nil)
              :& (dissoc props :on-save :on-stop :title)})))


(defnc todo-item
  [{:keys [id done title]}]
  (let [[editing set-editing!] (use-state false)]
    (d/li {:class (str (when done "completed ")
                       (when editing "editing"))}
          (d/div {:class "view"}
                 (d/input {:class "toggle"
                           :type "checkbox"
                           :checked done
                           :on-change #(dispatch [:toggle-done id])})
                 (d/label {:on-double-click #(set-editing! true)}
                          title)
                 (d/button {:class "destroy"
                            :on-click #(dispatch [:delete-todo id])}))
          (when editing
            ($ todo-input
               {:class "edit"
                :title title
                :on-save #(if (seq %)
                            (dispatch [:save id %])
                            (dispatch [:delete-todo id]))
                :on-stop #(set-editing! false)})))))


(defnc task-list
  []
  (let [visible-todos (subscribe [:visible-todos])
        all-complete? (subscribe [:all-complete?])]
    (d/section {:id "main"}
               (d/input {:id "toggle-all"
                         :type "checkbox"
                         :checked all-complete?
                         :on-change #(dispatch [:complete-all-toggle])})
               (d/label {:for "toggle-all"}
                        "Mark all as complete")
               (d/ul {:id "todo-list"}
                     (for [todo  visible-todos]
                       ($ todo-item {:key (:id todo) :& todo}))))))


(defnc footer-controls
  []
  (let [[active done] (subscribe [:footer-counts])
        showing       (subscribe [:showing])
        a-fn          (fn [filter-kw txt]
                        (d/a {:class (when (= filter-kw showing) "selected")
                              :href (str "#/" (name filter-kw))} txt))]
    (d/footer {:id "footer"}
     (d/span {:id "todo-count"}
             (d/strong active) " " (case active 1 "item" "items") " left")
     (d/ul {:id "filters"}
      (d/li (a-fn :all    "All"))
      (d/li (a-fn :active "Active"))
      (d/li (a-fn :done   "Completed")))
     (when (pos? done)
       (d/button {:id "clear-completed"
                  :on-click #(dispatch [:clear-completed])}
         "Clear completed")))))


(defnc task-entry
  []
  (d/header {:id "header"}
            (d/h1 "todos")
            ($ todo-input
               {:id "new-todo"
                :placeholder "What needs to be done?"
                :on-save #(when (seq %)
                            (dispatch [:add-todo %]))})))


(defnc todo-app
  []
  (<>
   (d/section {:id "todoapp"}
              ($ task-entry)
              (when (seq (subscribe [:todos]))
                ($ task-list))
              ($ footer-controls))
   (d/footer {:id "info"}
             (d/p "Double-click to edit a todo"))))
