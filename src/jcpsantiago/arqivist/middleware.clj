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
   [com.brunobonacci.mulog :as mulog]))

;; Slack middleware ----------------------------------------------------------
(defn wrap-keep-raw-json-string
  "Middleware that slurps the bytestream in the :body of a request,
   updating it with the unparsed, uncoerced json string.
   Needed to verify Slack requests."
  [handler id]
  (fn [request]
    (mulog/log ::keeping-raw-json-string :middleware-id id :uri (:uri request) :local-time (java.time.LocalDateTime/now))
    (handler (update request :body slurp))))

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
    (let [keywordized-headers (keywordize-keys (:headers request))]
      (if (every? #(contains? keywordized-headers %) [:x-slack-request-timestamp :x-slack-signature])
        (let [{:keys [x-slack-signature x-slack-request-timestamp]} keywordized-headers
              slack-signature (string/replace x-slack-signature #"v0=" "")
              slack-request?  (from-slack? slack-env
                                           x-slack-request-timestamp
                                           (:body request)
                                           slack-signature)]
          (if slack-request?
            (do
              (mulog/log ::verify-slack-request
                         :middleware-id id
                         :success :true
                         :local-time (java.time.LocalDateTime/now))
              (handler request))
            ;; else if request is invalid
            (do
              (mulog/log ::verify-slack-request
                         :middleware-id id
                         :success :false
                         :error "The request is not from slack"
                         :local-time (java.time.LocalDateTime/now))
              {:status 403, :body "Invalid credentials provided"})))
        ;; else if headers are missing
        (do
          (mulog/log ::verify-slack-request
                     :middleware-id id
                     :success :false
                     :error "The necessary headers are missing"
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
(defn verify-atlassian-lifecycle
  "Middleware to verify the JWT token present in
  Atlassian lifecycle installed/uninstalled events."
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
