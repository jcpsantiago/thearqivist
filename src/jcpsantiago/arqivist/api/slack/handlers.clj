(ns jcpsantiago.arqivist.api.slack.handlers
  "Ring handlers and support functions for Slack requests."
  (:require
   [clj-slack.oauth :as clj-slack]
   [clojure.spec.alpha :as spec]
   [com.brunobonacci.mulog :as mulog]
   [jcpsantiago.arqivist.api.slack.ui-blocks :as ui]
   [jcpsantiago.arqivist.api.slack.pages :as pages]
   [jcpsantiago.arqivist.api.slack.specs :as specs]
   [jcpsantiago.arqivist.api.slack.utils :as utils]
   [jsonista.core :as json]
   [next.jdbc.sql :as sql]
   [ring.util.response :refer [response content-type]]))

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

;;
;; ------------------------------------------------------
;; Handlers for Slack Slash commands
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

(defn slash-command
  "
  Handler function for /slack/slash route.
  This is the main entrypoint to the app's functionality.

  Users will type `/arqive [options]` in Slack.
  See j.a.a.s.specs ns for the request spec. 
  "
  [_]
  (fn [{{{:keys [text]} :form} :parameters}]
    (mulog/log ::handling-slack-slash-command
               :text text
               :local-time (java.time.LocalDateTime/now))
    (let [first-word (re-find #"^\w+" text)]
      ;; TODO: respond to empty `text` with a modal containing various options
      (case first-word
        "help" (help-message)
        ;; TODO: add jobs handler
        ;; "jobs" (list-jobs)
        ;; TODO: add save once handler
        ;; "once" (save-to-confluence request)
        ;; TODO: add stop saving job handler
        ;; "stop" (drop-job request)
        ;; TODO: add save daily, etc job handler
        ;; "daily" (save-continously request)
        ;; TODO: add changelog handler
        ;; "changelog" (changelog)
        ;; TODO: if no match, respond with help message but explain there were no known keywords
        (response (str "I'm sorry but I don't know what"
                       " `" text "` " "means :sweat_smile:\nCheck the available commands with "
                       "`/arqive help`"))))))

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

          token-response (clj-slack/access
                          ;; there is no token yet, and this API call ignores it
                          {:api-url "https://slack.com/api" :token ""}
                          client-id client-secret code redirect-uri)]

      (cond
        ;; In case someone gets the "Add to Slack" link without installing in Confluence first
        (nil? tenant_id)
        (do
          (mulog/log ::inserting-slack-team
                     :base-url state
                     :error "No tenant found in the db for the given base-url"
                     :local-time (java.time.LocalDateTime/now))
          (-> pages/sad-slack-outcome response (content-type "text/html")))

        (spec/invalid? (spec/conform ::specs/oauth-access token-response))
        (do
          (mulog/log ::inserting-slack-team
                     :error (spec/explain-data ::specs/oauth-access token-response)
                     :local-time (java.time.LocalDateTime/now))
          (-> pages/sad-slack-outcome response (content-type "text/html")))

        (:ok token-response) (utils/insert-slack-team! token-response tenant_id db-connection)

        :else (do
                (mulog/log ::inserting-slack-team
                           :tenant-id tenant_id
                           :error (:error token-response)
                           :local-time (java.time.LocalDateTime/now))
                (-> pages/sad-slack-outcome response (content-type "text/html")))))))
