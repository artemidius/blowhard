# ADR 0034: Own portable application services outside platform UI

## Status

Accepted.

## Context

The desktop host previously assembled conversation runs and every shared presenter inside a JVM-only runtime. Android
and iOS could compile the same contracts and UI, but reusing the actual application service graph would require copying
that assembly and its shutdown order into each host. Tying those resources to a Compose function would also make
recomposition an unsafe lifecycle boundary.

## Decision

Introduce a small Kotlin Multiplatform `app-composition` module. `ApplicationRuntime` owns application run execution and
shared presenters for one process session. Hosts supply connection settings, provider generation, stores, native picker,
clipboard, export, and approval capabilities through explicit dependencies. They also supply the clock, run identity,
initial presentation state, and coroutine context through explicit configuration.

The runtime is created outside UI recomposition. Its suspending, idempotent shutdown closes screen presentation resources
before application run execution. Platform stores and provider clients remain host-owned and close after the portable
runtime. No provider or fixture is selected implicitly by the composition module.

Desktop delegates portable assembly to this runtime while retaining ownership of JVM resources and synchronous
process shutdown. Android retains the runtime in an activity-independent `ViewModel`; iOS retains it for the exported
Compose controller lifetime. Until each host installs native capabilities, both mobile graphs explicitly inject
fail-closed implementations rather than a demo provider or an implicit fallback.

## Consequences

- Application assembly and shutdown order have one multiplatform implementation.
- Native capabilities stay narrow and testable instead of leaking into shared UI.
- Contract tests prove duplicate-run rejection, idempotent closure, and use of the explicitly injected live provider.
- Hosts must provide every capability deliberately, including fail-closed implementations while native integrations are
  unavailable.
