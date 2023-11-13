# The Arqivist

<p align="center">
 <img src="https://arqivist.app/img/arqivist.jpg" width="20%">
</p>
<p align="center">
  <i>Hard at work archiving your messages.</i>
</p>

⚠️ This repo has unstable code, DO NOT RELY ON IT.
The version available at the [Atlassian Marketplace](https://marketplace.atlassian.com/apps/1227973)
uses a private version of the code, and is stable.

## Saving Slack messages

The Arqivist is a Slack bot which you can summon to create a Confluence page with the contents of a message thread, or a channel.

## Requirements

To run The Arqivist yourself you need

* admin access to a Confluence account
* admin access to a Slack workspace
* a running instance of The Arqivist + a Postgres database (see data model at [dbdiagram.io](https://dbdiagram.io/d/6551f3787d8bbd6465102527)

## Installing in Confluence

If you your own running instance of The Arqivist, you can install it manually in Confluence by going to "Apps" > "Manage apps" in Confluence's top navbar, then clicking "Upload app"
and pasting the URL to your `descriptor.json` file.

## Installing in Slack

WIP

## How to use The Arqivist

After installing The Arqivist in Slack, you can interact with the bot in two ways

* `/arqive [keyword]` [slash command](https://api.slack.com/interactivity/slash-commands) is the main point of entry to The Arqivist. It expects no keyword, or one of these:
  * no keyword — opens setup modal to select how often to save the channel to Confluence and other remote locations (if available)
  * `help` — show help information with the available keywords, ways of interacting etc.

## Use-cases

* Turn ad-hoc questions into citeable and linkeable documentation
* Incident management
* Compliance
* Backup

## Hacking locally

* Start a Postgres instance with `docker compose up -d postgres`
* Start a REPL with `make repl`
* Start the system with `(start)` in the Clojure REPL
* `make lint` to check for format and lint issue, `make lint-fix` to automatically fix them (stage or commit other changes first) - requires node.js locally

## Contributors

[![](https://contrib.rocks/image?repo=jcpsantiago/thearqivist)](https://github.com/jcpsantiago/thearqivist/graphs/contributors)

## Contributing

If you found a bug, or want to propose new features please open an issue. PRs are also welcome!

## License

MIT
