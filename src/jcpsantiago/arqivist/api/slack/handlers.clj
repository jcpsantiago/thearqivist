(ns jcpsantiago.arqivist.api.slack.handlers
  "Ring handlers and support functions for Slack requests."
  (:require
   [clj-slack.oauth :as slack-oauth]
   [clj-slack.views :as slack-views]
   [clojure.spec.alpha :as spec]
   [clojure.string :refer [trim]]
   [com.brunobonacci.mulog :as mulog]
   [jcpsantiago.arqivist.api.slack.ui-blocks :as ui]
   [jcpsantiago.arqivist.api.slack.pages :as pages]
   [jcpsantiago.arqivist.api.slack.specs :as specs]
   [jcpsantiago.arqivist.api.slack.utils :as utils]
   [jcpsantiago.arqivist.messages :as messages]
   [jcpsantiago.arqivist.utils :as core-utils]
   [jsonista.core :as json]
   [next.jdbc.sql :as sql]
   ;; needed because PostgreSQL can't translate java datetime into SQL timestamp
   ;;https://cljdoc.org/d/com.github.seancorfield/next.jdbc/1.3.894/api/next.jdbc.date-time
   [next.jdbc.date-time]
   [ring.util.response :refer [bad-request response content-type]]))

;;
;; ------------------------------------------------------
;; Handlers for Slack interactivity
;;

(defn update-modal-response
  "
  Creates a ring response to update a modal based on a ui modal fn (see j.a.a.s.ui-blocks).
  "
  [ui-modal-fn request]
  (-> {:response_action "update"
       :view (ui-modal-fn request)}
      json/write-value-as-string
      response
      (content-type "application/json")))

(defn setup-archival-handler
  [system request job]
  (try
    (let [context (mulog/local-context)]
      (future
        (mulog/with-context context
          (messages/start-job system request job core-utils/persist-job!)))

      (update-modal-response ui/confirm-job-started-modal request))

    (catch Exception e
      (mulog/log ::handle-setup-archival-modal
                 :success :false
                 :error e
                 :error-message (ex-message e)
                 :local-time (java.time.LocalDateTime/now)))))
      ;; TODO: add ephemeral error message

(defn exists-once-confirmation-handler
  [system request job]
  (try
    (let [{:keys [channel_id existing-job]} (get-in request [:parameters :form :payload :view :private_metadata :channel_id])
          ;; FIXME: do we need this????
          updated-job (-> existing-job
                          (assoc :frequency (:frequency job)))]
      (mulog/log ::handling-existing-job-confirmation
                 :job existing-job
                 :request request
                 :local-time (java.time.LocalDateTime/now))
      (future
        (mulog/with-context (mulog/local-context)
          (messages/start-job system request updated-job core-utils/update-job!)))

      ;; job started confirmation modal
      (update-modal-response ui/confirm-job-started-modal request))

    (catch Exception e
      (mulog/log ::handle-setup-archival-modal
                 :success :false
                 :error e
                 :error-message (ex-message e)
                 :local-time (java.time.LocalDateTime/now)))))
      ;; TODO: add ephemeral error message

(defn view-submission
  "
  Creates a job to save a channel with a user-selected frequency in the setup-archival-modal.
  Also updates the view with feedback for the user.
  "
  [system request]
  (let [callback-id (get-in request [:parameters :form :payload :view :callback_id])
        job (core-utils/request->job request)]

    (case callback-id
      "setup-archival" (setup-archival-handler system request job)
      ;; TODO: also needs the new job, update existing job inside with the new information
      ;; otherwise we're overwriting with the same information over and over, or losing new info
      "exists-once-confirmation" (exists-once-confirmation-handler system request job))))

(defn interaction-handler
  "
  Ring handler for user interactions with modals and shortcuts.
  This is activated when users submit a modal (a 'view_submission'), or
  interact with a message shortcut (a 'message_action').
  "
  [system]
  (fn [{{{{:keys [type view]} :payload} :form} :parameters :as request}]
    (let [context {:type type
                   :callback-id (:callback_id view)
                   :frequency (get-in view [:state :values :archive_frequency_selector :radio_buttons-action :selected_option :value])}]

      (mulog/with-context
       context
        (mulog/log ::interaction-payload
                   :local-time (java.time.LocalDateTime/now))
        (case type
          "view_submission" (view-submission system request)
          "message_action" "TODO"
          (bad-request "Unknown type"))))))

;;
;; ------------------------------------------------------
;; Handlers for the `/arqive` slash command
;;
(defn help-message
  "
  Creates a response with information on how to use The Arqivist.
  Meant as the response to the `/arqive help` slash command + option,
  as well as other interactions where the user must be reminded of how to use the app.
  "
  []
  (-> (ui/help-message)
      json/write-value-as-string
      response
      (content-type "application/json")))

