(ns jcpsantiago.arqivist.api.slack.router
  "Reitit routes for interacting with Slack."
  (:require
   [jcpsantiago.arqivist.middleware :as middleware-arqivist]
   [jcpsantiago.arqivist.api.slack.handlers :as handlers]
   [jcpsantiago.arqivist.api.slack.specs :as specs]))

(defn routes
  "Routes invoked by Slack:
   * slash         — triggered after the user uses the `/arqive` slash command
   * interactivity — triggered after the user submits a modal
   * redirect      — called as part of the OAuth process at the end of installation"
  [system]
  (let [wrap-verify-slack-request (partial middleware-arqivist/wrap-verify-slack-request (:slack-env system))
        wrap-add-slack-team-attributes (partial middleware-arqivist/wrap-add-slack-team-attributes (:db-connection system))]
    ["/slack"
     {:swagger {:tags ["Slack"]}}
     ["/interactivity"
      {:swagger {:externalDocs
                 {:description "Slack docs about Slash Commands"
                  :url "https://api.slack.com/interactivity/slash-commands"}}
       :middleware [[wrap-verify-slack-request :verify-slack-request]
                    [middleware-arqivist/wrap-parse-interaction-payload "parse-interaction-payload"]
                    [wrap-add-slack-team-attributes :add-slack-team-attributes]]
       :post
       {:summary "Target for Slash Command interactions"
        :description "This endpoint receives all interactions initiated by typing the `/arqive` slash command."
        :parameters {:header ::specs/request-header-attributes
                     :form ::specs/interaction-payload}
        :responses {200 {:body string?}}
        :handler (handlers/interaction-handler system)}}]

     ;; TODO: Add example from https://api.slack.com/interactivity/slash-commands#app_command_handling
     ["/slash"
      {:swagger {:externalDocs
                 {:description "Slack docs about Slash Commands"
                  :url "https://api.slack.com/interactivity/slash-commands"}}
       :middleware [[wrap-verify-slack-request :verify-slack-request]
                    [wrap-add-slack-team-attributes :add-slack-team-attributes]]
       :post
       {:summary "Target for Slash Command interactions"
        :description "This endpoint receives all interactions initiated by typing the `/arqive` slash command."
        :parameters {:header ::specs/request-header-attributes
                     :form ::specs/slash-form-params}
        :responses {200 {:body string?}}
        :handler (handlers/slash-command system)}}]

     ["/redirect"
      {:swagger {:externalDocs
                 {:description "Slack OAuthV2 docs"
                  :url "https://api.slack.com/authentication/oauth-v2"}}
       :get
       {:summary "OAuth2 redirect target"
        :description "This endpoint receives the data about the workspace, after a user successfully added the app to their account."
        :parameters {:query ::specs/oauth-redirect}
        :responses {200 {:body string?}
                    500 {:body string?}}
        :handler (handlers/oauth-redirect system)}}]]))
