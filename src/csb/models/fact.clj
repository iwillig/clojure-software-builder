(ns csb.models.fact
  "Model for facts (knowledge items with full-text search)."
  (:require
   [csb.annotations.datalevin]
   [csb.annotations.failjure]
   [csb.models :as models]
   [datalevin.core :as d]
   [failjure.core :as f]
   [typed.clojure :as t])
  (:import
   (java.util
    UUID)))

(set! *warn-on-reflection* true)

;; Type aliases
(t/defalias FactParams
  "Input parameters for creating/updating a fact.
   Uses non-namespaced underscore keys that get converted to namespaced keywords."
  (t/HMap :mandatory {:name t/Str}
          :optional {:description t/Str}))

(t/defalias Fact
  "A fact entity from the database"
  (t/HMap :mandatory {:db/id t/Int
                      :fact/id UUID
                      :fact/name t/Str}
          :optional {:fact/description t/Str}))

;; CRUD Operations

(t/ann create [t/Any FactParams :-> t/Any])
(defn create
  "Create a new fact in the database.
   Accepts non-namespaced underscore keys: :name, :description.
   Returns the transaction result or a Failure."
  [conn {:keys [name description]}]
  (f/try*
   (let [id (UUID/randomUUID)
         temp-id (models/next-temp-id)
         tx-data [(cond-> {:db/id temp-id
                           :fact/id id
                           :fact/name name}
                    description (assoc :fact/description description))]]
     (d/transact! conn tx-data))))

(t/ann get-by-id [t/Any UUID :-> t/Any])
(defn get-by-id
  "Get a fact by its UUID.
   Returns the fact map or nil if not found."
  [db id]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?id
         :where [?e :fact/id ?id]]
       db id))

(t/ann get-all [t/Any :-> t/Any])
(defn get-all
  "Get all facts from the database.
   Returns a vector of fact maps."
  [db]
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :fact/id]]
       db))

(t/ann search [t/Any t/Str :-> t/Any])
(defn search
  "Full-text search for facts by name or description.
   Returns a vector of matching fact maps.
   Falls back to simple pattern matching if full-text search is not configured."
  [db query]
  (let [fulltext-result (f/try*
                         (d/q '[:find [(pull ?e [*]) ...]
                                :in $ ?query
                                :where [(fulltext $ ?query) [[?e _ _ _]]]]
                              db query))]
    (if (f/failed? fulltext-result)
      ;; Fallback: if fulltext search fails, do a simple contains search on name
      (d/q '[:find [(pull ?e [*]) ...]
             :in $ ?pattern
             :where
             [?e :fact/name ?name]
             [(clojure.string/includes? ?name ?pattern)]]
           db query)
      fulltext-result)))

(t/ann update-fact [t/Any UUID FactParams :-> t/Any])
(defn update-fact
  "Update an existing fact by UUID.
   Accepts non-namespaced underscore keys: :name, :description.
   Returns the transaction result or a Failure."
  [conn id {:keys [name description]}]
  (f/try*
   (let [fact (get-by-id (d/db conn) id)]
     (if fact
       (let [tx-data [(cond-> {:db/id (:db/id fact)}
                        name (assoc :fact/name name)
                        description (assoc :fact/description description))]]
         (d/transact! conn tx-data))
       (f/fail (str "Fact not found: " id))))))

(t/ann delete-fact [t/Any UUID :-> t/Any])
(defn delete-fact
  "Delete a fact by UUID.
   Returns the transaction result or a Failure."
  [conn id]
  (f/try*
   (let [fact (get-by-id (d/db conn) id)]
     (if fact
       (d/transact! conn [[:db/retractEntity (:db/id fact)]])
       (f/fail (str "Fact not found: " id))))))

(comment
  ;; Example usage
  (require '[csb.components.database :as db])
  (def conn (:connection (db/new-database "test.db")))
  (create conn {:name "Clojure"
                :description "A functional programming language"})
  (get-all (d/db conn))
  (search (d/db conn) "Clojure"))
