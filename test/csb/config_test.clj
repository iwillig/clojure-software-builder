(ns csb.config-test
  (:require
   [typed.clojure :as t]
   [csb.config]
   [clojure.test :refer [deftest is testing]]))

(deftest type-check
  (testing "checking-types-config"
    (is (t/check-ns-clj 'csb.config))))