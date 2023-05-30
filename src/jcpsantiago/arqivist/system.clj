(ns jcpsantiago.arqivist.system
  "Sets up the system components using donut.system to enable
   a REPL-heavy lifestyle."
  (:require
   [com.brunobonacci.mulog :as mulog]
   [donut.system :as donut]
   [hikari-cp.core :as hikari]
   [org.httpkit.client :as http-client]
   [org.httpkit.server :as http-server]
   [jcpsantiago.arqivist.router :as router]
   [jsonista.core :as jsonista]
   [migratus.core :as migratus]))

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
                    [{{:keys [dev]} ::donut/config}]
                    (mulog/start-publisher! dev))

           :stop (fn mulog-publisher-stop
                   [{::donut/keys [instance]}]
                   (instance))

           :config {:dev {:type :console :pretty? true}}})

(def migrations
  "Database migration component using migratus"
  #::donut{:start (fn migrate-database
                    [{{:keys [creds]} ::donut/config}]
                    (mulog/log ::migrating-db :local-time (java.time.LocalDateTime/now))
                    (try
                      (migratus/migrate creds)
                      (catch
                       org.postgresql.util.PSQLException e
                        (mulog/log ::connecting-db-failed
                                   :error-message (ex-message e)
                                   :error-data (ex-data e)))))

           :config {:creds {:store :database
                            :migrations-dir "resources/migrations"
                            :db {:datasource (donut/ref [:db :db-connection])}}}})

(def db-connection
  "Database connection component.
   Uses HikariCP to create and manage a connection pool."
  #::donut{:start (fn create-db-connection
                    [{{:keys [options]} ::donut/config}]
                    (mulog/log ::creating-db-connection :local-time (java.time.LocalDateTime/now))
                    (hikari/make-datasource options))

           :stop (fn closing-db-connection
                   [{::donut/keys [instance]}]
                   (mulog/log ::closing-db-connection
                              :local-time (java.time.LocalDateTime/now))
                   (hikari/close-datasource instance))

           :config {:options (donut/ref [:env :datasource-options])}})

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
                             :cache (donut/ref [:cache :cache])
                             :atlassian-env (donut/ref [:env :atlassian])}
                    :options {:port (donut/ref [:env :port])
                              :join? false}}})

#_{:clj-kondo/ignore [:unused-binding]}
(def system
  "The whole system:
   * Cache        — atom to keep data between API calls
   * Event logger — mulog publishing as edn, json in prod (WIP)
   * Persistence  — migrations and db connection
   * Webserver    — http-kit"

  ;; TODO:
  ;; * Add tasks with chime as task scheduler (using channels?)

  {::donut/defs
   {;; Environmental variables
    :env {:atlassian {:vendor-name (or (System/getenv "ARQIVIST_VENDOR_NAME") "burstingburrito")
                      :vendor-url (or (System/getenv "ARQIVIST_VENDOR_URL") "https://burstingburrito.com")
                      :base-url (or (System/getenv "ARQIVIST_BASE_URL") (ngrok-tunnel-url))
                      :descriptor-key (or (System/getenv "ARQIVIST_ATLASSIAN_DESCRIPTOR_KEY") "thearqivist-dev")}

          :slack {:client-id (System/getenv "ARQIVIST_SLACK_CLIENT_ID")
                  :client-secret (System/getenv "ARQIVIST_SLACK_CLIENT_SECRET")
                  :signing-secret (System/getenv "ARQIVIST_SLACK_SIGNING_SECRET")
                  :share-url (System/getenv "ARQIVIST_SLACK_SHARE_URL")}
          
          :port (parse-long (or (System/getenv "ARQIVIST_PORT") "8989"))

          :datasource-options {;; NOTE: No idea what each of these actually do, should learn :D
                               :maximum-pool-size 5
                               :minimum-idle 2
                               :idle-timeout 12000
                               :max-lifetime 300000
                               :jdbc-url (or (System/getenv "JDBC_DATABASE_URL")
                                             "jdbc:postgresql://localhost/arqivist?user=arqivist&password=arqivist")}}

    ;; Cache component to hold data between API calls
    ;; TODO: explore core.cache instead of just an atom
    :cache {:cache #::donut{:start (atom {})}}

    ;; Event logger
    :event-log {:publisher event-logger}

    ;; Persistence components
    :db {:migrations migrations
         :db-connection db-connection}

    ;; HTTP server components
    :http {:server http-server}}})
