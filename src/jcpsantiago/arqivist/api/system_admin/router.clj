(ns jcpsantiago.arqivist.api.system-admin.router
  "Administration routing"
  (:require [jcpsantiago.arqivist.api.system-admin.handler :as handler]))

(defn routes
  "Reitit route configuration for system-admin endpoint"
  []
  ["/system-admin"
   {:swagger {:tags ["Application Support"]}}
   ["/status"
    {:get
     {:summary "Status of The Arqivist"
      :description "Ping The Arqivist to see if is responding to a simple request and therefore alive"
      :responses {200 {:body {:application string? :status string?}}}
      :handler handler/status}}]])
