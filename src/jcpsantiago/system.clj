(ns jcpsantiago.system
  "Sets up the system components using donut.system to enable
   a REPL-heavy lifestyle."
  (:require
   [donut.system :as donut]
   [hikari-cp.core :as hikari]
   [migratus.core :as migratus]
   [com.brunobonacci.mulog :as mulog]
   [org.httpkit.server :as http]))

(def migrations
  "Database migration component using migratus"
  #::donut{:start (fn migrate-database [{{:keys [creds]} ::donut/config}]
                    (mulog/log ::migrating-db :local-time (java.time.LocalDateTime/now))
                    ;; TODO: wrap in a try?
                    ;; should we allow the app to boot if there is no database available?
                    (migratus/migrate creds))
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
                    (let [handler (str system)]
                      (mulog/log ::starting-server
                                 :local-time (java.time.LocalDateTime/now)
                                 :port (:port options))

                   ;; TODO: add the actual server :)
                      "fooooo"))
                   ;; (http/run-server handler options)))

           :stop (fn stop-server
                   [{::donut/keys [instance]}]
                   (mulog/log ::stopping-server :local-time (java.time.LocalDateTime/now))
                   (when-not (nil? instance)
                     (instance :timeout 100)))

           ;; TODO: review this
           :config {:system {:db-connection (donut/ref [:db :db-connection])}
                    :options {:port (donut/ref [:env :port])
                              :join? false}}})

(def system
  "The whole system:
   * Persistence — migrations and db connection
   * Webserver   — http-kit"

   ;; TODO:
   ;; * Add chime as a task scheduler
   ;; * mulog component?
   ;; * cache to keep data in-between API calls during interactive use

  {::donut/defs
   {;; Environmental variables
    :env {:port (or (System/getenv "ARQIVIST_PORT") 8989)
          :datasource-options {;; NOTE: No idea what each of these actually do, should learn :D
                               :maximum-pool-size 5
                               :minimum-idle 2
                               :idle-timeout 12000
                               :max-lifetime 300000
                               :jdbc-url (or (System/getenv "JDBC_DATABASE_URL")
                                             "jdbc:postgresql://localhost/arqivist?user=arqivist&password=arqivist")}}

    ;; Persistence components
    :db {:migrations migrations
         :db-connection db-connection}

    ;; HTTP server components
    :http {:server http-server}}})
