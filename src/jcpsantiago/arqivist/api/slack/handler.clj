(ns jcpsantiago.arqivist.api.slack.handler
  "Ring handlers and support functions for Slack requests."
  (:require
   [com.brunobonacci.mulog :as mulog]))

;; ------------------------------------------------------
;; Handlers for Slack Message Shortcut 

(defn message-action-handler
  "Handles requests sent to /slack/shortcut with type `message_action`"
  [_]
  (mulog/log ::handling-slack-message-action
             :local-time (java.time.LocalDateTime/now)))

(defn view-submission-handler
  "Handles requests sent to /slack/shortcut with type `view_submission`"
  [_]
  (mulog/log ::handling-slack-view-submission
             :local-time (java.time.LocalDateTime/now)))

;; Shortcut entrypoint
(defn slack-shortcut
  "Handler function for /slack/shortcut route,
  NOTE: request validated via body parameters in router definition using billie.fraud.spec
  Arguments: TODO"
  [{{{{payload-type :type} :payload} :form} :parameters}]
  (mulog/log ::handling-slack-shortcut
             :local-time (java.time.LocalDateTime/now))

  (future
    (case payload-type
      "message_action" (message-action-handler "placeholder")
      "view_submission" (view-submission-handler "placeholder")
      "Payload of unknown type received, swiftly ignored."))

      ;; Immediate response to Slack
  {:status 200
   :body ""
   :headers {}})
