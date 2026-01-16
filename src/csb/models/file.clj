(ns csb.models.file
  "Model for files (project files with summaries)."
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
(t/defalias FileParams
  "Input parameters for creating/updating a file.
   Uses non-namespaced underscore keys that get converted to namespaced keywords."
  (t/HMap :mandatory {:path t/Str
                      :project t/Int}
          :optional {:summary t/Str}))

(t/defalias File
  "A file entity from the database"
  (t/HMap :mandatory {:db/id t/Int
                      :file/id UUID
                      :file/path t/Str
                      :file/project t/Int
                      :file/created-at Date
                      :file/updated-at Date}
          :optional {:file/summary t/Str}))

;; CRUD Operations

(t/ann create [t/Any FileParams :-> t/Any])
(defn create
  "Create a new file in the database.
   Accepts non-namespaced underscore keys: :path, :project, :summary.
   Returns the transaction result or a Failure."
  [conn {:keys [path project summary]}]
  (f/try*
   (let [id (UUID/randomUUID)
         now (Date.)
         temp-id (models/next-temp-id)
         tx-data [(cond-> {:db/id temp-id
                           :file/id id
                           :file/path path
                           :file/project project
                           :file/created-at now
                           :file/updated-at now}
                    summary (assoc :file/summary summary))]]
     (d/transact! conn tx-data))))

(t/ann get-by-id [t/Any UUID :-> t/Any])
(defn get-by-id
  "Get a file by its UUID.
   Returns the file map or nil if not found."
  [db id]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?id
         :where [?e :file/id ?id]]
       db id))

(t/ann get-all [t/Any :-> t/Any])
(defn get-all
  "Get all files from the database.
   Returns a vector of file maps."
  [db]
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :file/id]]
       db))

(t/ann get-by-project [t/Any t/Int :-> t/Any])
(defn get-by-project
  "Get all files for a specific project by project entity ID.
   Returns a vector of file maps."
  [db project-eid]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?project-eid
         :where [?e :file/project ?project-eid]]
       db project-eid))

(t/ann get-by-path [t/Any t/Int t/Str :-> t/Any])
(defn get-by-path
  "Get a file by project entity ID and path.
   Returns the file map or nil if not found."
  [db project-eid path]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?project-eid ?path
         :where [?e :file/project ?project-eid]
         [?e :file/path ?path]]
       db project-eid path))

(t/ann update-file [t/Any UUID FileParams :-> t/Any])
(defn update-file
  "Update an existing file by UUID.
   Accepts non-namespaced underscore keys: :path, :project, :summary.
   Returns the transaction result or a Failure."
  [conn id {:keys [path project summary]}]
  (f/try*
   (let [file (get-by-id (d/db conn) id)]
     (if file
       (let [now (Date.)
             tx-data [(cond-> {:db/id (:db/id file)
                               :file/updated-at now}
                        path (assoc :file/path path)
                        project (assoc :file/project project)
                        summary (assoc :file/summary summary))]]
         (d/transact! conn tx-data))
       (f/fail (str "File not found: " id))))))

(t/ann delete-file [t/Any UUID :-> t/Any])
(defn delete-file
  "Delete a file by UUID.
   Returns the transaction result or a Failure."
  [conn id]
  (f/try*
   (let [file (get-by-id (d/db conn) id)]
     (if file
       (d/transact! conn [[:db/retractEntity (:db/id file)]])
       (f/fail (str "File not found: " id))))))

(comment
  ;; Example usage
  (require '[csb.components.database :as db]
           '[csb.models.project :as project])
  (def conn (:connection (db/new-database "test.db")))
  (project/create conn {:name "my-project"})
  (let [proj (project/get-by-name (d/db conn) "my-project")]
    (create conn {:path "src/core.clj"
                  :project (:db/id proj)
                  :summary "Core namespace"}))
  (get-all (d/db conn)))
