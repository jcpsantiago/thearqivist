# Welcome 👋

<figure>
    <img src="https://arqivist.app/img/arqivist.jpg"
         alt="The Arqivist's logo is a medieval scribe working on a document"
  width=200
      height=200>
    <figcaption>The Arqivist hard at work</figcaption>
</figure>

**The Arqivist** integrates Confluence and Slack,
letting you _create Confluence pages_ with the contents of your channels or message threads.
It does not store or log any messages, files or other PII outside Confluence,
and is thus **fully GDPR compliant**.

You can try The Arqivist for free at the [Atlassian marketplace:octicons-link-external-16:](https://marketplace.atlassian.com/apps/1227973),
or explore the source code at [jcpsantiago/thearqivist:octicons-link-external-16:](https://github.com/jcpsantiago/thearqivist).

Hop over to [Getting started](getting_started.md) for instructions on how to use the app.

## Use-cases

* **Creating ad-hoc documentation** — No docs? No problem! Ask in Slack, then create a Confluence page with the answer to build your mini "internal StackOverflow".
* **Incident management** — Create a dedicated channel for an incident e.g. via [Datadog:octicons-link-external-16:](https://www.datadoghq.com/blog/incident-response-with-datadog/), and save the whole discussion for future reference.
* **Disaster recovery** — Make Slack part of your disaster-recovery strategy, and backup the contents of critical channels.

## Roadmap

You can follow development in our [GitHub Project:octicons-link-external-16:](https://github.com/users/jcpsantiago/projects/1/views/1).

* Ability to _fully_ backup Slack all channels (i.e. all messages, all files, all emojis), 
all the time — at the moment you must select which channels you want to backup one by one.
* Backup raw Slack JSON to external cloud storage such as Google Drive, One Drive, Dropbox, etc. 

If you have a use-case missing from our roadmap,
open an issue, or reach out to us. We'd love to hear from you!

### Out of scope

Due to the permissions given to the app,
it won't be possible to backup private conversations between users i.e. outside of channels.

You can still backup these messages via Slack's native (manual) backup feature.

## Getting help

There are two ways of getting help at the moment:

* Opening an issue in [GitHub:octicons-link-external-16:](https://github.com/jcpsantiago/thearqivist/issues)
* Sending an email to [supervisor@arqivist.app :material-email-fast:](mailto:supervisor@arqivist.app)

We strive to answer all communications within 24h.

We do not regurlary visit the Atlassian Community forums at the moment.

## Motivation

A lot of critical knowledge is created and shared via Slack.
However, no one is in all channels, nor has the time to read all of them.
We were missing a way to curate important discussions,
and preserve them. Enter The Arqivist.

Conversations saved outside Slack,
could be linked and referenced in other docs and tickets.
This, in turn, would make them more visible and consequential.

Our experience has shown that Slack is great for _fast_ knowledge transfer,
but it should be complemented with a less interactive medium for long-term efficiency.

## The people behind The Arqivist

[João Santiago](https://github.com/jcpsantiago) is the main author.

[John Stevenson aka Practicalli](https://github.com/practicalli-john) is currently collaborating.

## Collaborating

The Arqivist's source code is released as open-source software 
under the [MIT license:octicons-link-external-16:](https://opensource.org/license/mit/).

If you found and fixed a bug, go ahead an open an PR (thanks!).
If you would like to collaborate more actively, reach out to us, let's talk!
