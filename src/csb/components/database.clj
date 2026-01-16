(ns csb.components.database
  (:require
   [clojure.edn :as edn]
   [clojure.java.io :as io]
   [com.stuartsierra.component :as c]
   [csb.annotations.datalevin]
   [csb.components]
   [datalevin.core :as d]
   [typed.clojure :as t])
  (:import
   (java.io
    PushbackReader)))

(t/ann schema-path t/Str)
(def schema-path
  "db/schema.edn")

(t/ann schema [:-> t/Map])

(t/ann edn/read [t/Any :-> t/Map])

(defn schema
  []
  (with-open [reader (io/reader
                      (io/resource schema-path))]
    (edn/read (PushbackReader. reader))))


(t/ann d/create-conn [t/Str t/Any :-> (t/Atom1 t/Any)])
(t/ann d/close  [t/Any :-> nil])


(t/ann-record Database [db-path :- t/Str
                        connection :- t/Any])

(defrecord Database [db-path connection]
  c/Lifecycle
  (start [this]
    (let [conn (d/create-conn db-path (schema))]
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
