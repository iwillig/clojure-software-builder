(ns csb.models.plan
  "Model for plans (development plans linked to projects)."
  (:require
   [csb.annotations.datalevin]
   [csb.annotations.failjure]
   [csb.models :as models]
   [datalevin.core :as d]
   [failjure.core :as f]
   [typed.clojure :as t])
  (:import
   (java.util
    Date
    UUID)))

(set! *warn-on-reflection* true)

;; Type aliases
(t/defalias PlanParams
  "Input parameters for creating/updating a plan.
   Uses non-namespaced underscore keys that get converted to namespaced keywords."
  (t/HMap :mandatory {:name t/Str
                      :project t/Int}
          :optional {:context t/Str
                     :state t/Int}))

(t/defalias Plan
  "A plan entity from the database"
  (t/HMap :mandatory {:db/id t/Int
                      :plan/id UUID
                      :plan/name t/Str
                      :plan/project t/Int
                      :plan/created-at Date
                      :plan/updated-at Date}
          :optional {:plan/context t/Str
                     :plan/state t/Int}))

;; CRUD Operations

(t/ann create [t/Any PlanParams :-> t/Any])
(defn create
  "Create a new plan in the database.
   Accepts non-namespaced underscore keys: :name, :project, :context, :state.
   Returns the transaction result or a Failure."
  [conn {:keys [name project context state]}]
  (f/try*
   (let [id (UUID/randomUUID)
         now (Date.)
         temp-id (models/next-temp-id)
         tx-data [(cond-> {:db/id temp-id
                           :plan/id id
                           :plan/name name
                           :plan/project project
                           :plan/created-at now
                           :plan/updated-at now}
                    context (assoc :plan/context context)
                    state (assoc :plan/state state))]]
     (d/transact! conn tx-data))))

(t/ann get-by-id [t/Any UUID :-> t/Any])
(defn get-by-id
  "Get a plan by its UUID.
   Returns the plan map or nil if not found."
  [db id]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?id
         :where [?e :plan/id ?id]]
       db id))

(t/ann get-all [t/Any :-> t/Any])
(defn get-all
  "Get all plans from the database.
   Returns a vector of plan maps."
  [db]
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :plan/id]]
       db))

(t/ann get-by-project [t/Any t/Int :-> t/Any])
(defn get-by-project
  "Get all plans for a specific project by project entity ID.
   Returns a vector of plan maps."
  [db project-eid]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?project-eid
         :where [?e :plan/project ?project-eid]]
       db project-eid))

(t/ann get-by-state [t/Any t/Str :-> t/Any])
(defn get-by-state
  "Get all plans with a specific state by state string ID.
   Returns a vector of plan maps."
  [db state-id]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?state-id
         :where [?state :plan-state/id ?state-id]
         [?e :plan/state ?state]]
       db state-id))

(t/ann update-plan [t/Any UUID PlanParams :-> t/Any])
(defn update-plan
  "Update an existing plan by UUID.
   Accepts non-namespaced underscore keys: :name, :project, :context, :state.
   Returns the transaction result or a Failure."
  [conn id {:keys [name project context state]}]
  (f/try*
   (let [plan (get-by-id (d/db conn) id)]
     (if plan
       (let [now (Date.)
             tx-data [(cond-> {:db/id (:db/id plan)
                               :plan/updated-at now}
                        name (assoc :plan/name name)
                        project (assoc :plan/project project)
                        context (assoc :plan/context context)
                        state (assoc :plan/state state))]]
         (d/transact! conn tx-data))
       (f/fail (str "Plan not found: " id))))))

(t/ann delete-plan [t/Any UUID :-> t/Any])
(defn delete-plan
  "Delete a plan by UUID.
   Returns the transaction result or a Failure."
  [conn id]
  (f/try*
   (let [plan (get-by-id (d/db conn) id)]
     (if plan
       (d/transact! conn [[:db/retractEntity (:db/id plan)]])
       (f/fail (str "Plan not found: " id))))))

(comment
  ;; Example usage
  (require '[csb.components.database :as db]
           '[csb.models.project :as project])
  (def conn (:connection (db/new-database "test.db")))
  (project/create conn {:name "my-project"})
  (let [proj (project/get-by-name (d/db conn) "my-project")]
    (create conn {:name "Initial Plan"
                  :project (:db/id proj)
                  :context "Build the MVP"}))
  (get-all (d/db conn)))
