(ns jcpsantiago.thearqivist-test
  (:require [clojure.test :refer [deftest is testing]]
            [jcpsantiago.arqivist.core :as core]))


(deftest application-test
  (testing "TODO: Start with a failing test, make it pass, then refactor"

    (is (= "The Arqivist is brought to you by Bursting burrito"
           (core/greet)))

    (is (= "The Arqivist is brought to you by Practicalli Engineering"
           (core/greet {:team-name "Practicalli Engineering"})))))
