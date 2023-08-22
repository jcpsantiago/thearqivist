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
   [jcpsantiago.arqivist.api.confluence.utils :as utils]
   [jcpsantiago.arqivist.api.slack.specs :as specs]
   [ring.util.response :refer [bad-request]]
   [jsonista.core :as json]))

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
          (nil? (:token slack-connection))
          (do
            (mulog/log ::add-slack-team-attributes
                       :success :false
                       :error "Missing Slack connection token"
                       :team-id team_id
                       :local-time (java.time.LocalDateTime/now))
            (bad-request ""))

          ;; NOTE: edge-case, only became relevant during development
          ;; it shouldn't be possible to have access to the app
          ;; without having installed it first
          (nil? slack-team-row)
          (do
            (mulog/log ::add-slack-team-attributes
                       :success :false
                       :error "Missing Slack credentials in the db"
                       ;; TODO: add this to the logging context?
                       :team-id team_id
                       :local-time (java.time.LocalDateTime/now))
            (bad-request ""))

          :else
          (do
            (mulog/log ::add-slack-team-attributes
                       :success :false
                       :error "Spec does not conform"
                       :explanation (spec/explain ::specs/team-attributes slack-team-row))
            (bad-request ""))))

      (catch Exception e
        (mulog/log ::add-slack-team-attributes
                   :success :false
                   :exception e
                   :error (.getMessage e)
                   :local-time (java.time.LocalDateTime/now))))))

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
                       :incoming-qsh incoming-qsh
                       :calculated-qsh calculated-qsh
                       :local-time (java.time.LocalDateTime/now))
            (handler request))
          (do
            (mulog/log ::verify-atlassian-server-request
                       :base-url base-url
                       :incoming-qsh incoming-qsh
                       :calculated-qsh calculated-qsh
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
