(ns jcpsantiago.arqivist.api.confluence.pages
  (:require
   [hiccup.element :refer [image]]
   [hiccup.page :refer [html5 include-js include-css]]
   [jcpsantiago.arqivist.api.confluence.utils :as confluence-utils]
   [com.brunobonacci.mulog :as mulog]
   [next.jdbc.sql :as sql]
   [ring.util.response :refer [response content-type]]))

(defn get-started-page
  [system base-url]
  (let [tenant-name (confluence-utils/tenant-name base-url)]
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

(defn get-started-handler
  "
  Handler to render the 'Get started' page in Confluence.
  This page is used to connect Confluence to Slack via the 'Add to Slack' button.
  `xdm_e` is a parameter from Atlassian equals to the baseUrl of the tenant, used here
  to link Confluence and Slack via the `state` query parameter.

  `xdm_e` is being deprecated, but then not really because it seems everyone needs it.
  See https://community.developer.atlassian.com/t/deprecation-of-xdm-e-usage/32768/10
  "
  [system]
  (fn [request]
    (let [base-url (str (get-in request [:parameters :query :xdm_e])
                        (get-in request [:parameters :query :cp]))
          tenant-connected? (-> (sql/find-by-keys
                                 (:db-connection system)
                                 :atlassian_tenants
                                 {:base_url base-url}
                                 {:columns [[:id :id]]})
                                first :atlassian_tenants/id)]
      (if tenant-connected?
        (-> (get-started-page system base-url) response (content-type "text/html; charset=utf-8"))
        ;; edge-case where someone might get the 'get started' url, but hasn't connected Confluence yet
        (do
          (mulog/log ::confluence-get-started
                     :base-url base-url
                     :error "Tenant tried to see 'Get started' page, but was not found in the db"
                     :local-time (java.time.LocalDateTime/now))
          ;; TODO: add an actual page here with an error id
          (-> "Something went wrong..." response (content-type "text/html")))))))
