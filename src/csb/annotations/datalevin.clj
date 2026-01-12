(ns csb.annotations.datalevin
  "Type annotations for the datalevin database library."
  (:require
   [datalevin.core :as d]
   [typed.clojure :as t]))

;; Type annotations for datalevin functions used in the application
(t/ann d/create-conn [t/Str t/Any :-> (t/Atom1 t/Any)])
(t/ann d/close [t/Any :-> nil])
(t/ann d/transact! [(t/Atom1 t/Any) (t/Vec t/Any) :-> (t/Vec t/Any)])
(t/ann d/q [t/Any (t/Atom1 t/Any) :-> (t/Vec t/Any)])
(t/ann d/db [(t/Atom1 t/Any) :-> t/Any])

;; Reference the namespace to prevent unused import warning
(comment
  d/create-conn
  d/close
  d/transact!
  d/q
  d/db)
