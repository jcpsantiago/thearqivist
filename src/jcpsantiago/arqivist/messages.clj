(ns jcpsantiago.arqivist.messages
  "
  Namespace with the core functions to fetch, parse, and format Slack messages,
  as well as deal with scheduling.
  "
  (:require
   [clojure.string :as string]
   [clojure.spec.alpha :as spec]
   [com.brunobonacci.mulog :as mulog]
   [clj-slack.chat :as slack-chat]
   [clj-slack.conversations :as slack-convo]
   [java-time.api :as java-time]
   [jcpsantiago.arqivist.parsers :as parsers]
   [jcpsantiago.arqivist.utils :as core-utils]
   [jcpsantiago.arqivist.api.slack.utils :as slack-utils]
   [jcpsantiago.arqivist.api.confluence.pages :as confluence-pages]
   [jcpsantiago.arqivist.api.confluence.utils :as confluence-utils]
   [jcpsantiago.arqivist.api.confluence.specs :as confluence-specs]
   [next.jdbc.sql :as sql]
   [next.jdbc.date-time]))

(defmulti archive!
  "
  Dispatch function for creating archives of Slack messages.
  The `job` hash-map contains information about the :target, which
  is used to select which method to use.
  "
  (fn [job _ _ _ _] (:jobs/target job)))

