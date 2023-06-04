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