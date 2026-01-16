(ns csb.system
  (:require
   [com.stuartsierra.component :as c]
   [csb.annotations.component]
   [csb.components.database :as database]
   [csb.components.http-server :as http-server]
   [csb.components.ring-app :as ring-app]
   [csb.config]
   [csb.routes :as routes]
   [typed.clojure :as t])
  (:import
   (com.stuartsierra.component
    SystemMap)
   (csb.config
    Config)))

(t/ann new-system [Config :-> t/Any])

(defn new-system
  [config]
  (t/tc-ignore
   (c/system-map
    :database (database/new-database (:db-path config))
    :ring-app (c/using
               (ring-app/new-ring-app routes/api-routes)
               [:database])
    :http-server (c/using
                  (http-server/->http-server (:port config))
                  [:ring-app]))))


(comment
  (Config.)
  (SystemMap.)
  (new-system {}))
