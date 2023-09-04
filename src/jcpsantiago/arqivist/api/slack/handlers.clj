(ns jcpsantiago.arqivist.api.slack.handlers
  "Ring handlers and support functions for Slack requests."
  (:require
   [clj-slack.oauth :as slack-oauth]
   [clj-slack.views :as slack-views]
   [clj-slack.users :as slack-users]
   [clojure.spec.alpha :as spec]
   [clojure.string :refer [trim]]
   [com.brunobonacci.mulog :as mulog]
   [jcpsantiago.arqivist.messages :as messages]
   [jcpsantiago.arqivist.specs :as core-specs]
   [jcpsantiago.arqivist.api.slack.ui-blocks :as ui]
   [jcpsantiago.arqivist.api.slack.pages :as pages]
   [jcpsantiago.arqivist.api.slack.specs :as specs]
   [jcpsantiago.arqivist.api.slack.utils :as utils]
   [jsonista.core :as json]
   [next.jdbc.sql :as sql]
   [ring.util.response :refer [bad-request response content-type]]))

;;
;; ------------------------------------------------------
;; Handlers for Slack interactivity
;;

(defn request->job
  "
  Creates a `job` from a ring request sent via the /interactivity endpoint.
  "
  [action request]
  (let [slack-connection (:slack-connection request)
        registering_user (get-in request [:slack-team-attributes :slack_teams/registering_user])
        {:keys [tz real_name]} (-> (slack-users/info slack-connection registering_user) :user)
        view (get-in request [:parameters :form :payload :view])
        {:keys [channel_id channel_name user_id domain]} (-> view :private_metadata read-string)
        frequency (get-in view [:state :values :archive_frequency_selector
                                :radio_buttons-action :selected_option :value])
        job-map {:target :confluence :action action :frequency frequency :channel-id channel_id
                 :channel-name (str "#" channel_name) :user-id user_id :user-name real_name
                 :timezone tz :created-at (java.time.Instant/now) :domain domain}
        job (spec/conform ::core-specs/job job-map)]

    (if (spec/invalid? job)
      ;; FIXME: throw here? we have to do something :D
      (mulog/log ::request->job
                 :success :false
                 :job job
                 :job-map job-map
                 :view view
                 :request request
                 :explanation (spec/explain-data ::core-specs/job job-map))
      job)))

(defn start-job
  "
  Collects the data needed to create a `job`, then sends it to the scribe for archival.
  Meant to run async, because the scribe can be slow.
  "
  [action system request]
  (mulog/log ::start-job-1 :request request :system system :action action)
  (let [atlassian_tenant_id (get-in request [:slack-team-attributes :slack_teams/atlassian_tenant_id])
        confluence-tenant-attributes (sql/get-by-id
                                      (:db-connection system)
                                      :atlassian_tenants
                                      atlassian_tenant_id)
        job (request->job action request)]
    (mulog/log ::start-job
               :source :user-interaction
               :job job
               :confluence-tenant confluence-tenant-attributes
               :local-time (java.time.LocalDateTime/now))
    (messages/the-scribe
     job
     (:slack-connection request)
     confluence-tenant-attributes)))

(defn view-submission
  "
  Creates a job to save a channel with a user-selected frequency in the setup-archival-modal.
  Also updates the view with feedback for the user.
  "
  [system request]
  (mulog/log ::view-submitted
             :local-time (java.time.LocalDateTime/now))
  (let [context (mulog/local-context)]
    ;; start archival job in a different thread, because it might take time
    (future
      (mulog/with-context
       context
       (start-job "create" system request)))
    ;; immediate response with an updated modal giving feedback to user
    (-> {:response_action "update"
         :view (ui/confirm-job-started-modal request)}
        json/write-value-as-string
        response
        (content-type "application/json"))))

(defn interaction-handler
  "
  Ring handler for user interactions with modals and shortcuts.
  This is activated when users submit a modal (a 'view_submission'), or
  interact with a message shortcut (a 'message_action').
  "
  [system]
  (fn [{{{{:keys [type team user] :as payload} :payload} :form} :parameters :as request}]
    (let [context {:type type
                   :team (:id team)
                   :user (:id user)
                   :frequency (get-in payload [:view :state :values :archive_frequency_selector :radio_buttons-action :selected_option :value])}]

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
  [request]
  (let [res (slack-views/open
             (:slack-connection request)
             (json/write-value-as-string (ui/setup-archival-modal request))
             (get-in request [:parameters :form :trigger_id]))]

    (if (:ok res)
      (do
        (mulog/log ::save-to-confluence-modal
                   :success :true
                   :local-time (java.time.LocalDateTime/now))
        (response ""))

      (do
        (mulog/log ::save-to-confluence-modal
                   :success :false
                   :error (:error res)
                   :response_metadata (:response_metadata res)
                   :local-time (java.time.LocalDateTime/now))
        (bad-request "")))))

(defn slash-command
  "
  Handler function for /slack/slash route.
  This is the main entrypoint to the app's functionality.

  Users will type `/arqive [options]` in Slack.
  See j.a.a.s.specs ns for the request spec. 
  "
  [_]
  (fn [{{{:keys [text]} :form} :parameters :as request}]
    (mulog/log ::handling-slack-slash-command
               :text text
               :local-time (java.time.LocalDateTime/now))
    (let [trimmed-text (trim text)]
      (case trimmed-text
        "" (setup-archival-modal request)
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
