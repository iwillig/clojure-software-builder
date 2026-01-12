(ns csb.system-test
  (:require
   [typed.clojure :as t]
   [csb.system]
   [clojure.test :refer [deftest is testing]]))

(deftest type-check
  (testing "checking-types-system"
    (is (t/check-ns-clj 'csb.system))))