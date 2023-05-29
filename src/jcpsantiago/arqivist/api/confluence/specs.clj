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

(spec/def ::lifecycle
  (spec/keys
    :req-un [::key ::clientKey ::sharedSecret ::baseUrl ::serviceEntitlementNumber]
    :opt-un [::displayUrl ::productType ::description ::eventType]))
