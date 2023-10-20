(ns jcpsantiago.arqivist.messages
  "
  Namespace with the core functions to fetch, parse, and format Slack messages,
  as well as deal with scheduling.
  "
  (:require
   [com.brunobonacci.mulog :as mulog]
   [clj-slack.chat :as slack-chat]
   [jcpsantiago.arqivist.parsers :as parsers]
   [jcpsantiago.arqivist.api.slack.utils :as slack-utils]
   [jcpsantiago.arqivist.api.confluence.pages :as confluence-pages]))

(defmulti archive!
  "
  Dispatch function for creating archives of Slack messages.
  The `job` hash-map contains information about the :target, which
  is used to select which method to use.
  "
  (fn [job _ _ _] (:target job)))

(defmethod archive! :confluence
  [job slack-connection confluence-credentials messages]
  (let [{:keys [action]} job
        page-rows (->> messages
                       (pmap #(parsers/parse-message job slack-connection %))
                       (sort-by :ts #(compare %2 %1))
                       (map confluence-pages/page-row))]
    ;; NOTE: the create-content! fn saves data in Confluence's page storage,
    ;; so we don't save it ourselves. It stores a date as the 'next-update'
    ;; so we don't need to keep track of scheduling.
    (case action
      "create"   (->> page-rows
                      (confluence-pages/archival-page job)
                      (confluence-pages/create-content! job confluence-credentials))
      "update" "TBD")))

(defn the-scribe
  "
  The scribe is the main function for handling archival jobs.
  It takes a `job` (see j.a.spec ns) and a slack connection,
  and pulls, prepares and creates archives in the target destination.
  "
  [job slack-connection target-credentials]
  (let [{:keys [channel-id action user-id]} job
        messages (slack-utils/fetch-conversation-history channel-id slack-connection)
        ;; TODO: create a spec for a uniform response format for archive!
        res (archive! job slack-connection target-credentials messages)]

    (if (contains? res :archive-url)
      (do
        (mulog/log ::scribe-archive
                   :success :true
                   :local-time (java.time.LocalDateTime/now))
        (when (= "create" action)
          (slack-chat/post-message
           slack-connection
           channel-id
           (str "<@" user-id "> requested the archival of this channel.\n"
                "Find it in " (:archive-url res)))))
      ;; Something didn't work, the page was not created
      (do
        (mulog/log ::scribe-archive
                   :success :false
                   :local-time (java.time.LocalDateTime/now))))))
        ;; (slack-chat/post-ephemeral
        ;;  slack-connection
        ;;  channel-id
        ;;  (str "<@" user-id "> there was a problem archiving this channel. "
        ;;       "I've alerted my supervisor, and a fix will be deployed ASAP so please try again later.\n"
        ;;       "In case the error persists, please contact supervisor@arqivist.app directly and share the"
        ;;       "error code: `" (:mulog/root-trace (mulog/local-context)) "`.")
        ;;  ;; FIXME: HACK UNTIL THE FIX IS MERGED IN UPSTREAM
        ;;  {:user user-id})))))