(defn setup-archival-modal
  "
  Block-kit representation of the 'Save to channel to Confluence' modal.
  This modal is shown to the user after usage of the `/arqive once|daily|weekly` slash command.
  "
  [system request]
  (try
    (let [channel_id (get-in request [:parameters :form :channel_id])
          open-view (partial slack-views/open (:slack-connection request))
          trigger_id (get-in request [:parameters :form :trigger_id])
          existing-job (sql/get-by-id (:db-connection system)
                                      :jobs
                                      channel_id
                                      :slack_channel_id
                                      {})]

      (if (empty? existing-job)
        (let [res (open-view (json/write-value-as-string (ui/setup-archival-modal request)) trigger_id)]
          (if (:ok res)
            (do
              (mulog/log ::save-to-confluence-modal
                         :success :true
                         :local-time (java.time.LocalDateTime/now))
              (response ""))

            ;; Couldn't open the setup archive modal
            ;; response-metadata has the reason, see slack docs
            (do
              (mulog/log ::save-to-confluence-modal
                         :success :false
                         :error (:error res)
                         :response-metadata (:response_metadata res)
                         :local-time (java.time.LocalDateTime/now))
              (response (core-utils/error-response-text)))))

       ;; If a job already exists in the database ↓
       ;; TODO: use cond here to account for all cases like once in db and once in setup modal
        (if (= "once" (:jobs/frequency existing-job))
          (do
            (mulog/log ::open-exists-once-confirmation-modal)
            (open-view
             (json/write-value-as-string (ui/exists-once-confirmation-modal existing-job request))
             trigger_id))

          (do
            (mulog/log ::open-exists-recurrent-confirmation-modal)
            (open-view
             (json/write-value-as-string (ui/exists-recurrent-information-modal existing-job request))
             trigger_id)))))

    (catch Exception e
      (mulog/log ::save-to-confluence-modal
                 :success :false
                 :error e
                 :request request))))

(defn slash-command
  "
  Handler function for /slack/slash route.
  This is the main entrypoint to the app's functionality.

  Users will type `/arqive [options]` in Slack.
  See j.a.a.s.specs ns for the request spec. 
  "
  [system]
  (fn [{{{:keys [text]} :form} :parameters :as request}]
    (mulog/log ::handling-slack-slash-command
               :text text
               :local-time (java.time.LocalDateTime/now))
    (let [trimmed-text (trim text)]
      (case trimmed-text
        "" (setup-archival-modal system request)
        "help" (help-message)
        ;; TODO: add jobs handler
        ;; "jobs" (list-jobs)
        ;; TODO: add stop saving job handler
        ;; "stop" (drop-job request)
        ;; TODO: add changelog handler
        ;; "changelog" (changelog)
        ;; TODO: if no match, respond with help message but explain there were no known keywords
        (response (str "I'm sorry but I don't know what"
                       " `" text "` " "means :sweat_smile:\n"
                       "Enter `/arqive` without any keywords to see options for saving the current channel or "
                       "check the available commands with `/arqive help`"))))))

;;
;; ------------------------------------------------------
;; Handler for OAuth redirection, installation in Slack
;;
;; TODO: Add alerting system on errors
(defn oauth-redirect
  "
  Handler for the redirect step in the OAuth dance.
  Inserts a Slack team's credentials into the database, linking it to an existing Atlassian tenant.
  "
  [system]
  (fn [request]
    (mulog/log ::oauth-redirect :local-time (java.time.LocalDateTime/now))
    (let [{:keys [db-connection slack-env]} system
          {:keys [client-id client-secret redirect-uri]} slack-env
          {:keys [code state]} (get-in request [:parameters :query])
          {:keys [:atlassian_tenants/tenant_id]} (sql/get-by-id
                                                  db-connection
                                                  :atlassian_tenants
                                                  state
                                                  :base_url
                                                  {:columns [[:id :tenant_id]]})

          token-response (slack-oauth/access
                          ;; there is no token yet, and this API call ignores it
                          {:api-url "https://slack.com/api" :token ""}
                          client-id client-secret code redirect-uri)]

      (cond
        ;; In case someone gets the "Add to Slack" link without installing in Confluence first
        (nil? tenant_id)
        (do
          (mulog/log ::inserting-slack-team
                     :success :false
                     :base-url state
                     :error "No tenant found in the db for the given base-url"
                     :local-time (java.time.LocalDateTime/now))
          (-> pages/sad-slack-outcome response (content-type "text/html")))

        (spec/invalid? (spec/conform ::specs/oauth-access token-response))
        (do
          (mulog/log ::inserting-slack-team
                     :success :false
                     :error "OAuth token response does not conform to spec"
                     :explanation (spec/explain ::specs/oauth-access token-response)
                     :local-time (java.time.LocalDateTime/now))
          (-> pages/sad-slack-outcome response (content-type "text/html")))

        (:ok token-response) (utils/insert-slack-team! token-response tenant_id db-connection)

        :else (do
                (mulog/log ::inserting-slack-team
                           :success :false
                           :tenant-id tenant_id
                           :error (:error token-response)
                           :local-time (java.time.LocalDateTime/now))
                (-> pages/sad-slack-outcome response (content-type "text/html")))))))
