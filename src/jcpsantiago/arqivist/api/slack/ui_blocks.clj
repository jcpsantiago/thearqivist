(ns jcpsantiago.arqivist.api.slack.ui-blocks
  "
  Namespace with functions to build Slack UIs.
  See https://api.slack.com/block-kit/building for the official documentation.
  "
  (:require
   [clj-slack.views :as slack-views]
   [jsonista.core :as json]
   [java-time.api :as java-time]))

(defn help-message
  "
  Slack Block Kit representation of the response to /arqive help
  "
  []
  {:blocks
   [{:type "section",
     :text
     {:type "mrkdwn",
      :text
      "Hey there 👋 I'm The Arqivist. I'm here to help you save your Slack messages as Confluence pages and beyond.\nHere are the two main ways to do that:"}}
    {:type "section",
     :text {:type "mrkdwn", :text "*Use the `/arqive` slash command*"}}
    {:type "context",
     :elements
     [{:type "mrkdwn",
       :text
       "✅ See options for saving a channel with `/arqive`\n👀 View all channels being archived with `/arqive jobs`\n❌ Stop saving the current channel with `/arqive stop`
       "}]}
    {:type "section", :text {:type "mrkdwn", :text " "}}
    {:type "section",
     :text
     {:type "mrkdwn",
      :text
      "*Use the _Archive thread_ action.* If you want to save a message thread as a Confluence page, select `Archive thread` in a message's context menu."}}
    {:type "divider"}
    {:type "context",
     :elements
     [{:type "mrkdwn",
       :text ":sos: Get help at any time with `/arqive help`"}]}]})

(defn setup-archival-modal
  "
  Modal asking the user to setup the channel archival job.
  "
  [request]
  (let [{{{:keys [team_domain channel_name channel_id user_id user_name]} :form} :parameters} request]
    {:type "modal"
     :callback_id "new-archive-confirmation"
     :title {:type "plain_text" :text "The Arqivist" :emoji true}
     :submit {:type "plain_text" :text "Create archive" :emoji true}
     :close {:type "plain_text" :text "Cancel" :emoji true}
     :private_metadata (pr-str {:channel_name channel_name
                                :channel_id channel_id
                                :user_name user_name
                                :user_id user_id
                                :domain team_domain})
     :blocks
     [{:type "section"
       :text
       {:type "mrkdwn"
        :text (str "Looks like you want to create an archive from "
                   "*#" channel_name "*")}}
      {:type "divider"}
      {:type "input"
       :element
       {:type "radio_buttons"
        :options
        [{:text {:type "plain_text" :text "Once" :emoji true}
          :value "once"}
         {:text {:type "plain_text" :text "Daily" :emoji true}
          :value "daily"}
         {:text {:type "plain_text" :text "Weekly" :emoji true}
          :value "weekly"}]
        :action_id "radio_buttons-action"}
       :block_id "archive_frequency_selector"
       :label
       {:type "plain_text"
        :text "How often do you want to archive it?"
        :emoji true}}

      {:type "divider"}

      {:type "context"
       :elements
       [{:type "mrkdwn"
         :text "For *daily* and *weekly*: the archive is created _now_, then updated at 12am UTC at the end of the selected period e.g. 12am of the next day or 12am of Saturday."}
        {:type "mrkdwn"
         :text ":sos: `/arqive help` — if you're stuck, check the docs"}]}]}))

(defn two-column-section
  "
 Takes a key-value vector ['key' 'value'] of strings,
 and returns a Slack block-kit structure for a section with two-columns.
 "
  [key-val-vector]
  (let [columns (reduce
                 (fn [prev [k v]]
                   (reduce conj prev
                           [{:type "mrkdwn"
                             :text k}
                            {:type "mrkdwn"
                             :text v}]))
                 [] key-val-vector)]
    {:type "section"
     :fields columns}))

(defn slack-nice-datetime
  ([ts formatting]
   (slack-nice-datetime ts formatting nil))
  ([ts formatting fallback]
   (str "<!date^" ts "^" formatting
        (when fallback (str "|" fallback))
        ">")))

;; TODO: move to utils ns
(defn to-seconds-from-epoch
  [epoch]
  (quot (java-time/to-millis-from-epoch epoch) 1000))

(defn job-characteristics
  "
  Returns a map with a `two-column-section` UI section block with information about a `job`.
  "
  [job]
  (let [{:keys [:jobs/owner_slack_user_id :jobs/frequency :jobs/due_date
                :jobs/created_at :jobs/last_slack_conversation_datetime
                :jobs/timezone]} job
        due_date_tz (when due_date
                      (java-time/local-date-time
                       (java-time/instant (* 1000 due_date))
                       "UTC"))
        last_slack_conversation_tz (java-time/local-date-time
                                    (java-time/instant (* 1000 last_slack_conversation_datetime))
                                    timezone)]
    ;; NOTE: Slack does not allow more than 10 fields per block
    ;; each "row" here would be two fields so we can have a max of 5 k-v pairs
    (two-column-section
     [["*Owner*: " (str "<@" owner_slack_user_id ">")]
      ["*Created at*: " (slack-nice-datetime created_at "{date_num}" created_at)]
      ["*Frequency*:" (str "`" frequency "`")]
      ["*Next archival at*:" (if due_date_tz
                               (slack-nice-datetime due_date "{date_num}" due_date_tz)
                               "Not scheduled")]
      ["*Archived until*:" (slack-nice-datetime last_slack_conversation_datetime "{date_num} {time}" last_slack_conversation_tz)]])))

(defn exists-once-modal
  "
  Modal informing the user the current channel has already been saved once
  "
  [request existing-job]
  (let [{{{:keys [team_domain channel_name channel_id user_id user_name]} :form} :parameters} request
        {:keys [:jobs/last_slack_conversation_datetime :jobs/timezone
                :jobs/slack_channel_id :jobs/target_url]} existing-job
        last_slack_conversation_tz (java-time/local-date-time
                                    (java-time/instant (* 1000 last_slack_conversation_datetime))
                                    timezone)]

    {:type "modal"
     :callback_id "exists-once-confirmation"
     :title {:type "plain_text" :text "The Arqivist" :emoji true}
     :submit {:type "plain_text" :text "Create archive" :emoji true}
     :close {:type "plain_text" :text "Cancel" :emoji true}
     :private_metadata (pr-str {:channel_name channel_name
                                :channel_id channel_id
                                :user_name user_name
                                :user_id user_id
                                :domain team_domain
                                :existing-job existing-job})
     :blocks
     [{:type "header"
       :text {:type "plain_text"
              :text "Previous archive found"
              :emoji true}}

      {:type "section"
       :text
       {:type "mrkdwn"
        :text (str "<#" slack_channel_id "> is already archived, "
                   "you can find it <" target_url "|here>.\n")}}

      (job-characteristics existing-job)

      {:type "section"
       :text
       {:type "mrkdwn"
        :text (str
               "If you would you like to setup a *recurrent archival* instead of a one time manual job, "
               "select another frequency, otherwise please select `once` again. I'll "
               "append new messages since "
               (str "<!date^" last_slack_conversation_datetime "^{date_num} {time}|" last_slack_conversation_tz "> ")
               "to the archive.")}}

      {:type "divider"}
      {:type "input"
       :element
       {:type "radio_buttons"
        :options
        [{:text {:type "plain_text" :text "Once" :emoji true}
          :value "once"}
         {:text {:type "plain_text" :text "Daily" :emoji true}
          :value "daily"}
         {:text {:type "plain_text" :text "Weekly" :emoji true}
          :value "weekly"}]
        :action_id "radio_buttons-action"}
       :block_id "archive_frequency_selector"
       :label
       {:type "plain_text"
        :text "How often do you want to archive it?"
        :emoji true}}]}))

;; TODO: add CRUD options to the modal
(defn exists-recurrent-modal
  "
  Modal informing the user the current channel already has a recurrent job setup.
  "
  [_ existing-job]
  (let [{:keys [:jobs/slack_channel_id :jobs/target_url]} existing-job]
    {:type "modal"
     :callback_id "exists-once-confirmation"
     :title {:type "plain_text" :text "The Arqivist" :emoji true}
     :close {:type "plain_text" :text "Close" :emoji true}
     :blocks
     [{:type "header"
       :text {:type "plain_text"
              :text "Previous archive found"
              :emoji true}}
      {:type "section"
       :text
       {:type "mrkdwn"
        :text (str "<#" slack_channel_id "> is already being archived, "
                   "you can find it <" target_url "|here>.\n")}}
      (job-characteristics existing-job)]}))

(defn confirm-job-started-modal
  [request]
  (let [payload (get-in request [:parameters :form :payload])
        channel_name (-> payload :view :private_metadata read-string :channel_name)
        frequency (get-in payload [:view :state :values :archive_frequency_selector :radio_buttons-action :selected_option :value])]
    {:type "modal"
     :close {:type "plain_text" :text "Ok" :emoji true}
     :title
     {:type "plain_text" :text "The Archivist" :emoji true}
     :blocks
     [{:type "section"
       :text
       {:type "mrkdwn"
        :text
        (str "Understood!\nI'll create an archive for *#" channel_name "*"
             (if-not (= "once" frequency)
               (str ", and will updated it *" frequency "*.\n")
               ".\n")
             "You'll get a notification when it's ready :white_check_mark:")}}]}))

(defn open-job-exists-modal!
  [request existing-job]
  (let [trigger_id (get-in request [:parameters :form :trigger_id])
        open-view! (partial slack-views/open (:slack-connection request))]
    (if (= "once" (:jobs/frequency existing-job))
      (open-view! (json/write-value-as-string (exists-once-modal request existing-job)) trigger_id)
      (open-view! (json/write-value-as-string (exists-recurrent-modal request existing-job)) trigger_id))))
