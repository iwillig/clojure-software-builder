(ns csb.models.plan-state
  "Model for plan states (enum-like entity)."
  (:require
   [csb.annotations.datalevin]
   [csb.annotations.failjure]
   [csb.models :as models]
   [datalevin.core :as d]
   [failjure.core :as f]
   [typed.clojure :as t]))

(set! *warn-on-reflection* true)

;; Type aliases
(t/defalias PlanStateId
  "String identifier for plan state (e.g., 'draft', 'active')"
  t/Str)

(t/defalias PlanState
  "A plan state entity from the database"
  (t/HMap :mandatory {:db/id t/Int
                      :plan-state/id PlanStateId}))

;; Predefined states
(t/ann states (t/Vec t/Str))
(def states
  "Available plan states"
  ["draft" "active" "completed" "cancelled"])

(t/ann seed [t/Any :-> t/Any])
(defn seed
  "Insert predefined plan states into the database.
   Safe to call multiple times due to :db.unique/value on :plan-state/id."
  [conn]
  (f/try*
   (let [tx-data (mapv (fn [state-id]
                         {:db/id (models/next-temp-id)
                          :plan-state/id state-id})
                       states)]
     (d/transact! conn tx-data))))

(t/ann get-all [t/Any :-> t/Any])
(defn get-all
  "Get all plan states from the database."
  [db]
  (d/q '[:find [(pull ?e [*]) ...]
         :where [?e :plan-state/id]]
       db))

(t/ann get-by-id [t/Any PlanStateId :-> t/Any])
(defn get-by-id
  "Get a plan state by its string ID."
  [db state-id]
  (d/q '[:find (pull ?e [*]) .
         :in $ ?state-id
         :where [?e :plan-state/id ?state-id]]
       db state-id))

(comment
  ;; Example usage
  (require '[csb.components.database :as db])
  (def conn (:connection (db/new-database "test.db")))
  (seed conn)
  (get-all (d/db conn))
  (get-by-id (d/db conn) "draft"))
