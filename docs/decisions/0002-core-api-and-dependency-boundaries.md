# ADR 0002: Core API and dependency boundaries

- Status: accepted
- Date: 2026-11-09

## Context

Desktop and future mobile clients need the same application behavior without depending on Compose,
provider payloads, storage records, or operating-system types. Provider adapters also need a stable
boundary that does not grant them ownership of application policy or run lifecycle.

## Decision

Use three initial core modules with one-way dependencies:

```text
clients ---> shared-ui ---> core:api
   |                          ^
   |                          |
   +------> core:application -+----> core:domain
   |                                    |
   +------> adapters:fixture ---------->+

adapters:fixture ----------------> core:domain
adapters:mistral ----------------> core:domain
```

- `core:api` owns client-facing identifiers, the start, observe, and cancel capability, and
  immutable run states. It has no dependency on another project module.
- `core:domain` owns validated internal values, provider-neutral generation events, and the outbound
  text-generation port. It depends on API identifiers but is not exposed to presentation clients.
- `core:application` owns validation, policy, orchestration, and run lifecycle implementation.
- Presentation consumes only `core:api` when application behavior is introduced.
- `shared-ui` owns platform-neutral screen state, user actions, presenter logic, and Compose UI. It
  has no dependency on application implementations or provider adapters.
- Client composition roots select concrete application and adapter implementations, wire them to
  `shared-ui`, and close their owned resources.
- Provider, persistence, and operating-system adapters implement core-owned ports and remain outside
  all core modules.
- `adapters:fixture` is the first provider adapter. It depends only on the domain-owned generation
  port and contains no application policy.
- `adapters:mistral` implements provider-backed model discovery. It maps Mistral transport types
  into the domain-owned catalog contract without exposing Ktor or provider payloads to clients.

The public observation contract is passive: collecting state never starts or repeats generation.
Cancelling one observer does not cancel the run; cancellation is an explicit capability operation.
Expected validation and execution failures use typed outcomes rather than exceptions.

Gradle checks direct project dependencies against an allowlist. Core modules compile with explicit
public API mode so an accidental public declaration fails compilation. Public core contracts are
documented with KDoc. Until a stable release, incompatible contract changes require an explicit
decision and corresponding consumer-test update; additive changes are preferred.

## Consequences

- Core API common sources have no Compose, desktop, Android, UIKit, transport, or storage dependency;
  adding one requires a visible build and architecture change.
- Provider execution is owned by the application coroutine collecting the generation port.
- New module dependencies require an explicit architecture decision and allowlist update.
- Detailed concurrency, retention, and terminal-state behavior remains an application implementation
  concern and is introduced with the run-lifecycle increment.
