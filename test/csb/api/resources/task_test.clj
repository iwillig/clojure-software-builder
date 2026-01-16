(ns csb.api.resources.task-test
  (:require
   [clojure.data.json :as json]
   [clojure.string]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.api.resources.task :as task-api]
   [csb.models.plan :as plan]
   [csb.models.project :as project]
   [csb.models.task :as task]
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

(defn create-test-plan!
  "Create a test project and plan, return the plan."
  []
  (let [proj (create-test-project!)]
    (plan/create th/*test-conn* {:plan/name "test-plan"
                                 :plan/project (:db/id proj)})
    (first (plan/get-all (th/test-db)))))

;; -----------------------------------------------------------------------------
;; Type Checking
;; -----------------------------------------------------------------------------

(deftest type-check
  (testing "types are valid"
    (is (t/check-ns-clj 'csb.api.resources.task))))

;; -----------------------------------------------------------------------------
;; Collection Resource Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-empty-test
  (testing "GET /api/tasks returns empty list when no tasks"
    (let [handler (task-api/collection (th/test-db))
          request (make-request {:method :get :uri "/api/tasks"})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

(deftest collection-get-with-tasks-test
  (testing "GET /api/tasks returns all tasks"
    (let [p (create-test-plan!)]
      (task/create th/*test-conn* {:task/name "task-1"
                                   :task/plan (:db/id p)})
      (task/create th/*test-conn* {:task/name "task-2"
                                   :task/plan (:db/id p)})
      (let [handler (task-api/collection (th/test-db))
            request (make-request {:method :get :uri "/api/tasks"})
            response (handler request)
            tasks (parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 2 (count tasks)))
        ;; JSON response has non-namespaced keys
        (is (= #{"task-1" "task-2"}
               (set (map :name tasks))))))))

(deftest collection-post-valid-test
  (testing "POST /api/tasks creates a new task"
    (let [p (create-test-plan!)
          handler (task-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/tasks"
                                 :body (json-body {:name "new-task"
                                                   :plan (:db/id p)
                                                   :context "Do something"})})
          response (handler request)]
      (is (= 201 (:status response)))
      (let [created (parse-json-response response)]
        (is (:created created))
        (is (some? (:id created))))
      ;; Verify task was created
      (let [found (first (task/get-all (th/test-db)))]
        (is (some? found))
        (is (= "new-task" (:task/name found)))
        (is (= "Do something" (:task/context found)))
        (is (false? (:task/completed found)))))))

(deftest collection-post-missing-name-test
  (testing "POST /api/tasks returns 400 when name is missing"
    (let [p (create-test-plan!)
          handler (task-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/tasks"
                                 :body (json-body {:plan (:db/id p)})})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests
      (is (= 400 (:status response))))))

(deftest collection-post-missing-plan-test
  (testing "POST /api/tasks returns 400 when plan is missing"
    (let [handler (task-api/collection (th/test-db))
          request (make-request {:method :post
                                 :uri "/api/tasks"
                                 :body (json-body {:name "no-plan"})})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests
      (is (= 400 (:status response))))))

;; -----------------------------------------------------------------------------
;; By Plan Resource Tests
;; -----------------------------------------------------------------------------

(deftest by-plan-get-empty-test
  (testing "GET /api/plans/:id/tasks returns empty list when no tasks for plan"
    (let [p (create-test-plan!)
          handler (task-api/by-plan (th/test-db)
                                    {:route-params {:plan-id (str (:db/id p))}})
          request (make-request {:method :get
                                 :uri (str "/api/plans/" (:db/id p) "/tasks")})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

(deftest by-plan-get-with-tasks-test
  (testing "GET /api/plans/:id/tasks returns only tasks for that plan"
    (let [proj (create-test-project!)
          _ (plan/create th/*test-conn* {:plan/name "plan-1"
                                         :plan/project (:db/id proj)})
          _ (plan/create th/*test-conn* {:plan/name "plan-2"
                                         :plan/project (:db/id proj)})
          plans (plan/get-all (th/test-db))
          p1 (first (filter #(= "plan-1" (:plan/name %)) plans))
          p2 (first (filter #(= "plan-2" (:plan/name %)) plans))]
      (task/create th/*test-conn* {:task/name "task-for-plan1"
                                   :task/plan (:db/id p1)})
      (task/create th/*test-conn* {:task/name "task-for-plan2"
                                   :task/plan (:db/id p2)})
      (let [handler (task-api/by-plan (th/test-db)
                                      {:route-params {:plan-id (str (:db/id p1))}})
            request (make-request {:method :get
                                   :uri (str "/api/plans/" (:db/id p1) "/tasks")})
            response (handler request)
            tasks (parse-json-response response)]
        (is (= 200 (:status response)))
        (is (= 1 (count tasks)))
        ;; JSON response has non-namespaced keys
        (is (= "task-for-plan1" (:name (first tasks))))))))

(deftest by-plan-post-test
  (testing "POST /api/plans/:id/tasks creates task for that plan"
    (let [p (create-test-plan!)
          handler (task-api/by-plan (th/test-db)
                                    {:route-params {:plan-id (str (:db/id p))}})
          request (make-request {:method :post
                                 :uri (str "/api/plans/" (:db/id p) "/tasks")
                                 :body (json-body {:name "new-task"})})
          response (handler request)]
      (is (= 201 (:status response)))
      ;; Verify task was created with correct plan
      (let [found (first (task/get-by-plan (th/test-db) (:db/id p)))]
        (is (some? found))
        (is (= "new-task" (:task/name found)))
        (is (= (:db/id p) (:task/plan found)))))))

;; -----------------------------------------------------------------------------
;; Subtasks Resource Tests
;; -----------------------------------------------------------------------------

(deftest subtasks-get-empty-test
  (testing "GET /api/tasks/:id/subtasks returns empty list when no subtasks"
    (let [p (create-test-plan!)]
      (task/create th/*test-conn* {:task/name "parent-task"
                                   :task/plan (:db/id p)})
      (let [parent (first (task/get-all (th/test-db)))
            handler (task-api/subtasks (th/test-db)
                                       {:route-params {:parent-id (str (:task/id parent))}})
            request (make-request {:method :get
                                   :uri (str "/api/tasks/" (:task/id parent) "/subtasks")})
            response (handler request)]
        (is (= 200 (:status response)))
        (is (= [] (parse-json-response response)))))))

(deftest subtasks-get-with-children-test
  (testing "GET /api/tasks/:id/subtasks returns only direct children"
    (let [p (create-test-plan!)]
      (task/create th/*test-conn* {:task/name "parent-task"
                                   :task/plan (:db/id p)})
      (let [parent (first (task/get-all (th/test-db)))]
        (task/create th/*test-conn* {:task/name "child-1"
                                     :task/plan (:db/id p)
                                     :task/parent (:db/id parent)})
        (task/create th/*test-conn* {:task/name "child-2"
                                     :task/plan (:db/id p)
                                     :task/parent (:db/id parent)})
        (task/create th/*test-conn* {:task/name "sibling"
                                     :task/plan (:db/id p)})
        (let [handler (task-api/subtasks (th/test-db)
                                         {:route-params {:parent-id (str (:task/id parent))}})
              request (make-request {:method :get
                                     :uri (str "/api/tasks/" (:task/id parent) "/subtasks")})
              response (handler request)
              subtasks (parse-json-response response)]
          (is (= 200 (:status response)))
          (is (= 2 (count subtasks)))
          ;; JSON response has non-namespaced keys
          (is (= #{"child-1" "child-2"}
                 (set (map :name subtasks)))))))))

(deftest subtasks-post-test
  (testing "POST /api/tasks/:id/subtasks creates child task"
    (let [p (create-test-plan!)]
      (task/create th/*test-conn* {:task/name "parent-task"
                                   :task/plan (:db/id p)})
      (let [parent (first (task/get-all (th/test-db)))
            handler (task-api/subtasks (th/test-db)
                                       {:route-params {:parent-id (str (:task/id parent))}})
            request (make-request {:method :post
                                   :uri (str "/api/tasks/" (:task/id parent) "/subtasks")
                                   :body (json-body {:name "new-subtask"})})
            response (handler request)]
        (is (= 201 (:status response)))
        ;; Verify subtask was created with correct parent and plan
        (let [children (task/get-children (th/test-db) (:db/id parent))
              child (first children)]
          (is (= 1 (count children)))
          (is (= "new-subtask" (:task/name child)))
          (is (= (:db/id parent) (:task/parent child)))
          (is (= (:db/id p) (:task/plan child))))))))

(deftest subtasks-not-found-test
  (testing "GET /api/tasks/:id/subtasks returns 404 when parent not found"
    (let [fake-id "00000000-0000-0000-0000-000000000000"
          handler (task-api/subtasks (th/test-db)
                                     {:route-params {:parent-id fake-id}})
          request (make-request {:method :get
                                 :uri (str "/api/tasks/" fake-id "/subtasks")})
          response (handler request)]
      (is (= 404 (:status response))))))

;; -----------------------------------------------------------------------------
;; Item Resource Tests
;; -----------------------------------------------------------------------------

(deftest item-get-found-test
  (testing "GET /api/tasks/:id returns task when found"
    (let [p (create-test-plan!)]
      (task/create th/*test-conn* {:task/name "find-me"
                                   :task/plan (:db/id p)
                                   :task/context "Found it"})
      (let [tsk (first (task/get-all (th/test-db)))
            handler (task-api/item (th/test-db)
                                   {:route-params {:id (str (:task/id tsk))}})
            request (make-request {:method :get
                                   :uri (str "/api/tasks/" (:task/id tsk))})
            response (handler request)]
        (is (= 200 (:status response)))
        ;; JSON response has non-namespaced keys
        (let [result (parse-json-response response)]
          (is (= "find-me" (:name result)))
          (is (= "Found it" (:context result)))
          (is (false? (:completed result))))))))

(deftest item-get-not-found-test
  (testing "GET /api/tasks/:id returns 404 when not found"
    (let [fake-id "00000000-0000-0000-0000-000000000000"
          handler (task-api/item (th/test-db)
                                 {:route-params {:id fake-id}})
          request (make-request {:method :get
                                 :uri (str "/api/tasks/" fake-id)})
          response (handler request)]
      (is (= 404 (:status response))))))

(deftest item-put-test
  (testing "PUT /api/tasks/:id updates task"
    (let [p (create-test-plan!)]
      (task/create th/*test-conn* {:task/name "to-update"
                                   :task/plan (:db/id p)
                                   :task/context "original"})
      (let [tsk (first (task/get-all (th/test-db)))
            handler (task-api/item (th/test-db)
                                   {:route-params {:id (str (:task/id tsk))}})
            request (make-request {:method :put
                                   :uri (str "/api/tasks/" (:task/id tsk))
                                   :body (json-body {:name "updated-name"
                                                     :context "updated"
                                                     :completed true})})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify update
        (let [updated (task/get-by-id (th/test-db) (:task/id tsk))]
          (is (= "updated-name" (:task/name updated)))
          (is (= "updated" (:task/context updated)))
          (is (true? (:task/completed updated))))))))

(deftest item-patch-test
  (testing "PATCH /api/tasks/:id partially updates task"
    (let [p (create-test-plan!)]
      (task/create th/*test-conn* {:task/name "to-patch"
                                   :task/plan (:db/id p)
                                   :task/context "original"})
      (let [tsk (first (task/get-all (th/test-db)))
            handler (task-api/item (th/test-db)
                                   {:route-params {:id (str (:task/id tsk))}})
            request (make-request {:method :patch
                                   :uri (str "/api/tasks/" (:task/id tsk))
                                   :body (json-body {:completed true})})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify patch - name and context should be unchanged
        (let [patched (task/get-by-id (th/test-db) (:task/id tsk))]
          (is (= "to-patch" (:task/name patched)))
          (is (= "original" (:task/context patched)))
          (is (true? (:task/completed patched))))))))

(deftest item-delete-test
  (testing "DELETE /api/tasks/:id removes task"
    (let [p (create-test-plan!)]
      (task/create th/*test-conn* {:task/name "to-delete"
                                   :task/plan (:db/id p)})
      (let [tsk (first (task/get-all (th/test-db)))
            task-id (:task/id tsk)
            handler (task-api/item (th/test-db)
                                   {:route-params {:id (str task-id)}})
            request (make-request {:method :delete
                                   :uri (str "/api/tasks/" task-id)})
            response (handler request)]
        (is (#{200 204} (:status response)))
        ;; Verify deletion
        (is (nil? (task/get-by-id (th/test-db) task-id)))))))

;; -----------------------------------------------------------------------------
;; HTML Response Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-html-test
  (testing "GET /api/tasks with Accept: text/html returns HTML"
    (let [p (create-test-plan!)]
      (task/create th/*test-conn* {:task/name "html-task"
                                   :task/plan (:db/id p)})
      (let [handler (task-api/collection (th/test-db))
            request (make-request {:method :get
                                   :uri "/api/tasks"
                                   :headers {"accept" "text/html"}})
            response (handler request)]
        (is (= 200 (:status response)))
        (is (string? (:body response)))
        (is (clojure.string/includes? (:body response) "html-task"))))))
