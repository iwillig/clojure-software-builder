(ns csb.models.project
  "Model for projects."
  (:require
   [csb.annotations.datalevin]
   [csb.annotations.failjure]
   [csb.models :as models]
   [datalevin.core :as d]
   [failjure.core :as f]
   [typed.clojure :as t])
  (:import
   (java.util
    Date)))

(set! *warn-on-reflection* true)

;; Type aliases
(t/defalias ProjectParams
  "Input parameters for creating/updating a project.
   Uses non-namespaced underscore keys that get converted to namespaced keywords."
  (t/HMap :mandatory {:name t/Str}
          :optional {:description t/Str
                     :path t/Str}))

(t/defalias Project
  "A project entity from the database"
  (t/HMap :mandatory {:db/id t/Int
                      :project/name t/Str
                      :project/created-at Date
                      :project/updated-at Date}
          :optional {:project/description t/Str
                     :project/path t/Str}))

;; CRUD Operations

(t/ann create [t/Any ProjectParams :-> t/Any])
(defn create
  "Create a new project in the database.
   Accepts non-namespaced underscore keys: :name, :description, :path.
   Returns the transaction result or a Failure."
  [conn {:keys [name description path]}]
  (f/try*
   (let [now (Date.)
         temp-id (models/next-temp-id)
         tx-data [(cond-> {:db/id temp-id
                           :project/name name
                           :project/created-at now
                           :project/updated-at now}
                    description (assoc :project/description description)
                    path (assoc :project/path path))]]
     (d/transact! conn tx-data))))

(t/ann get-by-name [t/Any t/Str :-> t/Any])
(defn get-by-name
  "Get a project by its unique name.
   Returns the project map or nil if not found."
  [db name]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?name
         :where [?e :project/name ?name]]
       db name))

(t/ann get-all [t/Any :-> t/Any])
(defn get-all
  "Get all projects from the database.
   Returns a vector of project maps."
  [db]
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :project/name]]
       db))

(t/ann update-project [t/Any t/Int ProjectParams :-> t/Any])
(defn update-project
  "Update an existing project by entity ID.
   Accepts non-namespaced underscore keys: :name, :description, :path.
   Returns the transaction result or a Failure."
  [conn entity-id {:keys [name description path]}]
  (f/try*
   (let [now (Date.)
         tx-data [(cond-> {:db/id entity-id
                           :project/updated-at now}
                    name (assoc :project/name name)
                    description (assoc :project/description description)
                    path (assoc :project/path path))]]
     (d/transact! conn tx-data))))

(t/ann delete-project [t/Any t/Int :-> t/Any])
(defn delete-project
  "Delete a project by entity ID.
   Returns the transaction result or a Failure."
  [conn entity-id]
  (f/try*
   (d/transact! conn [[:db/retractEntity entity-id]])))

(comment
  ;; Example usage
  (require '[csb.components.database :as db])
  (def conn (:connection (db/new-database "test.db")))
  (create conn {:name "my-project"
                :description "A test project"})
  (get-by-name (d/db conn) "my-project")
  (get-all (d/db conn)))
