(ns csb.models.task-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.generators :as gen]
   [csb.models.task :as task]
   [csb.test-helpers :as th]
   [failjure.core :as f]
   [typed.clojure :as t]))

(use-fixtures :each th/with-test-db)

;; Type checking test
(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.models.task))))

;; Unit tests
(deftest create-test
  (testing "create inserts a task"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln (gen/create-test-plan th/*test-conn* (:db/id proj))
          params (gen/gen-task-params-with (:db/id pln)
                                           {:task/name "Test Task"
                                            :task/context "Do something"})
          result (task/create th/*test-conn* params)]
      (is (not (f/failed? result)))
      (let [all-tasks (task/get-all (th/test-db))]
        (is (= 1 (count all-tasks)))
        (let [created (first all-tasks)]
          (is (= "Test Task" (:task/name created)))
          (is (= "Do something" (:task/context created)))
          (is (= false (:task/completed created)))
          (is (some? (:task/id created)))
          (is (some? (:task/created-at created))))))))

(deftest create-with-parent-test
  (testing "create task with parent"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln (gen/create-test-plan th/*test-conn* (:db/id proj))
          parent (gen/create-test-task th/*test-conn* (:db/id pln))
          child (gen/create-test-task-with-parent th/*test-conn*
                                                  (:db/id pln)
                                                  (:db/id parent))
          children (task/get-children (th/test-db) (:db/id parent))]
      (is (= 1 (count children)))
      (is (= (:task/id child) (:task/id (first children)))))))

(deftest get-by-id-test
  (testing "get-by-id returns task when found"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln (gen/create-test-plan th/*test-conn* (:db/id proj))
          created (gen/create-test-task th/*test-conn* (:db/id pln))
          found (task/get-by-id (th/test-db) (:task/id created))]
      (is (some? found))
      (is (= (:task/name created) (:task/name found)))))
  (testing "get-by-id returns nil when not found"
    (let [found (task/get-by-id (th/test-db) (java.util.UUID/randomUUID))]
      (is (nil? found)))))

(deftest get-by-plan-test
  (testing "get-by-plan returns tasks for specific plan"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln1 (gen/create-test-plan th/*test-conn* (:db/id proj))
          pln2 (gen/create-test-plan th/*test-conn* (:db/id proj))
          _ (task/create th/*test-conn* (gen/gen-task-params-with (:db/id pln1) {:task/name "Task A"}))
          _ (task/create th/*test-conn* (gen/gen-task-params-with (:db/id pln1) {:task/name "Task B"}))
          _ (task/create th/*test-conn* (gen/gen-task-params-with (:db/id pln2) {:task/name "Task C"}))
          pln1-tasks (task/get-by-plan (th/test-db) (:db/id pln1))
          pln2-tasks (task/get-by-plan (th/test-db) (:db/id pln2))]
      (is (= 2 (count pln1-tasks)))
      (is (= #{"Task A" "Task B"} (set (map :task/name pln1-tasks))))
      (is (= 1 (count pln2-tasks)))
      (is (= #{"Task C"} (set (map :task/name pln2-tasks)))))))

(deftest get-root-tasks-test
  (testing "get-root-tasks returns only tasks without parents"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln (gen/create-test-plan th/*test-conn* (:db/id proj))
          _ (task/create th/*test-conn* (gen/gen-task-params-with (:db/id pln) {:task/name "Root 1"}))
          _ (task/create th/*test-conn* (gen/gen-task-params-with (:db/id pln) {:task/name "Root 2"}))
          root1 (first (filter #(= "Root 1" (:task/name %))
                               (task/get-all (th/test-db))))
          _ (task/create th/*test-conn* (gen/gen-task-params-with (:db/id pln)
                                                                  {:task/name "Child"
                                                                   :task/parent (:db/id root1)}))
          roots (task/get-root-tasks (th/test-db) (:db/id pln))]
      (is (= 2 (count roots)))
      (is (= #{"Root 1" "Root 2"} (set (map :task/name roots)))))))

(deftest get-incomplete-test
  (testing "get-incomplete returns only incomplete tasks"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln (gen/create-test-plan th/*test-conn* (:db/id proj))
          _ (task/create th/*test-conn* (gen/gen-task-params-with (:db/id pln) {:task/name "Incomplete"}))
          _ (task/create th/*test-conn* (gen/gen-task-params-with (:db/id pln)
                                                                  {:task/name "Complete"
                                                                   :task/completed true}))
          incomplete (task/get-incomplete (th/test-db) (:db/id pln))]
      (is (= 1 (count incomplete)))
      (is (= "Incomplete" (:task/name (first incomplete)))))))

(deftest get-completed-test
  (testing "get-completed returns only completed tasks"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln (gen/create-test-plan th/*test-conn* (:db/id proj))
          _ (task/create th/*test-conn* (gen/gen-task-params-with (:db/id pln) {:task/name "Incomplete"}))
          _ (task/create th/*test-conn* (gen/gen-task-params-with (:db/id pln)
                                                                  {:task/name "Complete"
                                                                   :task/completed true}))
          completed (task/get-completed (th/test-db) (:db/id pln))]
      (is (= 1 (count completed)))
      (is (= "Complete" (:task/name (first completed)))))))

(deftest mark-completed-test
  (testing "mark-completed sets task as completed"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln (gen/create-test-plan th/*test-conn* (:db/id proj))
          original (gen/create-test-task th/*test-conn* (:db/id pln))
          _ (task/mark-completed th/*test-conn* (:task/id original))
          updated (task/get-by-id (th/test-db) (:task/id original))]
      (is (= false (:task/completed original)))
      (is (= true (:task/completed updated)))))
  (testing "mark-completed fails for non-existent task"
    (let [result (task/mark-completed th/*test-conn* (java.util.UUID/randomUUID))]
      (is (f/failed? result)))))

(deftest update-task-test
  (testing "update-task modifies existing task"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln (gen/create-test-plan th/*test-conn* (:db/id proj))
          original (gen/create-test-task th/*test-conn* (:db/id pln))
          _ (task/update-task th/*test-conn*
                              (:task/id original)
                              {:task/context "updated context"})
          updated (task/get-by-id (th/test-db) (:task/id original))]
      (is (= "updated context" (:task/context updated)))
      (is (not= (:task/updated-at original)
                (:task/updated-at updated)))))
  (testing "update-task fails for non-existent task"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln (gen/create-test-plan th/*test-conn* (:db/id proj))
          result (task/update-task th/*test-conn*
                                   (java.util.UUID/randomUUID)
                                   {:task/name "Should Fail"
                                    :task/plan (:db/id pln)})]
      (is (f/failed? result)))))

(deftest delete-task-test
  (testing "delete-task removes task"
    (let [proj (gen/create-test-project th/*test-conn*)
          pln (gen/create-test-plan th/*test-conn* (:db/id proj))
          original (gen/create-test-task th/*test-conn* (:db/id pln))
          result (task/delete-task th/*test-conn* (:task/id original))]
      (is (not (f/failed? result)))
      (is (nil? (task/get-by-id (th/test-db) (:task/id original))))))
  (testing "delete-task fails for non-existent task"
    (let [result (task/delete-task th/*test-conn* (java.util.UUID/randomUUID))]
      (is (f/failed? result)))))
