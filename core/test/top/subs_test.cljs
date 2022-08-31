(ns top.subs-test
  (:require [cljs.test :refer-macros [is deftest testing use-fixtures]]
            [top.subs :as subs]))

(use-fixtures :each
  {:before subs/unregister})

(deftest test-atom-signal
  (let [a (atom 0)]
    (is (= 0 (subs/-value a)))))
