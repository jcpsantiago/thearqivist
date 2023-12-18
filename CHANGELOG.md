# jcpsantiago/thearqivist Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

* **Added** for new features
* **Changed** for changes in existing functionality
* **Deprecated** for soon-to-be removed features
* **Resolved** resolved issue
* **Security** vulnerability related change

## [Unreleased]

* ci: update MegaLinter and Quality check actions to latest versions
* Use donut.system to create a system, enable REPL-driven development [#5](https://github.com/jcpsantiago/thearqivist/issues/5)
* Added scaffolding for routes using reitit
* dev: add `lint-fix` Makefile task to automatically apply changes from MegaLinter
* [#30](https://github.com/jcpsantiago/thearqivist/issues/30) Add atlassian lifecycle endpoints
* [#34](https://github.com/jcpsantiago/thearqivist/issues/34) Add middleware to verify Atlassian lifecycle event requests
* [#32](https://github.com/jcpsantiago/thearqivist/issues/32) Add Slack redirect endpoint
* [#35](https://github.com/jcpsantiago/thearqivist/issues/35) Add Slack request verification middleware
* Refactor PR #38, improve readability of Slack middleware
* dev: refactor user namespace removing reloading of mulog publisher and portal
* [#43](https://github.com/jcpsantiago/thearqivist/issues/43) Add Confluence Get started page
* dev: refactor slack verification middleware to keep the original body, plus parse header parameters
* [#45](https://github.com/jcpsantiago/thearqivist/issues/45) Add help keyword to slash command
* dev: mkdocs makefile targets for local documentation development
* dev: docker-db target to run postgresql database
* Part of [#47](https://github.com/jcpsantiago/thearqivist/issues/47) Slack modal for `/arqive` slash command
* Part of [#47](https://github.com/jcpsantiago/thearqivist/issues/47) Add modals for setting up and saving a channel
* [#16](https://github.com/jcpsantiago/thearqivist/issues/16) Add specs and real-life examples of all Slack payloads
* [#57](https://github.com/jcpsantiago/thearqivist/issues/57) Switch to clojure.java-time
* [#47](https://github.com/jcpsantiago/thearqivist/issues/47) Save channel `once`
* Add `app.json` and define healthchecks for deployments with Dokku/Heroku
* Correctly set the logging as pretty EDN for dev, and JSON for prod
* Uninstall app from user's Slack if they uninstall from Confluence
* [#88](https://github.com/jcpsantiago/thearqivist/issues/88) Automatically join public channels
* dev: update hack locally details for starting a REPL
* [#63](https://github.com/jcpsantiago/thearqivist/issues/63) Enable recurrent jobs: new `jobs` table, rebased migrations, new modals

## 0.1.0 - 2023-04-20

### Added

* [#1](https://github.com/practicalli/clojure/issues/1) Created jcpsantiago/thearqivist project with deps-new using practicalli.template/service
