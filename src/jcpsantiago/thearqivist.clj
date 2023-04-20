;; ---------------------------------------------------------
;; jcpsantiago.thearqivist
;;
;; TODO: Provide a meaningful description of the project
;; ---------------------------------------------------------


(ns jcpsantiago.thearqivist
  (:gen-class)
  (:require
    [com.brunobonacci.mulog :as mulog]))


#_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
(defn greet
  "Greeting message via Clojure CLI clojure.exec"

  ;; TODO: fix greet function with hash-map argument {:team-name "secret engineering"}
  ([] (greet "secret engineering"))
  ([{:keys [team-name]}]
   (str "jcpsantiago thearqivist service developed by the " team-name " team")))


(defn -main
  "Entry point into the application via clojure.main -M"
  [& args]
  (mulog/log ::application-starup :arguments args)
  (greet {:team-name (first args)}))



;; Rich comment block with redefined vars ignored
#_{:clj-kondo/ignore [:redefined-var]}
(comment

  (-main)
  (-main "Jenny")

  #_()) ;; End of rich comment block
