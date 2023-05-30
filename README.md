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
* a running instance of The Arqivist + a Postgres database

## Installing in Confluence

If you your own running instance of The Arqivist, you can install it manually in Confluence by going to "Apps" > "Manage apps" in Confluence's top navbar, then clicking "Upload app"
and pasting the URL to your `descriptor.json` file.

## Installing in Slack

WIP

## How to use The Arqivist

After installing The Arqivist in Slack, you can interact with the bot in two ways

* `/arqive` [slash command](https://api.slack.com/interactivity/slash-commands) — creates a Confluence page with all the messages of the _current channel_
* [Message shortcut](https://api.slack.com/interactivity/shortcuts/using#message_shortcuts) — creates a Confluence page with all the messages in the _current thread_


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
