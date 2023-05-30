(ns jcpsantiago.arqivist.api.confluence.handlers
  (:require
   [com.brunobonacci.mulog :as mulog]
   [org.httpkit.client :as httpkit]
   [jcpsantiago.arqivist.api.confluence.utils :as utils]
   [next.jdbc.sql :as sql]))

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
      {:status 200
       :body
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
                                :extractions (mapv utils/content-properties-extraction (utils/content-properties-ks))}]}]}}})))

(defn installed
  "
  Ring handler for the 'installed' Atlassian lifecycle event.
  The event is triggered once someone installs the app, either via the marketplace,
  or by manually uploading/pointing to the descriptor.json file e.g. private installations.

  It creates a new entry in the atlassian_tenants table with the keys needed for the app to work.

  Takes a donut.system as input to return a function ready with a db connection which takes
  a ring request as input.
  "
  [lifecycle-payload tenant_id system]
  (let [db-connection (:db-connection system)
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
    (mulog/log ::atlassian-installed :base-url-short url-short)
    (if (nil? tenant_id)
      (let [db-insert-fn! (partial sql/insert! db-connection :atlassian_tenants)]
        (mulog/log ::inserting-new-atlassian-tenant :base-url baseUrl)
        (-> data-to-insert
            (db-insert-fn! {:return-keys true})))
      (do
        (mulog/log ::updating-existing-atlassian-tenant :base-url baseUrl)
        (sql/update! db-connection :atlassian_tenants data-to-insert {:id tenant_id})
        {:status 200 :body "OK"}))))

(defn enabled
  "
  Ring handler for the 'enabled' event.
  Not used at the moment.
  "
  [lifecycle-payload _ system]
  (mulog/log ::atlassian-enabled :base-url (:baseUrl lifecycle-payload) :local-time (java.time.LocalDateTime/now))
  (let [res (utils/create-space! (get-in system [:env :atlassian :descriptor-key]) lifecycle-payload)]
    (if (= (:status res) 200)
      {:status 200 :body "OK"}
      ;; TODO: something more useful here? This endpint is not consumed by anyone though, only the Atlassian bots
      {:status 500 :body "ERROR"})))

(defn uninstalled
  "
  Ring handler for the 'uninstalled' Atlassian lifecycle event:
   * deletes all rows in db related to the tenant originating the request
   * uninstalls the app from the Slack workspace connected to the tenant

  Takes a donut.system object as input, returns a function ready with a database connection,
  and taking a ring request as input.
  "
  [lifecycle-payload tenant_id system]
  (let [db-connection (:db-connection system)
        {:keys [baseUrl]} lifecycle-payload
        ;; Slack credentials linked to the tenant, used below to uninstall
        {:keys [:slack_teams/access_token
                :slack_teams/team_name
                :slack_teams/external_team_id]}
        (-> (sql/find-by-keys db-connection :slack_teams {:atlassian_tenant_id tenant_id})
            first)]
    ;; drop the row corresponding to the current tenant, plus the row in  'slack_teams'
    ;; TODO: wrap in try/catch
    (mulog/log ::uninstalling-app
               :base-url baseUrl
               :slack-team-id external_team_id
               :slack-team-name team_name
               :local-time (java.time.LocalDateTime/now))
    (sql/delete! db-connection :atlassian_tenants {:id tenant_id})
    ;; TODO: wrap in try/catch
    @(httpkit/get
      (str "https://slack.com/api/apps.uninstall?"
           "client_id=" (get-in system [:env :slack :arqivist-slack-client-id])
           "&client_secret=" (get-in system [:env :slack :arqivist-slack-client-secret])
           {:headers {"Content-Type" "application/json; charset=utf-8"}
            :oauth-token access_token}))
    {:status 200 :body "OK"}))

(defn lifecycle
  "
  Handler for the Atlassian lifecycle events.
  "
  [system]
  (fn [request]
    (let [db-connection (:db-connection system)
          lifecycle-payload (:body-params request)
          event-type (:eventType lifecycle-payload)
          {:keys [:atlassian_tenants/tenant_id]} (-> (sql/find-by-keys
                                                      db-connection
                                                      :atlassian_tenants
                                                      {:base_url_short (utils/base-url-short (:baseUrl lifecycle-payload))}
                                                      {:columns [[:id :tenant_id]]})
                                                     first)]
      (mulog/log ::lifecycle-event :event-type event-type :base-url (:baseUrl lifecycle-payload) :local-time (java.time.LocalDateTime/now))
      (case event-type
        "installed" (installed lifecycle-payload tenant_id system)
        "enabled" (enabled lifecycle-payload tenant_id system)
        "uninstalled" (uninstalled lifecycle-payload tenant_id system)))))
