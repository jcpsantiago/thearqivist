(ns jcpsantiago.arqivist.utils
  "Utility functions parked here until we find a better final home."
  (:require
   [clj-slack.chat :as slack-chat]
   [clj-slack.users :as slack-users]
   [clojure.spec.alpha :as spec]
   [com.brunobonacci.mulog :as mulog]
   [java-time.api :as java-time]
   [jcpsantiago.arqivist.specs :as core-specs]
   [next.jdbc.sql :as sql]))

(defn ts->datetime
  "Convert a UNIX timestamp into a java instant."
  [ts tz]
  (let [epoch-seconds (Long/parseLong ts)
        zone (java.time.ZoneId/of tz)]
    (-> epoch-seconds
        java.time.Instant/ofEpochSecond
        (.atZone zone))))

(defn unix-epoch
  []
  (quot (System/currentTimeMillis) 1000))

(defn to-seconds-from-epoch
  [epoch]
  (quot (java-time/to-millis-from-epoch epoch) 1000))

;; Messaging with the user
(defn error-response-text
  "
  Returns a string with text for informing the user a generic error has happened.
  Contains root-trace id from mulog.
  "
  ([]
   (error-response-text nil))
  ([user-id]
   (str  (when user-id (str "<@" user-id "> "))
         "Something didn't work 😣\n"
         "I've alerted my supervisor, and a fix will be deployed ASAP so please try again later.\n"
         "In case the error persists, please contact supervisor@arqivist.app directly and share the "
         "error code: `" (:mulog/root-trace (mulog/local-context)) "`.")))

(defn ephemeral-error-message!
  "
  Posts an ephemeral (only `user-id` will see it) in `channel-id` with a generic
  error message.
  "
  [user-id channel-id slack-connection]
  (slack-chat/post-ephemeral
   slack-connection
   channel-id
   (error-response-text user-id)
   ;; FIXME: HACK UNTIL THE FIX IS MERGED IN UPSTREAM
   {:user user-id}))

(defn request->job
  "
  Helper fn to create a `job` from a ring request originating in the `/interactivity` endpoint.
  Throws an exception if `job` has an invalid spec.
  "
  [request]
  (let [slack-connection (:slack-connection request)
        slack-team-id (get-in request [:slack-team-attributes :slack_teams/id])
        registering_user (get-in request [:slack-team-attributes :slack_teams/registering_user])
        tz (-> (slack-users/info slack-connection registering_user) :user :tz)
        view (get-in request [:parameters :form :payload :view])
        {:keys [channel_id user_id]} (-> view :private_metadata read-string)
        frequency (get-in view [:state :values :archive_frequency_selector
                                :radio_buttons-action :selected_option :value])
        job-map {:jobs/target "confluence" :jobs/frequency frequency
                 :jobs/slack_team_id slack-team-id :jobs/slack_channel_id channel_id
                 :jobs/owner_slack_user_id user_id :jobs/timezone tz :jobs/created_at (quot (System/currentTimeMillis) 1000)}
        job (spec/conform ::core-specs/job job-map)]

    (if (spec/invalid? job)
      (do
        (mulog/log ::request->job
                   :success :false
                   :message "Job spec is invalid"
                   :job job
                   :request request
                   :explanation (spec/explain-data ::core-specs/job job-map)
                   :local-time (java.time.LocalDateTime/now))
        (throw (ex-info "Job spec is invalid" (spec/explain-data ::core-specs/job job-map))))

      (do
        (mulog/log ::request->job
                   :success :true
                   :local-time (java.time.LocalDateTime/now))
        job))))

(defn persist-job!
  "
  Takes a `job` and persists it in the database.
  "
  [system job]
  (try
    (let [inserted (sql/insert! (:db-connection system) :jobs job)]
      (mulog/log ::create-recurrent-job-in-db
                 :success :true
                 :inserted inserted
                 :local-time (java.time.LocalDateTime/now))
      inserted)

    (catch Exception e
      (mulog/log ::create-recurrent-job-in-db
                 :success :false
                 :job job
                 :error (ex-data e)
                 :error-message (ex-message e)
                 :local-time (java.time.LocalDateTime/now))
      (throw (ex-info "Failed to insert job in database" e)))))

(defn update-job!
  "
  Takes a `job` and persists it in the database.
  "
  [system job]
  (try
    (sql/update! (:db-connection system)
                 :jobs
                 job
                 {:id (:jobs/id job)})

    (mulog/log ::update-job-in-db
               :success :true
               :local-time (java.time.LocalDateTime/now))

    (catch Exception e
      (mulog/log ::update-job-in-db
                 :success :false
                 :job job
                 :error (ex-data e)
                 :error-message (ex-message e)
                 :local-time (java.time.LocalDateTime/now))
      (throw (ex-info "Failed to insert job in database" e)))))

