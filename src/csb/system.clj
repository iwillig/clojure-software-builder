(ns csb.system
  (:require
   [com.stuartsierra.component :as c]
   [csb.components.database :as database]
   [csb.config]
   [typed.clojure :as t])
  (:import
   (com.stuartsierra.component
    SystemMap)
   (csb.config
    Config)))

(t/ann new-system [Config -> SystemMap])
(t/ann c/system-map [t/Any :* -> SystemMap])


(defn new-system
  [config]
  (c/system-map
   :database (database/new-database (:db-path config))))


(comment
  (Config.)
  (SystemMap.)
  (new-system {}))
