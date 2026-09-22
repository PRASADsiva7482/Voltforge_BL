# VoltForge AI gateway policy

The Spring backend is the only browser-facing boundary for the local VoltForge
AI service. Browser requests are authenticated with the JWT subject, and a
project-bound request is accepted only when that subject can access the
project. The gateway also compares the submitted `projectRevision` with the
current project revision before forwarding a request. A mismatch returns
`AI_PROJECT_REVISION_STALE` and no AI call is made.

Structured context containing `projectId` or `projectRevision` must agree with
the selected project scope. Client-supplied `authenticatedUserId`, memory, and
service credentials are never authoritative; the private AI token is injected
by the server-side WebClient.

Streaming uses a direct `bodyToFlux(ServerSentEvent<String>)` relay. The
per-user and per-project admission limits are non-blocking. A Reactor client
cancellation releases the admission lease and cancels the upstream WebClient
response, so a disconnected browser does not hold AI capacity. Named upstream
events and their order are preserved, including citations, proposals,
completion, and typed errors.

The gateway bounds the request body, history, and large context fields. AI
failure returns a generic degraded response or a versioned SSE error and never
includes upstream exception text, URLs, project content, or credentials. The
gateway does not mutate projects; preview/apply/undo remains an explicit,
revision-guarded operation owned by VFAI-029.

Defaults can be overridden with:

- `VOLTFORGE_AI_MAX_REQUEST_BYTES`
- `VOLTFORGE_AI_MAX_STREAMS_PER_USER`
- `VOLTFORGE_AI_MAX_STREAMS_PER_PROJECT`
- `VOLTFORGE_AI_STREAMING_TIMEOUT`
