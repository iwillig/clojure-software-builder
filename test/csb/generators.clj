(ns csb.generators
  "Test data generators using typed.malli.generator.

   Provides functions to generate test data for model entities
   based on their TypedClojure type definitions."
  (:require
   [csb.models.fact :as fact]
   [csb.models.file :as file]
   [csb.models.file-content :as file-content]
   [csb.models.plan :as plan]
   [csb.models.plan-state :as plan-state]
   [csb.models.project :as project]
   [csb.models.task :as task]
   [csb.test-helpers :as th]
   [typed.clojure :as t]
   [typed.malli.generator :as tmg]))

;; =============================================================================
;; Project Generators
;; =============================================================================

(defn gen-project-params
  "Generate random ProjectParams for testing."
  []
  (tmg/generate (t/HMap :mandatory {:project/name t/Str}
                        :optional {:project/description t/Str
                                   :project/path t/Str})))

(defn gen-project-params-with
  "Generate ProjectParams with specific overrides."
  [overrides]
  (merge (gen-project-params) overrides))

(defn create-test-project
  "Create a project in the test database and return it.
   Requires *test-conn* to be bound."
  [conn]
  (let [params (gen-project-params-with
                {:project/name (str "test-project-" (random-uuid))})]
    (project/create conn params)
    (project/get-by-name (th/test-db) (:project/name params))))

;; =============================================================================
;; Fact Generators
;; =============================================================================

(defn gen-fact-params
  "Generate random FactParams for testing."
  []
  (tmg/generate (t/HMap :mandatory {:fact/name t/Str}
                        :optional {:fact/description t/Str})))

(defn gen-fact-params-with
  "Generate FactParams with specific overrides."
  [overrides]
  (merge (gen-fact-params) overrides))

(defn create-test-fact
  "Create a fact in the test database and return it.
   Requires *test-conn* to be bound."
  [conn]
  (let [params (gen-fact-params-with
                {:fact/name (str "test-fact-" (random-uuid))})]
    (fact/create conn params)
    (first (fact/get-all (th/test-db)))))

;; =============================================================================
;; Plan Generators
;; =============================================================================

(defn gen-plan-params
  "Generate random PlanParams for testing.
   Note: :plan/project must be provided as it's a required reference."
  [project-eid]
  (merge
   (tmg/generate (t/HMap :mandatory {:plan/name t/Str}
                         :optional {:plan/context t/Str}))
   {:plan/project project-eid}))

(defn gen-plan-params-with
  "Generate PlanParams with specific overrides.
   Note: :plan/project must be provided."
  [project-eid overrides]
  (merge (gen-plan-params project-eid) overrides))

(defn create-test-plan
  "Create a plan in the test database and return it.
   Requires a project entity ID and *test-conn* to be bound."
  [conn project-eid]
  (let [params (gen-plan-params-with
                project-eid
                {:plan/name (str "test-plan-" (random-uuid))})]
    (plan/create conn params)
    (first (plan/get-all (th/test-db)))))

;; =============================================================================
;; Task Generators
;; =============================================================================

(defn gen-task-params
  "Generate random TaskParams for testing.
   Note: :task/plan must be provided as it's a required reference."
  [plan-eid]
  (merge
   (tmg/generate (t/HMap :mandatory {:task/name t/Str}
                         :optional {:task/context t/Str}))
   {:task/plan plan-eid}))

(defn gen-task-params-with
  "Generate TaskParams with specific overrides.
   Note: :task/plan must be provided."
  [plan-eid overrides]
  (merge (gen-task-params plan-eid) overrides))

(defn create-test-task
  "Create a task in the test database and return it.
   Requires a plan entity ID and *test-conn* to be bound."
  [conn plan-eid]
  (let [params (gen-task-params-with
                plan-eid
                {:task/name (str "test-task-" (random-uuid))})]
    (task/create conn params)
    (first (task/get-by-plan (th/test-db) plan-eid))))

(defn create-test-task-with-parent
  "Create a child task with a parent in the test database.
   Requires plan-eid, parent-eid, and *test-conn* to be bound."
  [conn plan-eid parent-eid]
  (let [params (gen-task-params-with
                plan-eid
                {:task/name (str "test-child-task-" (random-uuid))
                 :task/parent parent-eid})]
    (task/create conn params)
    (first (task/get-children (th/test-db) parent-eid))))

;; =============================================================================
;; File Generators
;; =============================================================================

(defn gen-file-params
  "Generate random FileParams for testing.
   Note: :file/project must be provided as it's a required reference."
  [project-eid]
  (merge
   (tmg/generate (t/HMap :mandatory {:file/path t/Str}
                         :optional {:file/summary t/Str}))
   {:file/project project-eid}))

(defn gen-file-params-with
  "Generate FileParams with specific overrides.
   Note: :file/project must be provided."
  [project-eid overrides]
  (merge (gen-file-params project-eid) overrides))

(defn create-test-file
  "Create a file in the test database and return it.
   Requires a project entity ID and *test-conn* to be bound."
  [conn project-eid]
  (let [params (gen-file-params-with
                project-eid
                {:file/path (str "src/test-" (random-uuid) ".clj")})]
    (file/create conn params)
    (first (file/get-by-project (th/test-db) project-eid))))

;; =============================================================================
;; File Content Generators
;; =============================================================================

(defn gen-file-content-params
  "Generate random FileContentParams for testing.
   Note: :file-content/file must be provided as it's a required reference."
  [file-eid]
  (merge
   (tmg/generate (t/HMap :mandatory {:file-content/content t/Str}
                         :optional {:file-content/compact-ast t/Str}))
   {:file-content/file file-eid}))

(defn gen-file-content-params-with
  "Generate FileContentParams with specific overrides.
   Note: :file-content/file must be provided."
  [file-eid overrides]
  (merge (gen-file-content-params file-eid) overrides))

(defn create-test-file-content
  "Create a file content in the test database and return it.
   Requires a file entity ID and *test-conn* to be bound."
  [conn file-eid]
  (let [params (gen-file-content-params-with
                file-eid
                {:file-content/content (str "(ns test-" (random-uuid) ")")})]
    (file-content/create conn params)
    (file-content/get-latest-by-file (th/test-db) file-eid)))

;; =============================================================================
;; Plan State Helpers
;; =============================================================================

(defn seed-plan-states
  "Seed all plan states into the test database.
   Requires *test-conn* to be bound."
  [conn]
  (plan-state/seed conn))

(defn get-plan-state
  "Get a plan state by ID from the test database.
   Common states: 'draft', 'active', 'completed', 'cancelled'"
  [db state-id]
  (plan-state/get-by-id db state-id))

;; =============================================================================
;; Convenience References
;; =============================================================================

(comment
  ;; Example usage in tests:
  ;; (require '[csb.test-helpers :as th]
  ;;          '[csb.generators :as gen])

  (th/with-test-db
    (fn []
      ;; Generate and create a project
      (let [proj (create-test-project th/*test-conn*)]
        (println "Created project:" (:project/name proj))

        ;; Generate and create a plan for that project
        (let [a-plan (create-test-plan th/*test-conn* (:db/id proj))]
          (println "Created plan:" (:plan/name a-plan)))

        ;; Generate params without creating
        (println "Random params:" (gen-project-params))))))
