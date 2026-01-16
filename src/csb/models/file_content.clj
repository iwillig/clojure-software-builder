(ns csb.models.file-content
  "Model for file contents (file content with optional AST)."
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
(t/defalias FileContentParams
  "Input parameters for creating/updating file content.
   Uses non-namespaced underscore keys that get converted to namespaced keywords."
  (t/HMap :mandatory {:file t/Int
                      :content t/Str}
          :optional {:compact_ast t/Str
                     :parsed_at Date}))

(t/defalias FileContent
  "A file content entity from the database"
  (t/HMap :mandatory {:db/id t/Int
                      :file-content/id UUID
                      :file-content/file t/Int
                      :file-content/content t/Str
                      :file-content/created-at Date
                      :file-content/updated-at Date}
          :optional {:file-content/compact-ast t/Str
                     :file-content/parsed-at Date}))

;; CRUD Operations

(t/ann create [t/Any FileContentParams :-> t/Any])
(defn create
  "Create a new file content in the database.
   Accepts non-namespaced underscore keys: :file, :content, :compact_ast, :parsed_at.
   Returns the transaction result or a Failure."
  [conn {:keys [file content compact_ast parsed_at]}]
  (f/try*
   (let [id (UUID/randomUUID)
         now (Date.)
         temp-id (models/next-temp-id)
         tx-data [(cond-> {:db/id temp-id
                           :file-content/id id
                           :file-content/file file
                           :file-content/content content
                           :file-content/created-at now
                           :file-content/updated-at now}
                    compact_ast (assoc :file-content/compact-ast compact_ast)
                    parsed_at (assoc :file-content/parsed-at parsed_at))]]
     (d/transact! conn tx-data))))

(t/ann get-by-id [t/Any UUID :-> t/Any])
(defn get-by-id
  "Get file content by its UUID.
   Returns the file content map or nil if not found."
  [db id]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?id
         :where [?e :file-content/id ?id]]
       db id))

(t/ann get-all [t/Any :-> t/Any])
(defn get-all
  "Get all file contents from the database.
   Returns a vector of file content maps."
  [db]
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :file-content/id]]
       db))

(t/ann get-by-file [t/Any t/Int :-> t/Any])
(defn get-by-file
  "Get all file contents for a specific file by file entity ID.
   Returns a vector of file content maps."
  [db file-eid]
  (d/q '[:find [(pull ?e [*]) ...]
         :in $ ?file-eid
         :where [?e :file-content/file ?file-eid]]
       db file-eid))

(t/ann get-latest-by-file [t/Any t/Int :-> t/Any])
(defn get-latest-by-file
  "Get the most recent file content for a specific file.
   Returns the file content map or nil if not found."
  [db file-eid]
  (t/tc-ignore
   (let [contents (get-by-file db file-eid)]
     (when (seq contents)
       (->> contents
            (sort-by :file-content/created-at)
            last)))))

(t/ann update-file-content [t/Any UUID FileContentParams :-> t/Any])
(defn update-file-content
  "Update an existing file content by UUID.
   Accepts non-namespaced underscore keys: :file, :content, :compact_ast, :parsed_at.
   Returns the transaction result or a Failure."
  [conn id {:keys [file content compact_ast parsed_at]}]
  (f/try*
   (let [fc (get-by-id (d/db conn) id)]
     (if fc
       (let [now (Date.)
             tx-data [(cond-> {:db/id (:db/id fc)
                               :file-content/updated-at now}
                        file (assoc :file-content/file file)
                        content (assoc :file-content/content content)
                        compact_ast (assoc :file-content/compact-ast compact_ast)
                        parsed_at (assoc :file-content/parsed-at parsed_at))]]
         (d/transact! conn tx-data))
       (f/fail (str "File content not found: " id))))))

(t/ann delete-file-content [t/Any UUID :-> t/Any])
(defn delete-file-content
  "Delete file content by UUID.
   Returns the transaction result or a Failure."
  [conn id]
  (f/try*
   (let [content (get-by-id (d/db conn) id)]
     (if content
       (d/transact! conn [[:db/retractEntity (:db/id content)]])
       (f/fail (str "File content not found: " id))))))

(comment
  ;; Example usage
  (require '[csb.components.database :as db]
           '[csb.models.project :as project]
           '[csb.models.file :as file])
  (def conn (:connection (db/new-database "test.db")))
  (project/create conn {:name "my-project"})
  (let [proj (project/get-by-name (d/db conn) "my-project")]
    (file/create conn {:path "src/core.clj" :project (:db/id proj)})
    (let [f (first (file/get-all (d/db conn)))]
      (create conn {:file (:db/id f)
                    :content "(ns core)"})))
  (get-all (d/db conn)))
