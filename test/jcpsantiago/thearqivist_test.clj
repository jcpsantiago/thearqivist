(ns jcpsantiago.thearqivist-test
  (:require [clojure.test :refer [deftest is testing]]
            [jcpsantiago.thearqivist :as thearqivist]))

(deftest service-test
  (testing "TODO: Start with a failing test, make it pass, then refactor"
    (is (= (thearqivist/greet)
            "Welcome to Clojure everyone"))
    (is (= (thearqivist/greet "Practicalli")
            "Welcome to Clojure Practicalli"))))


(deftest application-test
  (testing "TODO: Start with a failing test, make it pass, then refactor"

    ;; TODO: fix greet function to pass test
    (is (= (thearqivist/greet)
            "jcpsantiago service developed by the secret engineering team"))

    ;; TODO: fix test by calling greet with {:team-name "Practicalli Engineering"}
    (is (= (thearqivist/greet "Practicalli Engineering")
            "jcpsantiago service developed by the Practicalli Engineering team"))))
