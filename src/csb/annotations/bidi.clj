(ns csb.annotations.bidi
  "Type annotations for the bidi routing library."
  (:require
   [bidi.bidi :as bidi]
   [typed.clojure :as t]))

;; Type annotations for bidi functions used in the application
(t/ann bidi/match-route [t/Any t/Any :-> (t/Option t/Any)])
(t/ann bidi/path-for [t/Any t/Any :* :-> t/Str])

;; Reference the namespace to prevent unused import warning
(comment
  bidi/match-route
  bidi/path-for)
