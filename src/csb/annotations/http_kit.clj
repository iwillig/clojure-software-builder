(ns csb.annotations.http-kit
  "Type annotations for the http-kit HTTP server library."
  (:require
   [org.httpkit.server :as http]
   [typed.clojure :as t]))

;; ServerStopFn type - function that stops HTTP server
(t/defalias ServerStopFn
  (t/IFn [':timeout t/Int :-> t/Any]))

;; Type annotations for http-kit functions used in the application
(t/ann http/run-server [[t/Any :-> t/Any] t/Any :-> ServerStopFn])
(t/ann http/server-stop! [t/Any :-> t/Any])

;; Reference the namespace to prevent unused import warning
(comment
  http/run-server
  http/server-stop!)
