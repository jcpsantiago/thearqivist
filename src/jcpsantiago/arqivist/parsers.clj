(ns jcpsantiago.arqivist.parsers
  "
  Parsers for Slack messages useful for targets which need some preprocessing e.g. Confluence.
  "
  (:require
   [clojure.string :as string]
   [jcpsantiago.arqivist.utils :as utils]
   [jcpsantiago.arqivist.api.slack.utils :as slack-utils]))

(defn parse-datetime
  "
  Translates the Slack unix timestamp into a human-friendly datetime.
  Uses the timezone present in the job object.
  "
  [message job]
  (let [formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")

        datetime (-> (:ts message)
                     (string/replace #"\..+" "")
                     (utils/ts->datetime (:timezone job))
                     (.format formatter))]
    (assoc message :datetime datetime)))

(defn parse-html-entities
  "
  Some entities must be escaped before creating a Confluence page to avoid errors.
  Escapes <, >, & and \" in the message text.
  "
  [message]
  (update message :text
          clojure.string/escape {\< "&lt;" \> "&gt;" \& "&amp;" \" "&quot;"}))

(defn parse-users
  "
  Slack messages have user ids e.g. U035UADB70S instead of user names.
  This fn translates the ids into human-friendly names.
  It adds a new :user-name key to the message object, which can be from a bot or a
  human user (must contain the :bot? key to work properly, see j.a.a.s.u/detect-bot-user).
  Additionally, translates user ids into names in the message text.
  "
  [message _ slack-connection]
  (if (:bot? message)
    (if (:bot_profile message)
      (assoc message :user-name (get-in message [:bot_profile :name]))
      (->> (slack-utils/slack-bots-info slack-connection (:bot_id message))
           :bot :name
           (assoc message :user-name)))
    (->> (slack-utils/slack-users-info slack-connection (:user message))
         :user :real_name
         (assoc message :user-name))))

(defn parse-message
  "
  Message preprocessing pipeline.
  "
  [job slack-connection {:keys [replies] :as message}]
  (if replies
    (let [parsed-parent (->> (dissoc message :replies)
                             (parse-message job slack-connection))]
      (->> (map #(parse-message job slack-connection %) replies)
           (assoc parsed-parent :replies)))
    ;; individual messages
    (-> message
        (parse-datetime job)
        (slack-utils/detect-bot-user)
        (parse-users job slack-connection)
        ;; must be the last in the queue,
        ;; because other parsers need to see <, > and & as is
        (parse-html-entities))))
