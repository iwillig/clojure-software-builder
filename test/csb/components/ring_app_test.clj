(ns csb.components.ring-app-test
  (:require
   [typed.clojure :as t]
   [csb.components.ring-app]
   [clojure.test :refer [deftest is testing]]))

(deftest type-check
  (testing "checking-types-ring-app"
    (is (t/check-ns-clj 'csb.components.ring-app))))