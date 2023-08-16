# Getting started

## Installing

1. Install The Arqivist in your Confluence account via the [Atlassian Marketplace:octicons-link-external-16:](https://marketplace.atlassian.com/apps/1227973).
2. Open the `Get started` page of The Arqivist and follow the instructions. Press the `Add to Slack` button to be redirected to your Slack account.
3. Review the permissions needed by The Arqivist ([see below](#permissions)), and accept the integration.
4. The Arqivist is now installed and ready to use :tada:

## Permissions

The Arqivist needs the following Slack scopes to work (links go to Slack's own docs):

| Scope                                                                                         | Reason                                                                         |
|-----------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| [channels:history :octicons-link-external-16:](https://api.slack.com/scopes/channels:history) | Access all content in public channels.                                         |
| [channels:join :octicons-link-external-16:](https://api.slack.com/scopes/channels:join)       | Lets the bot automatically join public channels when invoked                   |
| [channels:read :octicons-link-external-16:](https://api.slack.com/scopes/channels:read)       | Determine if a target channel is public or private                             |
| [chat:write :octicons-link-external-16:](https://api.slack.com/scopes/chat:write)             | Write message in channel announcing its archival                               |
| [commands :octicons-link-external-16:](https://api.slack.com/scopes/commands)                 | Needed for the `/arqive` slash command                                         |
| [groups:history :octicons-link-external-16:](https://api.slack.com/scopes/groups:history)     | Needed to collect content for archiving private channels                       |
| [groups:read :octicons-link-external-16:](https://api.slack.com/scopes/groups:read)           | Check which user created the private channel                                   |
| [users:read :octicons-link-external-16:](https://api.slack.com/scopes/users:read)             | Replace user ids in raw Slack messages with actual user names before archiving |

## Security and privacy

The Arqivist needs a wide set of permissions to work correctly.
To respect your data, and assure the safety of your conversations,
we do not save or log _any_ messages or PII.
On top of this, the app's code is open-source and auditable by anyone for full transparency.

*[PII]: Personally Identifiable Information

All Slack messages and files are processed without leaving a trail,
and then saved in your own Confluence instance.

For these reasons, The Arqivist is **fully GDPR compliant**.

## Archiving your first messages

WIP
