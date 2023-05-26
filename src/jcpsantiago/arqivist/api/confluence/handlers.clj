(ns jcpsantiago.arqivist.api.confluence.handlers)

;; FIXME: should be part of the configuration/system?
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

(defn app-descriptor-json
  "
   Handler for the /confluence/descriptor.json endpoint.
   Serves the descriptor.json file used by Atlassian to install apps in cloud instances.
   ':confluenceContentProperties' key sets up storage in all Confluence pages the app creates,
   ensuring all data is stored on the customer's instance.
   See official docs in https://developer.atlassian.com/cloud/confluence/connect-app-descriptor/,
   and comments below for unofficial information on specific keys.
   "
  [_]
  {:status 200
   :body
   {:key (System/getenv "ARQIVIST_ATLASSIAN_DESCRITPOR_KEY")
    :name "The Arqivist - Slack threads become Confluence pages"
    :description "Create Confluence pages from Slack conversations."
    :baseUrl (System/getenv "ARQIVIST_BASE_URL")
    :enableLicensing true
    :vendor {:name (System/getenv "ARQIVIST_VENDOR_NAME") 
             :url (System/getenv "ARQIVIST_VENDOR_URL")}
    :authentication {:type "jwt"}
    :lifecycle {:installed "/confluence/installed"
                :enabled "/confluence/enabled"
                :uninstalled "/confluence/uninstalled"}
    :scopes ["READ" "WRITE"]
    :modules
    {:postInstallPage
     {:url "/confluence/get-started"
      :name {:value "Get started with The Arqivist"
             :i18n "getstartedwiththearqivist.name"}
      :key "get-started"}
     :confluenceContentProperties
     [{:name {:value "Arqivist Metadata"}
       ;; This key must be camelcase or kebab-case, *never* snake_case
       :key "theArqivistMetadata"
       ;; this one must be snake_case... this is not documented, I just tried and failed a few times
       :keyConfigurations [{:propertyKey "the_arqivist_props"
                            :extractions (mapv content-properties-extraction (content-properties-ks))}]}]}}})

(defn installed
  "
  Ring handler for the 'installed' Atlassian lifecycle event.
  The event is triggered once someone installs the app, either via the marketplace,
  or by manually uploading/pointing to the descriptor.json file e.g. private installations.

  It creates a new entry in the atlassian_tenants table with the keys needed for the app to work.

  Takes a donut.system as input to return a function ready with a db connection which takes
  a ring request as input.
  "
  [system]
  (fn [request]
    ;; FIXME: Do something with this to validate the incoming request
    ;; https://developer.atlassian.com/cloud/jira/platform/connect-app-descriptor/#lifecycle
    (future (println "Ready to do some work!"))
    {:status 200 :body "OK"}))

(defn enabled
  [_]
  {:status 200 :body "OK"})

(defn uninstalled
  "
  Ring handler for the 'uninstalled' Atlassian lifecycle event:
   * deletes all rows in db related to the tenant originating the request
   * uninstalls the app from the Slack workspace connected to the tenant

  Takes a donut.system object as input, returns a function ready with a database connection,
  and taking a ring request as input.
  "
  [system]
  (fn [uninstalled-payload]
  {:status 200 :body "OK"}))
