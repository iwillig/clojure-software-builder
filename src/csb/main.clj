(ns csb.main
  (:gen-class)
  (:require [typed.clojure :as t]))


(t/ann -main [t/Str :* -> nil])

(defn -main
  [& args]
  (println args))
