(ns csb.main-test
  (:require
   [typed.clojure :as t]
   [csb.main]
   [clojure.test :refer [deftest is testing]]))

(deftest type-check
  (testing "checking-types-main"
    (is (t/check-ns-clj 'csb.main))))
