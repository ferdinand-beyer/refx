(ns refx.subs-test
  (:require [cljs.test :refer-macros [async is deftest use-fixtures]]
            [refx.registry :as registry]
            [refx.subs :as subs]))

(use-fixtures :each
  {:before (fn []
             (subs/clear-subscription-cache!)
             (registry/clear-all!))})

(deftest test-atom-signal
  (let [a (atom 0)]
    (is (subs/signal? a))
    (is (= 0 (subs/-value a)))
    (subs/-add-listener a :a #(is (= 1 (subs/-value a))))
    (swap! a inc)
    (subs/-add-listener a :b #(is (= 2 (subs/-value a))))
    (subs/-remove-listener a :a)
    (swap! a inc)))

(deftest test-register-fns
  (let [signal (atom 0)]
    (subs/register :test
                   (fn [query-v]
                     (is (= [:test 1 2 3] query-v))
                     signal)
                   (fn [value query-v]
                     (is (= [:test 1 2 3] query-v))
                     (is (= @signal value))
                     :result))
    (let [sub (subs/sub [:test 1 2 3])]
      (is (= :result (subs/-value sub)))
      (is (= :result @sub)))))

(deftest test-unknown-subscription
  (let [sub (subs/sub [:missing])]
    (is (subs/signal? sub))
    (subs/-add-listener sub :test #())
    (subs/-remove-listener sub :test)
    (is (nil? (subs/-value sub)))))

(deftest test-cache
  (let [signal (atom 0)]
    (subs/register :test (constantly signal) identity)
    (let [s1 (subs/sub [:test])
          s2 (subs/sub [:test])
          s3 (subs/sub [:test 2])
          s4 (subs/sub [:test 2])]
      (is (identical? s1 s2))
      (is (identical? s3 s4))
      (is (not (identical? s1 s3))))))

(deftest test-computed-signal
  (let [source (atom {:a 1, :b 2})]
    (subs/register :a (constantly source) :a)
    (subs/register :b (constantly source) :b)
    (subs/register :sum
                   (fn [_]
                     [(subs/sub [:a])
                      (subs/sub [:b])])
                   (fn [[a b] _]
                     (+ a b)))
    (let [sub (subs/sub [:sum])]
      (is (= 3 @sub))
      (async done
             (subs/-add-listener sub :test (fn []
                                             (is (= 4 @sub))
                                             (done)))
             (swap! source update :b inc)))))

(deftest test-dynamic-signal
  (let [source (atom {:a 1, :b 2, :k :a})]
    (subs/register :a (constantly source) :a)
    (subs/register :b (constantly source) :b)
    (subs/register :k (constantly source) :k)
    (subs/register :dynamic
                   (fn [[_ k]]
                     (subs/sub [k]))
                   (fn [v [_ k]]
                     [:dynamic k v]))
    (let [sub (subs/sub [:dynamic (subs/sub [:k])])]
      (is (= [:dynamic :a 1] @sub))
      (async done
             (subs/-add-listener sub :test (fn []
                                             (is (= [:dynamic :b 2] @sub))
                                             (done)))
             (swap! source assoc :k :b)))))

(deftest test-cache-removal
  (let [source (atom 0)]
    (subs/register :a (constantly source) identity)
    (subs/register :b (constantly (subs/sub [:a])) inc)
    (subs/register :c (constantly (subs/sub [:b])) inc)
    (subs/register :k (constantly nil) (constantly :c))
    (subs/register :dynamic (fn [[_ k]] (subs/sub [k])) inc)
    (let [k   (subs/sub [:k])
          sub (subs/sub [:dynamic k])]
      (is (= #{[:a] [:b] [:c] [:k] [:dynamic k]}
             (-> @subs/sub-cache keys set)))
      (subs/-add-listener sub :test #())
      (subs/-remove-listener sub :test)
      (is (empty? @subs/sub-cache)))))
