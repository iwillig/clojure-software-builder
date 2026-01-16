(ns csb.test-helpers
  "Test utilities and fixtures for CSB tests.

   Provides database isolation via fixtures that create and destroy
   temporary databases for each test."
  (:require
   [com.stuartsierra.component :as component]
   [csb.components.database :as database]
   [datalevin.core :as d])
  (:import
   (java.io
    File)))

;; =============================================================================
;; Temporary Database Management
;; =============================================================================

(defn temp-db-dir
  "Creates a unique temporary database directory path for a test.

   Returns a string path in the system temp directory with a unique UUID."
  []
  (let [temp-dir (System/getProperty "java.io.tmpdir")
        test-id (str "csb-test-" (random-uuid))
        db-path (str temp-dir File/separator test-id)]
    db-path))

(defn delete-recursively
  "Recursively deletes a directory and all its contents.

   Accepts a java.io.File object."
  [^File file]
  (when (.exists file)
    (when (.isDirectory file)
      (doseq [child (.listFiles file)]
        (delete-recursively child)))
    (.delete file)))

;; =============================================================================
;; Test Database Connection
;; =============================================================================

(def ^:dynamic *test-conn*
  "Dynamic var holding the test database connection.

   Bound by the with-test-db fixture for each test.
   Use this in your tests instead of creating your own connection."
  nil)

;; =============================================================================
;; Fixtures
;; =============================================================================

(defn with-test-db
  "Fixture that creates a fresh isolated database for each test.

   Creates a temporary database directory, initializes a connection
   with the CSB schema via the Database component, runs the test,
   then cleans up by stopping the component and deleting the
   temporary directory.

   Usage:
     (ns my-test
       (:require [clojure.test :refer :all]
                 [csb.test-helpers :as th]))

     (use-fixtures :each th/with-test-db)

     (deftest my-test
       (let [conn th/*test-conn*]
         ;; use conn for test
         ))"
  [test-fn]
  (let [db-path (temp-db-dir)
        db-component (database/new-database db-path)
        started (component/start db-component)]
    (try
      (binding [*test-conn* (:connection started)]
        (test-fn))
      (finally
        (component/stop started)
        (delete-recursively (File. ^String db-path))))))

;; =============================================================================
;; Test Utilities
;; =============================================================================

(defn test-db
  "Returns the current test database (dereferenced connection).

   Use this when you need to query the database."
  []
  (d/db *test-conn*))
