(ns csb.annotations.clj-http
  "Type annotations for the clj-http HTTP client library."
  (:require
   [clj-http.client :as http]
   [typed.clojure :as t]))

;; Type annotations for clj-http functions used in the application
(t/ann http/get [t/Str :-> t/Any])
(t/ann http/post [t/Str t/Any :-> t/Any])
(t/ann http/put [t/Str t/Any :-> t/Any])
(t/ann http/delete [t/Str :-> t/Any])
(t/ann http/request [t/Any :-> t/Any])

;; Reference the namespace to prevent unused import warning
(comment
  http/get
  http/post
  http/put
  http/delete
  http/request)
