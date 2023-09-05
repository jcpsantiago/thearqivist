(ns jcpsantiago.arqivist.api.confluence.utils
  "
  Utility functions used in the Confluence handlers.
  "
  (:require
   [buddy.core.codecs :refer [bytes->hex]]
   [buddy.core.hash :refer [sha256]]
   [buddy.sign.jwt :as jwt]
   [clojure.string :as string]
   [org.httpkit.client :as httpkit]
   [java-time.api :as java-time]
   [jsonista.core :as jsonista]
   [ring.util.codec :refer [url-encode]]))

(defn atlassian-canonical-query-string
  "
  Creates the 'canonical query string' needed to calculate the query string hash.
  See the official docs in:
  https://developer.atlassian.com/cloud/bitbucket/query-string-hash/#canonical-query-string

  canonical-method: the HTTP method in all-caps e.g. GET or POST
  canonical-uri:    relative URI path for the Atlassian product or app e.g. / or /issue
  parameters:       a map of the query parameters from the request
  "
  [canonical-method canonical-uri parameters]
  ;; according to docs the JWT parameter is ignored if present
  (let [no-jwt (dissoc parameters :jwt)]
    (str
     canonical-method "&"
     canonical-uri "&"
     (when parameters
       (->> no-jwt
            ;; the query parameters must be sorted in ascending order
            (sort-by first)
            ;; both keys and values must be url (percent) encoded
            (map (fn [[k v]]
                   (str
                    (-> k name url-encode)
                    "="
                    (-> v str url-encode))))
            (interpose "&")
            (apply str))))))

(defn atlassian-query-string-hash
  "
  Calculates the Query String Hash needed for the JWT token.
  https://developer.atlassian.com/cloud/bitbucket/query-string-hash/
  
  canonical-query-string: a string like POST&/rest/api/2/issue&foo=bar
  See also atlassian-canonical-query-string fn.
  "
  [canonical-query-string]
  (-> canonical-query-string sha256 bytes->hex))

(defn atlassian-jwt
  "
  Calculates the JWT token for requests sent to Confluence.
  "
  [descriptor-key shared-secret canonical-method canonical-uri & [params]]
  (let [claims {:iss descriptor-key
                :iat (-> (java-time/instant)
                         java-time/to-millis-from-epoch
                         (quot 1000))
                :qsh (-> (atlassian-canonical-query-string canonical-method canonical-uri params)
                         atlassian-query-string-hash)
                :exp (-> (java-time/instant)
                         (java-time/plus (java-time/seconds 9000))
                         java-time/to-millis-from-epoch
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
      ;; "X-Atlassian-Token" "no-check"
      "Authorization" (str "JWT " jwt-token)}}
    opts)))

(defn get-spaces!
  "
  Calls the Confluence API and retrieves a list of existing spaces.
  See the example response payload in /test/jcpsantiago/get_confluence_spaces.json.
  "
  [descriptor-key lifecycle-payload shared_secret]
  (let [canonical-url "/rest/api/space"
        base-url (:baseUrl lifecycle-payload)
        jwt-token (atlassian-jwt descriptor-key shared_secret "GET" canonical-url)]
    (httpkit/get
     (str base-url canonical-url)
     (opts-with-jwt jwt-token))))

(defn space-exists?
  "
  Checks if a 'space-key' exists in a Confluence instance.
  Returns true/false, and throws if get-spaces! has non-200 status.
  "
  [atlassian-env lifecycle-payload shared_secret]
  (let [{:keys [descriptor-key space-key]} atlassian-env
        space-res @(get-spaces! descriptor-key lifecycle-payload shared_secret)]
    (if (= (:status space-res) 200)
      (if-let [space-results (-> space-res
                                 :body
                                 (jsonista/read-value jsonista/keyword-keys-object-mapper)
                                 :results)]

        (if (empty? (filter #(= (:key %) space-key) space-results))
          false
          true)

        ;; in the extremely unlikely event the :results are completely empty 
        ;; i.e. the instance has no spaces, we will assume that's real and our space doesn't exist yet
        false)

      ;; In case the call fails (non 200 status), for some reason. Really edge-casey here.
      (throw (Exception. "Couldn't get spaces from Confluence. Body: " (:body space-res))))))

(defn create-space!
  "
  Calls the Confluence API and creates a space to store Slack conversations.
  All archived conversations end up in this space.

  Takes a descriptor key provided by Atlassian for The Arqivist and a lifecycle payload as described
  in https://developer.atlassian.com/cloud/confluence/connect-app-descriptor/#lifecycle
  "
  [descriptor-key lifecycle-payload shared_secret space-key]
  (let [canonical-url "/rest/api/space"
        base-url (:baseUrl lifecycle-payload)
        jwt-token (atlassian-jwt descriptor-key shared_secret "POST" canonical-url)]
    (httpkit/post
     (str base-url canonical-url)
     (opts-with-jwt jwt-token {:body (jsonista/write-value-as-string
                                      ;; NOTE: for now we're hardcoding this, not sure if users will care about
                                      ;; using their own names for this space
                                      {:key space-key
                                       :name "Archived Slack threads"
                                       :description
                                       {:plain
                                        {:value "Space for Slack conversations archived by The Arqivist bot"
                                         :representation "plain"}}})}))))

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
