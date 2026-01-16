(ns csb.models.plan-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.models.plan :as plan]
   [csb.models.plan-state :as plan-state]
   [csb.models.project :as project]
   [csb.test-helpers :as th]
   [failjure.core :as f]
   [typed.clojure :as t]))

(use-fixtures :each th/with-test-db)

;; Helper to create a test project
(defn- create-test-project []
  (project/create th/*test-conn* {:project/name (str "test-project-" (random-uuid))})
  (first (project/get-all (th/test-db))))

;; Type checking test
(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.models.plan))))

;; Unit tests
(deftest create-test
  (testing "create inserts a plan"
    (let [proj (create-test-project)
          result (plan/create th/*test-conn*
                              {:plan/name "Test Plan"
                               :plan/project (:db/id proj)
                               :plan/context "Build something"})]
      (is (not (f/failed? result)))
      (let [all-plans (plan/get-all (th/test-db))]
        (is (= 1 (count all-plans)))
        (let [created (first all-plans)
              ;; References are returned as maps with :db/id
              project-ref (:plan/project created)]
          (is (= "Test Plan" (:plan/name created)))
          (is (= (:db/id proj) (:db/id project-ref)))
          (is (= "Build something" (:plan/context created)))
          (is (some? (:plan/id created)))
          (is (some? (:plan/created-at created)))
          (is (some? (:plan/updated-at created))))))))

(deftest create-minimal-test
  (testing "create works with only required fields"
    (let [proj (create-test-project)
          result (plan/create th/*test-conn*
                              {:plan/name "Minimal Plan"
                               :plan/project (:db/id proj)})]
      (is (not (f/failed? result)))
      (let [all-plans (plan/get-all (th/test-db))]
        (is (= 1 (count all-plans)))
        (is (= "Minimal Plan" (:plan/name (first all-plans))))))))

(deftest get-by-id-test
  (testing "get-by-id returns plan when found"
    (let [proj (create-test-project)]
      (plan/create th/*test-conn* {:plan/name "Find Me"
                                   :plan/project (:db/id proj)})
      (let [all-plans (plan/get-all (th/test-db))
            plan-id (:plan/id (first all-plans))
            found (plan/get-by-id (th/test-db) plan-id)]
        (is (some? found))
        (is (= "Find Me" (:plan/name found))))))
  (testing "get-by-id returns nil when not found"
    (let [found (plan/get-by-id (th/test-db) (java.util.UUID/randomUUID))]
      (is (nil? found)))))

(deftest get-all-test
  (testing "get-all returns empty vector when no plans"
    (let [all (plan/get-all (th/test-db))]
      (is (= [] all))))
  (testing "get-all returns all plans"
    (let [proj (create-test-project)]
      (plan/create th/*test-conn* {:plan/name "Plan 1" :plan/project (:db/id proj)})
      (plan/create th/*test-conn* {:plan/name "Plan 2" :plan/project (:db/id proj)})
      (plan/create th/*test-conn* {:plan/name "Plan 3" :plan/project (:db/id proj)})
      (let [all (plan/get-all (th/test-db))]
        (is (= 3 (count all)))
        (is (= #{"Plan 1" "Plan 2" "Plan 3"}
               (set (map :plan/name all))))))))

(deftest get-by-project-test
  (testing "get-by-project returns plans for specific project"
    (let [proj1 (create-test-project)
          proj2 (create-test-project)]
      (plan/create th/*test-conn* {:plan/name "Plan A" :plan/project (:db/id proj1)})
      (plan/create th/*test-conn* {:plan/name "Plan B" :plan/project (:db/id proj1)})
      (plan/create th/*test-conn* {:plan/name "Plan C" :plan/project (:db/id proj2)})
      (let [proj1-plans (plan/get-by-project (th/test-db) (:db/id proj1))
            proj2-plans (plan/get-by-project (th/test-db) (:db/id proj2))]
        (is (= 2 (count proj1-plans)))
        (is (= #{"Plan A" "Plan B"} (set (map :plan/name proj1-plans))))
        (is (= 1 (count proj2-plans)))
        (is (= #{"Plan C"} (set (map :plan/name proj2-plans))))))))

(deftest get-by-state-test
  (testing "get-by-state returns plans with specific state"
    (plan-state/seed th/*test-conn*)
    (let [proj (create-test-project)
          draft-state (plan-state/get-by-id (th/test-db) "draft")
          active-state (plan-state/get-by-id (th/test-db) "active")]
      (plan/create th/*test-conn* {:plan/name "Draft Plan"
                                   :plan/project (:db/id proj)
                                   :plan/state (:db/id draft-state)})
      (plan/create th/*test-conn* {:plan/name "Active Plan"
                                   :plan/project (:db/id proj)
                                   :plan/state (:db/id active-state)})
      (let [draft-plans (plan/get-by-state (th/test-db) "draft")
            active-plans (plan/get-by-state (th/test-db) "active")]
        (is (= 1 (count draft-plans)))
        (is (= "Draft Plan" (:plan/name (first draft-plans))))
        (is (= 1 (count active-plans)))
        (is (= "Active Plan" (:plan/name (first active-plans))))))))

(deftest update-plan-test
  (testing "update-plan modifies existing plan"
    (let [proj (create-test-project)]
      (plan/create th/*test-conn* {:plan/name "Original"
                                   :plan/project (:db/id proj)
                                   :plan/context "original context"})
      (let [original (first (plan/get-all (th/test-db)))
            _ (plan/update-plan th/*test-conn*
                                (:plan/id original)
                                {:plan/context "updated context"})
            updated (plan/get-by-id (th/test-db) (:plan/id original))]
        (is (= "updated context" (:plan/context updated)))
        (is (not= (:plan/updated-at original)
                  (:plan/updated-at updated))))))
  (testing "update-plan fails for non-existent plan"
    (let [proj (create-test-project)
          result (plan/update-plan th/*test-conn*
                                   (java.util.UUID/randomUUID)
                                   {:plan/name "Should Fail"
                                    :plan/project (:db/id proj)})]
      (is (f/failed? result)))))

(deftest delete-plan-test
  (testing "delete-plan removes plan"
    (let [proj (create-test-project)]
      (plan/create th/*test-conn* {:plan/name "To Delete"
                                   :plan/project (:db/id proj)})
      (let [original (first (plan/get-all (th/test-db)))
            result (plan/delete-plan th/*test-conn* (:plan/id original))]
        (is (not (f/failed? result)))
        (is (nil? (plan/get-by-id (th/test-db) (:plan/id original)))))))
  (testing "delete-plan fails for non-existent plan"
    (let [result (plan/delete-plan th/*test-conn* (java.util.UUID/randomUUID))]
      (is (f/failed? result)))))
