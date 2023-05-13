(ns jcpsantiago.arqivist.api.slack.router
  (:require
   [jcpsantiago.arqivist.api.slack.handler :as handler]))

(defn routes
  "Routes invoked by Slack:
   * shortcut — triggered after the user clicks the message shortcut button
   * slash    — triggered after the user uses the `/arqive` slash command
   * redirect — called as part of the OAuth process at the end of installation"
  [_]
  ["/slack"
   {:swagger {:tags ["Slack"]}}
   ["/shortcut"
    {:post
     {:summary "Target for Message Shortcut interactions"
      :description "This endpoint receives all interactions initiated by clicking the Message Shortcut button. It also receives all follow-up interactions with the use via modals."

      ;; TODO: learn how to set this up correctly :s
      ;; :parameters {:form :jcpsantiago.arqivist.api.slack.spec/shortcut-body}
      :responses {200 {:body string?}}
      :handler handler/slack-shortcut}}]

   ["/redirect"
    {:get
     {:summary "OAuth2 redirect target"
      :description "This endpoint receives the data about the workspace, after a user successfully added the app to their account."
      ;; :parameters {:body :jcpsantiago.arqivist.api.slack.spec/redirect-body}
      :responses {200 {:body string?}}
      :handler {:status 200 :body "HELLO"}}}]])
