(ns jcpsantiago.arqivist.api.slack.router
  (:require
   [jcpsantiago.arqivist.api.slack.handler :as handler]))

(defn routes
  "Routes invoked by Slack:
   * shortcut — triggered after the user clicks the message shortcut button
   * slash    — triggered after the user uses the `/arqive` slash command
   * redirect — called as part of the OAuth process at the end of installation"
  [_]
  [["/slack/shortcut"
    {:post
     {:summary "Handles requests started by the Slack message actions shortcut"
      :description ""

      ;; TODO: learn how to set this up correctly :s
      :parameters {:form :jcpsantiago.arqivist.api.slack.spec/shortcut-body}
      :responses {200 {:body string?}}
      :handler handler/slack-shortcut}}]
   ["/slack/redirect"
    {:get
     {:summary ""
      :description ""
      :parameters
      {:body :jcpsantiago.arqivist.api.slack.spec/redirect-body}
      :responses {200 {:body string?}}
      :handler (fn [x] (println "REDIRECT HANDLER"))}}]])
