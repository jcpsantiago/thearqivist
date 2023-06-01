(ns jcpsantiago.arqivist.api.slack.handlers
  "Ring handlers and support functions for Slack requests."
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [com.brunobonacci.mulog :as mulog]
   [org.httpkit.client :as httpkit]
   [jsonista.core :as jsonista]
   [next.jdbc.sql :as sql]
   [ring.util.response :refer [redirect]]))

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
;; Handler for OAuth redirection, installation in Slack
;; TODO: Add success and error pages
(defn oauth-redirect
  "
  Handler for the redirect step in the OAuth dance.
  Inserts a Slack team's credentials into the database, linking it to an existing Atlassian tenant.
  "
  [system]
  (fn [request]
    (mulog/log ::oauth-redirect :local-time (java.time.LocalDateTime/now))
    (let [db-connection (:db-connection system)
          slack-env (:slack-env system)
          {:keys [code state]} (get-in request [:parameters :query])]

        (let [{:keys [:atlassian_tenants/tenant_id]} (-> (sql/find-by-keys
                                                          db-connection
                                                          :atlassian_tenants
                                                          {:base_url_short state}
                                                          {:columns [[:id :tenant_id]]})
                                                         first)

              token-response (-> @(oauth-access!
                                   {:form-params {:code code}
                                    :basic-auth [(:client-id slack-env)
                                                 (:client-secret slack-env)]})
                                 :body
                                 (jsonista/read-value jsonista/keyword-keys-object-mapper))]

          (if (true? (:ok token-response))
            (let [{:keys [team authed_user scope access_token bot_user_id app_id]} token-response
                  {team_id :id team_name :name} team]

              (mulog/log ::inserting-slack-team :team-id team_id :tenant-id tenant_id :local-time (java.time.LocalDateTime/now))

              ;; TODO: try-catch etc
              (sql/insert! db-connection
                           :slack_teams
                           {:app_id app_id
                            :external_team_id team_id
                            :team_name team_name
                            :registering_user (:id authed_user)
                            :scopes scope
                            :access_token access_token
                            :bot_user_id bot_user_id
                            :atlassian_tenant_id tenant_id}
                           {:return-keys true})

              (redirect "https://arqivist.app"))

            (do
              (mulog/log ::inserting-slack-team :tenant-id tenant_id :error (:error token-response) :local-time (java.time.LocalDateTime/now))
              (redirect "https://arqivist.app"))))))))
