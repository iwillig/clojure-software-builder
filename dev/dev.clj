(ns dev
  (:require
   [clj-kondo.core :as clj-kondo]
   [clj-reload.core :as reload]
   [clojure.repl :refer [doc]]
   [com.stuartsierra.component :as component]
   [csb.config :as config]
   [csb.system :as system]
   [io.aviso.repl :as repl]
   [kaocha.repl :as k]
   [typed.clojure :as t]))

(repl/install-pretty-exceptions)

(reload/init
 {:dirs ["src" "dev" "test"]})

(def system
  "The system under development"
  nil)

(def initializer
  "No-argument function to return the initial (unstarted) system map
  for use during interactive development. Use `set-init` in your
  development namespace to provide an initializer function."
  (fn []
    (throw (ex-info "initializer not set, did you call `set-init`?"
                    {}))))

(defn set-init
  "Specifies the user-defined initializer function to use for
  constructing the system. Call `set-init` at the top-level in your
  development namespace. init-fn is a function of no arguments that
  should return an (unstarted) component system map."
  [init-fn]
  (alter-var-root #'initializer (constantly init-fn)))

(set-init (fn [] (system/new-system
                  (config/load-config))))

(defn start
  "Initializes and starts a new system running, updates #'system"
  []
  (alter-var-root #'system (fn [_] (component/start (initializer))))
  :ok)

(defn stop
  "Stops the system if it is currently running, updates #'system. Any
  exception thrown during `component/stop` will be printed but
  otherwise ignored."
  []
  (alter-var-root #'system
                  (fn [sys]
                    (when sys
                      (try (component/stop sys)
                           (catch Throwable t
                             (prn t)
                             sys)))))
  :ok)

(defn reset
  "Stops the system, reloads modified source files, and restarts the
  system."
  []
  (stop)
  (let [ret (reload/reload)]
    (if (instance? Throwable ret)
      (throw ret)  ;; let the REPL's exception handling take over
      (do
        (start)
        ret))))

(defn reload
  "Reloads and compiles the Clojure namespaces."
  []
  (reload/reload))

(defn lint
  "Lint the entire project (src and test directories)."
  []
  (-> (clj-kondo/run! {:lint ["src" "test" "dev"]})
      (clj-kondo/print!)))

(defn type-check
  "Checks the types using Clojure typed Clojure"
  []
  (t/check-dir-clj "src"))

(println (doc k/run))
(println (doc reset))
(println (doc reload))
(println (doc lint))
(println (doc type-check))
