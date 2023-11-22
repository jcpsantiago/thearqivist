(ns jcpsantiago.arqivist.messages
  "
  Namespace with the core functions to fetch, parse, and format Slack messages,
  as well as deal with scheduling.
  "
  (:require
   [clojure.string :as string]
   [com.brunobonacci.mulog :as mulog]
   [clj-slack.chat :as slack-chat]
   [clj-slack.conversations :as slack-convo]
   [jcpsantiago.arqivist.parsers :as parsers]
   [jcpsantiago.arqivist.utils :as core-utils]
   [jcpsantiago.arqivist.api.slack.utils :as slack-utils]
   [jcpsantiago.arqivist.api.confluence.pages :as confluence-pages]
   [next.jdbc.sql :as sql]
   [next.jdbc.date-time]))

(defmulti archive!
  "
  Dispatch function for creating archives of Slack messages.
  The `job` hash-map contains information about the :target, which
  is used to select which method to use.
  "
  (fn [job _ _ _] (:target job)))

(defmethod archive! "confluence"
  [job slack-connection confluence-credentials messages]
  ;; TODO: Check if parent page already exists, else create it
  ;; Save new messages as child pages where title is <min date> — <max date>
  (let [page-rows (->> messages
                       (pmap #(parsers/parse-message job slack-connection %))
                       (sort-by :ts #(compare %2 %1))
                       (map confluence-pages/page-row))]
    ;; NOTE: the create-content! fn saves data in Confluence's page storage,
    ;; so we don't save it ourselves. It stores a date as the 'next-update'
    ;; so we don't need to keep track of scheduling.
    (->> page-rows
         (confluence-pages/archival-page job)
         (confluence-pages/create-content! job confluence-credentials))))

(defn the-scribe
  "
  The scribe is the main function for handling archival jobs.
  It takes a `job` (see j.a.spec ns) and a slack connection,
  and pulls, prepares and creates archives in the target destination.
  "
  [system job inform? slack-connection target-credentials]
  (let [{:keys [slack_channel_id owner_slack_user_id]} job
        channel-info-response (slack-convo/info slack-connection slack_channel_id)
        channel-name (get-in channel-info-response [:channel :name])
        messages (slack-utils/fetch-conversation-history slack_channel_id slack-connection)]

    (if (seq channel-name)
      ;; TODO: create a spec for a uniform response format for archive!
      (let [archival-response (-> (assoc job :channel-name channel-name)
                                  (archive! slack-connection target-credentials messages))]
        (if (contains? archival-response :archive-url)
          (do
            (let [last-ts (->> messages (sort-by :ts) last :ts)
                  last-datetime (-> last-ts
                                    (string/replace #"\..+" "")
                                    (Long/parseLong)
                                    (java.time.Instant/ofEpochSecond))
                  updates {:target_url (:archive-url archival-response)
                           :frequency (:frequency job)
                           :n_runs (or 1 (inc (:n_runs job)))
                           :last_slack_conversation_ts last-ts
                           :last_slack_conversation_datetime last-datetime
                           ;; TODO: util fn to calculate due date based on frequency
                           :due_date (java.time.LocalDateTime/now)
                           :updated_at (java.time.LocalDateTime/now)}]

              (sql/update!
               (:db-connection system)
               :jobs
               updates
               {:slack_team_id (:slack_team_id job)})

              (mulog/log ::scribe-archive
                         :success :true
                         :job job
                         :team-id (:slack_team_id job)
                         :local-time (java.time.LocalDateTime/now)))

            (when inform?
              (slack-chat/post-message
               slack-connection
               slack_channel_id
               (str "<@" owner_slack_user_id "> requested the archival of this channel.\n"
                    "Find it in " (:archive-url archival-response)))))

          (do
            (mulog/log ::scribe-archive
                       :success :false
                       :local-time (java.time.LocalDateTime/now))
            (core-utils/ephemeral-error-message! owner_slack_user_id slack_channel_id slack-connection))))
      (do
        (mulog/log ::scribe-archive
                   :success :false
                   :message "Could not retrieve channel name"
                   :slack-error (:error channel-info-response)
                   :local-time (java.time.LocalDateTime/now))
        (core-utils/ephemeral-error-message! owner_slack_user_id slack_channel_id slack-connection)))))

(defn start-job
  "
  Collects the data needed to create a `job`, then sends it to the scribe for archival.
  Meant to run async, because the scribe can be slow.

  `db-fn` is a function which either inserts or updates a row in the jobs table.
  "
  [system request job db-fn]
  (try
    (let [db-connection (:db-connection system)
          atlassian_tenant_id (get-in request [:slack-team-attributes :slack_teams/atlassian_tenant_id])
          confluence-tenant-attributes (sql/get-by-id db-connection :atlassian_tenants atlassian_tenant_id)]

      (db-fn system job)

      (the-scribe system job true (:slack-connection request) confluence-tenant-attributes)

      (mulog/log ::start-job
                 :success :true
                 :local-time (java.time.LocalDateTime/now)))

    (catch Exception e
      (mulog/log ::create-and-start-job
                 :success :false
                 :error e
                 :error-message (ex-message e)
                 :local-time (java.time.LocalDateTime/now))

      (let [{:keys [channel_id user_id]} (-> request :parameters :form :payload :view :private_metadata read-string)]
        (core-utils/ephemeral-error-message! user_id channel_id (:slack-connection request))))))

