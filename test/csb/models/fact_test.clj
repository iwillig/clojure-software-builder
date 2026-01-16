(ns csb.models.fact-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.models.fact :as fact]
   [csb.test-helpers :as th]
   [failjure.core :as f]
   [typed.clojure :as t]))

(use-fixtures :each th/with-test-db)

;; Type checking test
(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.models.fact))))

;; Unit tests
(deftest create-test
  (testing "create inserts a fact"
    (let [result (fact/create th/*test-conn*
                              {:fact/name "Test Fact"
                               :fact/description "A test fact"})]
      (is (not (f/failed? result)))
      (let [all-facts (fact/get-all (th/test-db))]
        (is (= 1 (count all-facts)))
        (let [created (first all-facts)]
          (is (= "Test Fact" (:fact/name created)))
          (is (= "A test fact" (:fact/description created)))
          (is (some? (:fact/id created))))))))

(deftest create-minimal-test
  (testing "create works with only required fields"
    (let [result (fact/create th/*test-conn*
                              {:fact/name "Minimal Fact"})]
      (is (not (f/failed? result)))
      (let [all-facts (fact/get-all (th/test-db))]
        (is (= 1 (count all-facts)))
        (is (= "Minimal Fact" (:fact/name (first all-facts))))))))

(deftest get-by-id-test
  (testing "get-by-id returns fact when found"
    (fact/create th/*test-conn* {:fact/name "Find Me"})
    (let [all-facts (fact/get-all (th/test-db))
          fact-id (:fact/id (first all-facts))
          found (fact/get-by-id (th/test-db) fact-id)]
      (is (some? found))
      (is (= "Find Me" (:fact/name found)))))
  (testing "get-by-id returns nil when not found"
    (let [found (fact/get-by-id (th/test-db) (java.util.UUID/randomUUID))]
      (is (nil? found)))))

(deftest get-all-test
  (testing "get-all returns empty vector when no facts"
    (let [all (fact/get-all (th/test-db))]
      (is (= [] all))))
  (testing "get-all returns all facts"
    (fact/create th/*test-conn* {:fact/name "Fact 1"})
    (fact/create th/*test-conn* {:fact/name "Fact 2"})
    (fact/create th/*test-conn* {:fact/name "Fact 3"})
    (let [all (fact/get-all (th/test-db))]
      (is (= 3 (count all)))
      (is (= #{"Fact 1" "Fact 2" "Fact 3"}
             (set (map :fact/name all)))))))

(deftest update-fact-test
  (testing "update-fact modifies existing fact"
    (fact/create th/*test-conn* {:fact/name "Original"
                                 :fact/description "original description"})
    (let [original (first (fact/get-all (th/test-db)))
          _ (fact/update-fact th/*test-conn*
                              (:fact/id original)
                              {:fact/description "updated description"})
          updated (fact/get-by-id (th/test-db) (:fact/id original))]
      (is (= "updated description" (:fact/description updated)))))
  (testing "update-fact fails for non-existent fact"
    (let [result (fact/update-fact th/*test-conn*
                                   (java.util.UUID/randomUUID)
                                   {:fact/name "Should Fail"})]
      (is (f/failed? result)))))

(deftest delete-fact-test
  (testing "delete-fact removes fact"
    (fact/create th/*test-conn* {:fact/name "To Delete"})
    (let [original (first (fact/get-all (th/test-db)))
          result (fact/delete-fact th/*test-conn* (:fact/id original))]
      (is (not (f/failed? result)))
      (is (nil? (fact/get-by-id (th/test-db) (:fact/id original))))))
  (testing "delete-fact fails for non-existent fact"
    (let [result (fact/delete-fact th/*test-conn* (java.util.UUID/randomUUID))]
      (is (f/failed? result)))))

;; Note: Full-text search tests are commented out as they require
;; specific setup and may need the search index to be built
;; (deftest search-test
;;   (testing "search finds facts by name"
;;     (fact/create th/*test-conn* {:fact/name "Clojure Language"
;;                                  :fact/description "A Lisp dialect"})
;;     (fact/create th/*test-conn* {:fact/name "Java Language"
;;                                  :fact/description "An OOP language"})
;;     (let [results (fact/search (th/test-db) "Clojure")]
;;       (is (= 1 (count results)))
;;       (is (= "Clojure Language" (:fact/name (first results)))))))
