(ns jcpsantiago.arqivist.api.slack.ui-blocks
  "
  Namespace with functions to build Slack UIs.
  See https://api.slack.com/block-kit/building for the official documentation.
  ")

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
  Modal asking the user to confirm saving a channel once.
  "
  [request]
  (let [channel_name (get-in request [:parameters :form :channel_name])]
    {:type "modal"
     :title {:type "plain_text" :text "The Arqivist" :emoji true}
     :submit {:type "plain_text" :text "Create archive" :emoji true}
     :close {:type "plain_text" :text "Cancel" :emoji true}
     :private_metadata (pr-str {:channel_name channel_name})
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
         :text "For *daily* and *weekly*: the archive is created _now_, then updated at 12am with the frequency you selected."}
        {:type "mrkdwn"
         :text ":sos: `/arqive help` — if you're stuck, check the docs"}]}]}))

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

