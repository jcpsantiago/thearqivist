(ns jcpsantiago.arqivist.api.slack.specs
  "
  Specs for Slack data, including incoming requests,
  db representations and internal maps.
  "
  (:require [clojure.spec.alpha :as spec]))

(spec/def ::access_token string?)
(spec/def ::scope string?)
(spec/def ::bot_user_id string?)
(spec/def ::app_id string?)
(spec/def ::id string?)
(spec/def ::name string?)
(spec/def ::token_type #{"bot" "user"})

(spec/def ::team
  (spec/keys
   :req-un [::id]
   :opt-un [::name]))

(spec/def ::authed_user
  (spec/keys
   :req-un [::id]
   :opt-un [::scope ::access_token ::token_type]))

;; OAuth redirect
(spec/def ::code string?)
(spec/def ::state (spec/nilable string?))

(spec/def ::oauth-redirect
  (spec/keys
    :req-un [::code ::state]))

;; Access token request
(spec/def ::access-token-request
  (spec/keys
   :req-un [::access_token ::scope ::bot_user_id ::app_id ::team ::authed_user]))

;; Slash command request
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
;; &response_url=https://hooks.slack.com/commandspec/1234/5678
;; &team_domain=example
;; &team_id=T0001
;; &text=94070
;; &trigger_id=13345224609.738474920.8088930838d88f008e0
;; &user_id=U2147483697
;; &user_name=Steve
;; token=<alphanumerical string>
(spec/def ::slash-form-params
  (spec/keys
   :req-un [::api_app_id ::trigger_id ::command ::channel_id ::token
            ::channel_name ::user_id ::is_enterprise_install ::team_id
            ::user_name ::team_domain ::response_url ::text]))

(spec/def ::shortcut-body
  (spec/keys
   :req-un [::token ::callback_id ::type ::trigger_id ::response_url
            ::team ::channel ::user ::message]))

(spec/def ::view-body
  (spec/keys
   :req-un [::id ::type ::title ::submit ::blocks
            ::private_metadata ::callback_id ::state
            ::hash ::response_urls]))

(spec/def ::view-submission-body
  (spec/keys
   :req-un [::type ::team ::user ::view]))


;; Internal representations ------------------------------------------------
(spec/def ::team-attributes
  (spec/keys
   :req-un [::id ::uuid ::app_id ::external_team_id ::team_name ::registering_user
            ::scopes ::access_token ::bot_user_id ::created_at]))

;; API responses -----------------------------------------------------------
(spec/def ::ok boolean?)
(spec/def ::error string?)
(spec/def ::warning string?)
(spec/def ::api-error
  (spec/keys
   :req-un [::ok ::error]))


(spec/def ::next_cursor string?)
(spec/def ::response_metadata
  (spec/keys
   :req-un [::next_cursor]))


(spec/def ::channel
  (spec/keys
   :req-un [::id ::name ::name_normalized ::is_channel ::created ::creator
            ::is_archived ::is_general ::is_shared ::parent_conversation
            ::is_ext_shared ::is_pending_ext_shared ::is_org_shared ::is_member
            ::is_private ::is_mpim ::last_read ::topic ::purpose ::previous_names]
   :opt-un [::warning]))

(spec/def ::convo-info
  (spec/or
   ::api-error
   (spec/keys
    :req-un [::ok ::channel])))

(spec/def ::convo-members
  (spec/or
   ::api-error
   (spec/keys
    :req-un [::ok ::members ::response_metadata])))

(spec/def ::profile
  (spec/keys
   :req-un [::title ::phone ::skype ::real_name ::real_name_normalized
            ::display_name ::display_name_normalized ::status_text
            ::status_emoji ::status_expiration ::email ::first_name
            ::last_name]))

(spec/def ::user-profile
  (spec/or
   ::api-error
   (spec/keys
    :req-un [::ok ::profile])))

(spec/def ::messages
  (spec/keys
   :req-un [::type ::user ::text ::ts]
   :opt-un [::attachments ::subtype ::hidden ::is_starred
            ::pinned_to ::reactions]))

(spec/def ::convo-history
  (spec/or
   ::api-error
   (spec/keys
    :req-un [::ok ::messages ::has_more ::pin_count ::response_metadata])))

(spec/def ::convo-join
  (spec/or
   ::api-error
   (spec/keys
    :req-un [::ok ::channel])))

(spec/def ::convo-replies
  (spec/or
   ::api-error
   (spec/keys
    :req-un [::ok ::messages])))
