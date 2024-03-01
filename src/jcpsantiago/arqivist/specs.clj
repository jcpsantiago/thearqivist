(ns jcpsantiago.arqivist.specs
  "
  Specs for the core business objects.
  "
  (:require
   [clojure.spec.alpha :as spec]))

(spec/def :jobs/id int?)
(spec/def :jobs/slack_team_id int?)
(spec/def :jobs/slack_channel_id string?)
(spec/def :jobs/owner_slack_user_id string?)
(spec/def :jobs/timezone string?)
(spec/def :jobs/frequency #{"once" "daily" "weekly"})
(spec/def :jobs/target #{"confluence"})
(spec/def :jobs/target_url string?)
(spec/def :jobs/latest_slack_conversation_datetime (spec/nilable inst?))
(spec/def :jobs/latest_slack_conversation_ts (spec/nilable string?))
(spec/def :jobs/latest_slack_conversation_unixts (spec/nilable int?))
;; NOTE: `once` jobs won't have a due date
(spec/def :jobs/due_date (spec/nilable inst?))
(spec/def :jobs/n_runs int?)
(spec/def :jobs/updated_at inst?)
(spec/def :jobs/created_at inst?)

(spec/def ::job
  (spec/keys
   :req [:jobs/slack_team_id :jobs/slack_channel_id :jobs/owner_slack_user_id :jobs/timezone
         :jobs/frequency :jobs/target]
   :opt [:jobs/id :jobs/latest_slack_conversation_unixts
         :jobs/latest_slack_conversation_ts :jobs/due_date :jobs/n_runs :jobs/updated_at :jobs/created_at
         :jobs/target_url :jobs/latest_slack_conversation_datetime]))

