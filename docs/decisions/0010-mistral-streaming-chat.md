# ADR 0010: Treat each streaming chat request as one owned attempt

- Status: accepted
- Date: 2026-11-09

## Context

Mistral's chat-completion endpoint can return partial text as data-only Server-Sent Events and marks
the end of a successful stream with `data: [DONE]`. A streaming POST differs from model discovery:
the server may have started generation before a transport failure becomes visible, and some output
may already have reached the application. Blind replay could therefore duplicate work, cost, or
visible text.

The transport also receives untrusted, incrementally decoded input. It needs explicit limits and
terminal-state rules while remaining cancellable by the application-owned run coroutine.

## Decision

- Implement `TextGenerationPort` in `MistralTextGenerationPort`. Collecting its cold flow performs
  exactly one `POST https://api.mistral.ai/v1/chat/completions`; the adapter never reconnects or
  retries internally.
- Send one validated user message, a validated model identifier, an optional bounded output-token
  limit, and an explicit `stream: true`. Internal run and conversation identifiers never enter the
  provider request.
- Read the response channel incrementally with strict UTF-8 line decoding. Support SSE comments,
  CRLF/LF delimiters, and multi-line `data` fields; ignore unrelated SSE fields.
- Require an `event-stream` response, at least one valid choice, and the documented `[DONE]`
  sentinel. Reject malformed JSON, multiple choices, an unexpected choice index, unsupported
  content, text after a finish reason, oversized input, and a successful stream without a valid
  terminal marker.
- Support plain string content and arrays composed only of text chunks. Usage information is
  optional and does not enter the domain contract.
- Coalesce small provider deltas before presentation. Bound prompt length, SSE line and event size,
  and total generated characters. Flush accepted text before reporting a non-cancellation failure.
- Propagate coroutine cancellation without translating it to a failure event. Closing collection
  therefore cancels the in-flight HTTP operation through structured concurrency.
- Preserve retry advice as explicit domain and client data. A bounded numeric `Retry-After` on a
  definite `429` rejection permits a distinct delayed attempt. Network ambiguity, temporary
  unavailability, malformed streams, and partial output permit only an explicit new attempt. Other
  failures are not retryable. No category authorizes automatic replay of the failed POST.

The wire contract follows Mistral's
[`POST /v1/chat/completions`](https://docs.mistral.ai/api/endpoint/chat)
documentation. Incremental line handling uses Ktor's bounded
[`readLineStrict`](https://api.ktor.io/ktor-io/io.ktor.utils.io/read-line-strict.html) primitive.

## Consequences

Cancellation has one clear owner, provider output remains ordered, and a failed attempt cannot
silently duplicate generation. Partial text already accepted by the adapter remains visible when a
stream fails. Provider error bodies, credentials, and request identifiers do not cross the domain
boundary.

The adapter is ready for a future live composition root but is not wired into the runnable demo.
Credential persistence, conversation-history requests, tools, images, citations, non-text content,
and automatic retry remain outside this decision.
