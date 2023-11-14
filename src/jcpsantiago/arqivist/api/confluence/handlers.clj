(ns jcpsantiago.arqivist.api.confluence.handlers
  "
  Handlers for interacting with Confluence.

  * descriptor.json — serves the descriptor file needed to start app installation
  * lifecycle endpoints used for the 'installed', 'enabled' and 'uninstalled' events
  "
  (:require
   [clojure.spec.alpha :as spec]
   [com.brunobonacci.mulog :as mulog]
   [org.httpkit.client :as httpkit]
   [jcpsantiago.arqivist.api.confluence.db :as db]
   [jcpsantiago.arqivist.api.confluence.pages :as pages]
   [jcpsantiago.arqivist.api.confluence.utils :as utils]
   [jcpsantiago.arqivist.api.slack.specs :as slack-specs]
   [jsonista.core :as jsonista]
   [next.jdbc.sql :as sql]
   [ring.util.response :refer [response content-type]]))

(defn app-descriptor-json
  "
   Handler for the /confluence/descriptor.json endpoint.
   Serves the descriptor.json file used by Atlassian to install apps in cloud instances.
   ':confluenceContentProperties' key sets up storage in all Confluence pages the app creates,
   ensuring all data is stored on the customer's instance.
   See official docs in https://developer.atlassian.com/cloud/confluence/connect-app-descriptor/,
   and comments below for unofficial information on specific keys.
   "
  [system]
  (fn [_]
    (let [env (get-in system [:atlassian-env])]
      (mulog/log ::serving-descriptor-json :local-time (java.time.LocalDateTime/now))
      (response
       {:key (:descriptor-key env)
        :name "The Arqivist - Slack conversations become Confluence pages"
        :description "Create Confluence pages from Slack conversations."
        :baseUrl (:base-url env)
        :enableLicensing true
        :vendor {:name (:vendor-name env)
                 :url (:vendor-url env)}
        :authentication {:type "jwt"}
        :lifecycle {:installed "/api/v1/confluence/installed"
                    :enabled "/api/v1/confluence/enabled"
                    :uninstalled "/api/v1/confluence/uninstalled"}
        :scopes ["READ" "WRITE"]
        :modules
        {:postInstallPage
         {:url "/api/v1/confluence/get-started"
          :name {:value "Get started with The Arqivist"
                 :i18n "getstartedwiththearqivist.name"}
          :key "get-started"}
         :confluenceContentProperties
         [{:name {:value "Arqivist Metadata"}
           ;; This key must be camelcase or kebab-case, *never* snake_case
           :key "theArqivistMetadata"
           ;; this one must be snake_case... this is not documented, I just tried and failed a few times
           :keyConfigurations [{:propertyKey "the_arqivist_props"
                                :extractions (mapv utils/content-properties-extraction (utils/content-properties-ks))}]}]}}))))

(defn installed
  "
  Ring handler for the 'installed' Atlassian lifecycle event.
  The event is triggered once someone installs the app, either via the marketplace,
  or by manually uploading/pointing to the descriptor.json file e.g. private installations.

  It creates a new entry in the atlassian_tenants table with the keys needed for the app to work.
  "
  [lifecycle-payload atlassian_tenant system]
  (let [db-connection (:db-connection system)
        tenant_id (:atlassian_tenants/id atlassian_tenant)
        {:keys [key clientKey accountId sharedSecret baseUrl displayUrl productType
                description serviceEntitlementNumber oauthClientId]} lifecycle-payload
        url-short (utils/base-url-short baseUrl)
        data-to-insert {:key key
                        :client_key clientKey
                        :tenant_name (utils/tenant-name baseUrl)
                        :account_id accountId
                        :shared_secret sharedSecret
                        :base_url baseUrl
                        :base_url_short url-short
                        :display_url displayUrl
                        :product_type productType
                        :description description
                        :service_entitlement_number serviceEntitlementNumber
                        :oauth_client_id oauthClientId}]

    (if (nil? tenant_id)
      ;; tenant did not connect before, we'll insert it into the db and create a space
      (let [inserted (db/insert-new-atlassian-tenant! data-to-insert db-connection)]
        (if inserted
          (response "OK")
          {:status 500 :body "Couldn't insert credentials into db."}))

      ;; else branch:
      ;; tenant already exists in the db, we'll update our data instead
      ;; this may happen because the tenant has new credentials from Atlassian
      (let [updated (db/update-existing-atlassian-tenant! data-to-insert tenant_id db-connection)]
        (if updated
          (response "OK")
          {:status 500 :body "Couldn't update tenant's credentials."})))))

