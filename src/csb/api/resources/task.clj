(ns csb.api.resources.task
  "Liberator resources for Task entity."
  (:require
   [csb.api.base :as base]
   [csb.api.html :as html]
   [csb.models.task :as task]
   [datalevin.core :as d]
   [liberator.core :refer [resource]]
   [typed.clojure :as t])
  (:import
   (java.util
    UUID)))

(set! *warn-on-reflection* true)

;; -----------------------------------------------------------------------------
;; HTML Rendering
;; -----------------------------------------------------------------------------

(t/ann render-task-item [t/Any :-> t/Any])
(defn render-task-item
  "Render a single task as hiccup for list view."
  [tsk]
  (let [id (:task/id tsk)
        completed? (:task/completed tsk)]
    [:div {:class (str "task-item" (when completed? " completed"))}
     [:input {:type "checkbox"
              :checked completed?
              :data-on-change (str "@post('/api/tasks/" id "/toggle')")}]
     [:a {:href (str "/api/tasks/" id)}
      (:task/name tsk)]
     (when-let [ctx (:task/context tsk)]
       [:span {:class "context"} ctx])]))

(t/ann render-task-detail [t/Any :-> t/Any])
(defn render-task-detail
  "Render task detail view as hiccup."
  [tsk]
  (let [id (:task/id tsk)]
    (html/entity-detail
     (str "task-" id)
     [:div
      [:h1 (:task/name tsk)]
      [:p {:class "status"}
       (if (:task/completed tsk) "Completed" "Pending")]
      (when-let [ctx (:task/context tsk)]
        [:p {:class "context"} ctx])
      [:div {:class "metadata"}
       [:span "Created: " (str (:task/created-at tsk))]
       [:span "Updated: " (str (:task/updated-at tsk))]]
      [:div {:class "actions"}
       (html/action-button "Delete"
                           (str "@delete('/api/tasks/" id "')")
                           {:class "danger"})
       [:a {:href (str "/api/tasks/" id "/subtasks")} "View Subtasks"]]])))

(t/ann render-tasks-list [(t/Seqable t/Any) :-> t/Any])
(defn render-tasks-list
  "Render list of tasks as hiccup."
  [tasks]
  (html/entity-list "tasks" "Tasks" tasks render-task-item))

;; -----------------------------------------------------------------------------
;; Collection Resource (GET list, POST create)
;; -----------------------------------------------------------------------------

(t/ann collection [t/Any :-> t/Any])
(defn collection
  "Create a Liberator resource for task collection.
   Handles GET (list all) and POST (create new)."
  [db]
  (let [conn (d/conn-from-db db)]
    (resource
     base/resource-defaults
     :allowed-methods [:get :post]

     :malformed? (fn [ctx]
                   (when (= :post (get-in ctx [:request :request-method]))
                     (let [body (base/get-body ctx)]
                       (cond
                         (nil? body)
                         [true {::base/error "Invalid JSON body"}]

                         (not (:name body))
                         [true {::base/error "Missing required field: name"}]

                         (not (:plan body))
                         [true {::base/error "Missing required field: plan"}]

                         :else
                         [false {::base/body body}]))))

     :handle-ok (fn [ctx]
                  (let [tasks (task/get-all db)]
                    (if (base/json-request? ctx)
                      tasks
                      (html/render (render-tasks-list tasks)))))

     :post! (fn [ctx]
              (let [body (::base/body ctx)
                    ;; Convert to namespaced keys for model
                    params {:task/name (:name body)
                            :task/plan (:plan body)
                            :task/context (:context body)
                            :task/completed (boolean (:completed body))}
                    result (task/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))]
                {::created-task (first (:tx-data result))}))

     :handle-created (fn [ctx]
                       (let [tsk (::created-task ctx)
                             id (:task/id tsk)]
                         (if (base/json-request? ctx)
                           {:id (str id) :created true}
                           (html/render
                            [:div {:id "task-created"}
                             [:p "Task created successfully"]
                             [:a {:href (str "/api/tasks/" id)} "View task"]])))))))

;; -----------------------------------------------------------------------------
;; By Plan Resource (GET tasks for a plan, POST create)
;; -----------------------------------------------------------------------------

(t/ann by-plan [t/Any t/Any :-> t/Any])
(defn by-plan
  "Create a Liberator resource for tasks within a plan.
   Handles GET (list by plan) and POST (create new)."
  [db request]
  (let [conn (d/conn-from-db db)
        plan-id-str (get-in request [:route-params :plan-id])
        plan-id (when plan-id-str (parse-long plan-id-str))]
    (resource
     base/resource-defaults
     :allowed-methods [:get :post]

     :malformed? (fn [ctx]
                   (when (= :post (get-in ctx [:request :request-method]))
                     (let [body (base/get-body ctx)]
                       (cond
                         (nil? body)
                         [true {::base/error "Invalid JSON body"}]

                         (not (:name body))
                         [true {::base/error "Missing required field: name"}]

                         :else
                         [false {::base/body body}]))))

     :handle-ok (fn [ctx]
                  (let [tasks (if plan-id
                                (task/get-by-plan db plan-id)
                                [])]
                    (if (base/json-request? ctx)
                      tasks
                      (html/render (render-tasks-list tasks)))))

     :post! (fn [ctx]
              (let [body (::base/body ctx)
                    ;; Convert to namespaced keys for model, inject plan-id
                    params {:task/name (:name body)
                            :task/plan plan-id
                            :task/context (:context body)
                            :task/completed (boolean (:completed body))}
                    result (task/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))]
                {::created-task (first (:tx-data result))}))

     :handle-created (fn [ctx]
                       (let [tsk (::created-task ctx)
                             id (:task/id tsk)]
                         (if (base/json-request? ctx)
                           {:id (str id) :created true}
                           (html/render
                            [:div {:id "task-created"}
                             [:p "Task created successfully"]
                             [:a {:href (str "/api/tasks/" id)} "View task"]])))))))

