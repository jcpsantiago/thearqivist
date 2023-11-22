(ns jcpsantiago.arqivist.api.slack.specs
  "
  Specs for Slack data, including incoming requests,
  db representations and internal maps.
  "
  (:require
   [clojure.spec.alpha :as spec]
   [clojure.string :refer [blank?]]))

;; Extra predicates
(defn non-blank-string?
  "
  Predicate for non blank strings.
  "
  [x]
  (and (string? x) (not (blank? x))))

(spec/def ::access_token non-blank-string?)
(spec/def ::scope non-blank-string?)
(spec/def ::bot_user_id non-blank-string?)
(spec/def ::app_id string?)
(spec/def ::id string?)
(spec/def ::name string?)
(spec/def ::username string?)
(spec/def ::token_type #{"bot" "user"})
(spec/def ::text string?)

(spec/def ::channel
  (spec/keys
   :req-un [::id ::name]))

;; Error response
(spec/def ::ok boolean?)
(spec/def ::error string?)
(spec/def ::error-response
  (spec/keys
   :req-un [::ok ::error]))

(spec/def ::team
  (spec/keys
   :req-un [::id]
   :opt-un [::name]))

(spec/def ::user
  (spec/keys
   :req-un [::id ::team_id ::name]
   :opt-un [::username]))

(spec/def ::authed_user
  (spec/keys
   :req-un [::id]
   :opt-un [::scope ::access_token ::token_type]))

;; OAuth redirect -----------------------------------------------
(spec/def :oauth/code non-blank-string?)
(spec/def :oauth/state non-blank-string?)

;; Initial request received from Slack once user allows the requested scopes
;; code is exchanged for a request token (see spec below)
;; official docs in https://api.slack.com/authentication/oauth-v2
(spec/def ::oauth-redirect
  (spec/keys
   :req-un [:oauth/code :oauth/state]))

;; Access token request
(spec/def ::oauth-access
  (spec/or
   :good-response (spec/keys
                   :req-un [::access_token ::scope ::bot_user_id ::app_id ::team ::authed_user])
   :error-response ::error-response))

;; App uninstallation -------------------------------------------
(spec/def ::apps-uninstall
  (spec/or
   :good-response (spec/keys :req-un [::ok])
   :error-response ::error-response))

;; User conversations API endpoint ------------------------------
(spec/def ::channels
  (spec/coll-of ::channel))

(spec/def ::users-conversations
  (spec/or
   :good-response (spec/keys :req-un [::ok ::channels])
   :error-response ::error-response))

;; Conversations join API endpoint ------------------------------
(spec/def ::conversations-join
  (spec/or
   :good-response (spec/keys :req-un [::ok ::channel])
   :error-response ::error-response))

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
(spec/def ::api_app_id string?)
(spec/def ::trigger_id string?)
(spec/def ::command non-blank-string?)
(spec/def ::channel_id string?)
(spec/def ::token string?)
(spec/def ::channel_name string?)
(spec/def ::user_id non-blank-string?)
(spec/def ::user_name string?)
(spec/def ::team_id string?)
(spec/def ::team_domain string?)
(spec/def ::response_url string?)
(spec/def ::text string?)

(spec/def ::slash-form-params
  (spec/keys
   :req-un [::api_app_id ::trigger_id ::command ::channel_id ::token ::user_name
            ::channel_name ::user_id ::team_id ::team_domain ::response_url ::text]))

;; The user will see a modal pop-up before any potentially destructive actions
;; When the user submits the modal, Slack sends an "interaction payload" of type "view_submission"
;; See official docs in https://api.slack.com/reference/interaction-payloads/views
;; See example payload in test/jcpsantiago/arqivist/api/slack/view_submission_payload.json
(spec/def :interaction/type #{"modal" "view_submission" "view_closed"})
(spec/def :interaction/callback_id string?)
(spec/def :interaction/value #{"once" "daily" "weekly"})

(spec/def :interaction/selected_option
  (spec/keys
   :req-un [:interaction/value]))

(spec/def :interaction/radio_buttons-action
  (spec/keys
   :req-un [:interaction/selected_option]))

(spec/def :interaction/archive_frequency_selector
  (spec/keys
   :req-un [:interaction/radio_buttons-action]))

(spec/def :interaction/values
  (spec/keys
   :req-un [:interaction/archive_frequency_selector]))

(spec/def :interaction/state
  (spec/keys
   :req-un [:interaction/values]))

(spec/def :interaction/view
  (spec/keys
   :req-un [::id ::team_id :interaction/type :interaction/callback_id :interaction/state]))

(spec/def ::view-submission-payload
  (spec/keys
   :req-un [:interaction/type ::team ::user ::api_app_id ::token ::trigger_id :interaction/view]))

;; The "view_submission" payload is a form with one key "payload" containing a JSON string
;; the JSON string follows the `view-submission-payload` spec
(spec/def ::payload string?)
(spec/def ::interaction-payload
  (spec/keys :req-un [::payload]))

;; Header parameters -------------------------------------------------------
;; Used to verify Slack requests
(spec/def ::x-slack-request-timestamp integer?)
(spec/def ::x-slack-signature non-blank-string?)

(spec/def ::request-header-attributes
  (spec/keys
   :req-un [::x-slack-signature ::x-slack-request-timestamp]))

;; Internal representations ------------------------------------------------
;; Slack team as stored in the production database
(spec/def :slack_teams/id pos-int?)
(spec/def :slack_teams/app_id string?)
(spec/def :slack_teams/external_team_id string?)
(spec/def :slack_teams/team_name string?)
(spec/def :slack_teams/registering_user string?)
(spec/def :slack_teams/scopes string?)
(spec/def :slack_teams/access_token string?)
(spec/def :slack_teams/bot_user_id string?)
(spec/def :slack_teams/created_at inst?)
(spec/def :slack_teams/atlassian_tenant_id pos-int?)

(spec/def ::team-attributes
  (spec/keys
   :req [:slack_teams/id :slack_teams/app_id
         :slack_teams/external_team_id :slack_teams/team_name
         :slack_teams/registering_user :slack_teams/scopes :slack_teams/access_token
         :slack_teams/bot_user_id :slack_teams/created_at :slack_teams/atlassian_tenant_id]))
