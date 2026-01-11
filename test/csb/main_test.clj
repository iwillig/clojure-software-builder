(ns csb.main-test
  (:require [clojure.test :refer [deftest is testing]]))

(deftest okay?
  (testing "Context of the test assertions"
    (is (= {} #{}))))
