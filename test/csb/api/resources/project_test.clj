(ns csb.api.resources.project-test
  (:require
   [clojure.data.json :as json]
   [clojure.string]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.api.resources.project :as project-api]
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

;; -----------------------------------------------------------------------------
;; Type Checking
;; -----------------------------------------------------------------------------

(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.api.resources.project))))

;; -----------------------------------------------------------------------------
;; Collection Resource Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-empty-test
  (testing "GET /api/projects returns empty list when no projects"
    (let [handler (project-api/collection (th/test-db))
          request (make-request {:method :get :uri "/api/projects"})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

(deftest collection-get-with-projects-test
  (testing "GET /api/projects returns all projects"
    (project/create th/*test-conn* {:project/name "project-1"})
    (project/create th/*test-conn* {:project/name "project-2"})
    (let [handler (project-api/collection (th/test-db))
          request (make-request {:method :get :uri "/api/projects"})
          response (handler request)
          projects (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 2 (count projects)))
      ;; JSON response has non-namespaced keys
      (is (= #{"project-1" "project-2"}
             (set (map :name projects)))))))

(deftest collection-post-valid-test
  (testing "POST /api/projects creates a new project"
    (let [handler (project-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/projects"
                                 :body (json-body {:name "new-project"
                                                   :description "A new project"})})
          response (handler request)]
      (is (= 201 (:status response)))
      (let [created (parse-json-response response)]
        (is (:created created))
        (is (some? (:id created))))
      ;; Verify project was created
      (let [found (project/get-by-name (th/test-db) "new-project")]
        (is (some? found))
        (is (= "A new project" (:project/description found)))))))

(deftest collection-post-missing-name-test
  (testing "POST /api/projects returns 400 when name is missing"
    (let [handler (project-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/projects"
                                 :body (json-body {:description "No name"})})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests
      (is (= 400 (:status response))))))

(deftest collection-post-invalid-json-test
  (testing "POST /api/projects returns 400 for invalid JSON (nil body)"
    (let [handler (project-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/projects"
                                 :body nil})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests (invalid JSON)
      (is (= 400 (:status response))))))

;; -----------------------------------------------------------------------------
;; Item Resource Tests
;; -----------------------------------------------------------------------------

(deftest item-get-found-test
  (testing "GET /api/projects/:id returns project when found"
    (project/create th/*test-conn* {:project/name "find-me"
                                    :project/description "Found it"})
    (let [proj (project/get-by-name (th/test-db) "find-me")
          handler (project-api/item (th/test-db)
                                    {:route-params {:id (str (:db/id proj))}})
          request (make-request {:method :get
                                 :uri (str "/api/projects/" (:db/id proj))})
          response (handler request)]
      (is (= 200 (:status response)))
      ;; JSON response has non-namespaced keys
      (let [result (parse-json-response response)]
        (is (= "find-me" (:name result)))
        (is (= "Found it" (:description result)))))))

(deftest item-get-not-found-test
  (testing "GET /api/projects/:id returns 404 when not found"
    (let [handler (project-api/item (th/test-db)
                                    {:route-params {:id "99999"}})
          request (make-request {:method :get
                                 :uri "/api/projects/99999"})
          response (handler request)]
      (is (= 404 (:status response))))))

(deftest item-put-test
  (testing "PUT /api/projects/:id updates project"
    (project/create th/*test-conn* {:project/name "to-update"
                                    :project/description "original"})
    (let [proj (project/get-by-name (th/test-db) "to-update")
          handler (project-api/item (th/test-db)
                                    {:route-params {:id (str (:db/id proj))}})
          request (make-request {:method :put
                                 :uri (str "/api/projects/" (:db/id proj))
                                 :body (json-body {:name "updated-name"
                                                   :description "updated"})})
          response (handler request)]
      (is (#{200 204} (:status response)))
      ;; Verify update
      (let [updated (project/get-by-name (th/test-db) "updated-name")]
        (is (some? updated))
        (is (= "updated" (:project/description updated)))))))

(deftest item-patch-test
  (testing "PATCH /api/projects/:id partially updates project"
    (project/create th/*test-conn* {:project/name "to-patch"
                                    :project/description "original"})
    (let [proj (project/get-by-name (th/test-db) "to-patch")
          handler (project-api/item (th/test-db)
                                    {:route-params {:id (str (:db/id proj))}})
          request (make-request {:method :patch
                                 :uri (str "/api/projects/" (:db/id proj))
                                 :body (json-body {:description "patched"})})
          response (handler request)]
      (is (#{200 204} (:status response)))
      ;; Verify patch
      (let [patched (project/get-by-name (th/test-db) "to-patch")]
        (is (= "patched" (:project/description patched)))))))

(deftest item-delete-test
  (testing "DELETE /api/projects/:id removes project"
    (project/create th/*test-conn* {:project/name "to-delete"})
    (let [proj (project/get-by-name (th/test-db) "to-delete")
          handler (project-api/item (th/test-db)
                                    {:route-params {:id (str (:db/id proj))}})
          request (make-request {:method :delete
                                 :uri (str "/api/projects/" (:db/id proj))})
          response (handler request)]
      (is (#{200 204} (:status response)))
      ;; Verify deletion
      (is (nil? (project/get-by-name (th/test-db) "to-delete"))))))

;; -----------------------------------------------------------------------------
;; HTML Response Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-html-test
  (testing "GET /api/projects with Accept: text/html returns HTML"
    (project/create th/*test-conn* {:project/name "html-project"})
    (let [handler (project-api/collection (th/test-db))
          request (make-request {:method :get
                                 :uri "/api/projects"
                                 :headers {"accept" "text/html"}})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (string? (:body response)))
      (is (clojure.string/includes? (:body response) "html-project")))))
