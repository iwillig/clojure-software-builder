(ns csb.api.resources.file-content
  "Liberator resources for FileContent entity."
  (:require
   [csb.api.base :as base]
   [csb.api.html :as html]
   [csb.models.file-content :as file-content]
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

(t/ann render-content-item [t/Any :-> t/Any])
(defn render-content-item
  "Render a single file content as hiccup for list view."
  [fc]
  (let [id (:file-content/id fc)]
    [:div {:class "file-content-item"}
     [:a {:href (str "/api/file-content/" id)}
      (str "Version " id)]
     [:span {:class "created-at"}
      (str (:file-content/created-at fc))]]))

(t/ann render-content-detail [t/Any :-> t/Any])
(defn render-content-detail
  "Render file content detail view as hiccup."
  [fc]
  (let [id (:file-content/id fc)]
    (html/entity-detail
     (str "file-content-" id)
     [:div
      [:h1 (str "Content Version " id)]
      [:pre {:class "content"} (:file-content/content fc)]
      (when-let [ast (:file-content/compact-ast fc)]
        [:details
         [:summary "Compact AST"]
         [:pre {:class "ast"} ast]])
      [:div {:class "metadata"}
       [:span "Created: " (str (:file-content/created-at fc))]
       [:span "Updated: " (str (:file-content/updated-at fc))]]
      [:div {:class "actions"}
       (html/action-button "Delete"
                           (str "@delete('/api/file-content/" id "')")
                           {:class "danger"})]])))

(t/ann render-contents-list [(t/Seqable t/Any) :-> t/Any])
(defn render-contents-list
  "Render list of file contents as hiccup."
  [contents]
  (html/entity-list "file-contents" "File Content Versions" contents render-content-item))

;; -----------------------------------------------------------------------------
;; Collection Resource (GET list, POST create)
;; -----------------------------------------------------------------------------

(t/ann collection [t/Any :-> t/Any])
(defn collection
  "Create a Liberator resource for file content collection.
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

                          (not (:content body))
                          [true {::base/error "Missing required field: content"}]

                          (not (:file body))
                          [true {::base/error "Missing required field: file"}]

                          :else
                          [false {::base/body body}]))))

      :handle-ok (fn [ctx]
                   (let [contents (file-content/get-all db)]
                     (if (base/json-request? ctx)
                       contents
                       (html/render (render-contents-list contents)))))

      :post! (fn [ctx]
               (let [body (::base/body ctx)
                     ;; Convert to namespaced keys for model
                     params {:file-content/content (:content body)
                             :file-content/file (:file body)
                             :file-content/compact-ast (:compact-ast body)}
                     result (file-content/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))]
                 {::created-content (first (:tx-data result))}))

      :handle-created (fn [ctx]
                        (let [fc (::created-content ctx)
                              id (:file-content/id fc)]
                          (if (base/json-request? ctx)
                            {:id (str id) :created true}
                            (html/render
                             [:div {:id "file-content-created"}
                              [:p "File content created successfully"]
                              [:a {:href (str "/api/file-content/" id)} "View content"]]))))))))

;; -----------------------------------------------------------------------------
;; By File Resource (GET content for a file, POST create)
;; -----------------------------------------------------------------------------

(t/ann by-file [t/Any t/Any :-> t/Any])
(defn by-file
  "Create a Liberator resource for content within a file.
   Handles GET (list by file) and POST (create new)."
  [db request]
  (t/tc-ignore
   (let [conn (d/conn-from-db db)
         file-id-str (get-in request [:route-params :file-id])
         file-id (when file-id-str (parse-long file-id-str))]
     (resource
      base/resource-defaults
      :allowed-methods [:get :post]

      :malformed? (fn [ctx]
                    (when (= :post (get-in ctx [:request :request-method]))
                      (let [body (base/get-body ctx)]
                        (cond
                          (nil? body)
                          [true {::base/error "Invalid JSON body"}]

                          (not (:content body))
                          [true {::base/error "Missing required field: content"}]

                          :else
                          [false {::base/body body}]))))

      :handle-ok (fn [ctx]
                   (let [contents (if file-id
                                    (file-content/get-by-file db file-id)
                                    [])]
                     (if (base/json-request? ctx)
                       contents
                       (html/render (render-contents-list contents)))))

      :post! (fn [ctx]
               (let [body (::base/body ctx)
                     ;; Convert to namespaced keys for model, inject file-id
                     params {:file-content/content (:content body)
                             :file-content/file file-id
                             :file-content/compact-ast (:compact-ast body)}
                     result (file-content/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))]
                 {::created-content (first (:tx-data result))}))

      :handle-created (fn [ctx]
                        (let [fc (::created-content ctx)
                              id (:file-content/id fc)]
                          (if (base/json-request? ctx)
                            {:id (str id) :created true}
                            (html/render
                             [:div {:id "file-content-created"}
                              [:p "File content created successfully"]
                              [:a {:href (str "/api/file-content/" id)} "View content"]]))))))))

;; -----------------------------------------------------------------------------
;; Item Resource (GET single, PUT/PATCH update, DELETE)
;; -----------------------------------------------------------------------------

(t/ann item [t/Any t/Any :-> t/Any])
(defn item
  "Create a Liberator resource for a single file content.
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
                   (when-let [fc (file-content/get-by-id db id)]
                     {::content fc})))

      ;; For PUT/PATCH on existing resources, this is not a new resource
      :new? false

      :malformed? (fn [ctx]
                    (let [method (get-in ctx [:request :request-method])]
                      (when (#{:put :patch} method)
                        (let [body (base/get-body ctx)]
                          (cond
                            (nil? body)
                            [true {::base/error "Invalid JSON body"}]

                            (and (= :put method) (not (:content body)))
                            [true {::base/error "Missing required field: content"}]

                            :else
                            [false {::base/body body}])))))

      :handle-ok (fn [ctx]
                   (let [fc (::content ctx)]
                     (if (base/json-request? ctx)
                       fc
                       (html/render (render-content-detail fc)))))

      :put! (fn [ctx]
              (let [body (::base/body ctx)
                    ;; Convert to namespaced keys for model
                    params {:file-content/content (:content body)
                            :file-content/compact-ast (:compact-ast body)}]
                (file-content/update-file-content conn id (into {} (filter (fn [[_ v]] (some? v)) params)))
                {::updated true}))

      :patch! (fn [ctx]
                (let [body (::base/body ctx)
                      ;; Convert to namespaced keys for model (only include provided fields)
                      params (cond-> {}
                               (contains? body :content) (assoc :file-content/content (:content body))
                               (contains? body :compact-ast) (assoc :file-content/compact-ast (:compact-ast body)))]
                  (file-content/update-file-content conn id params)
                  {::updated true}))

      :delete! (fn [_ctx]
                 (file-content/delete-file-content conn id)
                 {::deleted true})

      :handle-no-content (fn [_ctx]
                           nil)))))

(comment
  (collection nil)
  (item nil {:route-params {:id "123"}}))
