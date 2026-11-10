# Interface design

Blowhard uses a shared dark visual system across conversation, provider settings, and run
inspection. Charcoal surfaces separate navigation from the workspace; orange identifies primary
actions and focus; warm white and muted neutral text establish reading order.

Theme colors, typography, and shapes live in `BlowhardTheme.kt`. The three offset wind bars are
an original Blowhard mark. The project remains independent of Mistral AI.

Conversation content and the composer have a bounded reading width on large displays. Narrow
windows use the existing compact navigation. The welcome content scrolls when vertical space is
limited, and message actions wrap to available width. Demo scenarios expand on request; the demo
badge and offline disclosure stay visible. Settings and run inspection remain accessible through
the same navigation and actions.

The refresh changes presentation only. Credential handling, generation, persistence, recovery,
and diagnostic export continue through the existing shared contracts.
