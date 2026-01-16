(ns csb.models.file-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.generators :as gen]
   [csb.models.file :as file]
   [csb.test-helpers :as th]
   [failjure.core :as f]
   [typed.clojure :as t]))

(use-fixtures :each th/with-test-db)

;; Type checking test
(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.models.file))))

;; Unit tests
(deftest create-test
  (testing "create inserts a file"
    (let [proj (gen/create-test-project th/*test-conn*)
          params (gen/gen-file-params-with (:db/id proj)
                                           {:file/path "src/core.clj"
                                            :file/summary "Core namespace"})
          result (file/create th/*test-conn* params)]
      (is (not (f/failed? result)))
      (let [all-files (file/get-all (th/test-db))]
        (is (= 1 (count all-files)))
        (let [created (first all-files)]
          (is (= "src/core.clj" (:file/path created)))
          (is (= "Core namespace" (:file/summary created)))
          (is (some? (:file/id created)))
          (is (some? (:file/created-at created))))))))

(deftest create-minimal-test
  (testing "create works with only required fields"
    (let [proj (gen/create-test-project th/*test-conn*)
          params (gen/gen-file-params-with (:db/id proj)
                                           {:file/path "src/minimal.clj"})
          result (file/create th/*test-conn* params)]
      (is (not (f/failed? result)))
      (let [all-files (file/get-all (th/test-db))]
        (is (= 1 (count all-files)))
        (is (= "src/minimal.clj" (:file/path (first all-files))))))))

(deftest get-by-id-test
  (testing "get-by-id returns file when found"
    (let [proj (gen/create-test-project th/*test-conn*)
          created (gen/create-test-file th/*test-conn* (:db/id proj))
          found (file/get-by-id (th/test-db) (:file/id created))]
      (is (some? found))
      (is (= (:file/path created) (:file/path found)))))
  (testing "get-by-id returns nil when not found"
    (let [found (file/get-by-id (th/test-db) (java.util.UUID/randomUUID))]
      (is (nil? found)))))

(deftest get-by-project-test
  (testing "get-by-project returns files for specific project"
    (let [proj1 (gen/create-test-project th/*test-conn*)
          proj2 (gen/create-test-project th/*test-conn*)
          _ (file/create th/*test-conn* (gen/gen-file-params-with (:db/id proj1) {:file/path "src/a.clj"}))
          _ (file/create th/*test-conn* (gen/gen-file-params-with (:db/id proj1) {:file/path "src/b.clj"}))
          _ (file/create th/*test-conn* (gen/gen-file-params-with (:db/id proj2) {:file/path "src/c.clj"}))
          proj1-files (file/get-by-project (th/test-db) (:db/id proj1))
          proj2-files (file/get-by-project (th/test-db) (:db/id proj2))]
      (is (= 2 (count proj1-files)))
      (is (= #{"src/a.clj" "src/b.clj"} (set (map :file/path proj1-files))))
      (is (= 1 (count proj2-files)))
      (is (= #{"src/c.clj"} (set (map :file/path proj2-files)))))))

(deftest get-by-path-test
  (testing "get-by-path returns file when found"
    (let [proj (gen/create-test-project th/*test-conn*)
          _ (file/create th/*test-conn* (gen/gen-file-params-with (:db/id proj) {:file/path "src/find-me.clj"}))
          found (file/get-by-path (th/test-db) (:db/id proj) "src/find-me.clj")]
      (is (some? found))
      (is (= "src/find-me.clj" (:file/path found)))))
  (testing "get-by-path returns nil when not found"
    (let [proj (gen/create-test-project th/*test-conn*)
          found (file/get-by-path (th/test-db) (:db/id proj) "nonexistent.clj")]
      (is (nil? found)))))

(deftest update-file-test
  (testing "update-file modifies existing file"
    (let [proj (gen/create-test-project th/*test-conn*)
          original (gen/create-test-file th/*test-conn* (:db/id proj))
          _ (file/update-file th/*test-conn*
                              (:file/id original)
                              {:file/summary "updated summary"})
          updated (file/get-by-id (th/test-db) (:file/id original))]
      (is (= "updated summary" (:file/summary updated)))
      (is (not= (:file/updated-at original)
                (:file/updated-at updated)))))
  (testing "update-file fails for non-existent file"
    (let [proj (gen/create-test-project th/*test-conn*)
          result (file/update-file th/*test-conn*
                                   (java.util.UUID/randomUUID)
                                   {:file/path "should-fail.clj"
                                    :file/project (:db/id proj)})]
      (is (f/failed? result)))))

(deftest delete-file-test
  (testing "delete-file removes file"
    (let [proj (gen/create-test-project th/*test-conn*)
          original (gen/create-test-file th/*test-conn* (:db/id proj))
          result (file/delete-file th/*test-conn* (:file/id original))]
      (is (not (f/failed? result)))
      (is (nil? (file/get-by-id (th/test-db) (:file/id original))))))
  (testing "delete-file fails for non-existent file"
    (let [result (file/delete-file th/*test-conn* (java.util.UUID/randomUUID))]
      (is (f/failed? result)))))
