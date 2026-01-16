(ns csb.api.resources.plan
  "Liberator resources for Plan entity."
  (:require
   [csb.api.base :as base]
   [csb.api.html :as html]
   [csb.models.plan :as plan]
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

(t/ann render-plan-item [t/Any :-> t/Any])
(defn render-plan-item
  "Render a single plan as hiccup for list view."
  [p]
  (let [id (:plan/id p)]
    [:div {:class "plan-item"}
     [:a {:href (str "/api/plans/" id)}
      (:plan/name p)]
     (when-let [ctx (:plan/context p)]
       [:span {:class "context"} ctx])]))

(t/ann render-plan-detail [t/Any :-> t/Any])
(defn render-plan-detail
  "Render plan detail view as hiccup."
  [p]
  (let [id (:plan/id p)]
    (html/entity-detail
     (str "plan-" id)
     [:div
      [:h1 (:plan/name p)]
      (when-let [ctx (:plan/context p)]
        [:p {:class "context"} ctx])
      [:div {:class "actions"}
       (html/action-button "Delete"
                           (str "@delete('/api/plans/" id "')")
                           {:class "danger"})]])))

(t/ann render-plans-list [(t/Seqable t/Any) :-> t/Any])
(defn render-plans-list
  "Render list of plans as hiccup."
  [plans]
  (html/entity-list "plans" "Plans" plans render-plan-item))

;; -----------------------------------------------------------------------------
;; Collection Resource (GET list, POST create)
;; -----------------------------------------------------------------------------

(t/ann collection [t/Any :-> t/Any])
(defn collection
  "Create a Liberator resource for plan collection.
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

                          (not (:project body))
                          [true {::base/error "Missing required field: project"}]

                          :else
                          [false {::base/body body}]))))

      :handle-ok (fn [ctx]
                   (let [plans (plan/get-all db)]
                     (if (base/json-request? ctx)
                       plans
                       (html/render (render-plans-list plans)))))

      :post! (fn [ctx]
               (let [body (::base/body ctx)
                     ;; Convert to namespaced keys for model
                     params {:plan/name (:name body)
                             :plan/project (:project body)
                             :plan/context (:context body)}
                     result (plan/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))]
                 {::created-plan (first (:tx-data result))}))

      :handle-created (fn [ctx]
                        (let [p (::created-plan ctx)
                              id (:plan/id p)]
                          (if (base/json-request? ctx)
                            {:id (str id) :created true}
                            (html/render
                             [:div {:id "plan-created"}
                              [:p "Plan created successfully"]
                              [:a {:href (str "/api/plans/" id)} "View plan"]]))))))))

;; -----------------------------------------------------------------------------
;; By Project Resource (GET plans for a project, POST create)
;; -----------------------------------------------------------------------------

(t/ann by-project [t/Any t/Any :-> t/Any])
(defn by-project
  "Create a Liberator resource for plans within a project.
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

                          (not (:name body))
                          [true {::base/error "Missing required field: name"}]

                          :else
                          [false {::base/body body}]))))

      :handle-ok (fn [ctx]
                   (let [plans (if project-id
                                 (plan/get-by-project db project-id)
                                 [])]
                     (if (base/json-request? ctx)
                       plans
                       (html/render (render-plans-list plans)))))

      :post! (fn [ctx]
               (let [body (::base/body ctx)
                     ;; Convert to namespaced keys for model, inject project-id
                     params {:plan/name (:name body)
                             :plan/project project-id
                             :plan/context (:context body)}
                     result (plan/create conn (into {} (filter (fn [[_ v]] (some? v)) params)))]
                 {::created-plan (first (:tx-data result))}))

      :handle-created (fn [ctx]
                        (let [p (::created-plan ctx)
                              id (:plan/id p)]
                          (if (base/json-request? ctx)
                            {:id (str id) :created true}
                            (html/render
                             [:div {:id "plan-created"}
                              [:p "Plan created successfully"]
                              [:a {:href (str "/api/plans/" id)} "View plan"]]))))))))

;; -----------------------------------------------------------------------------
;; Item Resource (GET single, PUT/PATCH update, DELETE)
;; -----------------------------------------------------------------------------

(t/ann item [t/Any t/Any :-> t/Any])
(defn item
  "Create a Liberator resource for a single plan.
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
                   (when-let [p (plan/get-by-id db id)]
                     {::plan p})))

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
                   (let [p (::plan ctx)]
                     (if (base/json-request? ctx)
                       p
                       (html/render (render-plan-detail p)))))

      :put! (fn [ctx]
              (let [body (::base/body ctx)
                    ;; Convert to namespaced keys for model
                    params {:plan/name (:name body)
                            :plan/context (:context body)}]
                (plan/update-plan conn id (into {} (filter (fn [[_ v]] (some? v)) params)))
                {::updated true}))

      :patch! (fn [ctx]
                (let [body (::base/body ctx)
                      ;; Convert to namespaced keys for model (only include provided fields)
                      params (cond-> {}
                               (contains? body :name) (assoc :plan/name (:name body))
                               (contains? body :context) (assoc :plan/context (:context body)))]
                  (plan/update-plan conn id params)
                  {::updated true}))

      :delete! (fn [_ctx]
                 (plan/delete-plan conn id)
                 {::deleted true})

      :handle-no-content (fn [_ctx]
                           nil)))))

(comment
  ;; Test the resources
  (collection nil)
  (by-project nil {:route-params {:project-id "1"}})
  (item nil {:route-params {:id "123"}}))
