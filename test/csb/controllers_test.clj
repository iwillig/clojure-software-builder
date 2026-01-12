(ns csb.controllers-test
  (:require
   [typed.clojure :as t]
   [csb.controllers]
   [clojure.test :refer [deftest is testing]]))

(deftest type-check
  (testing "checking-types-controllers"
    (is (t/check-ns-clj 'csb.controllers))))