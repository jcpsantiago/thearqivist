(ns jcpsantiago.arqivist.specs
  "
  Specs for the core business objects.
  "
  (:require
   [clojure.spec.alpha :as spec]))

(spec/def ::id int?)
(spec/def ::slack_team_id int?)
(spec/def ::slack_channel_id string?)
(spec/def ::owner_slack_user_id string?)
(spec/def ::timezone string?)
(spec/def ::frequency #{"once" "daily" "weekly"})
(spec/def ::target #{"confluence"})
(spec/def ::target_url string?)
(spec/def ::last_slack_conversation_datetime inst?)
(spec/def ::last_slack_conversation_ts string?)
(spec/def ::due_date inst?)
(spec/def ::n_runs int?)
(spec/def ::updated_at inst?)
(spec/def ::created_at inst?)

(spec/def ::job
  (spec/keys
   :req-un [::slack_team_id ::slack_channel_id ::owner_slack_user_id ::timezone
            ::frequency ::target]
   :opt-un [::id ::last_slack_conversation_ts ::due_date ::n_runs ::updated_at ::created_at
            ::target_url ::last_slack_conversation_datetime]))

