(ns csb.routes
  "API routes using bidi. Each path maps to a single Liberator resource
   that handles all HTTP methods internally via :allowed-methods."
  (:require
   [csb.api.resources.fact :as fact]
   [csb.api.resources.file :as file]
   [csb.api.resources.file-content :as file-content]
   [csb.api.resources.plan :as plan]
   [csb.api.resources.project :as project]
   [csb.api.resources.task :as task]
   [typed.clojure :as t]))

(set! *warn-on-reflection* true)

;; -----------------------------------------------------------------------------
;; Route Definitions
;; -----------------------------------------------------------------------------
;; Routes are defined as a function that takes a database value and returns
;; bidi route structure. This allows resources to close over the database.
;;
;; Each path maps to ONE handler that handles all methods (GET, POST, PUT, etc.)
;; Liberator resources use :allowed-methods to determine valid methods.

(t/ann api-routes [t/Any :-> t/Any])
(defn api-routes
  "Generate bidi routes with database access.
   Returns a bidi route structure where each path maps to a Liberator resource."
  [db]
  ["" [;; Health check endpoint
       ["/health" (fn [_request]
                    {:status 200
                     :headers {"Content-Type" "application/json"}
                     :body "{\"status\":\"ok\"}"})]

       ;; Project routes
       ["/api/projects" (project/collection db)]
       [["/api/projects/" :id] (partial project/item db)]

       ;; Plan routes
       [["/api/projects/" :project-id "/plans"] (partial plan/by-project db)]
       ["/api/plans" (plan/collection db)]
       [["/api/plans/" :id] (partial plan/item db)]

       ;; Task routes
       [["/api/plans/" :plan-id "/tasks"] (partial task/by-plan db)]
       ["/api/tasks" (task/collection db)]
       [["/api/tasks/" :id] (partial task/item db)]
       [["/api/tasks/" :parent-id "/subtasks"] (partial task/subtasks db)]

       ;; File routes
       [["/api/projects/" :project-id "/files"] (partial file/by-project db)]
       ["/api/files" (file/collection db)]
       [["/api/files/" :id] (partial file/item db)]

       ;; File content routes
       [["/api/files/" :file-id "/content"] (partial file-content/by-file db)]
       ["/api/file-content" (file-content/collection db)]
       [["/api/file-content/" :id] (partial file-content/item db)]

       ;; Fact routes
       ["/api/facts" (fact/collection db)]
       ["/api/facts/search" (fact/search-resource db)]
       [["/api/facts/" :id] (partial fact/item db)]]])

(comment
  ;; Example of how routes work:
  ;; (api-routes some-db)
  ;; => ["" [["/api/projects" #<resource>]
  ;;         [["/api/projects/" :id] #<fn>]
  ;;         ...]]
  ;;
  ;; The partial function allows route params to be passed to the resource
  ;; constructor when the route is matched.
  )
