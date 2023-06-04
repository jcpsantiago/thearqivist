(ns jcpsantiago.arqivist.middleware
  "Extra ring middleware"
  (:require
   [buddy.core.keys :as keys]
   [buddy.sign.jws :as jws]
   [buddy.sign.jwt :as jwt]
   [clojure.string :as string]
   [com.brunobonacci.mulog :as mulog]))

;; Slack middleware
;; TODO: Add middleware for verifying if the request is from Slack

;; Logging middleware
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
