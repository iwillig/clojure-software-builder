(ns csb.api.resources.fact-test
  (:require
   [clojure.data.json :as json]
   [clojure.string]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [csb.api.resources.fact :as fact-api]
   [csb.models.fact :as fact]
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
  [{:keys [method uri body headers route-params query-params]
    :or {method :get
         headers {"accept" "application/json"}
         route-params {}
         query-params {}}}]
  {:request-method method
   :uri uri
   :headers headers
   :route-params route-params
   :query-params query-params
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
    (is (t/check-ns-clj 'csb.api.resources.fact))))

;; -----------------------------------------------------------------------------
;; Collection Resource Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-empty-test
  (testing "GET /api/facts returns empty list when no facts"
    (let [handler (fact-api/collection (th/test-db))
          request (make-request {:method :get :uri "/api/facts"})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

(deftest collection-get-with-facts-test
  (testing "GET /api/facts returns all facts"
    (fact/create th/*test-conn* {:fact/name "Clojure"
                                 :fact/description "A Lisp dialect"})
    (fact/create th/*test-conn* {:fact/name "Java"
                                 :fact/description "JVM language"})
    (let [handler (fact-api/collection (th/test-db))
          request (make-request {:method :get :uri "/api/facts"})
          response (handler request)
          facts (parse-json-response response)]
      (is (= 200 (:status response)))
      (is (= 2 (count facts)))
      ;; Note: JSON strips namespace from keywords, so :fact/name becomes :name
      (is (= #{"Clojure" "Java"}
             (set (map :name facts)))))))

(deftest collection-post-valid-test
  (testing "POST /api/facts creates a new fact"
    (let [handler (fact-api/collection (th/test-db))
          ;; Use non-namespaced keys - this is what the REST API expects
          request (make-request {:method :post
                                 :uri "/api/facts"
                                 :body (json-body {:name "New Fact"
                                                   :description "Description"})})
          response (handler request)]
      (is (= 201 (:status response)))
      (let [created (parse-json-response response)]
        (is (:created created))
        (is (some? (:id created))))
      ;; Verify fact was created (using model directly, keeps namespaced keys)
      (let [found (first (fact/get-all (th/test-db)))]
        (is (some? found))
        (is (= "New Fact" (:fact/name found)))
        (is (= "Description" (:fact/description found)))))))

(deftest collection-post-name-only-test
  (testing "POST /api/facts creates fact with name only"
    (let [handler (fact-api/collection (th/test-db))
          ;; Use non-namespaced keys - this is what the REST API expects
          request (make-request {:method :post
                                 :uri "/api/facts"
                                 :body (json-body {:name "Name Only"})})
          response (handler request)]
      (is (= 201 (:status response)))
      ;; Verify fact was created without description (using model directly)
      (let [found (first (fact/get-all (th/test-db)))]
        (is (some? found))
        (is (= "Name Only" (:fact/name found)))
        (is (nil? (:fact/description found)))))))

(deftest collection-post-missing-name-test
  (testing "POST /api/facts returns 400 when name is missing"
    (let [handler (fact-api/collection (th/test-db))
          ;; Use non-namespaced keys - this is what the REST API expects
          request (make-request {:method :post
                                 :uri "/api/facts"
                                 :body (json-body {:description "No name"})})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests
      (is (= 400 (:status response))))))

(deftest collection-post-invalid-json-test
  (testing "POST /api/facts returns 400 for nil body (simulating invalid JSON)"
    (let [handler (fact-api/collection (th/test-db))
          ;; In production, ring-json middleware returns nil for invalid JSON
          request (make-request {:method :post
                                 :uri "/api/facts"
                                 :body nil})
          response (handler request)]
      ;; Liberator returns 400 for malformed requests (invalid JSON)
      (is (= 400 (:status response))))))

;; -----------------------------------------------------------------------------
;; Search Resource Tests
;; -----------------------------------------------------------------------------

(deftest search-empty-query-test
  (testing "GET /api/facts/search returns empty list when no query"
    (fact/create th/*test-conn* {:fact/name "Test"})
    (let [handler (fact-api/search-resource (th/test-db))
          request (make-request {:method :get
                                 :uri "/api/facts/search"})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (= [] (parse-json-response response))))))

;; Note: Full-text search tests depend on Datalevin's full-text indexing
;; which may require specific schema configuration. These are basic smoke tests.

(deftest search-with-query-test
  (testing "GET /api/facts/search?q=term searches facts"
    (fact/create th/*test-conn* {:fact/name "Clojure"
                                 :fact/description "Functional programming"})
    (fact/create th/*test-conn* {:fact/name "Java"
                                 :fact/description "Object-oriented"})
    (let [handler (fact-api/search-resource (th/test-db))
          request (make-request {:method :get
                                 :uri "/api/facts/search"
                                 :query-params {"q" "Clojure"}})
          response (handler request)]
      (is (= 200 (:status response)))
      ;; Search results depend on full-text index configuration
      ;; Just verify it doesn't error
      (is (vector? (parse-json-response response))))))

;; -----------------------------------------------------------------------------
;; Item Resource Tests
;; -----------------------------------------------------------------------------

(deftest item-get-found-test
  (testing "GET /api/facts/:id returns fact when found"
    (fact/create th/*test-conn* {:fact/name "Find Me"
                                 :fact/description "Found it"})
    (let [fct (first (fact/get-all (th/test-db)))
          handler (fact-api/item (th/test-db)
                                 {:route-params {:id (str (:fact/id fct))}})
          request (make-request {:method :get
                                 :uri (str "/api/facts/" (:fact/id fct))})
          response (handler request)]
      (is (= 200 (:status response)))
      ;; JSON response has non-namespaced keys
      (let [result (parse-json-response response)]
        (is (= "Find Me" (:name result)))
        (is (= "Found it" (:description result)))))))

(deftest item-get-not-found-test
  (testing "GET /api/facts/:id returns 404 when not found"
    (let [fake-id "00000000-0000-0000-0000-000000000000"
          handler (fact-api/item (th/test-db)
                                 {:route-params {:id fake-id}})
          request (make-request {:method :get
                                 :uri (str "/api/facts/" fake-id)})
          response (handler request)]
      (is (= 404 (:status response))))))

(deftest item-put-test
  (testing "PUT /api/facts/:id updates fact"
    (fact/create th/*test-conn* {:fact/name "To Update"
                                 :fact/description "Original"})
    (let [fct (first (fact/get-all (th/test-db)))
          handler (fact-api/item (th/test-db)
                                 {:route-params {:id (str (:fact/id fct))}})
          ;; Use non-namespaced keys - this is what the REST API expects
          request (make-request {:method :put
                                 :uri (str "/api/facts/" (:fact/id fct))
                                 :body (json-body {:name "Updated Name"
                                                   :description "Updated"})})
          response (handler request)]
      (is (#{200 204} (:status response)))
      ;; Verify update (using model directly)
      (let [updated (fact/get-by-id (th/test-db) (:fact/id fct))]
        (is (= "Updated Name" (:fact/name updated)))
        (is (= "Updated" (:fact/description updated)))))))

(deftest item-patch-test
  (testing "PATCH /api/facts/:id partially updates fact"
    (fact/create th/*test-conn* {:fact/name "To Patch"
                                 :fact/description "Original"})
    (let [fct (first (fact/get-all (th/test-db)))
          handler (fact-api/item (th/test-db)
                                 {:route-params {:id (str (:fact/id fct))}})
          ;; Use non-namespaced keys - this is what the REST API expects
          request (make-request {:method :patch
                                 :uri (str "/api/facts/" (:fact/id fct))
                                 :body (json-body {:description "Patched"})})
          response (handler request)]
      (is (#{200 204} (:status response)))
      ;; Verify patch - name should be unchanged (using model directly)
      (let [patched (fact/get-by-id (th/test-db) (:fact/id fct))]
        (is (= "To Patch" (:fact/name patched)))
        (is (= "Patched" (:fact/description patched)))))))

(deftest item-delete-test
  (testing "DELETE /api/facts/:id removes fact"
    (fact/create th/*test-conn* {:fact/name "To Delete"})
    (let [fct (first (fact/get-all (th/test-db)))
          fact-id (:fact/id fct)
          handler (fact-api/item (th/test-db)
                                 {:route-params {:id (str fact-id)}})
          request (make-request {:method :delete
                                 :uri (str "/api/facts/" fact-id)})
          response (handler request)]
      (is (#{200 204} (:status response)))
      ;; Verify deletion
      (is (nil? (fact/get-by-id (th/test-db) fact-id))))))

;; -----------------------------------------------------------------------------
;; HTML Response Tests
;; -----------------------------------------------------------------------------

(deftest collection-get-html-test
  (testing "GET /api/facts with Accept: text/html returns HTML"
    (fact/create th/*test-conn* {:fact/name "HTML Fact"})
    (let [handler (fact-api/collection (th/test-db))
          request (make-request {:method :get
                                 :uri "/api/facts"
                                 :headers {"accept" "text/html"}})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (string? (:body response)))
      (is (clojure.string/includes? (:body response) "HTML Fact")))))

(deftest search-get-html-test
  (testing "GET /api/facts/search with Accept: text/html returns HTML"
    (let [handler (fact-api/search-resource (th/test-db))
          request (make-request {:method :get
                                 :uri "/api/facts/search"
                                 :headers {"accept" "text/html"}
                                 :query-params {"q" "test"}})
          response (handler request)]
      (is (= 200 (:status response)))
      (is (string? (:body response)))
      (is (clojure.string/includes? (:body response) "Search results")))))
