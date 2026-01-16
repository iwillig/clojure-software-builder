(ns csb.models.project-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.models.project :as project]
   [csb.test-helpers :as th]
   [failjure.core :as f]
   [typed.clojure :as t]))

(use-fixtures :each th/with-test-db)

;; Type checking test
(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.models.project))))

;; Unit tests
(deftest create-test
  (testing "create inserts a project"
    (let [result (project/create th/*test-conn*
                                 {:project/name "test-project"
                                  :project/description "A test project"})]
      (is (not (f/failed? result)))
      (let [found (project/get-by-name (th/test-db) "test-project")]
        (is (some? found))
        (is (= "test-project" (:project/name found)))
        (is (= "A test project" (:project/description found)))
        (is (some? (:project/created-at found)))
        (is (some? (:project/updated-at found)))))))

(deftest create-minimal-test
  (testing "create works with only required fields"
    (let [result (project/create th/*test-conn*
                                 {:project/name "minimal-project"})]
      (is (not (f/failed? result)))
      (let [found (project/get-by-name (th/test-db) "minimal-project")]
        (is (some? found))
        (is (= "minimal-project" (:project/name found)))))))

(deftest get-by-name-test
  (testing "get-by-name returns project when found"
    (project/create th/*test-conn* {:project/name "findme"})
    (let [found (project/get-by-name (th/test-db) "findme")]
      (is (some? found))
      (is (= "findme" (:project/name found)))))
  (testing "get-by-name returns nil when not found"
    (let [found (project/get-by-name (th/test-db) "nonexistent")]
      (is (nil? found)))))

(deftest get-all-test
  (testing "get-all returns empty vector when no projects"
    (let [all (project/get-all (th/test-db))]
      (is (= [] all))))
  (testing "get-all returns all projects"
    (project/create th/*test-conn* {:project/name "project-1"})
    (project/create th/*test-conn* {:project/name "project-2"})
    (project/create th/*test-conn* {:project/name "project-3"})
    (let [all (project/get-all (th/test-db))]
      (is (= 3 (count all)))
      (is (= #{"project-1" "project-2" "project-3"}
             (set (map :project/name all)))))))

(deftest update-project-test
  (testing "update-project modifies existing project"
    (project/create th/*test-conn* {:project/name "to-update"
                                    :project/description "original"})
    (let [original (project/get-by-name (th/test-db) "to-update")
          _ (project/update-project th/*test-conn*
                                    (:db/id original)
                                    {:project/description "updated"})
          updated (project/get-by-name (th/test-db) "to-update")]
      (is (= "updated" (:project/description updated)))
      (is (not= (:project/updated-at original)
                (:project/updated-at updated))))))

(deftest delete-project-test
  (testing "delete-project removes project"
    (project/create th/*test-conn* {:project/name "to-delete"})
    (let [original (project/get-by-name (th/test-db) "to-delete")
          result (project/delete-project th/*test-conn* (:db/id original))]
      (is (not (f/failed? result)))
      (is (nil? (project/get-by-name (th/test-db) "to-delete"))))))
