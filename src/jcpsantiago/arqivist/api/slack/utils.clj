(ns jcpsantiago.arqivist.api.slack.utils
  "
  Extra functions related to the Slack endpoints.
  "
  (:require
   [com.brunobonacci.mulog :as mulog]
   [jcpsantiago.arqivist.api.slack.pages :as pages]
   [next.jdbc.sql :as sql]
   [ring.util.response :refer [response content-type]]
   [clj-slack.conversations :as slack-convo]
   [clj-slack.users :as slack-users]
   [clj-slack.bots :as slack-bots]))

(def slack-users-info (memoize slack-users/info))
(def slack-bots-info  (memoize slack-bots/info))

(defn fetch-messages
  "
  Fetches messages from a channel or message thread, iterating over
  the response in case it is paginated.
  "
  ([slack-connection channel-id]
   (iteration
    (fn [k]
      (slack-convo/history slack-connection channel-id {:cursor k}))
    :kf (fn [res] (get-in res [:response_metadata :next_cursor]))
    :vf :messages
    :initk ""
    :somef :messages))
  ([slack-connection channel-id thread-ts]
   (iteration
    (fn [k]
      (slack-convo/replies slack-connection channel-id thread-ts {:cursor k}))
    :kf (fn [res] (get-in res [:response_metadata :next_cursor]))
    :vf :messages
    :initk ""
    :somef :messages)))

(defn fetch-replies
  "
  Fetches replies if message is a thread parent, otherwise returns the original message.
  Adds a new keyword :replies to the message hash-map.
  "
  [message slack-connection channel-id]
  (if (contains? message :thread_ts)
    (assoc message :replies (->> (fetch-messages slack-connection channel-id (:thread_ts message))
                                 (sequence cat)
                                 ;; NOTE: the first message is the thread initiator
                                 rest))
    message))

(defn fetch-conversation-history
  "
  Fetches messages from a Slack conversation, including replies in threads.
  Returns a list of Slack conversations
  "
  [channel-id slack-connection]
  (try
    (let [messages (->> (fetch-messages slack-connection channel-id)
                        ;; FIXME: we are looping twice over all messages
                        ;; Once to fetch all messages, then again to fetch all replies.
                        ;; Find a way to avoid a second map over _all_ messages
                        ;; e.g. get the replies when fetching messages initially?
                        ;; I didn't do it because it felt too much for a single fn
                        (sequence cat)
                        (map #(fetch-replies % slack-connection channel-id)))]
      (mulog/log ::fetching-conversation-history
                 :success :true)
      messages)
    (catch Exception e
      (mulog/log ::fetching-conversation-history
                 :success :false
                 :error (.getMessage e)
                 :exception e
                 :local-time (java.time.LocalDateTime/now)))))

(defn detect-bot-user
  "
  Adds a :bot? key to a message hash-map indicating whether the message is from a bot or not.
  "
  [message]
  (let [bot? (or
              (contains? message :bot_profile)
              (contains? message :bot_id)
              (= (:subtype_message message) "bot_message"))]
    (assoc message :bot? bot?)))

;; DB fns -----------------------------------------------------------------------
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

    ;; TODO: if the SQL error happens due to a duplication, show the good outcome page anyways
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
      (-> pages/good-slack-outcome response (content-type "text/html"))

      (catch Exception e
        (mulog/log ::inserting-slack-team :team-id team_id :tenant-id tenant_id :error (.getMessage e) :local-time (java.time.LocalDateTime/now))
        (-> pages/sad-slack-outcome response (content-type "text/html"))))))
