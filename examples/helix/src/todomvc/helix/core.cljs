(ns todomvc.helix.core
  (:require ["react" :as react]
            ["react-dom" :as react-dom]
            [helix.core :refer [$]]
            [todomvc.core]
            [todomvc.helix.views :as views]
            [refx.alpha :as refx]))

;; -- Entry Point -------------------------------------------------------------

(defn render
  []
  ;; Render the UI into the HTML's <div id="app" /> element
  ;; The view function `todomvc.views/todo-app` is the
  ;; root view for the entire UI.

  (react-dom/render ($ react/StrictMode ($ views/todo-app))
                    (.getElementById js/document "app")))

(defn ^:dev/after-load clear-cache-and-render!
  []
  ;; The `:dev/after-load` metadata causes this function to be called
  ;; after shadow-cljs hot-reloads code. We force a UI update by clearing
  ;; the Reframe subscription cache.
  (refx/clear-subscription-cache!)
  (render))

(defn ^:export init
  []
  (render))