;; -----------------------------------------------------------------------------
;; Subtasks Resource (GET children, POST create child)
;; -----------------------------------------------------------------------------

(t/ann subtasks [t/Any t/Any :-> t/Any])
(defn subtasks
  "Create a Liberator resource for subtasks of a task.
   Handles GET (list children) and POST (create child)."
  [db request]
  (let [conn (d/conn-from-db db)
        parent-id-str (get-in request [:route-params :parent-id])
        parent-id (when parent-id-str
                    (try (UUID/fromString parent-id-str) (catch Exception _ nil)))
        parent-task (when parent-id (task/get-by-id db parent-id))
        parent-eid (when parent-task (:db/id parent-task))]
    (resource
     base/resource-defaults
     :allowed-methods [:get :post]

     :exists? (fn [_ctx]
                (boolean parent-task))

     :malformed? (fn [ctx]
                   (when (= :post (get-in ctx [:request :request-method]))
                     (let [body (base/get-body ctx)]
                       (cond
                         (nil? body)
                         [true {::base/error "Invalid JSON body"}]

                         (not (:name body))
                         [true {::base/error "Missing required field: name"}]

                         :else
                         [false {::base/body body}]))))

     :handle-ok (fn [ctx]
                  (let [tasks (if parent-eid
                                (task/get-children db parent-eid)
                                [])]
                    (if (base/json-request? ctx)
                      tasks
                      (html/render (render-tasks-list tasks)))))

     :post! (fn [ctx]
              (let [body (::base/body ctx)
                    ;; Convert to namespaced keys for model, inject parent info
                    params {:task/name (:name body)
                            :task/plan (:task/plan parent-task)
                            :task/parent parent-eid
                            :task/context (:context body)
                            :task/completed (boolean (:completed body))}
                    result (task/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))]
                {::created-task (first (:tx-data result))}))

     :handle-created (fn [ctx]
                       (let [tsk (::created-task ctx)
                             id (:task/id tsk)]
                         (if (base/json-request? ctx)
                           {:id (str id) :created true}
                           (html/render
                            [:div {:id "task-created"}
                             [:p "Subtask created successfully"]
                             [:a {:href (str "/api/tasks/" id)} "View task"]])))))))

;; -----------------------------------------------------------------------------
;; Item Resource (GET single, PUT/PATCH update, DELETE)
;; -----------------------------------------------------------------------------

(t/ann item [t/Any t/Any :-> t/Any])
(defn item
  "Create a Liberator resource for a single task.
   Handles GET (retrieve), PUT/PATCH (update), DELETE."
  [db request]
  (let [conn (d/conn-from-db db)
        id-str (get-in request [:route-params :id])
        id (when id-str
             (try (UUID/fromString id-str) (catch Exception _ nil)))]
    (resource
     base/resource-defaults
     :allowed-methods [:get :put :patch :delete]

     :exists? (fn [_ctx]
                (when id
                  (when-let [tsk (task/get-by-id db id)]
                    {::task tsk})))

     ;; For PUT/PATCH on existing resources, this is not a new resource
     :new? false

     :malformed? (fn [ctx]
                   (let [method (get-in ctx [:request :request-method])]
                     (when (#{:put :patch} method)
                       (let [body (base/get-body ctx)]
                         (cond
                           (nil? body)
                           [true {::base/error "Invalid JSON body"}]

                           (and (= :put method) (not (:name body)))
                           [true {::base/error "Missing required field: name"}]

                           :else
                           [false {::base/body body}])))))

     :handle-ok (fn [ctx]
                  (let [tsk (::task ctx)]
                    (if (base/json-request? ctx)
                      tsk
                      (html/render (render-task-detail tsk)))))

     :put! (fn [ctx]
             (let [body (::base/body ctx)
                   ;; Convert to namespaced keys for model
                   params {:task/name (:name body)
                           :task/context (:context body)
                           :task/completed (:completed body)}]
               (task/update-task conn id (into {} (filter (fn [[_ v]] (some? v)) params)))
               {::updated true}))

     :patch! (fn [ctx]
               (let [body (::base/body ctx)
                     ;; Convert to namespaced keys for model (only include provided fields)
                     params (cond-> {}
                              (contains? body :name) (assoc :task/name (:name body))
                              (contains? body :context) (assoc :task/context (:context body))
                              (contains? body :completed) (assoc :task/completed (:completed body)))]
                 (task/update-task conn id params)
                 {::updated true}))

     :delete! (fn [_ctx]
                (task/delete-task conn id)
                {::deleted true})

     :handle-no-content (fn [_ctx]
                          nil))))

(comment
  (collection nil)
  (item nil {:route-params {:id "123"}}))
