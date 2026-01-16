(ns csb.annotations.failjure
  "Type annotations for the failjure error handling library."
  (:require
   [failjure.core :as f]
   [typed.clojure :as t])
  (:import
   (failjure.core
    Failure)))

;; Core failjure functions
(t/ann f/fail [t/Any :-> Failure])
(t/ann f/failed? [t/Any :-> t/Bool])
(t/ann f/message [t/Any :-> t/Any])
(t/ann f/try-fn [[:-> t/Any] :-> t/Any])

;; Reference the namespace to prevent unused import warning
(comment
  f/fail
  f/failed?
  f/message
  f/try-fn
  Failure)
