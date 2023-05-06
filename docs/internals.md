# Internals

```mermaid
sequenceDiagram
    autonumber
    participant U as User
    participant S as Slack
    participant A as The Arqivist
    participant C as Confluence
    U->>S: Interaction<br>(shortcut or slash)
    Note left of U: user is waiting
    S->>+A: Call matching endpoint
    A->>S: Respond 200 immediately
    Note over S,A: start checks<br>(depends on endpoint)
    A->A: Is message part of a thread?
    break message is not in a thread
        A-)U: Inform user that shortcuts only work in threads
    end
    A-)S: GET channel.info
    break channel is private
        A-)U: Ask user to invite the bot
    end
    A-)S: GET conversation.members
    opt bot is not member of channel
        A-)S: POST conversations.join
    end
    A-)C: CQL query for channel_id
    opt thread or channel is already in Confluence
        A-)U: Ask user if it's ok to overwrite existing page
    end
    Note over S,A: end checks
    A-)U: Show modal confirming action/requesting more info
    Note left of U: sees modal
    A--)S: GET conversation.history (`ch`)
    U->>S: Confirm
    Note left of U: user is waiting
    S->>A: Call view_submission endpoint
    loop For reply in `ch`
        A->>S: GET user.info
    end
    A->>C: Create page
    A->>-U: Feedback
    Note left of U: user sees feedback, end
```

## Interacting with Slack

There are two main ways to use the bot in Slack:

* [Message shortcut :octicons-link-external-16:](https://api.slack.com/interactivity/shortcuts/using#message_shortcuts)
* [Slash command :octicons-link-external-16:](https://api.slack.com/interactivity/slash-commands) 

### Message shortcut


### Slash command
