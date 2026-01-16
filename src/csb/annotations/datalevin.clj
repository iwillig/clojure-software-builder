(ns csb.annotations.datalevin
  "Type annotations for the datalevin database library."
  (:require
   [datalevin.core :as d]
   [typed.clojure :as t])
  (:import
   (datalevin.db
    DB)))

;; Type annotations for datalevin functions used in the application
(t/ann d/create-conn [t/Str t/Any :-> (t/Atom1 t/Any)])
(t/ann d/close [t/Any :-> nil])
(t/ann d/transact! [t/Any t/Any :-> t/Any])
(t/ann d/q [t/Any t/Any :* :-> t/Any])
(t/ann d/db [t/Any :-> t/Any])
(t/ann d/conn-from-db [t/Any :-> t/Any])
(t/ann d/pull [t/Any t/Any t/Any :-> t/Any])

(t/defalias DatalevinConn
  "Datalevin database connection atom"
  (t/Atom1 DB))


;; Reference the namespace to prevent unused import warning
(comment
  d/create-conn
  d/close
  d/transact!
  d/q
  d/db
  d/conn-from-db
  d/pull
  (type DB))
