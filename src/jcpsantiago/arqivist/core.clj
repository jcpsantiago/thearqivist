(ns jcpsantiago.arqivist.core
  "The start of something great!"
  (:gen-class)
  (:require
   [donut.system :as donut]
   [jcpsantiago.arqivist.system :refer [system]]
   [com.brunobonacci.mulog :as mulog]))

;; ---------------------------------------------------------
;; Start Mulog publisher - only once

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defonce mulog-publisher
  (mulog/start-publisher! {:type :console :pretty? true}))

;; ---------------------------------------------------------
;; Application

#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn greet
  "Greeting message via Clojure CLI clojure.exec"
  ([] (greet {:team-name "Bursting burrito"}))
  ([{:keys [team-name]}]
   (str "The Arqivist is brought to you by " team-name)))

(defn -main
  "The Arqivist is managed by donut system.
  The shutdown hook gracefully stops the service on receipt of a SIGTERM from the infrastructure,
  giving the application 30 seconds before forced termination."
  [& args]
  (let [team (first args)]
    (mulog/set-global-context!
     ;; TODO: get the version from a file or config, issue #23
     {:app-name "The Arqivist" :version  "{{version}}"})
    (mulog/log ::application-starup :arguments args)
    (if team
      (greet team)
      (greet)))

  (let [running-system (donut/signal system ::donut/start)]
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread. ^Runnable #(donut/signal running-system ::donut/stop)))))
