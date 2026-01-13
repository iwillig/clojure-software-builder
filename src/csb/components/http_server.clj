(ns csb.components.http-server
  (:require
   [com.stuartsierra.component :as c]
   [csb.annotations.http-kit]
   [org.httpkit.server :as http]
   [typed.clojure :as t]))

(t/ann-record HttpServer [port :- t/Int
                          server :- (t/Option t/Any)])

(defrecord HttpServer [port server]
  c/Lifecycle
  (start [this]
    (let [handler (fn [_] {:status 200
                           :body "Hello from HTTP server!"})
          server (http/run-server handler {:port port})]
      (assoc this :server server)))
  (stop [this]
    (when server
      (http/server-stop! server))
    (assoc this :server nil)))

(t/ann ->http-server [t/Int :-> HttpServer])

(defn ->http-server [port]
  (map->HttpServer {:port port}))
