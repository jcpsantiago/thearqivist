(ns jcpsantiago.thearqivist-test
  (:require [clojure.test :refer [deftest is testing]]
            [jcpsantiago.thearqivist :as thearqivist]))


(deftest application-test
  (testing "TODO: Start with a failing test, make it pass, then refactor"

    (is (= "thearqivist application developed by the secret engineering team"
           (thearqivist/greet)))

    (is (= "thearqivist application developed by the Practicalli Engineering team"
           (thearqivist/greet {:team-name "Practicalli Engineering"})))))
