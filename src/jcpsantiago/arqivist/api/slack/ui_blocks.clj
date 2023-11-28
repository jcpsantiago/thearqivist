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

(defn exists-once-modal
  "
  Modal informing the user the current channel has already been saved once
  "
  [existing-job request]
  (let [{{{:keys [team_domain channel_name channel_id user_id user_name]} :form} :parameters} request
        {:keys [:jobs/owner_slack_user_id :jobs/slack_channel_id :jobs/n_runs
                :jobs/created_at :jobs/target_url :jobs/last_slack_conversation_datetime]} existing-job
        created_at_ts (quot (java-time/to-millis-from-epoch created_at) 1000)
        last-slack-conversation-ts (quot (java-time/to-millis-from-epoch last_slack_conversation_datetime) 1000)]
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

      ;; TODO: extract into function, use for "jobs" command
      (two-column-section
       [["*Owner*: " (str "<@" owner_slack_user_id ">")]
        ["*Created at*: " (str "<!date^" created_at_ts "^{date_short}|" created_at ">")]
        ["*Frequency*:" "`once`"]
        ["*Archived until*:" (str "<!date^" last-slack-conversation-ts "^{date_num} {time}|" last_slack_conversation_datetime ">")]
        ["*Times executed*:" (str n_runs)]])

      {:type "section"
       :text
       {:type "mrkdwn"
        :text (str
               "If you would you like to setup a *recurrent archival* instead of a one time manual job, "
               "select another frequency, otherwise please select `once` again. I'll "
               "create a new archive with messages since "
               (str "<!date^" last-slack-conversation-ts "^{date_num} {time}|" last_slack_conversation_datetime "> ")
               "without overwriting the previous archive.")}}

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

(defn exists-recurrent-modal
  "
  Modal informing the user the current channel already has a recurrent job setup.
  "
  [request existing-job]
  (let [{{{:keys [team_domain channel_name channel_id user_id user_name]} :form} :parameters} request
        {:keys [:jobs/owner_slack_user_id :jobs/slack_channel_id
                :jobs/created_at :jobs/target_url]} existing-job]
    {:type "modal"
     :callback_id "exists-once-confirmation"
     :title {:type "plain_text" :text "Previous archive found" :emoji true}
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
        :text (str "<@" owner_slack_user_id "> already created a recurrent job " "<#" slack_channel_id ">"
                   " once in " created_at ". You can find it <here|" target_url ">\n"
                   "Would you like to setup recurrent archival instead of a one time job? "
                   "If so, select another frequency, otherwise please select `once` again.")}}
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
  [existing-job request]
  (let [trigger_id (get-in request [:parameters :form :trigger_id])
        open-view! (partial slack-views/open (:slack-connection request))]
    (if (= "once" (:jobs/frequency existing-job))
      (open-view! (json/write-value-as-string (exists-once-modal existing-job request)) trigger_id)
      (open-view! (json/write-value-as-string (exists-recurrent-modal existing-job request)) trigger_id))))