(defn enabled
  "
  Ring handler for the 'enabled' event issued after 'installed' is successful.
  It creates a space in the user's Confluence instance, used later to store all archived conversations.
  We can only create spaces, or interact with Confluence at all after Atlassian issues this event.
  "
  [lifecycle-payload atlassian_tenant system]
  (let [{:keys [db-connection atlassian-env]} system
        {:keys [descriptor-key space-key]} atlassian-env
        shared_secret (:atlassian_tenants/shared_secret atlassian_tenant)
        tenant_id (:atlassian_tenants/id atlassian_tenant)]
    (try
      (if (utils/space-exists? atlassian-env lifecycle-payload shared_secret)
        (do
          (mulog/log ::skipped-creating-arqivist-confluence-space
                     :local-time (java.time.LocalDateTime/now))
          (response "OK"))

        (let [;; We create a space for storing conversations, but never delete it even during uninstall,
              ;; this way users keep their messages no matter what
              create-space-res (utils/create-space! descriptor-key lifecycle-payload shared_secret space-key)]

          (if (= (:status create-space-res) 200)
            (do
              (mulog/log ::created-arqivist-confluence-space
                         :success :true
                         :local-time (java.time.LocalDateTime/now))
              (response "OK"))

            ;; in case we can't create a space, it's time to clean-up
            (do
              (mulog/log ::created-arqivist-confluence-space
                         :success :false
                         :confluence-http-status (:status create-space-res)
                         :error (:body create-space-res)
                         :descriptor-key descriptor-key
                         :local-time (java.time.LocalDateTime/now))

              ;; clean-up after the failure, we don't want to keep unnecessary credentials in our db
              (sql/delete! db-connection :atlassian_tenants {:id tenant_id})

              (mulog/log ::cleaning-up-inserting-new-tenant
                         :success :true
                         :local-time (java.time.LocalDateTime/now))
              {:status 500 :body "Couldn't create Confluence space."}))))

      (catch Exception e
        (mulog/log ::enabled-app
                   :success :false
                   :error e)
        {:status 500 :body "Couldn't create Confluence space."}))))

