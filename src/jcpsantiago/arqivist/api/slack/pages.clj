(ns jcpsantiago.arqivist.api.slack.pages
  (:require
   [hiccup.page :refer [html5 include-css]]))

(defn homepage-head
  []
  (list
   (include-css "https://unpkg.com/mvp.css")
   (include-css "https://use.typekit.net/jjm3zvf.css")
   [:head
    [:meta {:name "viewport" :content "width=device-width initial-scale=1.0"}]
    [:meta {:content "Save Slack messages in Confluence" :name "description"}]
    [:title "The Arqivist — save Slack messages in Confluence"]
    [:style
     "body.lightMode {
            --color: #118bee;
            --color-accent: #118bee15;
            --color-bg: #fff;
            --color-bg-secondary: #e9e9e9;
            --color-link: #118bee;
            --color-secondary: #920de9;
            --color-secondary-accent: #920de90b;
            --color-shadow: #f4f4f4;
            --color-table: #118bee;
            --color-text: #000;
            --color-text-secondary: #999;
        }"]]))

(defn slack-outcome
  [& message]
  (html5
   (homepage-head)
   [:header
    [:nav {:style "margin-bottom: 4em;"}
     [:a {:class "tk-rig-shaded-medium-face" :style "color: #000000;"} "The Slack Arqivist"]
     [:ul
      [:li [:a {:target "_blank" :href "https://marketplace.atlassian.com/apps/1227973/the-arqivist-slack-threads-become-confluence-pages?tab=pricing&hosting=cloud"}
            "Pricing " [:span "&#8599;"]]]
      [:li [:a {:target "_blank" :href "/terms"} "Terms"]]
      [:li [:a {:target "_blank" :href "/privacy"} "Privacy"]]]]
    [:img {:src "/img/arqivist.jpg" :height "200"}]
    message]))
