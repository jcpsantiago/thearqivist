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
       "👀 View all channels being archived with `/arqive jobs`\n✅ Save a channel once with `/arqive once`\n📓 Continuosly save the current channel with `/arqive daily [or weekly or monthly]`\n❌ Stop saving the current channel with `/arqive stop`"}]}
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
       :text "❓ Get help at any time with `/arqive help`"}]}]})
