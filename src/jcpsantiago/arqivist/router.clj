(ns jcpsantiago.arqivist.router
  "Slack Arqivist routing using reitit.
   * Slack routes
   * Confluence routes
   * System admin routes"
  (:require
   ;; Core Web Application Libraries
   [reitit.ring   :as ring]
   [muuntaja.core :as muuntaja]

   ;; Routing middleware
   [reitit.ring.middleware.muuntaja   :as middleware-muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   ;; [reitit.ring.middleware.exception  :as exception]

   ;; Arqivist middleware
   [jcpsantiago.arqivist.middleware :as middleware-arqivist]

   ;; Arqivist Routing
   [jcpsantiago.arqivist.api.system-admin.router :as system-admin]
   [jcpsantiago.arqivist.api.slack.router :as slack]
   [jcpsantiago.arqivist.api.confluence.router :as confluence]

   ;; Self-documenting API
   [reitit.swagger    :as api-docs]
   [reitit.swagger-ui :as api-docs-ui]

   ;; Provide details of parameters to API documentation UI (swagger)
   [reitit.coercion.spec]
   [reitit.ring.coercion :as coercion]

   ;; Error handling
   [reitit.dev.pretty :as pretty]))

(def open-api-docs
  "Open API docs general information about The Slack Arqivist,
  https://swagger.io/docs/specification/api-general-info/"
  ["/swagger.json"
   {:get {:no-doc  true
          :swagger
          {:info
           {:title "The Arqivist"
            :description "Creates Confluence pages from Slack conversations"
            :version "0.1.0"
            :termsOfService "https://arqivist.app/terms"
            :contact {:name "João Santiago"
                      :url "apps@jcpsantiago.xyz"}
            :license {:name (str "© João Santiago, " (java.time.Year/now))
                      :url "https://arqivist.app/terms"}
            :x-logo {:url "./favicon.png"}}}
          :handler (api-docs/create-swagger-handler)}}])

;; Global route Configuration - coersion and middleware applied to all routes
(def router-configuration
  "Reitit configuration of coercion, data format transformation and middleware for all routing"
  {:data {:coercion   reitit.coercion.spec/coercion
          :muuntaja   muuntaja/instance
          ;; TODO: Add middleware for Slack security checks
          :middleware [;; swagger feature for OpenAPI documentation
                       api-docs/swagger-feature
                       ;; query-params & form-params
                       parameters/parameters-middleware
                       ;; content-negotiation
                       middleware-muuntaja/format-middleware
                       ;; coercing response body
                       coercion/coerce-response-middleware
                       ;; coercing request parameters
                       coercion/coerce-request-middleware
                       ;; Pretty print exceptions
                       coercion/coerce-exceptions-middleware
                       ;; logging with mulog
                       [middleware-arqivist/wrap-trace-events :trace-events]]}
   ;; pretty-print reitit exceptions for human consumptions
   :exception pretty/exception})

(defn app
  "Router for all requests to The Arqivist service and OpenAPI documentation,
  using `ring-handler` to manage HTTP request and responses."
  [system]

  (ring/ring-handler
   (ring/router
    [open-api-docs
     (system-admin/routes)

     ;; The Arqivist routes
     ["/api"
      ["/v1"
       (slack/routes system)
       (confluence/routes system)]]]

    ;; Router configuration - middleware, coersion & content negotiation
    router-configuration)

   ;; Default handlers
   (ring/routes
    ;; Open API documentation as default route
    (api-docs-ui/create-swagger-ui-handler {:path "/"})

    ;; Respond to any other route - returns blank page
    ;; TODO: create custom handler for routes not recognised
    (ring/create-default-handler))))
