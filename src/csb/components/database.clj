(ns csb.components.database
  (:require
   [com.stuartsierra.component :as c]
   [csb.components]
   [datalevin.core :as d]
   [typed.clojure :as t]))


(t/ann d/create-conn [t/Str :-> (t/Atom1 t/Any)])
(t/ann d/close  [t/Any :-> nil])


(t/ann-record Database [db-path :- t/Str
                        connection :- t/Any])

(defrecord Database [db-path connection]
  c/Lifecycle
  (start [this]
    (let [conn (d/create-conn db-path)]
      (assoc this :connection conn)))
  (stop [this]
    (when-let [conn (:connection this)]
      (d/close conn))
    (assoc this :connection nil)))

(t/ann new-database [t/Str :-> Database])

(defn new-database
  [db-path]
  (map->Database {:db-path db-path}))

(comment
  (new-database "test.db"))
