(ns csb.annotations.mulog
  "Type annotations for the mulog logging library."
  (:require
   [com.brunobonacci.mulog.core :as mulog-core]
   [typed.clojure :as t]))

;; Type annotations for mulog internal vars used by the u/log macro
;; These must be annotated because the u/log macro expands to use them
(t/ann mulog-core/log* [t/Any t/Any :* :-> nil])
(t/ann mulog-core/*default-logger* t/Any)

;; Reference the namespace to prevent unused import warning
(comment
  mulog-core/log*
  mulog-core/*default-logger*)
