(ns jcpsantiago.arqivist.api.system-admin.handler
  "Administration handlers"
  (:require [ring.util.response :refer [response]]))

(def status
  "Simple status report for external monitoring services, e.g. Pingdom"
  (constantly (response {:application "The Arqivist" :status "Alive"})))
