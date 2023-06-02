(ns jcpsantiago.arqivist.api.confluence.utils
  "
  Utility functions used in the Confluence handlers.
  "
  (:require
   [buddy.core.codecs :refer [bytes->hex]]
   [buddy.core.hash :refer [sha256]]
   [buddy.sign.jwt :as jwt]
   [clojure.string :as string]
   [com.brunobonacci.mulog :as mulog]
   [org.httpkit.client :as httpkit]
   [java-time :as t]
   [jsonista.core :as jsonista]))

(defn atlassian-qsh
  "
  Builds the Query String Hash needed for the JWT token.
  https://developer.atlassian.com/cloud/bitbucket/query-string-hash/
  "
  [canonical-method canonical-url params]
  (-> (str canonical-method "&" canonical-url "&" params)
      sha256
      bytes->hex))

(defn atlassian-jwt
  "
  Calculates the JWT token for requests sent to Confluence.
  "
  [descriptor-key shared-secret canonical-method canonical-url & [params]]
  (let [claims {:iss descriptor-key
                :iat (-> (t/instant)
                         t/to-millis-from-epoch
                         (quot 1000))
                :qsh (atlassian-qsh canonical-method canonical-url params)
                :exp (-> (t/instant)
                         (t/plus (t/seconds 9000))
                         t/to-millis-from-epoch
                         (quot 1000))}]
    (jwt/sign claims shared-secret {:alg :hs256 :header {:typ "JWT"}})))

(defn opts-with-jwt
  "
  Convenience function to add a JWT token to Confluence API requests.
  "
  ([jwt-token]
   (opts-with-jwt jwt-token nil))
  ([jwt-token opts]
   (merge
    {:headers
     {"Content-Type" "application/json; charset=utf-8"
      ;; TODO: review why we need this, and document it.
      ;; I used this in the previous iteration after a lot of trial and error
      "X-Atlassian-Token" "no-check"
      "Authorization" (str "JWT " jwt-token)}}
    opts)))

(defn create-space!
  "
  Calls the Confluence API and creates a space to store Slack conversations.
  All archived conversations end up in this space.

  Takes a descriptor key provided by Atlassian for The Arqivist and a lifecycle payload as described
  in https://developer.atlassian.com/cloud/confluence/connect-app-descriptor/#lifecycle
  "
  [descriptor-key lifecycle-payload]
  (let [canonical-url "/rest/api/space"
        base-url (:baseUrl lifecycle-payload)
        jwt-token (atlassian-jwt descriptor-key (:sharedSecret lifecycle-payload) "POST" canonical-url)
        res @(httpkit/post
              (str base-url canonical-url)
              (opts-with-jwt jwt-token {:body (jsonista/write-value-as-string
                                               ;; NOTE: for now we're hardcoding this, not sure if users will care about
                                               ;; using their own names for this space
                                               {:key "SLCKARQVST"
                                                :name "Archived Slack threads"
                                                :description
                                                {:plain
                                                 {:value "Space for Slack conversations archived by The Arqivist bot"
                                                  :representation "plain"}}})}))]
    (if (= (:status res) 200)
      (do
        (mulog/log ::confluence-space-created :success :true)
        {:ok true})
      (do
        (mulog/log ::confluence-space-created :success :false :error (:body res))
        {:ok false}))))

(defn tenant-name
  "
  Takes an Atlassian base url string e.g. https://softpepe.atlassian.net,
  and returns just the tenant part ('softpepe').
  "
  [base-url]
  (->> (or base-url "")
       (re-find #"https://([a-z0-9\.-]+)\.atlassian.+")
       last))

(defn base-url-short
  "
  Shortens the base url received from Atlassian which usually has the format https://example.atlassian.net/wiki/[...].
  This shortened url is used later as an index in the db.
  "
  [base-url]
  (string/replace base-url #"\/wiki" ""))

;; FIXME: should this be part of the configuration/system?
(defn content-properties-ks
  "
  List of content properties we want to save in the Confluence page's storage.
  These can be thought of as key-value storage, and act as a our database.
  "
  []
  [:slack_thread_ts
   :slack_thread_creator
   :slack_thread_n_messages
   :slack_thread_last_message_ts
   :slack_channel_id])

(defn content-properties-extraction
  "
  Takes a keyword and returns a map of content properties
  following Atlassian's format.
  "
  [k]
  {:objectName (name k)
   :alias (str "arqivist_" (name k))
   :type "string"})
