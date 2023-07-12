(ns jcpsantiago.arqivist.api.slack.router
  "Reitit routes for interacting with Slack."
  (:require
   [jcpsantiago.arqivist.middleware :as middleware-arqivist]
   [jcpsantiago.arqivist.api.slack.handlers :as handlers]
   [jcpsantiago.arqivist.api.slack.specs :as specs]))

(defn routes
  "Routes invoked by Slack:
   * shortcut — triggered after the user clicks the message shortcut button
   * slash    — triggered after the user uses the `/arqive` slash command
   * redirect — called as part of the OAuth process at the end of installation"
  [system]
  (let [wrap-verify-slack-request (partial middleware-arqivist/wrap-verify-slack-request (:slack-env system))]
    ["/slack"
     {:swagger {:tags ["Slack"]}}
     ["/shortcut"
      {:swagger {:externalDocs
                 {:description "Slack docs about Message Shortcuts"
                  :url "https://api.slack.com/interactivity/shortcuts/using#message_shortcuts"}}
       :middleware [[wrap-verify-slack-request :verify-slack-request]]
       :post
       {:summary "Target for Message Shortcut interactions"
        :description "This endpoint receives all interactions initiated by clicking the Message Shortcut button. It also receives all follow-up interactions with the use via modals."
        ;; TODO: add the rest of the specs, not working yet
        ;; :parameters {:body :jcpsantiago.arqivist.api.slack.spec/shortcut-body}
        :responses {200 {:body string?}}
        :handler handlers/message-shortcut}}]

     ;; TODO: Add example from https://api.slack.com/interactivity/slash-commands#app_command_handling
     ["/slash"
      {:swagger {:externalDocs
                 {:description "Slack docs about Slash Commands"
                  :url "https://api.slack.com/interactivity/slash-commands"}}
       :middleware [[wrap-verify-slack-request :verify-slack-request]]
       :post
       {:summary "Target for Slash Command interactions"
        :description "This endpoint receives all interactions initiated by typing the `/arqive` slash command."
        ;; TODO: add the rest of the specs, not working yet
        ;; :parameters {:form :jcpsantiago.arqivist.api.slack.spec/slash-form-params}
        :responses {200 {:body string?}}
        :handler handlers/slash-command}}]

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
