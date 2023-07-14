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
 
## 0.1.0 - 2023-04-20

### Added

* [#1](https://github.com/practicalli/clojure/issues/1) Created jcpsantiago/thearqivist project with deps-new using practicalli.template/service

[Unreleased]: https://github.com/jcpsantiago/thearqivist/compare/0.1.1...HEAD
