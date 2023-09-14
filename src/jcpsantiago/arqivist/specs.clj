(ns jcpsantiago.arqivist.specs
  "
  Specs for the core business objects.
  "
  (:require
   [clojure.spec.alpha :as spec]))

(spec/def ::channel-id string?)
(spec/def ::channel-name string?)
(spec/def ::user-id string?)
(spec/def ::user-name string?)
(spec/def ::domain string?)
(spec/def ::created-at inst?)
(spec/def ::frequency #{"once" "daily" "weekly"})
(spec/def ::target #{:confluence})
(spec/def ::action #{"create" "update"})
(spec/def ::next-update inst?)
(spec/def ::thread-ts string?)
(spec/def ::timezone string?)

(spec/def ::job
  (spec/keys
   :req-un [::channel-id ::channel-name ::domain
            ::user-id ::user-name ::timezone
            ::created-at ::frequency ::target ::action]
   ;; FIXME: next-update shouldn't be opt, but it's not implemented yet
   :opt-un [::thread-ts ::next-update]))


