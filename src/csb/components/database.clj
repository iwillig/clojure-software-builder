(ns csb.components.database
  (:require [typed.clojure :as t]
            [com.stuartsierra.component :as c]))

(t/ann-record Database [db-path :- t/Str])

(defrecord Database [db-path]
  c/Lifecycle
  (start [_self])
  (stop  [_self]))

(comment
  (->Database nil))
