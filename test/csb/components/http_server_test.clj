(ns csb.components.http-server-test
  (:require
   [typed.clojure :as t]
   [csb.components.http-server]
   [clojure.test :refer [deftest is testing]]))

(deftest type-check
  (testing "checking-types-http-server"
    (is (t/check-ns-clj 'csb.components.http-server))))