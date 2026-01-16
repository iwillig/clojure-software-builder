(ns csb.api.resources.file
  "Liberator resources for File entity."
  (:require
   [csb.api.base :as base]
   [csb.api.html :as html]
   [csb.models.file :as file]
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

(t/ann render-file-item [t/Any :-> t/Any])
(defn render-file-item
  "Render a single file as hiccup for list view."
  [f]
  (let [id (:file/id f)]
    [:div {:class "file-item"}
     [:a {:href (str "/api/files/" id)}
      (:file/path f)]
     (when-let [summary (:file/summary f)]
       [:span {:class "summary"} summary])]))

(t/ann render-file-detail [t/Any :-> t/Any])
(defn render-file-detail
  "Render file detail view as hiccup."
  [f]
  (let [id (:file/id f)]
    (html/entity-detail
     (str "file-" id)
     [:div
      [:h1 (:file/path f)]
      (when-let [summary (:file/summary f)]
        [:p {:class "summary"} summary])
      [:div {:class "metadata"}
       [:span "Created: " (str (:file/created-at f))]
       [:span "Updated: " (str (:file/updated-at f))]]
      [:div {:class "actions"}
       (html/action-button "Delete"
                           (str "@delete('/api/files/" id "')")
                           {:class "danger"})
       [:a {:href (str "/api/files/" id "/content")} "View Content"]]])))

(t/ann render-files-list [(t/Seqable t/Any) :-> t/Any])
(defn render-files-list
  "Render list of files as hiccup."
  [files]
  (html/entity-list "files" "Files" files render-file-item))

;; -----------------------------------------------------------------------------
;; Collection Resource (GET list, POST create)
;; -----------------------------------------------------------------------------

(t/ann collection [t/Any :-> t/Any])
(defn collection
  "Create a Liberator resource for file collection.
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

                          (not (:path body))
                          [true {::base/error "Missing required field: path"}]

                          (not (:project body))
                          [true {::base/error "Missing required field: project"}]

                          :else
                          [false {::base/body body}]))))

      :handle-ok (fn [ctx]
                   (let [files (file/get-all db)]
                     (if (base/json-request? ctx)
                       files
                       (html/render (render-files-list files)))))

      :post! (fn [ctx]
               (let [body (::base/body ctx)
                     ;; Convert to namespaced keys for model
                     params {:file/path (:path body)
                             :file/project (:project body)
                             :file/summary (:summary body)}
                     result (file/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))]
                 {::created-file (first (:tx-data result))}))

      :handle-created (fn [ctx]
                        (let [f (::created-file ctx)
                              id (:file/id f)]
                          (if (base/json-request? ctx)
                            {:id (str id) :created true}
                            (html/render
                             [:div {:id "file-created"}
                              [:p "File created successfully"]
                              [:a {:href (str "/api/files/" id)} "View file"]]))))))))

;; -----------------------------------------------------------------------------
;; By Project Resource (GET files for a project, POST create)
;; -----------------------------------------------------------------------------

(t/ann by-project [t/Any t/Any :-> t/Any])
(defn by-project
  "Create a Liberator resource for files within a project.
   Handles GET (list by project) and POST (create new)."
  [db request]
  (t/tc-ignore
   (let [conn (d/conn-from-db db)
         project-id-str (get-in request [:route-params :project-id])
         project-id (when project-id-str (parse-long project-id-str))]
     (resource
      base/resource-defaults
      :allowed-methods [:get :post]

      :malformed? (fn [ctx]
                    (when (= :post (get-in ctx [:request :request-method]))
                      (let [body (base/get-body ctx)]
                        (cond
                          (nil? body)
                          [true {::base/error "Invalid JSON body"}]

                          (not (:path body))
                          [true {::base/error "Missing required field: path"}]

                          :else
                          [false {::base/body body}]))))

      :handle-ok (fn [ctx]
                   (let [files (if project-id
                                 (file/get-by-project db project-id)
                                 [])]
                     (if (base/json-request? ctx)
                       files
                       (html/render (render-files-list files)))))

      :post! (fn [ctx]
               (let [body (::base/body ctx)
                     ;; Convert to namespaced keys for model, inject project-id
                     params {:file/path (:path body)
                             :file/project project-id
                             :file/summary (:summary body)}
                     result (file/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))]
                 {::created-file (first (:tx-data result))}))

      :handle-created (fn [ctx]
                        (let [f (::created-file ctx)
                              id (:file/id f)]
                          (if (base/json-request? ctx)
                            {:id (str id) :created true}
                            (html/render
                             [:div {:id "file-created"}
                              [:p "File created successfully"]
                              [:a {:href (str "/api/files/" id)} "View file"]]))))))))

;; -----------------------------------------------------------------------------
;; Item Resource (GET single, PUT/PATCH update, DELETE)
;; -----------------------------------------------------------------------------

(t/ann item [t/Any t/Any :-> t/Any])
(defn item
  "Create a Liberator resource for a single file.
   Handles GET (retrieve), PUT/PATCH (update), DELETE."
  [db request]
  (t/tc-ignore
   (let [conn (d/conn-from-db db)
         id-str (get-in request [:route-params :id])
         id (when id-str
              (try (UUID/fromString id-str) (catch Exception _ nil)))]
     (resource
      base/resource-defaults
      :allowed-methods [:get :put :patch :delete]

      :exists? (fn [_ctx]
                 (when id
                   (when-let [f (file/get-by-id db id)]
                     {::file f})))

      ;; For PUT/PATCH on existing resources, this is not a new resource
      :new? false

      :malformed? (fn [ctx]
                    (let [method (get-in ctx [:request :request-method])]
                      (when (#{:put :patch} method)
                        (let [body (base/get-body ctx)]
                          (cond
                            (nil? body)
                            [true {::base/error "Invalid JSON body"}]

                            (and (= :put method) (not (:path body)))
                            [true {::base/error "Missing required field: path"}]

                            :else
                            [false {::base/body body}])))))

      :handle-ok (fn [ctx]
                   (let [f (::file ctx)]
                     (if (base/json-request? ctx)
                       f
                       (html/render (render-file-detail f)))))

      :put! (fn [ctx]
              (let [body (::base/body ctx)
                    ;; Convert to namespaced keys for model
                    params {:file/path (:path body)
                            :file/summary (:summary body)}]
                (file/update-file conn id (into {} (filter (fn [[_ v]] (some? v)) params)))
                {::updated true}))

      :patch! (fn [ctx]
                (let [body (::base/body ctx)
                      ;; Convert to namespaced keys for model (only include provided fields)
                      params (cond-> {}
                               (contains? body :path) (assoc :file/path (:path body))
                               (contains? body :summary) (assoc :file/summary (:summary body)))]
                  (file/update-file conn id params)
                  {::updated true}))

      :delete! (fn [_ctx]
                 (file/delete-file conn id)
                 {::deleted true})

      :handle-no-content (fn [_ctx]
                           nil)))))

(comment
  (collection nil)
  (item nil {:route-params {:id "123"}}))
