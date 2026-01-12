(ns csb.routes-test
  (:require
   [typed.clojure :as t]
   [csb.routes]
   [clojure.test :refer [deftest is testing]]))

(deftest type-check
  (testing "checking-types-routes"
    (is (t/check-ns-clj 'csb.routes))))