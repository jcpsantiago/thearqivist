(ns jcpsantiago.arqivist.api.confluence.db
  (:require
   [com.brunobonacci.mulog :as mulog]
   [next.jdbc.sql :as sql]))

(defn upsert-atlassian-tenant!
  "
  Skeleton fn for handling either inserting or updating Atlassian tenant data
  received in 'installed' events.
  "
  [sqlfn event-k data-to-insert db-connection & opts]
  (try
    (sqlfn db-connection :atlassian_tenants data-to-insert (merge {:return-keys true} opts))

    (catch Exception e
      (mulog/log event-k
                 :success :false
                 :error (.getMessage e)
                 :local-time (java.time.LocalDateTime/now)))))

(def insert-new-atlassian-tenant!
  (partial upsert-atlassian-tenant! sql/insert! ::inserting-new-atlassian-tenant))

(def update-existing-atlassian-tenant!
  (partial upsert-atlassian-tenant! sql/update! ::updating-existing-atlassian-tenant))
