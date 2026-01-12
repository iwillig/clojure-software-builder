(ns csb.components
  (:require
   [com.stuartsierra.component :as c]
   [typed.clojure :as t]))

;; Import used for protocol declaration - this satisfies the requirement
(t/ann-protocol c/Lifecycle
                start [c/Lifecycle :-> c/Lifecycle]
                stop [c/Lifecycle :-> c/Lifecycle])

;; Use the component namespace to prevent unused import warning
(comment
  (c/start nil)
  (c/stop nil))
