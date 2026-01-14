(ns csb.models
  (:require
   [csb.annotations.datalevin]
   [failjure.core]
   [typed.clojure :as t])
  (:import
   (failjure.core
    Failure)
   (java.util.concurrent.atomic
    AtomicLong)))

(set! *warn-on-reflection* true)

(t/defalias Result
  "A result of a model db operation"
  (t/All [a]
         (t/U a Failure)))

(t/ann temp-id AtomicLong)
(defonce ^:private temp-id
  (AtomicLong. 0))

(t/ann next-temp-id [:-> t/Int])

(defn next-temp-id
  []
  (.decrementAndGet ^AtomicLong temp-id))


(comment
  (type Failure)
  (next-temp-id))