(defn uninstalled
  "
  Ring handler for the 'uninstalled' Atlassian lifecycle event:
    * deletes all rows in db related to the tenant originating the request
    * uninstalls the app from the Slack workspace connected to the tenant
  "
  [_ atlassian_tenant system]
  (let [db-connection (:db-connection system)
        tenant_id (:atlassian_tenants/id atlassian_tenant)
        ;; Slack credentials linked to the tenant, used below to uninstall
        {:keys [:slack_teams/access_token
                :slack_teams/team_name
                :slack_teams/external_team_id] :as slack_team}
        (-> (sql/find-by-keys db-connection :slack_teams {:atlassian_tenant_id tenant_id})
            first)]

    ;; drop the row corresponding to the current tenant, plus the row in 'slack_teams'
    (mulog/with-context {:slack-team-id external_team_id :slack-team-name team_name}
      (try
        (sql/delete! db-connection :atlassian_tenants {:id tenant_id})
        (mulog/log ::atlassian-tenant-dropped-from-db
                   :success :true
                   :local-time (java.time.LocalDateTime/now))

        (if (empty? slack_team)
          ;; it's possible the user never connected their Slack workspace at all,
          ;; in which case we don't need to do anything else
          (response "OK")

          ;; if there's a slack team connected, we have to uninstall the app
          (let [res (-> @(httpkit/get
                          (str "https://slack.com/api/apps.uninstall?"
                               "client_id=" (get-in system [:slack-env :client-id])
                               "&client_secret=" (get-in system [:slack-env :client-secret]))
                          {:headers {"Content-Type" "application/json; charset=utf-8"}
                           :oauth-token access_token})
                        :body
                        (jsonista/read-value jsonista/keyword-keys-object-mapper))]

            (cond
              (spec/invalid? (spec/conform ::slack-specs/apps-uninstall res))
              (do
                (mulog/log ::uninstalled-from-slack
                           :success :false
                           :error "Slack API response did not conform to spec"
                           :spec-explain (spec/explain ::slack-specs/apps-uninstall res)
                           :local-time (java.time.LocalDateTime/now))
                (-> {:status 500 :body "Couldn't uninstall from Slack, got invalid response"} (content-type "text-plain")))

              (:ok res)
              (do
                (mulog/log ::uninstalled-from-slack
                           :success :true
                           :local-time (java.time.LocalDateTime/now))
                (-> "OK" response (content-type "text/plain")))

              :else
              (do
                (mulog/log ::uninstalled-from-slack
                           :success :false
                           :error (:error res)
                           :local-time (java.time.LocalDateTime/now))
                (-> {:status 500 :body "Couldn't uninstall from Slack!"} (content-type "text-plain"))))))

        (catch Exception e
                            ;; TODO: send a Slack message to the admin user informing them this failed, and they need to manually remove the app
          (mulog/log ::uninstalling-app
                     :success :false
                     :error (.getMessage e)
                     :local-time (java.time.LocalDateTime/now)))))))

(defn lifecycle
  "
  Handler for the Atlassian lifecycle events, dispatches based on the 'eventType' key of the event payload.
  "
  [system]
  (fn [request]
    (let [db-connection (:db-connection system)
          lifecycle-payload (:body-params request)
          event-type (:eventType lifecycle-payload)
          base-url (:baseUrl lifecycle-payload)

          {:keys [:atlassian_tenants/id] :as atlassian_tenant}
          (-> (sql/find-by-keys
               db-connection
               :atlassian_tenants
               {:base_url_short (utils/base-url-short (:baseUrl lifecycle-payload))})
              first)]

      (mulog/log ::lifecycle-event :event-type event-type :base-url base-url :local-time (java.time.LocalDateTime/now))

      (mulog/with-context {:base-url (:baseUrl lifecycle-payload) :event-type event-type :tenant-id id}
        (case event-type
          "installed" (installed lifecycle-payload atlassian_tenant system)
          "enabled" (enabled lifecycle-payload atlassian_tenant system)
          "uninstalled" (uninstalled lifecycle-payload atlassian_tenant system))))))

(defn get-started
  "
  Handler to render the 'Get started' page in Confluence.
  This page is used to connect Confluence to Slack via the 'Add to Slack' button.
  `xdm_e` is a parameter from Atlassian equals to the baseUrl of the tenant, used here
  to link Confluence and Slack via the `state` query parameter.

  `xdm_e` is being deprecated, but then not really because it seems everyone needs it.
  See https://community.developer.atlassian.com/t/deprecation-of-xdm-e-usage/32768/10
  "
  [system]
  (fn [request]
    (let [base-url (str (get-in request [:parameters :query :xdm_e])
                        (get-in request [:parameters :query :cp]))
          tenant-connected? (-> (sql/find-by-keys
                                 (:db-connection system)
                                 :atlassian_tenants
                                 {:base_url base-url}
                                 {:columns [[:id :id]]})
                                first :atlassian_tenants/id)]
      (if tenant-connected?
        (-> (pages/get-started-page system base-url) response (content-type "text/html; charset=utf-8"))
        ;; edge-case where someone might get the 'get started' url, but hasn't connected Confluence yet
        (do
          (mulog/log ::confluence-get-started
                     :base-url base-url
                     :error "Tenant tried to see 'Get started' page, but was not found in the db"
                     :local-time (java.time.LocalDateTime/now))
          ;; TODO: add an actual page here with an error id
          (-> "Something went wrong..." response (content-type "text/html")))))))
