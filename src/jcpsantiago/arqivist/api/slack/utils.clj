(ns jcpsantiago.arqivist.api.slack.utils
  "
  Extra functions related to the Slack endpoints.
  "
  (:require
   [com.brunobonacci.mulog :as mulog]
   [next.jdbc.sql :as sql]
   [ring.util.response :refer [redirect]]))

(defn insert-slack-team!
  "
  Inserts information about a Slack team into the 'slack_teams' db table.
  Takes a ::specs/oauth-access response, a tenant_id (from db) and a db-connection as inputs,
  returns a ring response map.
  "
  [token-response tenant_id db-connection]
  (let [{:keys [team authed_user scope access_token bot_user_id app_id]} token-response
        {team_id :id team_name :name} team]

    (mulog/log ::inserting-slack-team :team-id team_id :local-time (java.time.LocalDateTime/now))

    (try
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
      (catch Exception e (mulog/log ::inserting-slack-team :team-id team_id :tenant-id tenant_id :error (.getMessage e) :local-time (java.time.LocalDateTime/now)))
      (finally (redirect "https://arqivist.app")))

    (redirect "https://arqivist.app")))
