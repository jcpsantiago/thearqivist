(ns jcpsantiago.arqivist.api.confluence.utils-test
  (:require [clojure.test :refer [are is deftest testing]]
            [jcpsantiago.arqivist.api.confluence.utils :as utils]))

(deftest atlassian-canonical-query-string-test
  ;; These tests follow the instructions in 
  ;; https://developer.atlassian.com/cloud/bitbucket/query-string-hash/#canonical-query-string

  (testing "Ignores the JWT parameter"
    (are [method uri parameters query-string]
         (= query-string (utils/atlassian-canonical-query-string method uri parameters))

      "GET" "/foobar" {:jwt "ABC.DEF.GHI"}                 "GET&/foobar&"
      "GET" "/foobar" {:jwt "ABC.DEF.GHI" :expand "names"} "GET&/foobar&expand=names"))

  (testing "URL-encode parameter keys"
    ;; NOTE: the Atlassian docs also show how to handle parameter keys with spaces (represented as + in form enconding)
    ;; but I'm not sure how such a parameter would be translated into a keyword
    (are [method uri parameters query-string]
         (= query-string (utils/atlassian-canonical-query-string method uri parameters))

      "GET" "/foobar" {:connect* "foo"} "GET&/foobar&connect%2A=foo"
      "GET" "/foobar" {:enabled "foo"}  "GET&/foobar&enabled=foo"))

  (testing "URL-encode parameter values"
    ;; NOTE: skipped some of the examples in the docs, because ring ensures they are not a problem
    (are [method uri parameters query-string]
         (= query-string (utils/atlassian-canonical-query-string method uri parameters))

      "GET" "/foobar" {:param "value"}                     "GET&/foobar&param=value"
      "GET" "/foobar" {:param "some spaces in this value"} "GET&/foobar&param=some%20spaces%20in%20this%20value"
      "GET" "/foobar" {:query "connect*"}                  "GET&/foobar&query=connect%2A"))

  (testing "Sort query parameter keys"
    (are [method uri parameters query-string]
         (= query-string (utils/atlassian-canonical-query-string method uri parameters))

      "GET" "/foobar" {:a "x" :b "y"}               "GET&/foobar&a=x&b=y"
      "GET" "/foobar" {:a10 1 :a1 2 :b1 3 :b10 4}   "GET&/foobar&a1=2&a10=1&b1=3&b10=4"
      "GET" "/foobar" {:A "A" :a "a" :b "b" :B "B"} "GET&/foobar&A=A&B=B&a=a&b=b")))

(deftest atlassian-query-string-hash-test
  ;; These tests follow the instructions in
  ;; https://developer.atlassian.com/cloud/bitbucket/query-string-hash/#qsh-claim
  (testing "Hash is calculated correctly"
    (are [query-string claim] (= claim (utils/atlassian-query-string-hash query-string))
      ;; the first example has typos in the official docs :D so it's not reliable
      ;; "GET" "/test" {:m "value"}     "be16910858a41fd19ea5c1b4e9decca9a784d1024cb00b2158defe2f29dc86dd"
      "POST&/rest/api/2/issue&" "43dd1779e33c34fae00c308d62e5dd153a32147d1bcb5d40b3936457fda0ece4")))

(deftest tenant-name-test
  (testing "Extracts tenant's name from base url"
    (are [name url]
         (= name (utils/tenant-name url))
      "fortytwo42-24"       "https://fortytwo42-24.atlassian.net"
      "bombastic"           "https://bombastic.atlassian.net"
      "loop-in-the-burrito" "https://loop-in-the-burrito.atlassian.net"
      "looping-burrito"     "https://looping-burrito.atlassian.net")))

