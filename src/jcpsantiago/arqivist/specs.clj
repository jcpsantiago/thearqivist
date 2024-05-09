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
(spec/def :jobs/last_slack_conversation_datetime int?)
(spec/def :jobs/last_slack_conversation_ts string?)
;; NOTE: `once` jobs won't have a due date
(spec/def :jobs/due_date (spec/nilable int?))
(spec/def :jobs/n_runs int?)
(spec/def :jobs/updated_at (spec/nilable int?))
(spec/def :jobs/created_at int?)

(spec/def ::job
  (spec/keys
   :req [:jobs/slack_team_id :jobs/slack_channel_id :jobs/owner_slack_user_id :jobs/timezone
         :jobs/frequency :jobs/target]
   :opt [:jobs/id :jobs/last_slack_conversation_ts :jobs/due_date :jobs/n_runs :jobs/updated_at :jobs/created_at
         :jobs/target_url :jobs/last_slack_conversation_datetime]))

