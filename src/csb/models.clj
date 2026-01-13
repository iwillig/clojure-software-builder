(ns csb.models
  (:require
   [typed.clojure :as t])
  (:import
   (java.util.concurrent.atomic
    AtomicLong)))

(set! *warn-on-reflection* true)

(t/ann temp-id AtomicLong)
(defonce ^:private temp-id
  (AtomicLong. 0))

(t/ann next-temp-id [:-> t/Int])

(defn next-temp-id
  []
  (.decrementAndGet ^AtomicLong temp-id))
