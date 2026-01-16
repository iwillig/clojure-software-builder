(ns csb.api.html
  "HTML rendering utilities using Hiccup for Datastar-compatible fragments."
  (:require
   [hiccup2.core :as h]
   [typed.clojure :as t]))

(set! *warn-on-reflection* true)

;; -----------------------------------------------------------------------------
;; Rendering Helpers
;; -----------------------------------------------------------------------------

(t/ann render [t/Any :-> t/Str])
(defn render
  "Render hiccup to HTML string."
  [hiccup]
  (str (h/html hiccup)))

(t/ann render-fragment [t/Any :-> t/Str])
(defn render-fragment
  "Render hiccup fragment (without doctype)."
  [hiccup]
  (str (h/html hiccup)))

;; -----------------------------------------------------------------------------
;; Common UI Components
;; -----------------------------------------------------------------------------

(t/ann error-div [t/Str :-> t/Any])
(defn error-div
  "Render an error message div."
  [message]
  [:div {:class "error" :role "alert"} message])

(t/ann loading-div [(t/Option t/Str) :-> t/Any])
(defn loading-div
  "Render a loading indicator."
  ([] (loading-div "Loading..."))
  ([message]
   [:div {:class "loading" :aria-busy "true"} message]))

;; -----------------------------------------------------------------------------
;; Datastar Attributes
;; -----------------------------------------------------------------------------

(t/ann ds-get [t/Str :-> t/Any])
(defn ds-get
  "Create data-on-click attribute for Datastar GET request."
  [url]
  {:data-on-click (str "@get('" url "')")})

(t/ann ds-post [t/Str :-> t/Any])
(defn ds-post
  "Create data-on-click attribute for Datastar POST request."
  [url]
  {:data-on-click (str "@post('" url "')")})

(t/ann ds-delete [t/Str :-> t/Any])
(defn ds-delete
  "Create data-on-click attribute for Datastar DELETE request."
  [url]
  {:data-on-click (str "@delete('" url "')")})

(t/ann ds-put [t/Str :-> t/Any])
(defn ds-put
  "Create data-on-click attribute for Datastar PUT request."
  [url]
  {:data-on-click (str "@put('" url "')")})

;; -----------------------------------------------------------------------------
;; Entity Rendering (base templates)
;; -----------------------------------------------------------------------------

(t/ann entity-list [t/Str t/Str (t/Seqable t/Any) [t/Any :-> t/Any] :-> t/Any])
(defn entity-list
  "Render a list of entities with a consistent structure.
   - id: DOM element id for Datastar targeting
   - title: List title
   - items: Collection of entities
   - render-item-fn: Function to render each item"
  [id title items render-item-fn]
  [:div {:id id :class "entity-list"}
   [:h2 title]
   (if (seq items)
     [:ul {:class "items"}
      (for [item items]
        ^{:key (:db/id item)}
        [:li {:class "item"} (render-item-fn item)])]
     [:p {:class "empty"} "No items found."])])

(t/ann entity-detail [t/Str t/Any :-> t/Any])
(defn entity-detail
  "Render entity detail view wrapper.
   - id: DOM element id for Datastar targeting
   - content: Hiccup content to render"
  [id content]
  [:div {:id id :class "entity-detail"}
   content])

(t/ann action-button [t/Str t/Str t/Any :-> t/Any])
(defn action-button
  "Render an action button with Datastar attributes.
   - label: Button text
   - action: Datastar action string (e.g., \"@delete('/api/projects/1')\")
   - attrs: Additional attributes"
  [label action attrs]
  [:button (merge {:data-on-click action
                   :class "action-button"}
                  attrs)
   label])

(comment
  (render [:div "Hello"])
  (render (error-div "Something went wrong"))
  (render (entity-list "projects" "Projects"
                       [{:db/id 1 :project/name "Foo"}
                        {:db/id 2 :project/name "Bar"}]
                       (fn [p] [:span (:project/name p)]))))
