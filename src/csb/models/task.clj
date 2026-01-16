(ns csb.models.task
  "Model for tasks (hierarchical work items within plans)."
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
(t/defalias TaskParams
  "Input parameters for creating/updating a task.
   Uses non-namespaced underscore keys that get converted to namespaced keywords."
  (t/HMap :mandatory {:name t/Str
                      :plan t/Int}
          :optional {:parent t/Int
                     :context t/Str
                     :completed t/Bool}))

(t/defalias Task
  "A task entity from the database"
  (t/HMap :mandatory {:db/id t/Int
                      :task/id UUID
                      :task/name t/Str
                      :task/plan t/Int
                      :task/created-at Date
                      :task/updated-at Date}
          :optional {:task/parent t/Int
                     :task/context t/Str
                     :task/completed t/Bool}))

;; CRUD Operations

(t/ann create [t/Any TaskParams :-> t/Any])
(defn create
  "Create a new task in the database.
   Accepts non-namespaced underscore keys: :name, :plan, :parent, :context, :completed.
   Returns the transaction result or a Failure."
  [conn {:keys [name plan parent context completed]}]
  (f/try*
   (let [id (UUID/randomUUID)
         now (Date.)
         temp-id (models/next-temp-id)
         tx-data [(cond-> {:db/id temp-id
                           :task/id id
                           :task/name name
                           :task/plan plan
                           :task/completed (or completed false)
                           :task/created-at now
                           :task/updated-at now}
                    parent (assoc :task/parent parent)
                    context (assoc :task/context context))]]
     (d/transact! conn tx-data))))

(t/ann get-by-id [t/Any UUID :-> t/Any])
(defn get-by-id
  "Get a task by its UUID.
   Returns the task map or nil if not found."
  [db id]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?id
         :where [?e :task/id ?id]]
       db id))

(t/ann get-all [t/Any :-> t/Any])
(defn get-all
  "Get all tasks from the database.
   Returns a vector of task maps."
  [db]
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :task/id]]
       db))

(t/ann get-by-plan [t/Any t/Int :-> t/Any])
(defn get-by-plan
  "Get all tasks for a specific plan by plan entity ID.
   Returns a vector of task maps."
  [db plan-eid]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?plan-eid
         :where [?e :task/plan ?plan-eid]]
       db plan-eid))

(t/ann get-children [t/Any t/Int :-> t/Any])
(defn get-children
  "Get all child tasks for a specific parent task by entity ID.
   Returns a vector of task maps."
  [db parent-eid]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?parent-eid
         :where [?e :task/parent ?parent-eid]]
       db parent-eid))

(t/ann get-root-tasks [t/Any t/Int :-> t/Any])
(defn get-root-tasks
  "Get all root tasks (tasks without parents) for a specific plan.
   Returns a vector of task maps."
  [db plan-eid]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?plan-eid
         :where [?e :task/plan ?plan-eid]
         (not [?e :task/parent])]
       db plan-eid))

(t/ann get-incomplete [t/Any t/Int :-> t/Any])
(defn get-incomplete
  "Get all incomplete tasks for a specific plan.
   Returns a vector of task maps."
  [db plan-eid]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?plan-eid
         :where [?e :task/plan ?plan-eid]
         (not [?e :task/completed true])]
       db plan-eid))

(t/ann get-completed [t/Any t/Int :-> t/Any])
(defn get-completed
  "Get all completed tasks for a specific plan.
   Returns a vector of task maps."
  [db plan-eid]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?plan-eid
         :where [?e :task/plan ?plan-eid]
         [?e :task/completed true]]
       db plan-eid))

(t/ann mark-completed [t/Any UUID :-> t/Any])
(defn mark-completed
  "Mark a task as completed by UUID.
   Returns the transaction result or a Failure."
  [conn id]
  (f/try*
   (let [task (get-by-id (d/db conn) id)]
     (if task
       (let [now (Date.)
             tx-data [{:db/id (:db/id task)
                       :task/completed true
                       :task/updated-at now}]]
         (d/transact! conn tx-data))
       (f/fail (str "Task not found: " id))))))

(t/ann update-task [t/Any UUID TaskParams :-> t/Any])
(defn update-task
  "Update an existing task by UUID.
   Accepts non-namespaced underscore keys: :name, :plan, :parent, :context, :completed.
   Returns the transaction result or a Failure."
  [conn id {:keys [name plan parent context completed]}]
  (f/try*
   (let [task (get-by-id (d/db conn) id)]
     (if task
       (let [now (Date.)
             tx-data [(cond-> {:db/id (:db/id task)
                               :task/updated-at now}
                        name (assoc :task/name name)
                        plan (assoc :task/plan plan)
                        parent (assoc :task/parent parent)
                        context (assoc :task/context context)
                        (some? completed) (assoc :task/completed completed))]]
         (d/transact! conn tx-data))
       (f/fail (str "Task not found: " id))))))

(t/ann delete-task [t/Any UUID :-> t/Any])
(defn delete-task
  "Delete a task by UUID.
   Returns the transaction result or a Failure."
  [conn id]
  (f/try*
   (let [task (get-by-id (d/db conn) id)]
     (if task
       (d/transact! conn [[:db/retractEntity (:db/id task)]])
       (f/fail (str "Task not found: " id))))))

(comment
  ;; Example usage
  (require '[csb.components.database :as db]
           '[csb.models.project :as project]
           '[csb.models.plan :as plan])
  (def conn (:connection (db/new-database "test.db")))
  (project/create conn {:name "my-project"})
  (let [proj (project/get-by-name (d/db conn) "my-project")]
    (plan/create conn {:name "Plan 1" :project (:db/id proj)})
    (let [p (first (plan/get-all (d/db conn)))]
      (create conn {:name "Task 1" :plan (:db/id p)})
      (create conn {:name "Task 2" :plan (:db/id p)})))
  (get-all (d/db conn)))
