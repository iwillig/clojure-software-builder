(ns csb.annotations.liberator
  "Type annotations for the liberator REST library."
  (:require
   [liberator.core :as l]
   [typed.clojure :as t]))

;; Type annotations for liberator macros and functions
(t/ann l/defresource [t/Sym t/Any :* :-> t/Any])

;; Reference the namespace to prevent unused import warning
(comment
  l/defresource)
