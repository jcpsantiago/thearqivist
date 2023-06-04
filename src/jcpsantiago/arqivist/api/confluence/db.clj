(ns jcpsantiago.arqivist.api.confluence.db
  (:require
   [com.brunobonacci.mulog :as mulog]
   [next.jdbc.sql :as sql]))

(defn insert-new-atlassian-tenant!
  "
  Skeleton fn for handling either inserting or updating Atlassian tenant data
  received in 'installed' events.
  "
  [data-to-insert db-connection]
  (try
    (let [inserted (sql/insert! db-connection :atlassian_tenants data-to-insert {:return-keys true})]
      (mulog/log ::inserting-new-atlassian-tenant
                 :success :true
                 :tenant-id (:atlassian_tenants/id inserted)
                 :local-time (java.time.LocalDateTime/now))
      inserted)

    (catch Exception e
      (mulog/log ::inserting-new-atlassian-tenant
                 :success :false
                 :error (.getMessage e)
                 :local-time (java.time.LocalDateTime/now)))))

(defn update-existing-atlassian-tenant!
  "
  Skeleton fn for handling either inserting or updating Atlassian tenant data
  received in 'installed' events.
  "
  [data-to-insert tenant_id db-connection]
  (try
    (let [updated (sql/update! db-connection :atlassian_tenants data-to-insert {:id tenant_id} {:return-keys true})]
      (mulog/log ::updating-existing-atlassian-tenant
                 :success :true
                 :local-time (java.time.LocalDateTime/now))
      updated)

    (catch Exception e
      (mulog/log ::updating-existing-atlassian-tenant
                 :success :false
                 :error (.getMessage e)
                 :local-time (java.time.LocalDateTime/now)))))
