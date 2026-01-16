(ns csb.api.resources.file-test
  (:require
   [clojure.data.json :as json]
   [clojure.string]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.api.resources.file :as file-api]
   [csb.models.file :as file]
   [csb.models.project :as project]
   [csb.test-helpers :as th]
   [typed.clojure :as t]))

(use-fixtures :each th/with-test-db)

;; -----------------------------------------------------------------------------
;; Test Utilities
;; -----------------------------------------------------------------------------

(defn json-body
  "Return a map for use as request body.
   Ring-json middleware parses the body before it reaches the handler,
   so we provide the parsed map directly for unit tests."
  [data]
  data)

(defn make-request
  "Create a mock ring request."
  [{:keys [method uri body headers route-params]
    :or {method :get
         headers {"accept" "application/json"}
         route-params {}}}]
  {:request-method method
   :uri uri
   :headers headers
   :route-params route-params
   :body body})

(defn parse-json-response
  "Parse JSON from response body."
  [response]
  (when-let [body (:body response)]
    (if (string? body)
      (json/read-str body :key-fn keyword)
      body)))

(defn create-test-project!
  "Create a test project and return it."
  []
  (project/create th/*test-conn* {:project/name "test-project"})
  (project/get-by-name (th/test-db) "test-project"))

;; -----------------------------------------------------------------------------
;; Type Checking
;; -----------------------------------------------------------------------------

(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.api.resources.file))))

;; -----------------------------------------------------------------------------
;; Collection Resource Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-empty-test
  (testing "GET /api/files returns empty list when no files"
    (let [handler (file-api/collection (th/test-db))
          request (make-request {:method :get :uri "/api/files"})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

(deftest collection-get-with-files-test
  (testing "GET /api/files returns all files"
    (let [proj (create-test-project!)]
      (file/create th/*test-conn* {:file/path "src/core.clj"
                                   :file/project (:db/id proj)})
      (file/create th/*test-conn* {:file/path "src/utils.clj"
                                   :file/project (:db/id proj)})
      (let [handler (file-api/collection (th/test-db))
            request (make-request {:method :get :uri "/api/files"})
            response (handler request)
            files (parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 2 (count files)))
        ;; JSON response has non-namespaced keys
        (is (= #{"src/core.clj" "src/utils.clj"}
               (set (map :path files))))))))

(deftest collection-post-valid-test
  (testing "POST /api/files creates a new file"
    (let [proj (create-test-project!)
          handler (file-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/files"
                                 :body (json-body {:path "src/new.clj"
                                                   :project (:db/id proj)
                                                   :summary "New file"})})
          response (handler request)]
      (is (= 201 (:status response)))
      (let [created (parse-json-response response)]
        (is (:created created))
        (is (some? (:id created))))
      ;; Verify file was created
      (let [found (first (file/get-all (th/test-db)))]
        (is (some? found))
        (is (= "src/new.clj" (:file/path found)))
        (is (= "New file" (:file/summary found)))))))

(deftest collection-post-missing-path-test
  (testing "POST /api/files returns 400 when path is missing"
    (let [proj (create-test-project!)
          handler (file-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/files"
                                 :body (json-body {:project (:db/id proj)})})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests
      (is (= 400 (:status response))))))

(deftest collection-post-missing-project-test
  (testing "POST /api/files returns 400 when project is missing"
    (let [handler (file-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/files"
                                 :body (json-body {:path "src/orphan.clj"})})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests
      (is (= 400 (:status response))))))

;; -----------------------------------------------------------------------------
;; By Project Resource Tests
;; -----------------------------------------------------------------------------

(deftest by-project-get-empty-test
  (testing "GET /api/projects/:id/files returns empty list when no files for project"
    (let [proj (create-test-project!)
          handler (file-api/by-project (th/test-db)
                                       {:route-params {:project-id (str (:db/id proj))}})
          request (make-request {:method :get
                                 :uri (str "/api/projects/" (:db/id proj) "/files")})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

(deftest by-project-get-with-files-test
  (testing "GET /api/projects/:id/files returns only files for that project"
    (let [proj1 (create-test-project!)
          _ (project/create th/*test-conn* {:project/name "other-project"})
          proj2 (project/get-by-name (th/test-db) "other-project")]
      (file/create th/*test-conn* {:file/path "src/proj1.clj"
                                   :file/project (:db/id proj1)})
      (file/create th/*test-conn* {:file/path "src/proj2.clj"
                                   :file/project (:db/id proj2)})
      (let [handler (file-api/by-project (th/test-db)
                                         {:route-params {:project-id (str (:db/id proj1))}})
            request (make-request {:method :get
                                   :uri (str "/api/projects/" (:db/id proj1) "/files")})
            response (handler request)
            files (parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 1 (count files)))
        ;; JSON response has non-namespaced keys
        (is (= "src/proj1.clj" (:path (first files))))))))

(deftest by-project-post-test
  (testing "POST /api/projects/:id/files creates file for that project"
    (let [proj (create-test-project!)
          handler (file-api/by-project (th/test-db)
                                       {:route-params {:project-id (str (:db/id proj))}})
          request (make-request {:method :post
                                 :uri (str "/api/projects/" (:db/id proj) "/files")
                                 :body (json-body {:path "src/new.clj"})})
          response (handler request)]
      (is (= 201 (:status response)))
      ;; Verify file was created with correct project
      (let [found (first (file/get-by-project (th/test-db) (:db/id proj)))]
        (is (some? found))
        (is (= "src/new.clj" (:file/path found)))
        (is (= (:db/id proj) (:db/id (:file/project found))))))))

;; -----------------------------------------------------------------------------
;; Item Resource Tests
;; -----------------------------------------------------------------------------

(deftest item-get-found-test
  (testing "GET /api/files/:id returns file when found"
    (let [proj (create-test-project!)]
      (file/create th/*test-conn* {:file/path "src/find-me.clj"
                                   :file/project (:db/id proj)
                                   :file/summary "Found it"})
      (let [f (first (file/get-all (th/test-db)))
            handler (file-api/item (th/test-db)
                                   {:route-params {:id (str (:file/id f))}})
            request (make-request {:method :get
                                   :uri (str "/api/files/" (:file/id f))})
            response (handler request)]
        (is (= 200 (:status response)))
        ;; JSON response has non-namespaced keys
        (let [result (parse-json-response response)]
          (is (= "src/find-me.clj" (:path result)))
          (is (= "Found it" (:summary result))))))))

(deftest item-get-not-found-test
  (testing "GET /api/files/:id returns 404 when not found"
    (let [fake-id "00000000-0000-0000-0000-000000000000"
          handler (file-api/item (th/test-db)
                                 {:route-params {:id fake-id}})
          request (make-request {:method :get
                                 :uri (str "/api/files/" fake-id)})
          response (handler request)]
      (is (= 404 (:status response))))))

(deftest item-put-test
  (testing "PUT /api/files/:id updates file"
    (let [proj (create-test-project!)]
      (file/create th/*test-conn* {:file/path "src/to-update.clj"
                                   :file/project (:db/id proj)
                                   :file/summary "original"})
      (let [f (first (file/get-all (th/test-db)))
            handler (file-api/item (th/test-db)
                                   {:route-params {:id (str (:file/id f))}})
            request (make-request {:method :put
                                   :uri (str "/api/files/" (:file/id f))
                                   :body (json-body {:path "src/updated.clj"
                                                     :summary "updated"})})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify update
        (let [updated (file/get-by-id (th/test-db) (:file/id f))]
          (is (= "src/updated.clj" (:file/path updated)))
          (is (= "updated" (:file/summary updated))))))))

(deftest item-patch-test
  (testing "PATCH /api/files/:id partially updates file"
    (let [proj (create-test-project!)]
      (file/create th/*test-conn* {:file/path "src/to-patch.clj"
                                   :file/project (:db/id proj)
                                   :file/summary "original"})
      (let [f (first (file/get-all (th/test-db)))
            handler (file-api/item (th/test-db)
                                   {:route-params {:id (str (:file/id f))}})
            request (make-request {:method :patch
                                   :uri (str "/api/files/" (:file/id f))
                                   :body (json-body {:summary "patched"})})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify patch - path should be unchanged
        (let [patched (file/get-by-id (th/test-db) (:file/id f))]
          (is (= "src/to-patch.clj" (:file/path patched)))
          (is (= "patched" (:file/summary patched))))))))

(deftest item-delete-test
  (testing "DELETE /api/files/:id removes file"
    (let [proj (create-test-project!)]
      (file/create th/*test-conn* {:file/path "src/to-delete.clj"
                                   :file/project (:db/id proj)})
      (let [f (first (file/get-all (th/test-db)))
            file-id (:file/id f)
            handler (file-api/item (th/test-db)
                                   {:route-params {:id (str file-id)}})
            request (make-request {:method :delete
                                   :uri (str "/api/files/" file-id)})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify deletion
        (is (nil? (file/get-by-id (th/test-db) file-id)))))))

;; -----------------------------------------------------------------------------
;; HTML Response Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-html-test
  (testing "GET /api/files with Accept: text/html returns HTML"
    (let [proj (create-test-project!)]
      (file/create th/*test-conn* {:file/path "src/html-file.clj"
                                   :file/project (:db/id proj)})
      (let [handler (file-api/collection (th/test-db))
            request (make-request {:method :get
                                   :uri "/api/files"
                                   :headers {"accept" "text/html"}})
            response (handler request)]
        (is (= 200 (:status response)))
        (is (string? (:body response)))
        (is (clojure.string/includes? (:body response) "src/html-file.clj"))))))