(defn parse-messages-and-create-page
  "
  Function orchestrating the creation of Confluence pages.
  "
  [job messages parent-id slack-connection confluence-credentials]
  (let [;; NOTE: Title renders as "YYYY-MM-DD HH:MM — YYYY-MM-DD HH:MM"
        latest-timestamp (max-key :ts messages)
        timezone (:jobs/timezone job)
        title (str
               (core-utils/slack-ts->datetime
                (min-key :ts messages)
                timezone)
               "—"
               (core-utils/slack-ts->datetime
                latest-timestamp
                timezone))
        page-rows (->> messages
                       (pmap #(parsers/parse-message job slack-connection %))
                       (sort-by :ts #(compare %2 %1))
                       (map confluence-pages/page-row))
        metadata-kvm {:arqivist_slack_thread_last_message_ts latest-timestamp
                      :arqivist_slack_channel_id (:jobs/slack_channel_id job)}
        page-attributes {:metadata-kvm metadata-kvm
                         :title title}]
    (->> page-rows
         (confluence-pages/archival-page job)
         (confluence-pages/create-content-body job parent-id page-attributes)
         (confluence-pages/create-content! confluence-credentials))))

(defmethod archive! "confluence"
  [job system slack-connection confluence-credentials messages]
  (mulog/log ::archive!
             :job job)
  (try
    (let [{:keys [:atlassian_tenants/base_url :atlassian_tenants/shared_secret]} confluence-credentials
          {:keys [:jobs/slack_channel_id]} job
          atlassian-env (:atlassian-env system)
          cql-response (confluence-utils/search-with-cql!
                        base_url shared_secret (:descriptor-key atlassian-env) "arqivist_parent_slack_channel_id" (str "parent_" slack_channel_id))
          [response-type response] (spec/conform ::confluence-specs/cql-search cql-response)
          [results-type results] (:results response)]
      (cond
        (and (= :good response-type) (= :empty results-type))
        ;; TODO: make the metadata literal into fn and reuse it where necessary
        (let [metadata-kvm {:arqivist_parent_slack_channel_id (str "parent_" slack_channel_id)}
              page-attributes {:metadata-kvm metadata-kvm
                               :title (:channel-name job)}
              {:keys [page-id]}
              (->> job
                   (confluence-pages/parent-page)
                   (confluence-pages/create-content-body job nil page-attributes)
                   (confluence-pages/create-content! confluence-credentials))]
          ;; TODO: add log
          (parse-messages-and-create-page job messages page-id slack-connection confluence-credentials))

        (and (= :good response-type) (= :populated results-type))
        (let [parent-id (get-in (first results) [:content :id])]
         ;; TODO: add log
          (parse-messages-and-create-page
           job messages parent-id slack-connection confluence-credentials))

        (= :error response-type)
        (do
          (mulog/log ::archive-confluence
                     :success :false
                     :message "Failed to search Confluence with CQL"
                     :error (:message response))
          response)))
    (catch Exception e
      (mulog/log ::archive-confluence
                 :success :false
                 :error (.getMessage e)
                 :exception e))))

(defn due-date
  "
  Takes `frequency` as a string, and returns `java-time/local-date-time`
  plus one unit of the provided frequency e.g. one week.

  If frequency is `once` returns nil.
  "
  [frequency]
  (case frequency
    "once" nil
    "daily"  (java-time/+ (java-time/local-date-time) (java-time/days 1))
    "weekly" (java-time/+ (java-time/local-date-time) (java-time/weeks 1))))

;; TODO:
;; * clean up in case it's a first-time run and creating the archive fails
(defn the-scribe
  "
  The scribe is the main function for handling archival jobs.
  It takes a `job` (see j.a.spec ns) and a slack connection,
  and pulls, prepares and creates archives in the target destination.
  "
  [system job inform? slack-connection target-credentials]
  (mulog/log ::the-scribe-pre
             :slack-connection slack-connection
             :job job
             :target-credentials target-credentials)
  (let [{:keys [:jobs/slack_channel_id :jobs/owner_slack_user_id]} job
        channel-info-response (slack-convo/info slack-connection slack_channel_id)
        channel-name (get-in channel-info-response [:channel :name])
        messages (slack-utils/fetch-conversation-history slack-connection slack_channel_id)]
    (mulog/log ::the-scribe-post-1
               :messages messages
               :channel-info channel-info-response)

    ;; TODO: check if messages is alright
    (if (seq channel-name)
      ;; TODO: create a spec for a uniform response format for archive!
      (let [archival-response (-> (assoc job :channel-name channel-name)
                                  (archive! system slack-connection target-credentials messages))]
        (if (contains? archival-response :archive-url)
          (do
            (let [frequency (:jobs/frequency job)
                  last-ts (->> messages (sort-by :ts) last :ts)
                  last-datetime (-> last-ts
                                    (string/replace #"\..+" "")
                                    (Long/parseLong)
                                    (java.time.Instant/ofEpochSecond))
                  n-runs (if (nil? (:jobs/n_runs job))
                           1
                           (inc (:jobs/n_runs job)))
                  updates {:jobs/target_url (:archive-url archival-response)
                           :jobs/frequency frequency
                           ;; NOTE: n_runs can't be null, inc explodes, check beforehand
                           :jobs/n_runs n-runs
                           :jobs/last_slack_conversation_ts last-ts
                           :jobs/last_slack_conversation_datetime last-datetime
                           :jobs/due_date (due-date frequency)
                           :jobs/updated_at (java-time/local-date-time)}]

              (sql/update!
               (:db-connection system)
               :jobs
               updates
               {:id (:jobs/id job)})

              (mulog/log ::scribe-archive
                         :success :true
                         :job job
                         :updates updates
                         :team-id (:jobs/slack_team_id job)
                         :local-time (java.time.LocalDateTime/now)))

            (when inform?
              (slack-chat/post-message
               slack-connection
               slack_channel_id
               (str "<@" owner_slack_user_id "> requested the archival of this channel.\n"
                    "Find it in " (:archive-url archival-response)))))

          ;; Something went wrong, no :archive-url present
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
          confluence-tenant-attributes (sql/get-by-id db-connection :atlassian_tenants atlassian_tenant_id)
          db-io-result (db-fn system job)
          job (merge job (select-keys db-io-result [:jobs/id]))]

      (mulog/log ::start-job-db-io
                 ;; FIXME: get the name of the function used as a string
                 :db-fn db-fn
                 :job job
                 :db-io-result db-io-result
                 :success :true
                 :local-time (java.time.LocalDateTime/now))

      (the-scribe system job true (:slack-connection request) confluence-tenant-attributes)

      (mulog/log ::start-job
                 :success :true
                 :local-time (java.time.LocalDateTime/now)))

    (catch Exception e
      (do
        (mulog/log ::create-and-start-job
                   :success :false
                   :error e
                   :error-message (ex-message e)
                   :local-time (java.time.LocalDateTime/now))
        (let [{:keys [channel_id user_id]} (-> request :parameters :form :payload :view :private_metadata read-string)]
          (core-utils/ephemeral-error-message! user_id channel_id (:slack-connection request)))))))

