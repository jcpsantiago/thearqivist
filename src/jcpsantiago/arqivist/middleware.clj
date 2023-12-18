(ns jcpsantiago.arqivist.middleware
  "Extra ring middleware"
  (:require
   [buddy.core.codecs :as codecs]
   [buddy.core.mac :as mac]
   [buddy.core.keys :as keys]
   [buddy.sign.jws :as jws]
   [buddy.sign.jwt :as jwt]
   [clojure.spec.alpha :as spec]
   [clojure.string :as string]
   [com.brunobonacci.mulog :as mulog]
   [next.jdbc.sql :as sql]
   [jcpsantiago.arqivist.utils :as core-utils]
   [jcpsantiago.arqivist.api.confluence.utils :as utils]
   [jcpsantiago.arqivist.api.slack.specs :as specs]
   [ring.util.response :refer [bad-request response]]
   [clj-slack.conversations :as slack-convo]
   [jsonista.core :as json]
   [clojure.core :as c]))

;; Slack middleware ----------------------------------------------------------
(defn wrap-keep-raw-json-string
  "Middleware that slurps the bytestream in the :body of a request,
   updating it with the unparsed, uncoerced json string.
   Needed to verify Slack requests."
  [handler id]
  (fn [request]
    (if (and (:body request) (re-find #"slack" (:uri request)))
      (let [raw-body (slurp (:body request))
            request' (-> request
                         (assoc :raw-body raw-body)
                         (assoc :body (-> raw-body
                                          (.getBytes "UTF-8")
                                          java.io.ByteArrayInputStream.)))]
        (mulog/log ::keeping-raw-json-string
                   :middleware-id id
                   :uri (:uri request)
                   :local-time (java.time.LocalDateTime/now))
        (handler request'))
      ;; not a slack route with a body
      (handler request))))

(defn from-slack?
  "Verifies if the request really came from Slack.
  https://api.slack.com/authentication/verifying-requests-from-slack"
  [signing-secret timestamp payload slack-signature]
  (try
    (assert signing-secret "Slack signing secret is missing, check env vars in the donut system!")

    (mac/verify (str "v0:" timestamp ":" payload)
                (codecs/hex->bytes slack-signature)
                {:key signing-secret :alg :hmac+sha256})

    (catch
     Exception e
      (mulog/log ::verify-mac-hash
                 :success :false
                 :error (.getMessage e)
                 :local-time (java.time.LocalDateTime/now)))))

(defn wrap-verify-slack-request
  "Ring middleware to verify the authenticity of requests
   hitting the Slack endpoints.
   See official docs https://api.slack.com/authentication/verifying-requests-from-slack"
  [slack-env handler id]
  (fn [request]
    (let [{:keys [x-slack-signature x-slack-request-timestamp]} (get-in request [:parameters :header])
          ;; NOTE: clojure spec ensures x-slack-signature is non-blank
          slack-signature (string/replace x-slack-signature #"v0=" "")
          valid-slack-request? (from-slack? (:signing-secret slack-env)
                                            x-slack-request-timestamp
                                            (:raw-body request)
                                            slack-signature)]
      (if valid-slack-request?
        (do
          (mulog/log ::verify-slack-request :middleware-id id :success :true
                     :local-time (java.time.LocalDateTime/now))
          (handler request))
        (do
          (mulog/log ::verify-slack-request :middleware-id id :success :false
                     :error "The request is not from Slack"
                     :local-time (java.time.LocalDateTime/now))
          {:status 403 :body "Invalid credentials provided"})))))

(defn wrap-parse-interaction-payload
  "
  Ring middleware to parse the JSON string in the payload key of Slack interaction payloads.
  "
  [handler _]
  (fn [request]
    (let [payload (get-in request [:parameters :form :payload])
          parsed (json/read-value payload json/keyword-keys-object-mapper)
          conformed (spec/conform ::specs/view-submission-payload parsed)]

      (if (spec/invalid? conformed)
        (do
          (mulog/log ::parse-interaction-payload
                     :success :false
                     :request request
                     :explanation (spec/explain-data ::specs/view-submission-payload parsed)
                     :local-time (java.time.LocalDateTime/now))
          (bad-request ""))
        (do
          (mulog/log ::parse-interaction-payload
                     :success :true
                     :local-time (java.time.LocalDateTime/now))
          (handler (assoc-in request [:parameters :form :payload] conformed)))))))

(defn wrap-add-slack-team-attributes
  "
  Ring middleware to add slack team credentials needed to use the Slack API.
  Every Slack interaction needs this, except for the /redirect endpoints
  which is called during installation.
  "
  [db-connection handler _]
  (fn [request]
    (try
      (let [team_id (or
                     ;; from slash command
                     (get-in request [:parameters :form :team_id])
                     ;; from interaction payload e.g. after submitting a view
                     (get-in request [:parameters :form :payload :team :id]))
            slack-team-row (sql/get-by-id db-connection :slack_teams
                                          team_id
                                          :external_team_id {})
            slack-team-attributes (spec/conform ::specs/team-attributes slack-team-row)
            ;; for use in clj-slack's functions, see https://github.com/julienXX/clj-slack
            slack-connection {:api-url "https://slack.com/api"
                              :token (:slack_teams/access_token slack-team-attributes)}]

        ;; TODO: add more non PII details to context
        (mulog/with-context {:slack-team-id team_id}
                            (cond
                              (and (spec/valid? ::specs/team-attributes slack-team-attributes)
                                   (seq (:token slack-connection)))
                              (do
                                (mulog/log ::add-slack-team-attributes
                                           :success :true
                                           :local-time (java.time.LocalDateTime/now))
                                (-> request
                                    (assoc :slack-team-attributes slack-team-attributes)
                                    (assoc :slack-connection slack-connection)
                                    handler))

                              (spec/invalid? slack-team-attributes)
                              (do
                                (mulog/log ::add-slack-team-attributes
                                           :success :false
                                           :message "Slack team attributes did not conform to spec"
                                           ;; FIXME: The explanation will contain the slack access key!
                                           ;; we have to obfuscate that before logging
                                           :explanation (spec/explain-data ::specs/team-attributes slack-team-row)
                                           :request request
                                           :local-time (java.time.LocalDateTime/now))
                                (response (core-utils/error-response-text)))

                              (nil? (:token slack-connection))
                              (do
                                (mulog/log ::add-slack-team-attributes
                                           :success :false
                                           :message "Missing Slack connection token"
                                           :local-time (java.time.LocalDateTime/now))
                                (response (core-utils/error-response-text)))

                              ;; NOTE: edge-case, only became relevant during development
                              ;; it shouldn't be possible to have access to the app
                              ;; without having installed it first
                              (nil? slack-team-row)
                              (do
                                (mulog/log ::add-slack-team-attributes
                                           :success :false
                                           :error "Missing Slack credentials in the db"
                                           :local-time (java.time.LocalDateTime/now))
                                (response (core-utils/error-response-text)))

                              :else
                              (do
                                (mulog/log ::add-slack-team-attributes
                                           :success :false
                                           :error "Spec does not conform"
                                           :explanation (spec/explain ::specs/team-attributes slack-team-row))
                                (response (core-utils/error-response-text))))))

      (catch Exception e
        (mulog/log ::add-slack-team-attributes
                   :success :false
                   :exception e
                   :error (.getMessage e)
                   :local-time (java.time.LocalDateTime/now))
        (response (core-utils/error-response-text))))))

;; Utils and handler for "join slack channel" middleware --- ↓
(defn try-conversations-join
  "
  Helper function to the wrap-join-slack-channel middleware.
  Tries to join a Slack channel. If the channel is private, informs the user of this fact, and
  warns about the implications.
  "
  [slack-connection channel-id handler request]
  (let [conversations-join-response (slack-convo/join slack-connection channel-id)
        conversations-join (spec/conform ::specs/conversations-join conversations-join-response)
        good-response? (= (first conversations-join) :good-response)]
    (cond
      (and (spec/valid? ::specs/conversations-join conversations-join-response) good-response?)
      (do
        (mulog/log ::try-conversations-join
                   :success :true
                   :local-time (java.time.LocalDateTime/now))
        (handler request))

      (not good-response?)
      (do
        (mulog/log ::try-conversations-join
                   :success :false
                   :message "Error calling the conversations.join Slack API method"
                   :slack-error (:error conversations-join-response)
                   :explanation (spec/explain-data ::specs/conversations-join conversations-join-response)
                   :local-time (java.time.LocalDateTime/now))
        (response (core-utils/error-response-text)))

      (not (spec/valid? ::specs/conversations-join conversations-join-response))
      (do
        (mulog/log ::try-conversations-join
                   :success :false
                   :message "conversations.join response did not conform to spec"
                   :explanation (spec/explain-data ::specs/conversations-join conversations-join-response)
                   :local-time (java.time.LocalDateTime/now))
        (response (core-utils/error-response-text))))))

(defn wrap-join-slack-channel
  "
  Ring middleware which checks if The Arqivist bot is a member of a channel,
  then joins the channel, or asks the user to invite the bot in case it's a private channel.

  * Must run _after_ the wrap-add-slack-team-attributes middleware to access slack-connection
  * Used when interacting with the slash command endpoint.
  "
  [handler _]
  (fn [request]
    (let [channel_id (get-in request [:parameters :form :channel_id])
          slack-connection (:slack-connection request)
          conversations-info-response (slack-convo/info slack-connection channel_id)
          conversations-info (spec/conform ::specs/conversations-info conversations-info-response)
          valid-spec? (spec/valid? ::specs/conversations-info conversations-info-response)
          good-response? (= (first conversations-info) :good-response)
          {:keys [is_member is_private]} (-> conversations-info second :channel)]

      (cond
        (and valid-spec? good-response? is_member)
        (do
          (mulog/log ::join-slack-channel
                     :success :true
                     :local-time (java.time.LocalDateTime/now))
          (handler request))

        (and valid-spec? good-response? (not is_member) (not is_private))
        (try-conversations-join slack-connection channel_id handler request)

        (or
         (and valid-spec? good-response? (not is_member) is_private)
         (= (:error conversations-info-response) "channel_not_found"))
        (do
          (mulog/log ::join-slack-channel
                     :success :false
                     :message "Not a member of the private channel yet, informed user"
                     :local-time (java.time.LocalDateTime/now))
          (response
           (str "⚠️  It looks like you are trying to archive a *private channel*. "
                "Archived channels do not carry over permissions, so assume the contents of the archive will be public.\n"
                "If you are sure that is what you want, please invite me to this "
                "channel first by mentioning me with <@the_arqivist>, "
                "and then use the slash command again.")))

        (not good-response?)
        (do
          (mulog/log ::join-slack-channel
                     :success :false
                     :message "Error calling the conversations.info Slack API method"
                     :slack-error (:error conversations-info-response)
                     :local-time (java.time.LocalDateTime/now))
          (response (core-utils/error-response-text)))

        (not valid-spec?)
        (do
          (mulog/log ::join-slack-channel
                     :success :false
                     :message "Response from the conversations.info Slack API method does not conform to spec"
                     :explanation (spec/explain-data ::specs/conversations-info conversations-info-response);
                     :local-time (java.time.LocalDateTime/now))
          (response (core-utils/error-response-text)))))))

;; Logging middleware -----------------------------------------------------
;; https://github.com/BrunoBonacci/mulog/blob/master/doc/ring-tracking.md
(defn wrap-trace-events
  "Log event trace for each api event with mulog/log."
  [handler id]
  (fn [request]
    ;; Add context of each request to all trace events generated for the specific request
    (mulog/with-context
     {:uri            (get request :uri)
      :request-method (get request :request-method)})

    ;; track the request duration and outcome
    (mulog/trace
     :io.redefine.datawarp/http-request
     {:pairs [:content-type     (get-in request [:headers "content-type"])
              :content-encoding (get-in request [:headers "content-encoding"])
              :middleware       id]
      ;; capture http status code from the response
      :capture (fn [{:keys [status]}] {:http-status status})}
     (handler request))))

;; Atlassian middleware -----------------------------------------------------
(defn verify-atlassian-iframe
  "
  Middleware to verify the JWT token present in
  Atlassian iframe requests e.g. for the Get Started page.
  "
  [system]
  (fn [handler _]
    (fn [request]
      (let [base-url (str (get-in request [:parameters :query :xdm_e])
                          (get-in request [:parameters :query :cp]))
            shared_secret (-> (sql/find-by-keys
                               (:db-connection system)
                               :atlassian_tenants
                               {:base_url base-url}
                               {:columns [[:shared_secret :shared_secret]]})
                              first :atlassian_tenants/shared_secret)
            {:keys [request-method uri] {:keys [query]} :parameters} request
            incoming-qsh (-> (:jwt query) (jwt/unsign shared_secret) :qsh)
            calculated-qsh (-> (utils/atlassian-canonical-query-string
                                (string/upper-case (name request-method)) uri query)
                               utils/atlassian-query-string-hash)]
        (if (= incoming-qsh calculated-qsh)
          (do
            (mulog/log ::verify-atlassian-server-request
                       :base-url base-url
                       :local-time (java.time.LocalDateTime/now))
            (handler request))
          (do
            (mulog/log ::verify-atlassian-server-request
                       :base-url base-url
                       :success :false
                       :error "Atlassian JWT is invalid."
                       :local-time (java.time.LocalDateTime/now))
            {:status 403 :body "Invalid request"}))))))

(defn verify-atlassian-lifecycle
  "
  Middleware to verify the JWT token present in
  Atlassian lifecycle installed/uninstalled events.
  "
  [handler _]
  (fn [request]
    (let [jwt-token (-> (or (get-in request [:headers "authorization"]) "")
                        (string/replace #"JWT\s" ""))]
      (try
        (let [jwt-header (jws/decode-header jwt-token)
              public-key (keys/public-key (str "https://connect-install-keys.atlassian.com/" (:kid jwt-header)))
              _ (jwt/unsign jwt-token public-key {:alg (:alg jwt-header)})]
          (mulog/log ::verifying-atlassian-lifecycle-event
                     :success :true
                     :local-time (java.time.LocalDateTime/now))
          (handler request))

        (catch Exception e
          (mulog/log ::verifying-atlassian-lifecycle-event
                     :success :false
                     :error (.getMessage e)
                     :local-time (java.time.LocalDateTime/now))
          ;; Atlassian only fails on 500, all other statuses will be seen as "OK"
          {:status 500 :body ""})))))
