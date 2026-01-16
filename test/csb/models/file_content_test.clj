(ns csb.models.file-content-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.generators :as gen]
   [csb.models.file-content :as file-content]
   [csb.test-helpers :as th]
   [failjure.core :as f]
   [typed.clojure :as t])
  (:import
   (java.util
    Date
    UUID)))

(use-fixtures :each th/with-test-db)

;; =============================================================================
;; Type Checking
;; =============================================================================

(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.models.file-content))))

;; =============================================================================
;; Create Tests
;; =============================================================================

(deftest create-file-content-test
  (testing "creates file content with required fields"
    (let [proj (gen/create-test-project th/*test-conn*)
          file (gen/create-test-file th/*test-conn* (:db/id proj))
          params {:file-content/file (:db/id file)
                  :file-content/content "(ns core)"}
          result (file-content/create th/*test-conn* params)]
      (is (not (f/failed? result)))
      (let [contents (file-content/get-all (th/test-db))]
        (is (= 1 (count contents)))
        (let [fc (first contents)]
          (is (uuid? (:file-content/id fc)))
          (is (= "(ns core)" (:file-content/content fc)))
          (is (instance? Date (:file-content/created-at fc)))
          (is (instance? Date (:file-content/updated-at fc)))))))

  (testing "creates file content with optional fields"
    (let [proj (gen/create-test-project th/*test-conn*)
          file (gen/create-test-file th/*test-conn* (:db/id proj))
          params {:file-content/file (:db/id file)
                  :file-content/content "(ns core)"
                  :file-content/compact-ast "[[:ns [:symbol core]]]"
                  :file-content/parsed-at (Date.)}
          result (file-content/create th/*test-conn* params)]
      (is (not (f/failed? result)))
      (let [fc (first (file-content/get-all (th/test-db)))]
        (is (= "[[:ns [:symbol core]]]" (:file-content/compact-ast fc)))
        (is (instance? Date (:file-content/parsed-at fc))))))

  (testing "creates file content using generator"
    (let [proj (gen/create-test-project th/*test-conn*)
          file (gen/create-test-file th/*test-conn* (:db/id proj))
          fc (gen/create-test-file-content th/*test-conn* (:db/id file))]
      (is (some? fc))
      (is (uuid? (:file-content/id fc)))
      (is (string? (:file-content/content fc))))))

;; =============================================================================
;; Read Tests
;; =============================================================================

(deftest get-by-id-test
  (testing "returns file content by UUID"
    (let [proj (gen/create-test-project th/*test-conn*)
          file (gen/create-test-file th/*test-conn* (:db/id proj))
          fc (gen/create-test-file-content th/*test-conn* (:db/id file))
          found (file-content/get-by-id (th/test-db) (:file-content/id fc))]
      (is (= (:file-content/id fc) (:file-content/id found)))
      (is (= (:file-content/content fc) (:file-content/content found)))))

  (testing "returns nil for non-existent UUID"
    (let [found (file-content/get-by-id (th/test-db) (UUID/randomUUID))]
      (is (nil? found)))))

(deftest get-all-test
  (testing "returns empty vector when no file contents exist"
    (is (= [] (file-content/get-all (th/test-db)))))

  (testing "returns all file contents"
    (let [proj (gen/create-test-project th/*test-conn*)
          file (gen/create-test-file th/*test-conn* (:db/id proj))]
      (gen/create-test-file-content th/*test-conn* (:db/id file))
      (gen/create-test-file-content th/*test-conn* (:db/id file))
      (gen/create-test-file-content th/*test-conn* (:db/id file))
      (is (= 3 (count (file-content/get-all (th/test-db))))))))

(deftest get-by-file-test
  (testing "returns file contents for a specific file"
    (let [proj (gen/create-test-project th/*test-conn*)
          file1 (gen/create-test-file th/*test-conn* (:db/id proj))
          file2 (gen/create-test-file th/*test-conn* (:db/id proj))]
      ;; Create 2 contents for file1
      (gen/create-test-file-content th/*test-conn* (:db/id file1))
      (gen/create-test-file-content th/*test-conn* (:db/id file1))
      ;; Create 1 content for file2
      (gen/create-test-file-content th/*test-conn* (:db/id file2))
      (is (= 2 (count (file-content/get-by-file (th/test-db) (:db/id file1)))))
      (is (= 1 (count (file-content/get-by-file (th/test-db) (:db/id file2)))))))

  (testing "returns empty vector for file with no contents"
    (let [proj (gen/create-test-project th/*test-conn*)
          file (gen/create-test-file th/*test-conn* (:db/id proj))]
      (is (= [] (file-content/get-by-file (th/test-db) (:db/id file)))))))

(deftest get-latest-by-file-test
  (testing "returns most recent file content"
    (let [proj (gen/create-test-project th/*test-conn*)
          file (gen/create-test-file th/*test-conn* (:db/id proj))]
      ;; Create multiple contents with delay to ensure different timestamps
      (file-content/create th/*test-conn*
                           {:file-content/file (:db/id file)
                            :file-content/content "version 1"})
      (Thread/sleep 10)
      (file-content/create th/*test-conn*
                           {:file-content/file (:db/id file)
                            :file-content/content "version 2"})
      (Thread/sleep 10)
      (file-content/create th/*test-conn*
                           {:file-content/file (:db/id file)
                            :file-content/content "version 3"})
      (let [latest (file-content/get-latest-by-file (th/test-db) (:db/id file))]
        (is (= "version 3" (:file-content/content latest))))))

  (testing "returns nil for file with no contents"
    (let [proj (gen/create-test-project th/*test-conn*)
          file (gen/create-test-file th/*test-conn* (:db/id proj))]
      (is (nil? (file-content/get-latest-by-file (th/test-db) (:db/id file)))))))

;; =============================================================================
;; Update Tests
;; =============================================================================

(deftest update-file-content-test
  (testing "updates file content fields"
    (let [proj (gen/create-test-project th/*test-conn*)
          file (gen/create-test-file th/*test-conn* (:db/id proj))
          fc (gen/create-test-file-content th/*test-conn* (:db/id file))
          original-updated-at (:file-content/updated-at fc)]
      (Thread/sleep 10)
      (let [result (file-content/update-file-content
                    th/*test-conn*
                    (:file-content/id fc)
                    {:file-content/file (:db/id file)
                     :file-content/content "updated content"
                     :file-content/compact-ast "[[:updated]]"})]
        (is (not (f/failed? result)))
        (let [updated (file-content/get-by-id (th/test-db) (:file-content/id fc))]
          (is (= "updated content" (:file-content/content updated)))
          (is (= "[[:updated]]" (:file-content/compact-ast updated)))
          (is (.after (:file-content/updated-at updated) original-updated-at))))))

  (testing "fails for non-existent file content"
    (let [result (file-content/update-file-content
                  th/*test-conn*
                  (UUID/randomUUID)
                  {:file-content/file 1
                   :file-content/content "test"})]
      (is (f/failed? result)))))

;; =============================================================================
;; Delete Tests
;; =============================================================================

(deftest delete-file-content-test
  (testing "deletes existing file content"
    (let [proj (gen/create-test-project th/*test-conn*)
          file (gen/create-test-file th/*test-conn* (:db/id proj))
          fc (gen/create-test-file-content th/*test-conn* (:db/id file))
          id (:file-content/id fc)]
      (is (some? (file-content/get-by-id (th/test-db) id)))
      (let [result (file-content/delete-file-content th/*test-conn* id)]
        (is (not (f/failed? result)))
        (is (nil? (file-content/get-by-id (th/test-db) id))))))

  (testing "fails for non-existent file content"
    (let [result (file-content/delete-file-content th/*test-conn* (UUID/randomUUID))]
      (is (f/failed? result)))))
