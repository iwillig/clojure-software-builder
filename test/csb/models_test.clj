(ns csb.models-test
  (:require
   [typed.clojure :as t]
   [csb.models]
   [clojure.test :refer [deftest is testing]]))

(deftest type-check
  (testing "checking-types-models"
    (is (t/check-ns-clj 'csb.models))))