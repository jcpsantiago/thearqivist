(ns jcpsantiago.thearqivist-test
  (:require [clojure.test :refer [deftest is testing]]
            [jcpsantiago.arqivist.core :as core]))


(deftest application-test
  (testing "TODO: Start with a failing test, make it pass, then refactor"

    (is (= "thearqivist application developed by the secret engineering team"
           (core/greet)))

    (is (= "thearqivist application developed by the Practicalli Engineering team"
           (core/greet {:team-name "Practicalli Engineering"})))))
