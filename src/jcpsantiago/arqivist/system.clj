(ns jcpsantiago.arqivist.system
  "Sets up the system components using donut.system to enable
   a REPL-heavy lifestyle."
  (:require
   [com.brunobonacci.mulog :as mulog]
   [donut.system :as donut]
   [org.httpkit.client :as http-client]
   [org.httpkit.server :as http-server]
   [jcpsantiago.arqivist.router :as router]
   [jsonista.core :as jsonista]
   [migratus.core :as migratus]
   [next.jdbc :as jdbc]))

;; TODO: move into system utils namespace?
(defn ngrok-tunnel-url
  "
  Helper function to grab the public url of a running ngrok tunnel.
  There's a more advanced library https://github.com/chpill/ngrok for this.
  "
  []
  (let [res @(http-client/get "http://localhost:4040/api/tunnels")]
    (-> (:body res)
        (jsonista/read-value jsonista/keyword-keys-object-mapper)
        :tunnels
        first
        :public_url)))

(def event-logger
  "mulog log publisher component"
  #::donut{:start (fn mulog-publisher-start
                    [{{:keys [service-profile]} ::donut/config}]
                    (let [publisher-config (if (= "dev" service-profile)
                                             {:type :console :pretty? true}
                                             {:type :console-json :pretty? false})]
                      (mulog/start-publisher! publisher-config)))

           :stop (fn mulog-publisher-stop
                   [{::donut/keys [instance]}]
                   (instance))

           :config {:service-profile (donut/ref [:env :service-profile])}})

(def migrations
  "Database migration component using migratus"
  #::donut{:start (fn migrate-database
                    [{{:keys [creds]} ::donut/config}]
                    (mulog/log ::migrating-db :local-time (java.time.LocalDateTime/now))
                    (try
                      (migratus/migrate creds)
                      (catch
                       Exception e
                        (mulog/log ::connecting-db-failed
                                   :error-message (ex-message e)
                                   :error-data (ex-data e)))))

           :config {:creds {:store :database
                            :migrations-dir "resources/migrations"
                            :db {:datasource (donut/ref [:db :db-connection])}}}})

(def db-connection
  "Database connection component."
  #::donut{:start (fn create-db-connection
                    [{{:keys [db-spec]} ::donut/config}]
                    (mulog/log ::creating-db-connection
                               :db-spec db-spec
                               :local-time (java.time.LocalDateTime/now))
                    (let [db-connection (jdbc/get-datasource db-spec)]
                      ;; NOTE: https://kerkour.com/sqlite-for-servers
                      (jdbc/execute! db-connection ["PRAGMA journal_mode = WAL;"])
                      (jdbc/execute! db-connection ["PRAGMA synchronous = NORMAL;"])
                      (jdbc/execute! db-connection ["PRAGMA cache_size = 1000000000;"])
                      (jdbc/execute! db-connection ["PRAGMA foreign_keys = true;"])
                      (jdbc/execute! db-connection ["PRAGMA temp_store = memory;"])
                      db-connection))

           :stop (fn closing-db-connection
                   [{::donut/keys [_instance]}]
                   (mulog/log ::closing-db-connection
                              :local-time (java.time.LocalDateTime/now)))

           :config {:db-spec {:dbtype "sqlite" :dbname "thearqivist_db"}}})

(def http-server
  "Webserver component using http-kit"
  #::donut{:start (fn start-server
                    [{{:keys [system options]} ::donut/config}]
                    (mulog/log ::starting-server
                               :local-time (java.time.LocalDateTime/now)
                               :port (:port options))
                    (http-server/run-server (router/app system) options))

           :stop (fn stop-server
                   [{::donut/keys [instance]}]
                   (mulog/log ::stopping-server :local-time (java.time.LocalDateTime/now))
                   (instance :timeout 100)
                   (mulog/log ::server-stopped :local-time (java.time.LocalDateTime/now)))

           ;; these components and config are passed on to the running instance
           :config {:system {:db-connection (donut/ref [:db :db-connection])
                             :atlassian-env (donut/ref [:env :atlassian])
                             :slack-env (donut/ref [:env :slack])}
                    :options {:port (donut/ref [:env :port])
                              :join? false}}})

#_{:clj-kondo/ignore [:unused-binding]}
(def system
  "The whole system:
   * Event logger — mulog publishing as edn, json in prod (WIP)
   * Persistence  — migrations and db connection
   * Webserver    — http-kit"

  ;; TODO:
  ;; * Add tasks with chime as task scheduler (using channels?)
  ;; * Add step to ensure critical env vars are not set/empty strings

  (let [service-profile (or (System/getenv "ARQIVIST_SERVICE_PROFILE") "dev")
        slack-redirect-uri (or (System/getenv "ARQIVIST_SLACK_REDIRECT_URI")
                               (str (ngrok-tunnel-url) "/api/v1/slack/redirect"))]
    {::donut/defs
     {;; Environmental variables
      :env {:service-profile service-profile
            :atlassian {:vendor-name (or (System/getenv "ARQIVIST_VENDOR_NAME") "burstingburrito")
                        :vendor-url (or (System/getenv "ARQIVIST_VENDOR_URL") "https://burstingburrito.com")
                        :base-url (or (System/getenv "ARQIVIST_BASE_URL") (ngrok-tunnel-url))
                        :descriptor-key (or (System/getenv "ARQIVIST_ATLASSIAN_DESCRIPTOR_KEY") "thearqivist-dev")
                        :space-key (or (System/getenv "ARQIVIST_CONFLUENCE_SPACE_KEY") "ARQIVISTSTORE")}

            :slack {:client-id (System/getenv "ARQIVIST_SLACK_CLIENT_ID")
                    :client-secret (System/getenv "ARQIVIST_SLACK_CLIENT_SECRET")
                    :signing-secret (System/getenv "ARQIVIST_SLACK_SIGNING_SECRET")
                    :redirect-uri slack-redirect-uri
                    :share-url (str (System/getenv "ARQIVIST_SLACK_SHARE_URL")
                                    "&redirect_uri=" slack-redirect-uri
                                    "&state=")}

            :port (parse-long (or (System/getenv "PORT")
                                  (System/getenv "ARQIVIST_PORT")
                                  "8989"))}

      ;; Event logger
      :event-log {:publisher event-logger}

      ;; Persistence components
      :db {:migrations migrations
           :db-connection db-connection}

      ;; HTTP server components
      :http {:server http-server}}}))
