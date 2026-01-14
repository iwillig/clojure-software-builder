(ns csb.system
  (:require
   [com.stuartsierra.component :as c]
   [csb.annotations.component]
   [csb.components.database :as database]
   [csb.components.http-server :as http-server]
   [csb.components.ring-app :as ring-app]
   [csb.config]
   [typed.clojure :as t])
  (:import
   (com.stuartsierra.component
    SystemMap)
   (csb.config
    Config)))

(t/ann new-system [Config :-> SystemMap])

(defn new-system
  [config]
  (c/system-map
   :database (database/new-database (:db-path config))
   :ring-app (ring-app/->ring-app [])
   :http-server (http-server/->http-server (:port config))))


(comment
  (Config.)
  (SystemMap.)
  (new-system {}))
