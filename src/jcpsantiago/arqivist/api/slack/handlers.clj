(ns jcpsantiago.arqivist.api.slack.handlers
  "Ring handlers and support functions for Slack requests."
  (:require
   [com.brunobonacci.mulog :as mulog]
   [org.httpkit.client :as httpkit]
   [jsonista.core :as jsonista]
   [next.jdbc.sql :as sql]))

;; TODO: move to utils/slack-api namespace
(defn oauth-access!
  "
  Sends a POST request to the oauth.v2.access Slack method.
  The response is documented in https://api.slack.com/authentication/oauth-v2#exchanging,
  and in the slack.specs namespace.

  Takes a map of form parameters with shape

  {:form-params {:code <temporary auth code>}
   :basic-auth [<slack client id>
                <slack client secret>]}

  Returns a promise.
  "
  [form-params]
  (httpkit/post "https://slack.com/api/oauth.v2.access" form-params))

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
(defn message-shortcut
  "Handler function for /slack/shortcut route,
  NOTE: request validated via form parameters in router definition using jcpsantiago.arqivist.api.slack.spec
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

;; ------------------------------------------------------
;; Handlers for Slack Slash commands

(defn slash-command
  "Handler function for /slack/slash route"
  [_]
  (mulog/log ::handling-slack-slash-command
             :local-time (java.time.LocalDateTime/now))
  {:status 200 :body "" :headers {}})

;; ------------------------------------------------------
;; Handler for OAuth redirection
(defn oauth-redirect
  "
  Handler for the redirect step in the OAuth dance.
  "
  [system]
  (fn [request]
    (mulog/log ::oauth-redirect :local-time (java.time.LocalDateTime/now))
    (let [db-connection (:db-connection system)
          slack-env (:slack-env system)
          {:keys [code state]} (:query-params request)

          {:keys [:atlassian_tenants/tenant_id]} (-> (sql/find-by-keys
                                                      db-connection
                                                      :atlassian_tenants
                                                      {:base_url_short state}
                                                      {:columns [[:id :tenant_id]]})
                                                     first)

          token-response-promise (oauth-access!
                                  {:form-params {:code code}
                                   :basic-auth [(:client-id slack-env)
                                                (:client-secret slack-env)]})

          token-response (-> @token-response-promise
                             :body
                             (jsonista/read-value jsonista/keyword-keys-object-mapper))

          {:keys [team authed_user scope access_token bot_user_id app_id]} token-response
          {team_id :id team_name :name} team]

      (mulog/log ::inserting-slack-team :team-id team_id :tenant-id tenant_id :local-time (java.time.LocalDateTime/now))

      ;; TODO: try-catch etc
      (sql/insert! db-connection
                   :atlassian_tenants
                   {:app_id app_id
                    :external_team_id team_id
                    :team_name team_name
                    :registering_user (:id authed_user)
                    :scopes scope
                    :access_token access_token
                    :bot_user_id bot_user_id
                    :atlassian_tenant_id tenant_id}
                   {:return-keys true})

      ;;TODO: Add success page here
      {:status 200 :body "YEY! You're connected!"})))
