(ns csb.annotations.http-kit
  "Type annotations for the http-kit HTTP server library."
  (:require
   [org.httpkit.server :as http]
   [typed.clojure :as t]))

;; Type annotations for http-kit functions used in the application
(t/ann http/run-server [(t/Fn [t/Any :-> t/Any]) t/Any :-> (t/Atom1 t/Any)])
(t/ann http/server-stop! [(t/Atom1 t/Any) :-> t/Any])

;; Reference the namespace to prevent unused import warning
(comment
  http/run-server
  http/server-stop!)
