(ns csb.api.sse
  "Datastar SSE (Server-Sent Events) integration helpers."
  (:require
   [csb.api.html :as html]
   [starfederation.datastar.clojure.adapter.http-kit :as ds-http-kit]
   [starfederation.datastar.clojure.api :as ds]
   [typed.clojure :as t]))

(set! *warn-on-reflection* true)

;; -----------------------------------------------------------------------------
;; SSE Response Helpers
;; -----------------------------------------------------------------------------

(t/ann sse-response [t/Any t/Any :-> t/Any])
(defn sse-response
  "Create an SSE response for Datastar.
   - request: Ring request map
   - on-open-fn: Function called with SSE channel when connection opens
                 (fn [sse] ...)"
  [request on-open-fn]
  (ds-http-kit/->sse-response
   request
   {ds-http-kit/on-open on-open-fn}))

(t/ann sse-response-with-close [t/Any t/Any t/Any :-> t/Any])
(defn sse-response-with-close
  "Create an SSE response with both open and close handlers.
   - request: Ring request map
   - on-open-fn: Function called when connection opens
   - on-close-fn: Function called when connection closes"
  [request on-open-fn on-close-fn]
  (ds-http-kit/->sse-response
   request
   {ds-http-kit/on-open on-open-fn
    ds-http-kit/on-close on-close-fn}))

;; -----------------------------------------------------------------------------
;; Datastar Event Helpers
;; -----------------------------------------------------------------------------

(t/ann patch-elements! [t/Any t/Any :-> nil])
(defn patch-elements!
  "Send a patch-elements event to update DOM.
   - sse: SSE channel
   - hiccup: Hiccup data structure to render and send"
  [sse hiccup]
  (ds/patch-elements! sse (html/render hiccup))
  nil)

(t/ann patch-html! [t/Any t/Str :-> nil])
(defn patch-html!
  "Send raw HTML string as patch-elements event.
   - sse: SSE channel
   - html-str: Pre-rendered HTML string"
  [sse html-str]
  (ds/patch-elements! sse html-str)
  nil)

(t/ann patch-signals! [t/Any t/Any :-> nil])
(defn patch-signals!
  "Send a patch-signals event to update client state.
   - sse: SSE channel
   - signals: Map of signal names to values"
  [sse signals]
  (ds/patch-signals! sse signals)
  nil)

(t/ann execute-script! [t/Any t/Str :-> nil])
(defn execute-script!
  "Send an execute-script event to run JavaScript on client.
   - sse: SSE channel
   - script: JavaScript code string"
  [sse script]
  (ds/execute-script! sse script)
  nil)

(t/ann remove-element! [t/Any t/Str :-> nil])
(defn remove-element!
  "Send a remove-element event to remove a DOM element.
   - sse: SSE channel
   - selector: CSS selector of element to remove"
  [sse selector]
  (ds/remove-element! sse selector)
  nil)

(t/ann redirect! [t/Any t/Str :-> nil])
(defn redirect!
  "Send a redirect event to navigate to a new URL.
   - sse: SSE channel
   - url: URL to redirect to"
  [sse url]
  (ds/redirect! sse url)
  nil)

(t/ann close! [t/Any :-> nil])
(defn close!
  "Close the SSE connection.
   - sse: SSE channel"
  [sse]
  (ds/close-sse! sse)
  nil)

;; -----------------------------------------------------------------------------
;; Common Patterns
;; -----------------------------------------------------------------------------

(t/ann send-entity-list! [t/Any t/Str t/Str (t/Seqable t/Any) [t/Any :-> t/Any] :-> nil])
(defn send-entity-list!
  "Send an entity list update via SSE.
   - sse: SSE channel
   - id: DOM element id
   - title: List title
   - items: Collection of entities
   - render-item-fn: Function to render each item"
  [sse id title items render-item-fn]
  (patch-elements! sse (html/entity-list id title items render-item-fn))
  nil)

(t/ann send-error! [t/Any t/Str t/Str :-> nil])
(defn send-error!
  "Send an error message via SSE.
   - sse: SSE channel
   - id: DOM element id to update
   - message: Error message"
  [sse id message]
  (patch-elements! sse [:div {:id id} (html/error-div message)])
  nil)

(t/ann send-loading! [t/Any t/Str :-> nil])
(defn send-loading!
  "Send a loading indicator via SSE.
   - sse: SSE channel
   - id: DOM element id to update"
  [sse id]
  (patch-elements! sse [:div {:id id} (html/loading-div)])
  nil)

(comment
  ;; Example usage in a handler:
  ;; (sse-response request
  ;;   (fn [sse]
  ;;     (send-loading! sse "projects")
  ;;     (let [projects (project/get-all db)]
  ;;       (send-entity-list! sse "projects" "Projects" projects
  ;;         (fn [p] [:span (:project/name p)])))
  ;;     (close! sse)))
  )
