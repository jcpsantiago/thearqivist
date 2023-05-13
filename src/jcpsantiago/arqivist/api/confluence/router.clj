(ns jcpsantiago.arqivist.api.confluence.router
  "Routes for interacting with Confluence")

(defn routes
  "Routes used by Confluence:
   * descriptor.json
   * enabled
   * installed
   * uninstalled
   * get-started"
  [_]
  ["/confluence"
   ["/descriptor.json"
    {:get
     {:summary "Serves the Application Descriptor file"
      :description ""
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]

   ["/enabled"
    {:post
     {:summary ""
      :description ""
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]

   ["/installed"
    {:post
     {:summary ""
      :description ""
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]

   ["/uninstalled"
    {:post
     {:summary ""
      :description ""
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]

   ["/get-started"
    {:get
     {:summary "App Get-started page in Confluence"
      :description "Serves the 'Get-started' page shown after the app is installed in Confluence. The page is rendered in an iframe, and contains a button to let the use connect their Slack workspace."
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO!"}}}]])
