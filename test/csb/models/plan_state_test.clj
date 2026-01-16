(ns csb.models.plan-state-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.models.plan-state :as plan-state]
   [csb.test-helpers :as th]
   [failjure.core :as f]
   [typed.clojure :as t]))

(use-fixtures :each th/with-test-db)

;; Type checking test
(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.models.plan-state))))

;; Unit tests
(deftest seed-test
  (testing "seed inserts all predefined states"
    (let [result (plan-state/seed th/*test-conn*)]
      (is (not (f/failed? result)))
      (let [all-states (plan-state/get-all (th/test-db))]
        (is (= 4 (count all-states)))
        (is (= #{"draft" "active" "completed" "cancelled"}
               (set (map :plan-state/id all-states))))))))

(deftest seed-idempotent-test
  (testing "seed is idempotent due to unique constraint"
    (plan-state/seed th/*test-conn*)
    (plan-state/seed th/*test-conn*)
    (let [all-states (plan-state/get-all (th/test-db))]
      (is (= 4 (count all-states))))))

(deftest get-by-id-test
  (testing "get-by-id returns correct state"
    (plan-state/seed th/*test-conn*)
    (let [draft (plan-state/get-by-id (th/test-db) "draft")]
      (is (some? draft))
      (is (= "draft" (:plan-state/id draft)))))
  (testing "get-by-id returns nil for unknown state"
    (let [unknown (plan-state/get-by-id (th/test-db) "unknown")]
      (is (nil? unknown)))))

(deftest states-constant-test
  (testing "states constant contains expected values"
    (is (= ["draft" "active" "completed" "cancelled"]
           plan-state/states))))
