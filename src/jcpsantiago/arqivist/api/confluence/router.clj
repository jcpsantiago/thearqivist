(ns jcpsantiago.arqivist.api.confluence.router
  "Reitit routes for interacting with Confluence."
  (:require
   [jcpsantiago.arqivist.api.confluence.handlers :as handlers]
   [jcpsantiago.arqivist.api.confluence.specs :as specs]))

(defn routes
  "Routes used by Confluence:
   * descriptor.json

   * Lifecycle endpoints
     * installed
     * enabled
     * uninstalled

   * get-started"
  [system]
  ["/confluence"
   {:swagger {:tags ["Confluence"]
              :externalDocs
              {:description "Connect app descriptor docs"
               :url "https://developer.atlassian.com/cloud/confluence/connect-app-descriptor/#app-descriptor-structure"}}}

   ["/descriptor.json"
    {:get
     {:summary "Serves the Application Descriptor file"
      :description ""
      :handler (handlers/app-descriptor-json system)}}]

   ;; FIXME: These routes need different kind of validation than the non-lifecycle routes
   ;; https://developer.atlassian.com/cloud/confluence/security-for-connect-apps/#validating-installation-lifecycle-requests
   ["/installed"
    {:post
     {:summary "Webhook for the 'install' event"
      :description "Webhook for the `install` event<br>
                    Triggered in various events, but the most important is the initial app installation to a site.<br>
                    A new `sharedSecret` is created for each installation event."
      :parameters {:body :jcpsantiago.arqivist.api.confluence.specs/lifecycle}
      :handler (handlers/lifecycle system)}}]

   ["/enabled"
    {:post
     {:summary "Webhook for the 'enabled' event"
      :description "Webhook for the `enabled` event.<br>
                    App is enabled and users can start using the app.<br>
                    Triggered after a successful app installation or upgrade. This event will not be triggered for any other type of installed lifecycle events."
      :parameters {:body :jcpsantiago.arqivist.api.confluence.specs/lifecycle}
      :handler (handlers/lifecycle system)}}]

   ["/uninstalled"
    {:post
     {:summary "Webhook for the 'uninstall' event"
      :description "Webhook for the `uninstall` event when a user uninstalls the app from a site."
      :parameters {:body :jcpsantiago.arqivist.api.confluence.specs/lifecycle}
      :handler (handlers/lifecycle system)}}]

   ["/get-started"
    {:get
     {:summary "App's 'Get-started' page in Confluence"
      :description "Serves the 'Get-started' page shown after the app is installed in Confluence. The page is rendered in an iframe, and contains a button to let the use connect their Slack workspace."
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]])
