# ADR 0007: Keep conversation presentation unidirectional and adapter-neutral

- Status: accepted
- Date: 2026-11-09

## Context

Desktop needs a complete offline conversation demonstration without binding reusable screens to a
provider, an operating system, or application implementation details. Streaming updates and Stop
must not accidentally create or duplicate generation work. Provider output is untrusted text, even
when it originates from the bundled fixture.

## Decision

- `shared-ui` depends only on `core:api`. It owns immutable `ConversationUiState`, explicit
  `ConversationAction` values, `ConversationPresenter`, and Compose rendering.
- One serialized presenter event loop processes user actions and observed snapshots. Each accepted
  start receives exactly one passive observer, and an active conversation ignores duplicate Send
  actions.
- Conversations keep independent message and run state. Selection changes presentation only; they
  neither restart nor cancel background work.
- Demo scenario selection is a small composition-root port keyed by conversation. The desktop
  implementation consumes each selection when the fixture begins, avoiding a process-wide mutable
  scenario race.
- The desktop entry point creates the fixture adapter, application lifecycle, and presenter. It
  closes presentation observers and application-owned runs before exiting.
- Generated content supports headings, paragraphs, bullet items, and fenced code. HTML, links, and
  all other constructs remain inert selectable text; the UI does not execute HTML or open links.
- Wide layouts show persistent conversation navigation. Compact layouts reuse the same state and
  actions with horizontal navigation, keeping future mobile shells free to provide different
  composition without changing the workflow contract.

## Consequences

Presenter behavior is tested without Compose or a platform window, while the desktop runtime is
tested with the real application lifecycle and fixture adapter. The presentation layer cannot
import provider DTOs, domain ports, or desktop APIs, and future live providers can replace the
fixture without changing screen actions.

Conversation history is in memory, the Markdown subset is intentionally small, and platform menus,
shortcuts, clipboard behavior, persistence, and a live provider remain separate increments.
