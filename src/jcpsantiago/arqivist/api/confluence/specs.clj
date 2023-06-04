(ns jcpsantiago.arqivist.api.confluence.specs
  (:require
   [clojure.spec.alpha :as spec]))

(spec/def ::key string?)
(spec/def ::clientKey string?)
(spec/def ::sharedSecret string?)
(spec/def ::baseUrl string?)
(spec/def ::displayUrl string?)
(spec/def ::productType string?)
(spec/def ::description string?)
(spec/def ::serviceEntitlementNumber (spec/nilable string?))
(spec/def ::eventType string?)

;; NOTE: this does not follow the expected format, as usual in Atlassian-land
;; https://developer.atlassian.com/cloud/confluence/connect-app-descriptor/#lifecycle-http-request-payload
;; thus the additional specs here for the different events
(spec/def ::lifecycle
  (spec/keys
   :req-un [::key ::clientKey ::baseUrl ::eventType]
   :opt-un [::displayUrl ::productType ::description ::serviceEntitlementNumber]))

(spec/def ::installed
  (spec/merge ::lifecycle (spec/keys :req-un [::sharedSecret])))
