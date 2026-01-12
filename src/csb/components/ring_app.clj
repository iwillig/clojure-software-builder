(ns csb.components.ring-app
  (:require
   [bidi.bidi :as bidi]
   [com.stuartsierra.component :as c]
   [csb.annotations.bidi]
   [typed.clojure :as t]))

(t/ann-record RingApp [routes :- t/Any
                       handler :- (t/Fn [t/Any :-> t/Any])])

(defrecord RingApp [routes handler]
  c/Lifecycle
  (start [this]
    (let [handler (fn [request]
                    (let [matched (bidi/match-route routes (:uri request))]
                      (if matched
                        ((:handler matched) request)
                        {:status 404 :body "Not found"})))]
      (assoc this :handler handler)))
  (stop [this]
    (assoc this :handler nil)))

(t/ann ->ring-app [t/Any :-> RingApp])

(defn ->ring-app [routes]
  (map->RingApp {:routes routes}))
