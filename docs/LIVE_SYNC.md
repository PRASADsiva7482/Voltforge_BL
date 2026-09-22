# Live Sync transport contract

Canvas persistence uses the guarded REST save described in [PROJECT_REVISIONS.md](PROJECT_REVISIONS.md). WebSocket canvas events are authorized live previews.

The supported STOMP frame budget is **1 MiB of UTF-8 bytes**, including headers. The browser checks the complete serialized frame before sending. The servlet text/binary buffers and outbound send buffer are 2 MiB, allowing the STOMP frame plus SockJS wrapping. Send timeout remains 15 seconds. These are bounded per-session limits, not permission to send arbitrarily large projects.

Spring configures native container buffers separately from STOMP message limits; see its [WebSocket transport documentation](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/server-config.html). `WebSocketConfig` sets both. JWT authentication, allowed origins, project access checks, and server-derived event identities remain enforced.

The browser pauses Live Sync after STOMP ERROR or close codes 1002, 1003, 1007, 1008 and 1009. It shows one actionable message, clears pending transport timers and retains the editor document. REST saves remain available when the update exceeds the transport limit. Reduce the project size and reopen it to resume Live Sync. Temporary disconnects retry after 1, 2, 4, 8 and then at most 12 seconds. Backoff resets only after 30 seconds connected; a brief CONNECTED response does not reset it. Navigation disposes old callbacks and timers.

Save successfully before reopening. Boundary testing found a separate REST/MySQL failure when saving a document with 1.1 MB of Unicode property data. The editor retained those local changes as unsaved; durable persistence of that large document did not pass. This is tracked as `VFOPT-X-001-F008` and is the next item. Transport acceptance does not establish large-document persistence support.

Firmware pin callbacks in `LogicRegistry` and `DigitalLogicChips` write through `updateRuntimeNode`. They update live device outputs without advancing the authored document revision, scheduling autosaves, or broadcasting the full canvas. The solver algorithms and AVR instruction budgets are unchanged.

Validation results and remaining limitations are retained in `Voltforge_UI/docs/VFOPT_X_001_FINDINGS_BACKLOG.json`, item `VFOPT-X-001-F002`. Temporary browser probes and raw reports are removed after validation, as requested.
