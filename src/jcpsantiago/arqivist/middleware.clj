(ns jcpsantiago.arqivist.middleware
  "Extra ring middleware"
  (:require
   [buddy.core.codecs :as codecs]
   [buddy.core.mac :as mac]
   [buddy.core.keys :as keys]
   [buddy.sign.jws :as jws]
   [buddy.sign.jwt :as jwt]
   [clojure.string :as string]
   [clojure.walk :refer [keywordize-keys]]
   [com.brunobonacci.mulog :as mulog]
   [next.jdbc.sql :as sql]
   [jcpsantiago.arqivist.api.confluence.utils :as utils]))

;; Slack middleware ----------------------------------------------------------
(defn wrap-keep-raw-json-string
  "Middleware that slurps the bytestream in the :body of a request,
   updating it with the unparsed, uncoerced json string.
   Needed to verify Slack requests."
  [handler id]
  (fn [request]
    (if (and (re-find #"slack" (:uri request))
             (seq (:body request)))
      (do
        (mulog/log ::keeping-raw-json-string
                   :middleware-id id
                   :uri (:uri request)
                   :local-time (java.time.LocalDateTime/now))
        (handler (update request :body slurp)))
      (handler request))))

(defn slack-headers-present?
  "Checks if the necessary headers to verify a Slack request are present.
   Used as a helper in error messages."
  [headers]
  (let [keywordized-headers (keywordize-keys headers)]
    (every? #(contains? keywordized-headers %) [:x-slack-request-timestamp :x-slack-signature])))

(defn from-slack?
  "Verifies if the request really came from Slack.
  https://api.slack.com/authentication/verifying-requests-from-slack"
  [slack-env timestamp payload slack-signature]
  (try
    (mac/verify (str "v0:" timestamp ":" payload)
                (codecs/hex->bytes slack-signature)
                {:key (:arqivist-slack-signing-secret slack-env) :alg :hmac+sha256})
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
    (let [headers (:headers request)
          {:keys [x-slack-signature x-slack-request-timestamp]} headers
          slack-signature (string/replace (or x-slack-signature "") #"v0=" "")
          valid-slack-request?  (from-slack? slack-env
                                             x-slack-request-timestamp
                                             (:body request)
                                             slack-signature)]
      (if valid-slack-request?
        (do
          (mulog/log ::verify-slack-request :middleware-id id :success :true
                     :local-time (java.time.LocalDateTime/now))
          (handler request))
        (do
          (mulog/log ::verify-slack-request :middleware-id id :success :false
                     :error (if (slack-headers-present? headers)
                              "The request is not from Slack"
                              "The necessary Slack headers are missing")
                     :local-time (java.time.LocalDateTime/now))
          {:status 403 :body "Invalid credentials provided"})))))

;; Logging middleware -----------------------------------------------------
;; https://github.com/BrunoBonacci/mulog/blob/master/doc/ring-tracking.md
(defn wrap-trace-events
  "Log event trace for each api event with mulog/log."
  [handler id]
  (fn [request]
    ;; Add context of each request to all trace events generated for the specific request
    (mulog/with-context
     {:uri            (get request :uri)
      :request-method (get request :request-method)}

     ;; track the request duration and outcome
     (mulog/trace :io.redefine.datawarp/http-request
                  ;; add key/value pairs for tracking event only
                  {:pairs [:content-type     (get-in request [:headers "content-type"])
                           :content-encoding (get-in request [:headers "content-encoding"])
                           :middleware       id]
                   ;; capture http status code from the response
                   :capture (fn [{:keys [status]}] {:http-status status})}

                  ;; call the request handler
                  (handler request)))))

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
