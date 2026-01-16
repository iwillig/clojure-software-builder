(ns csb.api.resources.fact
  "Liberator resources for Fact entity."
  (:require
   [csb.api.base :as base]
   [csb.api.html :as html]
   [csb.models.fact :as fact]
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

(t/ann render-fact-item [t/Any :-> t/Any])
(defn render-fact-item
  "Render a single fact as hiccup for list view."
  [fct]
  (let [id (:fact/id fct)]
    [:div {:class "fact-item"}
     [:a {:href (str "/api/facts/" id)}
      (:fact/name fct)]
     (when-let [desc (:fact/description fct)]
       [:span {:class "description"} desc])]))

(t/ann render-fact-detail [t/Any :-> t/Any])
(defn render-fact-detail
  "Render fact detail view as hiccup."
  [fct]
  (let [id (:fact/id fct)]
    (html/entity-detail
     (str "fact-" id)
     [:div
      [:h1 (:fact/name fct)]
      (when-let [desc (:fact/description fct)]
        [:p {:class "description"} desc])
      [:div {:class "actions"}
       (html/action-button "Delete"
                           (str "@delete('/api/facts/" id "')")
                           {:class "danger"})]])))

(t/ann render-facts-list [(t/Seqable t/Any) :-> t/Any])
(defn render-facts-list
  "Render list of facts as hiccup."
  [facts]
  (html/entity-list "facts" "Facts" facts render-fact-item))

;; -----------------------------------------------------------------------------
;; Collection Resource (GET list, POST create)
;; -----------------------------------------------------------------------------

(t/ann collection [t/Any :-> t/Any])
(defn collection
  "Create a Liberator resource for fact collection.
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
                   (let [facts (fact/get-all db)]
                     (if (base/json-request? ctx)
                       facts
                       (html/render (render-facts-list facts)))))

      :post! (fn [ctx]
               (let [body (::base/body ctx)
                     ;; Convert to namespaced keys for model
                     params {:fact/name (:name body)
                             :fact/description (:description body)}
                     result (fact/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))]
                 {::created-fact (first (:tx-data result))}))

      :handle-created (fn [ctx]
                        (let [fct (::created-fact ctx)
                              id (:fact/id fct)]
                          (if (base/json-request? ctx)
                            {:id (str id) :created true}
                            (html/render
                             [:div {:id "fact-created"}
                              [:p "Fact created successfully"]
                              [:a {:href (str "/api/facts/" id)} "View fact"]]))))))))

;; -----------------------------------------------------------------------------
;; Search Resource (GET search results)
;; -----------------------------------------------------------------------------

(t/ann search-resource [t/Any :-> t/Any])
(defn search-resource
  "Create a Liberator resource for fact search.
   Handles GET with query parameter for full-text search."
  [db]
  (t/tc-ignore
   (resource
    base/resource-defaults
    :allowed-methods [:get]

    :handle-ok (fn [ctx]
                 (let [query (get-in ctx [:request :query-params "q"] "")
                       facts (if (seq query)
                               (fact/search db query)
                               [])]
                   (if (base/json-request? ctx)
                     facts
                     (html/render
                      [:div {:id "search-results"}
                       [:h2 (str "Search results for: " query)]
                       (render-facts-list facts)])))))))

;; -----------------------------------------------------------------------------
;; Item Resource (GET single, PUT/PATCH update, DELETE)
;; -----------------------------------------------------------------------------

(t/ann item [t/Any t/Any :-> t/Any])
(defn item
  "Create a Liberator resource for a single fact.
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
                   (when-let [fct (fact/get-by-id db id)]
                     {::fact fct})))

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
                   (let [fct (::fact ctx)]
                     (if (base/json-request? ctx)
                       fct
                       (html/render (render-fact-detail fct)))))

      :put! (fn [ctx]
              (let [body (::base/body ctx)
                    ;; Convert to namespaced keys for model
                    params {:fact/name (:name body)
                            :fact/description (:description body)}]
                (fact/update-fact conn id (into {} (filter (fn [[_ v]] (some? v)) params)))
                {::updated true}))

      :patch! (fn [ctx]
                (let [body (::base/body ctx)
                      ;; Convert to namespaced keys for model (only include provided fields)
                      params (cond-> {}
                               (contains? body :name) (assoc :fact/name (:name body))
                               (contains? body :description) (assoc :fact/description (:description body)))]
                  (fact/update-fact conn id params)
                  {::updated true}))

      :delete! (fn [_ctx]
                 (fact/delete-fact conn id)
                 {::deleted true})

      :handle-no-content (fn [_ctx]
                           nil)))))

(comment
  (collection nil)
  (search-resource nil)
  (item nil {:route-params {:id "123"}}))
