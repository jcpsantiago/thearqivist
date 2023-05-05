# Internals

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant S as Slack
    participant A as The Arqivist
    participant C as Confluence
    U->>S: Interaction
    S->>A: Call matching endpoint
    A->>S: Respond 200 immediately
    A-)S: GET channel.info
    A-)S: GET conversation.members
    A-)C: CQL query for channel_id
    A-)U: Show modal confirming action/requesting more info
    A--)S: GET conversation.history
    U->>S: Confirm
    S->>A: Call view_submission endpoint
    loop For every message
        A->>S: GET user.info
    end
    A->>C: Create page
    A->>U: Feedback
```

## Interacting with Slack

There are two main ways to use the bot in Slack:

* [Message shortcut :octicons-link-external-16:](https://api.slack.com/interactivity/shortcuts/using#message_shortcuts)
* [Slash command :octicons-link-external-16:](https://api.slack.com/interactivity/slash-commands) 

### Message shortcut


### Slash command
