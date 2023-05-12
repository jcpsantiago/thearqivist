(ns jcpsantiago.system
  "Sets up the system components using donut.system to enable
   a REPL-heavy lifestyle."
  (:require
   [donut.system :as ds]
   [hikari-cp.core :as hikari]
   [migratus.core :as migratus]
   [com.brunobonacci.mulog :as mulog]
   [org.httpkit.server :as http]))

(def migrations
  #::ds{:start (fn [{{:keys [creds]} ::ds/config}]
                 (mulog/log ::migrating-db :local-time (java.time.LocalDateTime/now))
                 ;; TODO: wrap in a try?
                 ;; should we allow the app to boot if there is no database available?
                 (println "foo")
                 (migratus/migrate creds)
                 ;; TODO: why are we returning true here? Do we have to return something at all?
                 true)
        :config {:creds {:store :database
                         :migrations-dir "resources/migrations"
                         :db {:datasource (ds/ref [:db :db-connection])}}}})

(def db-connection
  #::ds{:start (fn [{{:keys [datasource-options]} ::ds/config}]
                 (mulog/log ::creating-db-datasource :local-time (java.time.LocalDateTime/now))
                 (hikari/make-datasource datasource-options))

        :stop (fn [{::ds/keys [instance]}]
                (mulog/log ::closing-db-datasource :local-time (java.time.LocalDateTime/now))
                (hikari/close-datasource instance))

        :config {:datasource-options {;; TODO: learn what these options actually do
                                      :maximum-pool-size 5
                                      :minimum-idle 2
                                      :idle-timeout 12000
                                      :max-lifetime 300000
                                      ;; TODO: should this whole config map be in :env?
                                      :jdbc-url (ds/ref [:env :jdbc-url])}}})

(def http-server
  #::ds{:start (fn [{{:keys [system options]} ::ds/config}]
                 (let [handler (str system)]
                   (mulog/log ::starting-server
                              :local-time (java.time.LocalDateTime/now)
                              :port (:port options))

                   ;; TODO: add the actual server :)
                   "fooooo"))
                   ;; (http/run-server handler options)))

        :stop (fn [{::ds/keys [instance]}]
                (mulog/log ::stopping-server :local-time (java.time.LocalDateTime/now))
                (when-not (nil? instance)
                  (instance :timeout 100)))

        ;; TODO: review this
        :config {:system {:db-connection (ds/ref [:db :db-connection])}
                 :options {:port (ds/ref [:env :port])
                           :join? false}}})

(def system
  {::ds/defs
   {;; Environmental variables
    :env {:jdbc-url (or (System/getenv "JDBC_DATABASE_URL")
                        "jdbc:postgresql://localhost/arqivist?user=arqivist&password=arqivist")
          :port (or (System/getenv "ARQIVIST_PORT") 8989)}

    ;; Persistence components
    :db {:migrations migrations
         :db-connection db-connection}

    ;; HTTP server components
    :http {:server http-server}}})
