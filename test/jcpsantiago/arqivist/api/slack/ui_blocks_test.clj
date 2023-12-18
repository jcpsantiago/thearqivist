(ns jcpsantiago.arqivist.api.slack.ui-blocks-test
  (:require [clojure.test :refer [are deftest testing]]
            [jcpsantiago.arqivist.api.slack.ui-blocks :as ui-blocks]))

(deftest slack-ui-blocks-test
  (testing "Build two-column layout section"
    (are [key-vals result-map]
         (= result-map (ui-blocks/two-column-section key-vals))

      [["*Foo*:" "bla"]
       ["*Bar*:" "meh"]]
      {:type "section"
       :fields
       [{:type "mrkdwn"
         :text "*Foo*:"}
        {:type "mrkdwn"
         :text "bla"}
        {:type "mrkdwn"
         :text "*Bar*:"}
        {:type "mrkdwn"
         :text "meh"}]})))
