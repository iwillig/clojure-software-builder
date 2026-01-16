(ns csb.components.http-server
  (:require
   [com.stuartsierra.component :as c]
   [csb.annotations.http-kit :as http-kit]
   [csb.components.ring-app :as ring-app]
   [org.httpkit.server :as http]
   [typed.clojure :as t])
  (:import
   (csb.components.ring_app
    RingApp)))

(t/ann-record HttpServer [port :- t/Int
                          server :- (t/Option http-kit/ServerStopFn)
                          ring-app :- (t/Option RingApp)])


(defrecord HttpServer [port server ring-app]
  c/Lifecycle
  (start [this]
    (let [handler (or (ring-app/get-handler ring-app)
                      (t/ann-form
                       (fn [_] {:status 503 :body "Service not ready"})
                       [t/Any :-> t/Any]))
          server (http/run-server handler {:port port})]
      (assoc this :server server)))
  (stop [this]
    (when server
      (server :timeout 1000))
    (assoc this :server nil)))

(t/ann ->http-server [t/Int :-> t/Any])

(defn ->http-server [port]
  (t/tc-ignore
   (map->HttpServer {:port port :server nil})))


(comment
  (pr http-kit/ServerTopFn)
  (type RingApp)
  (->http-server 3000))
