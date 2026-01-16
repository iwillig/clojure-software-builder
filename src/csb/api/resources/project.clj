(ns csb.api.resources.project
  "Liberator resources for Project entity."
  (:require
   [csb.api.base :as base]
   [csb.api.html :as html]
   [csb.models.project :as project]
   [datalevin.core :as d]
   [liberator.core :refer [resource]]
   [typed.clojure :as t]))

(set! *warn-on-reflection* true)

;; -----------------------------------------------------------------------------
;; HTML Rendering
;; -----------------------------------------------------------------------------

(t/ann render-project-item [t/Any :-> t/Any])
(defn render-project-item
  "Render a single project as hiccup for list view."
  [proj]
  (let [id (:db/id proj)]
    [:div {:class "project-item"}
     [:a {:href (str "/api/projects/" id)}
      (:project/name proj)]
     (when-let [desc (:project/description proj)]
       [:span {:class "description"} desc])]))

(t/ann render-project-detail [t/Any :-> t/Any])
(defn render-project-detail
  "Render project detail view as hiccup."
  [proj]
  (let [id (:db/id proj)]
    (html/entity-detail
     (str "project-" id)
     [:div
      [:h1 (:project/name proj)]
      (when-let [desc (:project/description proj)]
        [:p {:class "description"} desc])
      (when-let [path (:project/path proj)]
        [:p {:class "path"} "Path: " path])
      [:div {:class "metadata"}
       [:span "Created: " (str (:project/created-at proj))]
       [:span "Updated: " (str (:project/updated-at proj))]]
      [:div {:class "actions"}
       (html/action-button "Delete"
                           (str "@delete('/api/projects/" id "')")
                           {:class "danger"})]])))

(t/ann render-projects-list [(t/Seqable t/Any) :-> t/Any])
(defn render-projects-list
  "Render list of projects as hiccup."
  [projects]
  (html/entity-list "projects" "Projects" projects render-project-item))

;; -----------------------------------------------------------------------------
;; Collection Resource (GET list, POST create)
;; -----------------------------------------------------------------------------

(t/ann collection [t/Any :-> t/Any])
(defn collection
  "Create a Liberator resource for project collection.
   Handles GET (list all) and POST (create new)."
  [db]
  (t/tc-ignore
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

                          :else
                          [false {::base/body body}]))))

      :handle-ok (fn [ctx]
                   (let [projects (project/get-all db)]
                     (if (base/json-request? ctx)
                       projects
                       (html/render (render-projects-list projects)))))

      :post! (fn [ctx]
               (let [body (::base/body ctx)
                     ;; Convert to namespaced keys for model
                     params {:project/name (:name body)
                             :project/description (:description body)}
                     result (project/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))
                     ;; Get the created entity from tx-data (first datom has the entity id)
                     created-datom (first (:tx-data result))
                     entity-id (when created-datom (:e created-datom))]
                 {::created-id entity-id}))

      :handle-created (fn [ctx]
                        (let [id (::created-id ctx)]
                          (if (base/json-request? ctx)
                            {:id id :created true}
                            (html/render
                             [:div {:id "project-created"}
                              [:p "Project created successfully"]
                              [:a {:href (str "/api/projects/" id)} "View project"]]))))))))

;; -----------------------------------------------------------------------------
;; Item Resource (GET single, PUT/PATCH update, DELETE)
;; -----------------------------------------------------------------------------

(t/ann item [t/Any t/Any :-> t/Any])
(defn item
  "Create a Liberator resource for a single project.
   Handles GET (retrieve), PUT/PATCH (update), DELETE."
  [db request]
  (t/tc-ignore
   (let [conn (d/conn-from-db db)
         id-str (get-in request [:route-params :id])
         id (when id-str (parse-long id-str))]
     (resource
      base/resource-defaults
      :allowed-methods [:get :put :patch :delete]

      :exists? (fn [_ctx]
                 (when id
                   (when-let [proj (d/pull db '[*] id)]
                     (when (:project/name proj)
                       {::project proj}))))

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
                   (let [proj (::project ctx)]
                     (if (base/json-request? ctx)
                       proj
                       (html/render (render-project-detail proj)))))

      :put! (fn [ctx]
              (let [body (::base/body ctx)
                    ;; Convert to namespaced keys for model
                    params {:project/name (:name body)
                            :project/description (:description body)}]
                (project/update-project conn id (into {} (filter (fn [[_ v]] (some? v)) params)))
                {::updated true}))

      :patch! (fn [ctx]
                (let [body (::base/body ctx)
                      ;; Convert to namespaced keys for model (only include provided fields)
                      params (cond-> {}
                               (contains? body :name) (assoc :project/name (:name body))
                               (contains? body :description) (assoc :project/description (:description body)))]
                  (project/update-project conn id params)
                  {::updated true}))

      :delete! (fn [_ctx]
                 (project/delete-project conn id)
                 {::deleted true})

      :handle-no-content (fn [_ctx]
                           nil)))))

(comment
  ;; Test the resources
  (collection nil)
  (item nil {:route-params {:id "1"}}))
