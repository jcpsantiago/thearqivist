(ns jcpsantiago.arqivist.api.confluence.handlers
  (:require
   [com.brunobonacci.mulog :as mulog]
   [donut.system :as donut]
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
                            :extractions (mapv utils/content-properties-extraction (utils/content-properties-ks))}]}]}}})

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
    (let [db-connection (:db-connection system)
          lifecycle-payload (:body-params request)
          {:keys [key clientKey accountId sharedSecret baseUrl displayUrl productType
                  description serviceEntitlementNumber oauthClientId]} lifecycle-payload
          url-short (utils/base-url-short baseUrl)
          {:keys [:atlassian_tenants/tenant_id]} (-> (sql/find-by-keys
                                                      db-connection
                                                      :atlassian_tenants
                                                      {:base_url_short url-short}
                                                      {:columns [[:id :tenant_id]]})
                                                     first)
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
          (sql/update! db-connection :atlassian_tenants data-to-insert {:id tenant_id})))
      {:status 200 :body "OK"})))

(defn enabled
  "
  Ring handler for the 'enabled' event.
  Not used at the moment.
  "
  [system]
  (let [descriptor-key (get-in system [:env :atlassian :descriptor-key])]
    (fn [request]
      {:status 200 :body "OK"})))

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
