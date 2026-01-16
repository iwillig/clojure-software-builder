(ns csb.components.ring-app
  (:require
   [bidi.bidi :as bidi]
   [com.stuartsierra.component :as c]
   [csb.annotations.bidi]
   [datalevin.core :as d]
   [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
   [typed.clojure :as t]))

(set! *warn-on-reflection* true)

;; -----------------------------------------------------------------------------
;; Ring App Component
;; -----------------------------------------------------------------------------
;; The RingApp component creates a Ring handler from bidi routes.
;; It depends on the Database component to provide DB access to routes.
;;
;; - routes-fn: A function (fn [db] -> bidi-routes) that generates routes
;;              with database access
;; - database: Injected Database component (via component/using)
;; - handler: The compiled Ring handler (set on start)

(t/ann-record RingApp [routes-fn :- (t/Option [t/Any :-> t/Any])
                       database :- t/Any
                       handler :- (t/Option (t/IFn [t/Any :-> t/Any]))])

(defrecord RingApp [routes-fn database handler]
  c/Lifecycle
  (start [this]
    (let [conn (:connection database)
          db (when conn (d/db conn))
          routes (when (and routes-fn db) (routes-fn db))
          base-handler (t/ann-form
                        (fn [request]
                          (if routes
                            (let [matched (bidi/match-route routes (:uri request))]
                              (if matched
                                (t/tc-ignore
                                 (let [handler-fn (:handler matched)
                                       route-params (:route-params matched)
                                       request-with-params (assoc request :route-params route-params)]
                                   (handler-fn request-with-params)))
                                {:status 404
                                 :headers {"Content-Type" "text/plain"}
                                 :body "Not found"}))
                            {:status 503
                             :headers {"Content-Type" "text/plain"}
                             :body "Service not ready - no routes configured"}))
                        [t/Any :-> t/Any])
          ;; Wrap with JSON middleware
          ;; - wrap-json-body: parses JSON request bodies into :body as Clojure data
          ;; - wrap-json-response: converts Clojure data responses to JSON
          wrapped-handler (-> base-handler
                              (wrap-json-body {:keywords? true})
                              wrap-json-response)]
      (assoc this :handler wrapped-handler)))
  (stop [this]
    (assoc this :handler nil)))

;; -----------------------------------------------------------------------------
;; Constructor Functions
;; -----------------------------------------------------------------------------

(t/ann new-ring-app [[t/Any :-> t/Any] :-> RingApp])
(defn new-ring-app
  "Create a new RingApp component.
   - routes-fn: A function that takes a database value and returns bidi routes"
  [routes-fn]
  (map->RingApp {:routes-fn routes-fn}))

(t/ann ->ring-app [t/Any :-> RingApp])
(defn ->ring-app
  "Create a RingApp with static routes (legacy compatibility).
   Prefer new-ring-app for database-aware routes."
  [routes]
  (map->RingApp {:routes-fn (constantly routes)}))

(t/ann get-handler [(t/Option RingApp) :-> (t/Option (t/IFn [t/Any :-> t/Any]))])
(defn get-handler
  "Get the Ring handler from a RingApp component."
  [component]
  (when component
    (get component :handler)))

(comment
  (get-handler {})
  (->ring-app [])
  (new-ring-app (fn [_db] ["/" {"api" {:get (fn [_] {:status 200 :body "ok"})}}])))
