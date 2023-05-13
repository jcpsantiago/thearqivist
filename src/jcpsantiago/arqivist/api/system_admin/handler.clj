;; --------------------------------------------------
;; System Administration and Status check
;;
;; Aldo development time checking of requests by echoing the request as a response
;; --------------------------------------------------


(ns jcpsantiago.arqivist.api.system-admin.handler
  "Administration handlers"
  (:require [ring.util.response :refer [response]]))


;; --------------------------------------------------
;; Status of Service

(def status
  "Simple status report for external monitoring services, e.g. Pingdom"
  (constantly (response {:application "The Arqivist" :status "Alive"})))
