# Conversation presentation

The shared UI is a consumer of the public run contract. It never calls a provider adapter and never
collects generation work directly.

## Workspace interaction

The dark workspace uses orange accents for primary actions and focus. Its orbital artwork is
static, with no continuous motion. The welcome layout adapts to both width and height; starter
cards populate an editable draft without submitting a request. The composer groups text, optional
image attachment, and Send or Stop in one surface and retains the keyboard send shortcut.

The header keeps the current model discoverable and moves deletion into a secondary menu, followed
by the existing confirmation. Compact layouts place the model shortcut on its own row. History
shows active and exceptional run states without repeating successful completion labels. Settings
keep connection and model controls visible, with generation parameters behind a disclosure button.

## State ownership

```mermaid
flowchart LR
    UI[Compose screen] -->|ConversationAction| Presenter[ConversationPresenter]
    Presenter -->|start / observe / cancel| API[ConversationRuns]
    API -->|RunSnapshot Flow| Presenter
    Presenter -->|ConversationUiState| UI

    Desktop[Desktop composition root] --> Presenter
    Desktop --> History[ConversationHistoryLoader]
    History -->|Recovered initial state| Presenter
    Desktop --> Lifecycle[DefaultConversationRuns]
    Desktop --> Fixture[FixtureTextGenerationPort]
    Lifecycle --> Fixture
```

`ConversationPresenter` owns a serialized event loop. A successful Send records the user message,
creates one pending assistant message, and passively observes the accepted run. Streaming snapshots
replace that assistant message's text; terminal snapshots make its delivery state immutable.
Duplicate Send actions are ignored while the selected conversation is starting, queued, or
streaming.

The presenter accepts a validated initial state without depending on persistence types. Desktop
startup maps stored messages and attempts into that state after fail-closed recovery. Restored
identities are reserved by the presenter so newly created in-memory conversations and messages
cannot collide with displayed history. Interrupted responses retain partial text and are never
treated as active local runs.

## Explicit regeneration

Every accepted run labels its user and assistant messages with the same opaque attempt identity.
Regenerate resolves the prompt paired with the selected terminal response and asks
`ConversationRuns` to start a new run. It appends another pair with a new identity; the original
prompt, output, delivery state, and demo/live target remain unchanged.

The new attempt captures the mode and verified model selected when it is accepted. The control says
"Regenerate with current mode" to make that boundary visible. A start rejection preserves both
history and any unrelated composer draft.

Regeneration is never automatic. Successful and cancelled attempts allow it. Failed attempts allow
it only when the core reports `EXPLICIT_NEW_ATTEMPT`; delay-required and unsafe failures keep the
control unavailable.

Switching conversations never changes execution ownership. Several conversations may run within
the application's concurrency policy, while each conversation still has at most one active run.

## Offline demonstration

The desktop composition root offers four bundled scenarios:

| Scenario | Demonstrates |
| --- | --- |
| Success | Incremental Markdown-shaped output and completion |
| Failure | Partial output followed by a typed failure |
| Rate limited | A client-safe rate-limit message |
| Interrupted | Redaction of an unexpected stream interruption |

The scenario router is keyed by conversation and consumes a selection once. The fixture still runs
through `DefaultConversationRuns`; the screen has no fixture-specific execution path.

## Safe output rendering

Provider text is parsed into a deliberately small render model: headings, paragraphs, bullets, and
fenced code. Unsupported Markdown, raw HTML, and link syntax are displayed as inert text. This
keeps the demonstration selectable and readable without granting generated content navigation or
execution capabilities.
