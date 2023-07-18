;; ---------------------------------------------------------
;; REPL workflow development tools
;;
;; Include development tool libraries vai aliases from practicalli/clojure-cli-config
;; Start Rich Terminal UI REPL prompt:
;; `clojure -M:repl/reloaded`
;;
;; Or call clojure jack-in from an editor to start a repl
;; including the `:dev/reloaded` alias
;; - alias included in the Emacs `.dir-locals.el` file
;; ---------------------------------------------------------

#_{:clj-kondo/ignore [:unused-namespace :unused-referred-var]}
(ns user
  "Tools for REPL Driven Development"
  (:require
   ;; REPL Workflow
   [jcpsantiago.arqivist.system :refer [system]]
   [donut.system :as donut]
   [donut.system.repl :as donut-repl]
   [clojure.tools.namespace.repl :refer [set-refresh-dirs]]
   [portal]  ; launch portal
   [portal.api :as inspect]                          ; Data inspector

   ;; Logging
   [com.brunobonacci.mulog :as mulog]                ; Event Logging
   [mulog-events]))                                   ; Global context & Tap publisher

;; ---------------------------------------------------------
;; Help

(println "---------------------------------------------------------")
(println "Welcome to The Arqivist's dev environment")
(println)
(println)
(println "                          🧙‍♂️ ")
(println)
(println)
(println "Loading custom user namespace tools...")
(println "---------------------------------------------------------")

(defn help
  []
  (println "---------------------------------------------------------")
  (println "System Management:")
  (println "(restart)                      ; restarts the system and reloads namespaces")
  (println)
  (println "Namesapece Management:")
  (println "(namespace/refresh)            ; refresh all changed namespaces")
  (println "(namespace/refresh-all)        ; refresh all namespaces")
  (println)
  (println "Hotload libraries:             ; Clojure 1.12.x")
  (println "(add-lib 'library-name)")
  (println "(add-libs '{domain/library-name {:mvn/version \"v1.2.3\")")
  (println "(sync-deps)                    ; load dependencies from deps.edn")
  (println "- deps-* lsp snippets for adding library")
  (println)
  (println "Portal Inspector:")
  (println "- portal started by default, listening to all evaluations")
  (println "(inspect/clear)                ; clear all values in portal")
  (println "(remove-tap #'inspect/submit)  ; stop sending to portal")
  (println "(inspect/close)                ; close portal")
  (println)
  (println "(help)                         ; print help text")
  (println "---------------------------------------------------------"))

(help)

;; End of Help
;; ---------------------------------------------------------

;; ---------------------------------------------------------
;; Avoid reloading `dev` code
;; - code in `dev` directory should be evaluated if changed to reload into repl
(println
 "Set REPL refresh directories to "
 (set-refresh-dirs "src" "resources" "test"))
;; ---------------------------------------------------------

;; ---------------------------------------------------------
;; Starting the system
(defmethod donut/named-system :donut.system/repl
  [_]
  (mulog/log ::starting-system :local-time (java.time.LocalDateTime/now))
  system)

(defn start
  "Start system with donut, optionally passing a named system"
  ([] (donut-repl/start))
  ([system-config] (donut-repl/start system-config)))

(defn stop
  "Stop the currently running system"
  []
  (donut-repl/stop))

(defn restart
  "Restart the system with donut repl,
  Uses clojure.tools.namespace.repl to reload namespaces
  `(clojure.tools.namespace.repl/refresh :after 'donut.system.repl/start)`"
  []
  (donut-repl/restart))

;; ---------------------------------------------------------
;; Mulog event logging
;; `mulog-publisher` namespace used to launch tap> events to tap-source (portal)
;; and set global context for all events

;; Example mulog event message
#_(mulog/log ::dev-user-ns
             :message "Example event from user namespace"
             :ns (ns-publics *ns*))
;; ---------------------------------------------------------

;; ---------------------------------------------------------
;; Hotload libraries into running REPL
;; `deps-*` LSP snippets to add dependency forms
(comment
  ;; Require for Clojure 1.11.x and earlier
  (require '[clojure.tools.deps.alpha.repl :refer [add-libs]])

  (add-libs '{domain/library-name {:mvn/version "1.0.0"}})

  ;; Clojure 1.12.x only
  ;; (add-lib 'library-name)   ; find and add library
  ;; (sync-deps)               ; load dependencies in deps.edn (if not yet loaded)
  #_()) ; End of rich comment
;; ---------------------------------------------------------

;; ---------------------------------------------------------
;; Portal Data Inspector
(comment
  ;; Open a portal inspector in browser window - light theme
  ;; (inspect/open {:portal.colors/theme :portal.colors/solarized-light})

  (inspect/clear) ; Clear all values in portal window (allows garbage collection)

  (remove-tap #'inspect/submit) ; Remove portal from `tap>` sources

  (inspect/close) ; Close the portal window

  (inspect/docs) ; View docs locally via Portal

  #_()) ; End of rich comment

;; ---------------------------------------------------------
