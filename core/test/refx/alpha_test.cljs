(ns refx.alpha-test
  (:require [cljs.test :refer-macros [is deftest testing use-fixtures]]
            [refx.alpha :as rf]
            [refx.db :refer [app-db]]))

(defn wipe-app-state!
  "Clears all subscriptions in the registry together with subscription caches
   and app database."
  []
  (rf/clear-sub)
  (rf/clear-subscription-cache!)
  (reset! app-db {}))

(use-fixtures :each (fn [test-fn]
                      (test-fn)
                      (wipe-app-state!)))

(deftest syntax-sugar-text
  (let [store {:x 0
               :y 1
               :z 2}]
    (reset! app-db store)
    (testing "forward arrow syntax"
      (rf/reg-sub :x :-> :x)
      (rf/reg-sub :y :-> :y)
      (rf/reg-sub :z :-> :z)
      (doseq [sym (keys store)]
        (is (= (store sym)
               @(rf/sub [sym])))))
    (testing "reverse arrow syntax"
      ;; Note: the subscriptions for :x, :y, and :z are defined above.
      (rf/reg-sub
       :complex
       :<- [:x]
       :<- [:y]
       :<- [:z]
       :-> (partial reduce +))
      (let [expected (reduce + (vals store))
            actual   (rf/sub [:complex])]
        (is (= expected @actual))))
    (testing "explicit input function provided"
      (rf/reg-sub
       :complex-explicit
       (fn [_ _]
         [(rf/sub [:x])
          (rf/sub [:y])
          (rf/sub [:z])])
       (fn [[x y z] _]
         (+ x y z)))
      (let [expected (reduce + (vals store))
            actual   (rf/sub [:complex-explicit])]
        (is (= expected @actual))))
    (testing "special forward arrow syntax"
      (rf/reg-sub
       :sum
       :=> (fn [_ & qs]
             (reduce + qs)))
      (let [expected (+ 1 2 3)
            actual   (rf/sub [:sum 1 2 3])]
        (is (= expected @actual))))))
