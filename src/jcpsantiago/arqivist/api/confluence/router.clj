(ns jcpsantiago.arqivist.api.confluence.router
  "Reitit routes for interacting with Confluence.")

(defn routes
  "Routes used by Confluence:
   * descriptor.json

   -- Lifecycle endpoints
     * installed
     * enabled
     * uninstalled

   * get-started"
  [_]
  ["/confluence"
   {:swagger {:tags ["Confluence"]
              :externalDocs
              {:description "Connect app descriptor docs"
               :url "https://developer.atlassian.com/cloud/confluence/connect-app-descriptor/#app-descriptor-structure"}}}

   ["/descriptor.json"
    {:get
     {:summary "Serves the Application Descriptor file"
      :description ""
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]

   ["/installed"
    {:post
     {:summary "Webhook for the 'install' event"
      :description "Webhook for the `install` event<br>
                    Triggered in various events, but the most important is the initial app installation to a site.<br>
                    A new `sharedSecret` is created for each installation event."
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]

   ["/enabled"
    {:post
     {:summary "Webhook for the 'enabled' event"
      :description "Webhook for the `enabled` event.<br>
                    App is enabled and users can start using the app.<br>
                    Triggered after a successful app installation or upgrade. This event will not be triggered for any other type of installed lifecycle events."
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]

   ["/uninstalled"
    {:post
     {:summary "Webhook for the 'uninstall' event"
      :description "Webhook for the `uninstall` event when a user uninstalls the app from a site."
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]

   ["/get-started"
    {:get
     {:summary "App's 'Get-started' page in Confluence"
      :description "Serves the 'Get-started' page shown after the app is installed in Confluence. The page is rendered in an iframe, and contains a button to let the use connect their Slack workspace."
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]])
