(ns csb.api.resources.file-content-test
  (:require
   [clojure.data.json :as json]
   [clojure.string]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.api.resources.file-content :as file-content-api]
   [csb.models.file :as file]
   [csb.models.file-content :as file-content]
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

(defn create-test-file!
  "Create a test project and file, return the file."
  []
  (let [proj (create-test-project!)]
    (file/create th/*test-conn* {:file/path "src/test.clj"
                                 :file/project (:db/id proj)})
    (first (file/get-all (th/test-db)))))

;; -----------------------------------------------------------------------------
;; Type Checking
;; -----------------------------------------------------------------------------

(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.api.resources.file-content))))

;; -----------------------------------------------------------------------------
;; Collection Resource Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-empty-test
  (testing "GET /api/file-content returns empty list when no content"
    (let [handler (file-content-api/collection (th/test-db))
          request (make-request {:method :get :uri "/api/file-content"})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

(deftest collection-get-with-content-test
  (testing "GET /api/file-content returns all content"
    (let [f (create-test-file!)]
      (file-content/create th/*test-conn* {:file-content/file (:db/id f)
                                           :file-content/content "(ns test1)"})
      (file-content/create th/*test-conn* {:file-content/file (:db/id f)
                                           :file-content/content "(ns test2)"})
      (let [handler (file-content-api/collection (th/test-db))
            request (make-request {:method :get :uri "/api/file-content"})
            response (handler request)
            contents (parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 2 (count contents)))
        ;; JSON response has non-namespaced keys
        (is (= #{"(ns test1)" "(ns test2)"}
               (set (map :content contents))))))))

(deftest collection-post-valid-test
  (testing "POST /api/file-content creates new content"
    (let [f (create-test-file!)
          handler (file-content-api/collection (th/test-db))
          ;; Use non-namespaced keys - this is what the REST API expects
          request (make-request {:method :post
                                 :uri "/api/file-content"
                                 :body (json-body {:file (:db/id f)
                                                   :content "(ns new)"})})
          response (handler request)]
      (is (= 201 (:status response)))
      (let [created (parse-json-response response)]
        (is (:created created))
        (is (some? (:id created))))
      ;; Verify content was created
      (let [found (first (file-content/get-all (th/test-db)))]
        (is (some? found))
        (is (= "(ns new)" (:file-content/content found)))))))

(deftest collection-post-with-ast-test
  (testing "POST /api/file-content creates content with compact-ast"
    (let [f (create-test-file!)
          handler (file-content-api/collection (th/test-db))
          ;; Use non-namespaced keys - this is what the REST API expects
          request (make-request {:method :post
                                 :uri "/api/file-content"
                                 :body (json-body {:file (:db/id f)
                                                   :content "(ns ast)"
                                                   :compact-ast "[:ns ast]"})})
          response (handler request)]
      (is (= 201 (:status response)))
      ;; Verify content was created with AST
      (let [found (first (file-content/get-all (th/test-db)))]
        (is (some? found))
        (is (= "(ns ast)" (:file-content/content found)))
        (is (= "[:ns ast]" (:file-content/compact-ast found)))))))

(deftest collection-post-missing-content-test
  (testing "POST /api/file-content returns 400 when content is missing"
    (let [f (create-test-file!)
          handler (file-content-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/file-content"
                                 :body (json-body {:file (:db/id f)})})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests
      (is (= 400 (:status response))))))

(deftest collection-post-missing-file-test
  (testing "POST /api/file-content returns 400 when file is missing"
    (let [handler (file-content-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/file-content"
                                 :body (json-body {:content "(ns orphan)"})})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests
      (is (= 400 (:status response))))))

;; -----------------------------------------------------------------------------
;; By File Resource Tests
;; -----------------------------------------------------------------------------

(deftest by-file-get-empty-test
  (testing "GET /api/files/:id/content returns empty list when no content"
    (let [f (create-test-file!)
          handler (file-content-api/by-file (th/test-db)
                                            {:route-params {:file-id (str (:db/id f))}})
          request (make-request {:method :get
                                 :uri (str "/api/files/" (:db/id f) "/content")})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

(deftest by-file-get-with-content-test
  (testing "GET /api/files/:id/content returns only content for that file"
    (let [proj (create-test-project!)
          _ (file/create th/*test-conn* {:file/path "src/file1.clj"
                                         :file/project (:db/id proj)})
          _ (file/create th/*test-conn* {:file/path "src/file2.clj"
                                         :file/project (:db/id proj)})
          files (file/get-all (th/test-db))
          f1 (first (filter #(= "src/file1.clj" (:file/path %)) files))
          f2 (first (filter #(= "src/file2.clj" (:file/path %)) files))]
      (file-content/create th/*test-conn* {:file-content/file (:db/id f1)
                                           :file-content/content "(ns file1)"})
      (file-content/create th/*test-conn* {:file-content/file (:db/id f2)
                                           :file-content/content "(ns file2)"})
      (let [handler (file-content-api/by-file (th/test-db)
                                              {:route-params {:file-id (str (:db/id f1))}})
            request (make-request {:method :get
                                   :uri (str "/api/files/" (:db/id f1) "/content")})
            response (handler request)
            contents (parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 1 (count contents)))
        ;; JSON response has non-namespaced keys
        (is (= "(ns file1)" (:content (first contents))))))))

(deftest by-file-post-test
  (testing "POST /api/files/:id/content creates content for that file"
    (let [f (create-test-file!)
          handler (file-content-api/by-file (th/test-db)
                                            {:route-params {:file-id (str (:db/id f))}})
          request (make-request {:method :post
                                 :uri (str "/api/files/" (:db/id f) "/content")
                                 :body (json-body {:content "(ns new)"})})
          response (handler request)]
      (is (= 201 (:status response)))
      ;; Verify content was created with correct file
      (let [found (first (file-content/get-by-file (th/test-db) (:db/id f)))]
        (is (some? found))
        (is (= "(ns new)" (:file-content/content found)))
        (is (= (:db/id f) (:db/id (:file-content/file found))))))))

;; -----------------------------------------------------------------------------
;; Item Resource Tests
;; -----------------------------------------------------------------------------

(deftest item-get-found-test
  (testing "GET /api/file-content/:id returns content when found"
    (let [f (create-test-file!)]
      (file-content/create th/*test-conn* {:file-content/file (:db/id f)
                                           :file-content/content "(ns find-me)"
                                           :file-content/compact-ast "[:ns find-me]"})
      (let [fc (first (file-content/get-all (th/test-db)))
            handler (file-content-api/item (th/test-db)
                                           {:route-params {:id (str (:file-content/id fc))}})
            request (make-request {:method :get
                                   :uri (str "/api/file-content/" (:file-content/id fc))})
            response (handler request)]
        (is (= 200 (:status response)))
        ;; JSON response has non-namespaced keys
        (let [result (parse-json-response response)]
          (is (= "(ns find-me)" (:content result)))
          (is (= "[:ns find-me]" (:compact-ast result))))))))

(deftest item-get-not-found-test
  (testing "GET /api/file-content/:id returns 404 when not found"
    (let [fake-id "00000000-0000-0000-0000-000000000000"
          handler (file-content-api/item (th/test-db)
                                         {:route-params {:id fake-id}})
          request (make-request {:method :get
                                 :uri (str "/api/file-content/" fake-id)})
          response (handler request)]
      (is (= 404 (:status response))))))

(deftest item-put-test
  (testing "PUT /api/file-content/:id updates content"
    (let [f (create-test-file!)]
      (file-content/create th/*test-conn* {:file-content/file (:db/id f)
                                           :file-content/content "(ns original)"})
      (let [fc (first (file-content/get-all (th/test-db)))
            handler (file-content-api/item (th/test-db)
                                           {:route-params {:id (str (:file-content/id fc))}})
            request (make-request {:method :put
                                   :uri (str "/api/file-content/" (:file-content/id fc))
                                   :body (json-body {:content "(ns updated)"
                                                     :compact-ast "[:ns updated]"})})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify update
        (let [updated (file-content/get-by-id (th/test-db) (:file-content/id fc))]
          (is (= "(ns updated)" (:file-content/content updated)))
          (is (= "[:ns updated]" (:file-content/compact-ast updated))))))))

(deftest item-patch-test
  (testing "PATCH /api/file-content/:id partially updates content"
    (let [f (create-test-file!)]
      (file-content/create th/*test-conn* {:file-content/file (:db/id f)
                                           :file-content/content "(ns original)"})
      (let [fc (first (file-content/get-all (th/test-db)))
            handler (file-content-api/item (th/test-db)
                                           {:route-params {:id (str (:file-content/id fc))}})
            request (make-request {:method :patch
                                   :uri (str "/api/file-content/" (:file-content/id fc))
                                   :body (json-body {:compact-ast "[:ns patched]"})})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify patch - content should be unchanged
        (let [patched (file-content/get-by-id (th/test-db) (:file-content/id fc))]
          (is (= "(ns original)" (:file-content/content patched)))
          (is (= "[:ns patched]" (:file-content/compact-ast patched))))))))

(deftest item-delete-test
  (testing "DELETE /api/file-content/:id removes content"
    (let [f (create-test-file!)]
      (file-content/create th/*test-conn* {:file-content/file (:db/id f)
                                           :file-content/content "(ns to-delete)"})
      (let [fc (first (file-content/get-all (th/test-db)))
            content-id (:file-content/id fc)
            handler (file-content-api/item (th/test-db)
                                           {:route-params {:id (str content-id)}})
            request (make-request {:method :delete
                                   :uri (str "/api/file-content/" content-id)})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify deletion
        (is (nil? (file-content/get-by-id (th/test-db) content-id)))))))

;; -----------------------------------------------------------------------------
;; HTML Response Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-html-test
  (testing "GET /api/file-content with Accept: text/html returns HTML"
    (let [f (create-test-file!)]
      (file-content/create th/*test-conn* {:file-content/file (:db/id f)
                                           :file-content/content "(ns html-content)"})
      (let [handler (file-content-api/collection (th/test-db))
            request (make-request {:method :get
                                   :uri "/api/file-content"
                                   :headers {"accept" "text/html"}})
            response (handler request)]
        (is (= 200 (:status response)))
        (is (string? (:body response)))
        ;; HTML should contain the content version info
        (is (clojure.string/includes? (:body response) "Version"))))))
