(ns csb.annotations.component
  (:require
   [com.stuartsierra.component :as c]
   [typed.clojure :as t])
  (:import
   (com.stuartsierra.component
    SystemMap)))

(t/ann c/using [t/Any t/Map :-> c/Lifecycle])

(t/ann c/system-map [t/Any :* :-> SystemMap])

(comment
  (pr c/using)
  (pr SystemMap))
