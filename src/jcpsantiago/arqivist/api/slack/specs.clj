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
(spec/def ::token_type #{"bot" "user"})
(spec/def ::text string?)

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

(spec/def ::authed_user
  (spec/keys
   :req-un [::id]
   :opt-un [::scope ::access_token ::token_type]))

;; OAuth redirect -----------------------------------------------
(spec/def ::code non-blank-string?)
(spec/def ::state non-blank-string?)

;; Initial request received from Slack once user allows the requested scopes
;; code is exchanged for a request token (see spec below)
;; official docs in https://api.slack.com/authentication/oauth-v2
(spec/def ::oauth-redirect
  (spec/keys
   :req-un [::code ::state]))

;; Access token request
(spec/def ::oauth-access
  (spec/or
   :good-response (spec/keys
                   :req-un [::access_token ::scope ::bot_user_id ::app_id ::team ::authed_user])
   :error-response ::error-response))

;; App uninstallation
(spec/def ::apps-uninstall
  (spec/or
   :ok-response (spec/keys :req-un [::ok])
   :error-response ::api-error))

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


;; Header parameters -------------------------------------------------------
;; Used to verify Slack requests
(spec/def ::x-slack-request-timestamp integer?)
(spec/def ::x-slack-signature non-blank-string?)

(spec/def ::request-header-attributes
  (spec/keys
   :req-un [::x-slack-signature ::x-slack-request-timestamp]))

;; Internal representations ------------------------------------------------
(spec/def :slack_teams/:id pos-int?)
(spec/def :slack_teams/:uuid uuid?)
(spec/def :slack_teams/:app_id string?)
(spec/def :slack_teams/:external_team_id string?)
(spec/def :slack_teams/:team_name string?)
(spec/def :slack_teams/:registering_user string?)
(spec/def :slack_teams/:scopes string?)
(spec/def :slack_teams/:access_token string?)
(spec/def :slack_teams/:bot_user_id string?)
(spec/def :slack_teams/:created_at inst?)
(spec/def :slack_teams/:atlassian_tenant_id pos-int?)

(spec/def ::team-attributes
  (spec/keys
   :req [:slack_teams/:id :slack_teams/:uuid :slack_teams/:app_id
         :slack_teams/:external_team_id :slack_teams/:team_name
         :slack_teams/:registering_user :slack_teams/:scopes :slack_teams/:access_token
         :slack_teams/:bot_user_id :slack_teams/:created_at :slack_teams/:atlassian_tenant_id]))
