(ns csb.api.resources.plan-test
  (:require
   [clojure.data.json :as json]
   [clojure.string]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.api.resources.plan :as plan-api]
   [csb.models.plan :as plan]
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
    (is (t/check-ns-clj 'csb.api.resources.plan))))

;; -----------------------------------------------------------------------------
;; Collection Resource Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-empty-test
  (testing "GET /api/plans returns empty list when no plans"
    (let [handler (plan-api/collection (th/test-db))
          request (make-request {:method :get :uri "/api/plans"})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

(deftest collection-get-with-plans-test
  (testing "GET /api/plans returns all plans"
    (let [proj (create-test-project!)]
      (plan/create th/*test-conn* {:plan/name "plan-1"
                                   :plan/project (:db/id proj)})
      (plan/create th/*test-conn* {:plan/name "plan-2"
                                   :plan/project (:db/id proj)})
      (let [handler (plan-api/collection (th/test-db))
            request (make-request {:method :get :uri "/api/plans"})
            response (handler request)
            plans (parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 2 (count plans)))
        ;; JSON response has non-namespaced keys
        (is (= #{"plan-1" "plan-2"}
               (set (map :name plans))))))))

(deftest collection-post-valid-test
  (testing "POST /api/plans creates a new plan"
    (let [proj (create-test-project!)
          handler (plan-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/plans"
                                 :body (json-body {:name "new-plan"
                                                   :project (:db/id proj)
                                                   :context "Build MVP"})})
          response (handler request)]
      (is (= 201 (:status response)))
      (let [created (parse-json-response response)]
        (is (:created created))
        (is (some? (:id created))))
      ;; Verify plan was created
      (let [found (first (plan/get-all (th/test-db)))]
        (is (some? found))
        (is (= "new-plan" (:plan/name found)))
        (is (= "Build MVP" (:plan/context found)))))))

(deftest collection-post-missing-name-test
  (testing "POST /api/plans returns 400 when name is missing"
    (let [proj (create-test-project!)
          handler (plan-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/plans"
                                 :body (json-body {:project (:db/id proj)})})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests
      (is (= 400 (:status response))))))

(deftest collection-post-missing-project-test
  (testing "POST /api/plans returns 400 when project is missing"
    (let [handler (plan-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/plans"
                                 :body (json-body {:name "no-project"})})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests
      (is (= 400 (:status response))))))

;; -----------------------------------------------------------------------------
;; By Project Resource Tests
;; -----------------------------------------------------------------------------

(deftest by-project-get-empty-test
  (testing "GET /api/projects/:id/plans returns empty list when no plans for project"
    (let [proj (create-test-project!)
          handler (plan-api/by-project (th/test-db)
                                       {:route-params {:project-id (str (:db/id proj))}})
          request (make-request {:method :get
                                 :uri (str "/api/projects/" (:db/id proj) "/plans")})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

(deftest by-project-get-with-plans-test
  (testing "GET /api/projects/:id/plans returns only plans for that project"
    (let [proj1 (create-test-project!)
          _ (project/create th/*test-conn* {:project/name "other-project"})
          proj2 (project/get-by-name (th/test-db) "other-project")]
      (plan/create th/*test-conn* {:plan/name "plan-for-proj1"
                                   :plan/project (:db/id proj1)})
      (plan/create th/*test-conn* {:plan/name "plan-for-proj2"
                                   :plan/project (:db/id proj2)})
      (let [handler (plan-api/by-project (th/test-db)
                                         {:route-params {:project-id (str (:db/id proj1))}})
            request (make-request {:method :get
                                   :uri (str "/api/projects/" (:db/id proj1) "/plans")})
            response (handler request)
            plans (parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 1 (count plans)))
        ;; JSON response has non-namespaced keys
        (is (= "plan-for-proj1" (:name (first plans))))))))

(deftest by-project-post-test
  (testing "POST /api/projects/:id/plans creates plan for that project"
    (let [proj (create-test-project!)
          handler (plan-api/by-project (th/test-db)
                                       {:route-params {:project-id (str (:db/id proj))}})
          request (make-request {:method :post
                                 :uri (str "/api/projects/" (:db/id proj) "/plans")
                                 :body (json-body {:name "new-plan"})})
          response (handler request)]
      (is (= 201 (:status response)))
      ;; Verify plan was created with correct project
      (let [found (first (plan/get-by-project (th/test-db) (:db/id proj)))]
        (is (some? found))
        (is (= "new-plan" (:plan/name found)))
        (is (= (:db/id proj) (:db/id (:plan/project found))))))))

;; -----------------------------------------------------------------------------
;; Item Resource Tests
;; -----------------------------------------------------------------------------

(deftest item-get-found-test
  (testing "GET /api/plans/:id returns plan when found"
    (let [proj (create-test-project!)]
      (plan/create th/*test-conn* {:plan/name "find-me"
                                   :plan/project (:db/id proj)
                                   :plan/context "Found it"})
      (let [p (first (plan/get-all (th/test-db)))
            handler (plan-api/item (th/test-db)
                                   {:route-params {:id (str (:plan/id p))}})
            request (make-request {:method :get
                                   :uri (str "/api/plans/" (:plan/id p))})
            response (handler request)]
        (is (= 200 (:status response)))
        ;; JSON response has non-namespaced keys
        (let [result (parse-json-response response)]
          (is (= "find-me" (:name result)))
          (is (= "Found it" (:context result))))))))

(deftest item-get-not-found-test
  (testing "GET /api/plans/:id returns 404 when not found"
    (let [fake-id "00000000-0000-0000-0000-000000000000"
          handler (plan-api/item (th/test-db)
                                 {:route-params {:id fake-id}})
          request (make-request {:method :get
                                 :uri (str "/api/plans/" fake-id)})
          response (handler request)]
      (is (= 404 (:status response))))))

(deftest item-put-test
  (testing "PUT /api/plans/:id updates plan"
    (let [proj (create-test-project!)]
      (plan/create th/*test-conn* {:plan/name "to-update"
                                   :plan/project (:db/id proj)
                                   :plan/context "original"})
      (let [p (first (plan/get-all (th/test-db)))
            handler (plan-api/item (th/test-db)
                                   {:route-params {:id (str (:plan/id p))}})
            request (make-request {:method :put
                                   :uri (str "/api/plans/" (:plan/id p))
                                   :body (json-body {:name "updated-name"
                                                     :context "updated"})})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify update
        (let [updated (plan/get-by-id (th/test-db) (:plan/id p))]
          (is (= "updated-name" (:plan/name updated)))
          (is (= "updated" (:plan/context updated))))))))

(deftest item-patch-test
  (testing "PATCH /api/plans/:id partially updates plan"
    (let [proj (create-test-project!)]
      (plan/create th/*test-conn* {:plan/name "to-patch"
                                   :plan/project (:db/id proj)
                                   :plan/context "original"})
      (let [p (first (plan/get-all (th/test-db)))
            handler (plan-api/item (th/test-db)
                                   {:route-params {:id (str (:plan/id p))}})
            request (make-request {:method :patch
                                   :uri (str "/api/plans/" (:plan/id p))
                                   :body (json-body {:context "patched"})})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify patch - name should be unchanged
        (let [patched (plan/get-by-id (th/test-db) (:plan/id p))]
          (is (= "to-patch" (:plan/name patched)))
          (is (= "patched" (:plan/context patched))))))))

(deftest item-delete-test
  (testing "DELETE /api/plans/:id removes plan"
    (let [proj (create-test-project!)]
      (plan/create th/*test-conn* {:plan/name "to-delete"
                                   :plan/project (:db/id proj)})
      (let [p (first (plan/get-all (th/test-db)))
            plan-id (:plan/id p)
            handler (plan-api/item (th/test-db)
                                   {:route-params {:id (str plan-id)}})
            request (make-request {:method :delete
                                   :uri (str "/api/plans/" plan-id)})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify deletion
        (is (nil? (plan/get-by-id (th/test-db) plan-id)))))))

;; -----------------------------------------------------------------------------
;; HTML Response Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-html-test
  (testing "GET /api/plans with Accept: text/html returns HTML"
    (let [proj (create-test-project!)]
      (plan/create th/*test-conn* {:plan/name "html-plan"
                                   :plan/project (:db/id proj)})
      (let [handler (plan-api/collection (th/test-db))
            request (make-request {:method :get
                                   :uri "/api/plans"
                                   :headers {"accept" "text/html"}})
            response (handler request)]
        (is (= 200 (:status response)))
        (is (string? (:body response)))
        (is (clojure.string/includes? (:body response) "html-plan"))))))
