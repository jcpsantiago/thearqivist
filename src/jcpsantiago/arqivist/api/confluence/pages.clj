(ns jcpsantiago.arqivist.api.confluence.pages
  (:require
   [clojure.string :as string]
   [com.brunobonacci.mulog :as mulog]
   [hiccup2.core :refer [html]]
   [hiccup.element :refer [image link-to]]
   [hiccup.page :refer [html5 include-js include-css]]
   [jcpsantiago.arqivist.api.confluence.utils :as utils]
   [jsonista.core :as json]
   [org.httpkit.client :as http]))

(defn ts->datetime
  "Convert a UNIX timestamp into a human friendly string."
  [ts tz]
  (let [epoch-seconds (Long/parseLong ts)
        zone (java.time.ZoneId/of tz)
        formatter (java.time.format.DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm:ss")]
    (-> epoch-seconds
        java.time.Instant/ofEpochSecond
        (.atZone zone)
        (.format formatter))))

;; Archival page -----------------------------------------------------------
;; NOTE: the official Confluence markup docs are in https://confluence.atlassian.com/doc/confluence-storage-format-790796544.html
;; TODO:
;; * Also include message attachments
(defn status-macro
  "
  Official docs in https://confluence.atlassian.com/conf59/status-macro-792499207.html
  "
  [title color]
  [:ac:structured-macro {:ac:name "status"}
   [:ac:parameter {:ac:name "title"} title]
   [:ac:parameter {:ac:name "colour"} color]])

(defn single-message-block
  "
  Creates the hiccup structure for a non-threaded message.
  Will render the name of the user, the timestamp of the message,
  and the actual text.
  "
  [{:keys [user-name datetime text bot?]}]
  (list
   (if bot?
     [:h4 user-name " " (status-macro "bot" "Gray")
      [:br]
      [:code datetime]]
     [:h4 user-name
      [:br]
      [:code datetime]])
   [:p text]))

(defn thread-message-block
  "
  Creates the hiccup structure for a threaded message.
  The parent message is rendered on the left of the page, using `single-message-block`,
  but the thread replies are rendered on the right. An arrow makes this distinction clearer.
  Each reply in the thread is rendered as a normal `single-message-block`.
  "
  [message]
  [:ac:layout-section {:ac:type "two_equal"
                       :ac:breakout-mode "default"}
   [:ac:layout-cell
    (single-message-block message)]
   [:ac:layout-cell
    [:p
     [:ac:emoticon {:ac:name "blue-star"
                    :ac:emoji-shortname ":arrow_heading_down:"
                    :ac:emoji-id "2935"
                    :ac:emoji-fallback "⤵"}]
     " " ; just a spacer, an actual <br> would be too much visually
     (status-macro "replies" "Blue")]
    (map single-message-block (:replies message))]])

(defn page-row
  "
  Dispatcher fn for hiccup of different message types (single vs threaded).
  "
  [message]
  (if (contains? message :replies)
    (do
      (mulog/log ::contains-replies
                 :messages message)
      (thread-message-block message))
    (single-message-block message)))

(defn card-link
  "
  Render a link as a card in Confluence, instead of a normal link.

  Confluence's API complains about XHTML parsing problems if    
  what we send them has unespaced ampersands. It took me a while
  to figure this out -_-'                                       
  "
  [url]
  (link-to
   {:data-card-appearance "block"
    :class "external-link"
    :rel "nofollow"}
   url
   (string/replace url #"&" "&amp;")))

(defn archival-page
  "
  HTML for the archival page.
  Takes a seq of page-rows (see page-row fn) and returns an HTML string.
  "
  [job page-rows]
  (let [{:keys [user-name timezone domain channel-id]} job
        channel-url (str "https://" domain ".slack.com/archives/" channel-id)]
    (str
     (html
      (list
       ;; Warning header -----------------------------------------------------------------------------
       [:ac:structured-macro {:ac:name "note", :ac:schema-version "1"}
        [:ac:rich-text-body
         [:p " This page was created automatically by The Arqivist Slack bot. Do not edit by hand!"]]]

       ;; Start of page ------------------------------------------------------------------------------
       [:p (str "All conversation timestamps are in " timezone " timezone.")]
       [:p
        [:strong "Original Slack 💬 in"]
        (card-link channel-url)]
       ;; keeping this in case we decide to use a normal redirect instead
       ;; (card-link (str "https://slack.com/app_redirect?channel=" channel-id))]
       [:p
        (str user-name " requested the archival of this thread on ")]

       ;; Start of messages --------------------------------------------------------------------------
       [:h1  "💬 Messages"]
       [:p {:style "text-align: center;"}
        [:span {:style "color: rgb(151,160,175);"} "Latest"]]
       [:hr]
       (into [:div] page-rows)
       [:hr]
       [:p {:style "text-align: center;"}
        [:span {:style "color: rgb(151,160,175);"} "The End"]])))))

(defn create-content-body
  [{:keys [channel-name]} page-html]
  {:type "page"
   :title channel-name
   ;; FIXME: should the space key be part of the job object?
   ;; it must be in sync with whatever we set in the create-space! fn at onboarding
   ;; meaning it's a critical assumption that can go wrong if we change things
   :space {:key "SLCKARQVST"}
   :body {:storage {:value page-html :representation "storage"}}
   :metadata
   {:properties
    ;; NOTE: if the properties below are not explicitly set,
    ;; confluence creates a full-width page which looks ugly
    {:editor
     {:key "editor"
      ;; NOTE: the :version :number is how confluence keeps track of changes
      ;; the :value is for human consumption. This never changes though, thus hardcoded.
      :version {:number 1}
      :value "v2"}
     :content-appearance-draft
     {:key "content-appearance-draft"
      :version {:number 1}
      :value "default"}
     :content-appearance-published
     {:key "content-appearance-published"
      :version {:number 1}
      :value "default"}}}
   ;; FIXME: version must be different on each update.
   ;; should we use a unix timestamp to simplify?
   :version {:number 1}})

(defn create-content!
  [job credentials page-html]
  (let [{:keys [atlassian_tenants/key
                atlassian_tenants/shared_secret
                atlassian_tenants/base_url]} credentials
        canonical-url "/rest/api/content"
        jwt-token (utils/atlassian-jwt key shared_secret "POST" canonical-url)
        body (create-content-body job page-html)
        res @(http/post
              (str base_url canonical-url)
              (utils/opts-with-jwt
               jwt-token
               {:body (json/write-value-as-string body)}))]
    (if (= 200 (:status res))
      (let [page-uri (-> res :body (json/read-value json/keyword-keys-object-mapper) :_links :webui)]
        (mulog/log ::create-confluence-page
                   :success :true
                   :local-time (java.time.LocalDateTime/now))
        {:archive-url (str base_url page-uri)})
      (do
        (mulog/log ::create-confluence-page
                   :success :false
                   :error-data (-> res
                                   :body
                                   (json/read-value json/keyword-keys-object-mapper))
                   :http-status (:status res)
                   :local-time (java.time.LocalDateTime/now))
        res))))

;; Get started page --------------------------------------------------------
(defn get-started-page
  [system base-url]
  (let [tenant-name (utils/tenant-name base-url)]
    (html5
     (include-css "https://cdnjs.cloudflare.com/ajax/libs/aui/9.4.3/aui/aui-prototyping.min.css")
     (include-js "https://cdnjs.cloudflare.com/ajax/libs/aui/9.4.3/aui/aui-css-deprecations.min.js")
     [:header
      [:script {:data-options "sizeToParent:true;hideFooter:true"
                :type "text/javascript"
                :src "https://connect-cdn.atl-paas.net/all.js"}]
      [:meta {:charset "UTF-8"}]
      [:meta {:http-equiv "X-UA-Compatible" :content "IE=EDGE"}]
      [:meta {:name "viewport" :content "width=device-width initial-scale=1.0"}]]
     [:body.aui-page-focused.aui-page-size-large
      [:div#page
       [:header#header
        {:role "banner"}
        [:nav.aui-header
         {:role "navigation"}
         [:div.aui-header-primary
          [:span#logo.aui-header-logo.aui-header-logo-custom
           (image {:alt "The Arqivist"} "/img/arqivist.jpg")
           [:span.aui-header-logo-text
            {:style "margin-left: 1rem;"}
            [:strong "The Arqivist"]]]]]]
       ;; TODO page with error, in case something goes wrong i.e. tenant-name is nil
       [:div#content
        [:div.aui-page-header
         {:style "margin-top: 4rem;"}
         [:div.aui-page-header-inner
          [:div.aui-page-header-main
           [:h1 "You installed The Arqivist 🎉"]]]]
        [:div.aui-page-panel
         [:div.aui-page-panel-inner
          [:main#main.aui-page-panel-content
           {:role "main"}
           [:h2 "Let's get started"]
           [:p "👋 Hey there " tenant-name " team!"]
           [:p "Before you can start archiving your Slack threads in Confluence, we must first connect the two products. To do this, hit the button below."]
           [:br]
           [:p
            {:align "center"}
            [:a
             ;; make sure the share-url ends with &state=
             {:href (str (get-in system [:slack-env :share-url]) base-url)
              :target "_blank"}
             [:img
              {:alt "Add to Slack"
               :src "https://platform.slack-edge.com/img/add_to_slack.png"
               :srcset "https://platform.slack-edge.com/img/add_to_slack.png 1x, https://platform.slack-edge.com/img/add_to_slack@2x.png 2x"
               :width "139" :height "40"}]]]
           [:br]
           [:p
            "Once you have authorized the Slack ↔️  Confluence connection, The Arqivist will be available in your Slack app."]
           [:p
            "To use it, look for the ••• button on the top-right corner of a Slack thread or message inside a thread"]
           [:div {:align "center"}
            (image "/img/get-started/slack_more_actions_dialog.jpg")]
           [:p "and then choose "
            [:strong "archive thread"]
            " from the menu. In case it's not there, look for it under \"More message shortcuts...\""]
           [:div {:align "center"}
            (image "/img/get-started/slack_shortcut_dialog.jpg")]
           [:p "then finally give your thread a good title"]
           [:div {:align "center"}
            (image "/img/get-started/slack_good_modal.jpg")]
           [:p "If all went well, The Arqivist will share the link to the newly created Confluence page."]
           [:p "In case anything goes wrong, please open a support ticket in the "
            [:a {:href "https://softpepe.atlassian.net/servicedesk/customer/portal/1"}
             "Helpdesk"]
            " or send me an email directly at "
            [:a {:href "mailto:supervisor@arqivist.app"} "supervisor@arqivist.app"] "."]
           [:p "Happy archiving!"]]]]]
       [:footer#footer
        {:role "contentinfo"}
        [:section.footer-body
         [:ul
          [:li
           [:a {:href "https://arqivist.app", :target "_blank"}
            "The Arqivist"]
           " is made with ♥  in Berlin."]]]]]])))

