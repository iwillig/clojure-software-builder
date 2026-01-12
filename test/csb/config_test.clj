(ns csb.config-test
  (:require
   [csb.config]
   [clojure.test :refer [deftest is testing]]))

(deftest config-loaded-correctly
  (testing "config loads correctly"
    (let [config (csb.config/load-config)]
      (is (map? config))
      (is (= 3000 (:port config)))
      (is (= "db-path" (:db-path config))))))