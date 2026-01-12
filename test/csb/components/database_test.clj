(ns csb.components.database-test
  (:require
   [typed.clojure :as t]
   [csb.components.database]
   [clojure.test :refer [deftest is testing]]))

(deftest type-check
  (testing "checking-types-database"
    (is (t/check-ns-clj 'csb.components.database))))