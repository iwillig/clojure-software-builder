(ns csb.api.base
  "Base resource defaults and utilities for Liberator API."
  (:require
   [typed.clojure :as t]))

(set! *warn-on-reflection* true)

;; -----------------------------------------------------------------------------
;; Content Negotiation Helpers
;; -----------------------------------------------------------------------------

(t/ann media-type [t/Any :-> (t/Option t/Str)])
(defn media-type
  "Get the negotiated media type from liberator context."
  [ctx]
  (get-in ctx [:representation :media-type]))

(t/ann json-request? [t/Any :-> t/Bool])
(defn json-request?
  "Check if the negotiated response is JSON."
  [ctx]
  (= "application/json" (media-type ctx)))

(t/ann html-request? [t/Any :-> t/Bool])
(defn html-request?
  "Check if the negotiated response is HTML."
  [ctx]
  (= "text/html" (media-type ctx)))

;; -----------------------------------------------------------------------------
;; Resource Defaults
;; -----------------------------------------------------------------------------

(def ^:const available-media-types
  "Default media types for all resources."
  ["application/json" "text/html"])

(t/ann handle-exception [t/Any :-> t/Any])
(defn handle-exception
  "Default exception handler for resources."
  [ctx]
  (if (json-request? ctx)
    {:error "Internal server error"}
    "<div class=\"error\">Internal server error</div>"))

(t/ann handle-malformed [t/Any :-> t/Any])
(defn handle-malformed
  "Default malformed request handler."
  [ctx]
  (let [error (::error ctx "Malformed request")]
    (if (json-request? ctx)
      {:error error}
      (str "<div class=\"error\">" error "</div>"))))

(t/ann handle-not-found [t/Any :-> t/Any])
(defn handle-not-found
  "Default not found handler."
  [ctx]
  (if (json-request? ctx)
    {:error "Not found"}
    "<div class=\"error\">Not found</div>"))

(t/ann handle-method-not-allowed [t/Any :-> t/Any])
(defn handle-method-not-allowed
  "Default method not allowed handler."
  [ctx]
  (if (json-request? ctx)
    {:error "Method not allowed"}
    "<div class=\"error\">Method not allowed</div>"))

(t/ann resource-defaults (t/Map t/Keyword t/Any))
(def resource-defaults
  "Default configuration for all Liberator resources.
   Use as first argument to defresource."
  {:available-media-types available-media-types
   :handle-exception handle-exception
   :handle-malformed handle-malformed
   :handle-not-found handle-not-found
   :handle-method-not-allowed handle-method-not-allowed})

;; -----------------------------------------------------------------------------
;; Request Helpers
;; -----------------------------------------------------------------------------

(t/ann get-body [t/Any :-> (t/Option t/Any)])
(defn get-body
  "Get the parsed JSON body from the request.
   The body is parsed by ring-json middleware before reaching the handler."
  [ctx]
  (get-in ctx [:request :body]))

(t/ann route-params [t/Any :-> (t/Map t/Keyword t/Any)])
(defn route-params
  "Get route parameters from request."
  [ctx]
  (get-in ctx [:request :route-params] {}))

(t/ann get-route-param [t/Any t/Keyword :-> (t/Option t/Any)])
(defn get-route-param
  "Get a specific route parameter."
  [ctx param]
  (get (route-params ctx) param))

;; -----------------------------------------------------------------------------
;; Response Helpers
;; -----------------------------------------------------------------------------

(t/ann created-response [t/Any t/Str :-> t/Any])
(defn created-response
  "Build response for newly created resource."
  [ctx location]
  (assoc-in ctx [:response :headers "Location"] location))

(comment
  resource-defaults)
