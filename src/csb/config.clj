(ns csb.config
  (:require [typed.clojure :as t]))


(defrecord Config [port db-path])

(t/ann-record Config [port :- t/Int
                      db-path :- t/Str])


(defn load-config
  []
  (map->Config {:port    3000
                :db-path "db-path"}))

(t/ann load-config
       [nil :-> Config])
