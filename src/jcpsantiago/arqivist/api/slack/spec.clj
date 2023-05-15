(ns jcpsantiago.arqivist.api.slack.spec
  "Specs for Slack data, including incoming requests, db
  representations and internal maps."
  (:require [clojure.spec.alpha :as s]))


;; Incoming requests -------------------------------------------------------

;; Slash commands are sent via POST requests with Content-type application/x-www-form-urlencoded.
;; See the docs in https://api.slack.com/interactivity/slash-commands#app_command_handling
;;
;; Example payload: 
;; &api_app_id=A123456
;; &channel_id=C2147483705
;; &channel_name=test
;; &command=/weather
;; &enterprise_id=E0001
;; &enterprise_name=Globular%20Construct%20Inc
;; &response_url=https://hooks.slack.com/commands/1234/5678
;; &team_domain=example
;; &team_id=T0001
;; &text=94070
;; &trigger_id=13345224609.738474920.8088930838d88f008e0
;; &user_id=U2147483697
;; &user_name=Steve
;; token=<alphanumerical string>
(s/def ::slash-form-params
  (s/keys
   :req-un [::api_app_id ::trigger_id ::command ::channel_id ::token
            ::channel_name ::user_id ::is_enterprise_install ::team_id
            ::user_name ::team_domain ::response_url ::text]))

(s/def ::shortcut-body
  (s/keys
   :req-un [::token ::callback_id ::type ::trigger_id ::response_url
            ::team ::channel ::user ::message]))

(s/def ::view-body
  (s/keys
   :req-un [::id ::type ::title ::submit ::blocks
            ::private_metadata ::callback_id ::state
            ::hash ::response_urls]))

(s/def ::view-submission-body
  (s/keys
   :req-un [::type ::team ::user ::view]))


;; Internal representations ------------------------------------------------
(s/def ::team-attributes
  (s/keys
   :req-un [::id ::uuid ::app_id ::external_team_id ::team_name ::registering_user
            ::scopes ::access_token ::bot_user_id ::created_at]))

;; API responses -----------------------------------------------------------
(s/def ::ok boolean?)
(s/def ::error string?)
(s/def ::warning string?)
(s/def ::api-error
  (s/keys
   :req-un [::ok ::error]))


(s/def ::next_cursor string?)
(s/def ::response_metadata
  (s/keys
   :req-un [::next_cursor]))


(s/def ::channel
  (s/keys
   :req-un [::id ::name ::name_normalized ::is_channel ::created ::creator
            ::is_archived ::is_general ::is_shared ::parent_conversation
            ::is_ext_shared ::is_pending_ext_shared ::is_org_shared ::is_member
            ::is_private ::is_mpim ::last_read ::topic ::purpose ::previous_names]
   :opt-un [::warning]))

(s/def ::convo-info
  (s/or
   ::api-error
   (s/keys
    :req-un [::ok ::channel])))

(s/def ::convo-members
  (s/or
   ::api-error
   (s/keys
    :req-un [::ok ::members ::response_metadata])))

(s/def ::profile
  (s/keys
   :req-un [::title ::phone ::skype ::real_name ::real_name_normalized
            ::display_name ::display_name_normalized ::status_text
            ::status_emoji ::status_expiration ::email ::first_name
            ::last_name]))

(s/def ::user-profile
  (s/or
   ::api-error
   (s/keys
    :req-un [::ok ::profile])))

(s/def ::messages
  (s/keys
   :req-un [::type ::user ::text ::ts]
   :opt-un [::attachments ::subtype ::hidden ::is_starred
            ::pinned_to ::reactions]))

(s/def ::convo-history
  (s/or
   ::api-error
   (s/keys
    :req-un [::ok ::messages ::has_more ::pin_count ::response_metadata])))

(s/def ::convo-join
  (s/or
   ::api-error
   (s/keys
    :req-un [::ok ::channel])))

(s/def ::convo-replies
  (s/or
   ::api-error
   (s/keys
    :req-un [::ok ::messages])))
