(ns csb.components.ring-app
  (:require
   [bidi.bidi :as bidi]
   [com.stuartsierra.component :as c]
   [csb.annotations.bidi]
   [typed.clojure :as t]))

(t/ann-record RingApp [routes :- t/Any
                       handler :- (t/Option (t/IFn [t/Any :-> t/Any]))])

(defrecord RingApp [routes handler]
  c/Lifecycle
  (start [this]
    (let [handler (t/ann-form
                   (fn [request]
                     (let [matched (bidi/match-route routes (:uri request))]
                       (if matched
                         (t/tc-ignore ((:handler matched) request))
                         {:status 404 :body "Not found"})))
                   [t/Any :-> t/Any])]
      (assoc this :handler handler)))
  (stop [this]
    (assoc this :handler nil)))

(t/ann ->ring-app [t/Any :-> RingApp])

(defn ->ring-app [routes]
  (map->RingApp {:routes routes :handler nil}))
