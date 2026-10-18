# Persistent AI conversations

Implemented 2026-10-17 across Voltforge_BL, Voltforge_UI, and Voltforge_AI.

## Root cause

The editor's AiChatPanel held messages and its session identifier in React state. Unmounting/reloading lost the transcript, and changing projects cleared that state. The AI memory API stores bounded, approved engineering context; it is not conversation history. The configured database had no chat/conversation tables to recover.

## Database rollout executed

The configured development MySQL database (`voltforge_db`, dev/development) was inspected before changes. `docs/schema/VF_CHAT_001.sql` adds `ai_conversations` and `ai_messages`, using the existing UUID, timestamp, collation and JPA conventions. It was applied with `rtk python scripts/chat_schema.py --apply`.

The script preserves a schema snapshot in `target/chat-schema-before-*.sql`. There were no pre-existing chat records; user/project records were untouched. MySQL DDL commits implicitly, so the migration is additive and repeatable using `CREATE TABLE IF NOT EXISTS`. Flyway remains disabled, as in the existing project: its earlier database migration history has no corresponding source migrations here.

Verified on development MySQL: tables, indexes, foreign keys, inserts, updates, ordered reads, and cascading deletion of synthetic messages. Verification DML ran in a transaction and was rolled back. A second opt-in Spring smoke test used the actual configured MySQL datasource and existing AI gateway; it saved both sides of a conversation, reopened them, and verified idempotent replay. Its synthetic account and conversation were removed afterwards.

- Owner deletion cascades to conversations/messages.
- Project deletion sets the conversation project reference to null, preserving history.
- Conversation deletion cascades to messages; the API also deletes children explicitly for ORM consistency.
- `(conversation_id, sequence_number)` and `(conversation_id, client_request_id, role)` are unique.
- Owner/activity and conversation/status indexes support listing and pending-turn checks.
- No automatic retention expiry was introduced. Chats remain until explicitly deleted or their owner account is deleted.

## API

All routes use the existing authenticated BL base URL and `ApiResponse` / `PagedResponse` conventions.

| Method | Path under `/api/v1/ai/conversations` | Behavior |
| --- | --- | --- |
| POST | `/` | Create; optional owned `projectId`; returns 201 and saved ID |
| GET | `/` | Owner-only summaries; `q` title search, optional `projectId`, `page`, `size` |
| GET | `/{id}` | Restore summary; owner check |
| PATCH | `/{id}` | Rename with validated `title` |
| DELETE | `/{id}` | Confirmed UI deletion; rejects actively generating conversations |
| GET | `/{id}/messages` | Owner-only message page, descending sequence (UI renders ascending) |
| POST | `/{id}/messages/stream` | Send with `message`, UUID `clientRequestId`, optional `retry`, explicit `includeProjectContext` |
| POST | `/{id}/stop` | Mark pending response interrupted; browser also cancels its stream |

List sizes are capped at 50 conversations and 100 messages. UI requests 30/50 and loads more incrementally. Title search is parameterized and escapes LIKE metacharacters. No all-transcripts fetch is used.

## Reliability and security

ConversationStore derives owner identity from the authenticated JWT subject and registered user; every read/write/rename/delete/stop verifies it. Private project association and context sharing require project ownership. Revision checks and the existing gateway size policy, stream admission limits, and application rate limiting remain active. Client history and identity are never authoritative.

A transaction locks the conversation row, saves the user turn and pending assistant record, assigns ordered sequence numbers and a retry attempt identifier, then commits before calling the provider. SSE `accepted` confirms the committed user message. Streaming checkpoints retain partial output; final persistence precedes `saved`. Completed request IDs replay without another provider call. Failed retries reuse the same user/assistant IDs; late writes from an earlier attempt cannot overwrite a newer attempt. Concurrent turns in one conversation return 409.

Disconnects/cancellation/failures retain the user message and mark the assistant interrupted/failed. A process crash can leave a pending record; reads/sends recover records untouched for five minutes as interrupted. Streams have a two-minute idle timeout and three-minute overall bound. Partial checkpoints occur about once per second when provider events arrive; an abrupt crash may lose tokens since the last checkpoint, not the accepted user message.

Messages are limited to 6,000 characters, titles to 200, search to 120, persisted assistant output to 120,000. Oversized responses are explicitly failed. Provider context uses at most 20 recent completed messages and 60,000 total characters, with 12,000-character excerpts per message. Full stored transcripts are retained for browsing.

Only allowlisted public response metadata is stored. Hidden reasoning, provider keys and system prompts are not conversation messages. AI route logging no longer prints message excerpts. Project circuit/code/simulation/approved-memory context is sent only after checking the explicit editor checkbox. General chat carries no project scope to the AI service.

## UI and modified areas

- `Voltforge_UI/src/features/ai/`: existing AiChatPanel integrated with conversationApi/useConversationHistory, history sidebar, new-chat project dialog, full AI workspace page and responsive token-based styling.
- `Voltforge_UI/src/App.tsx`, `config/navigation.tsx`: protected `/ai-chat` route and workspace navigation.
- `Voltforge_UI/src/features/editor/CircuitEditorPage.tsx`: conversation links reopen the existing editor assistant popup.
- `Voltforge_UI/src/api/services.ts`, `types/domain.ts`: existing authenticated stream transport supports conversation routes/request IDs.
- `Voltforge_UI/src/components/ui/CodeBlock.tsx`: escaped React syntax tokens, working copy and clipboard-failure state. Existing safe Markdown/link renderer is reused.
- BL `ai/conversation/`: entities, repositories, DTO validation, store, streaming wrapper, controller.
- AI `api/routes.py`: remove private message excerpts from request logs; existing provider integration retained.

Active IDs are stored per account/project and in navigation URLs; these are selection hints, never the source of transcript data or authorization. Invalid IDs show an error. Explicit New Chat preserves all other conversations. Search, rename/delete confirmation, grouped history, retry, stop, older-message loading and project navigation use persisted IDs. Desktop and mobile use the same component in graphite and ivory themes. Existing circuit proposal review/undo and approved-memory controls remain in the editor.

## Validation and limits

Tests: ConversationIntegrationTest exercises the real Spring HTTP/SSE server and isolated H2 persistence with a controlled provider, including cross-user access, project ownership, retries, cancellation, pagination and validation. DevelopmentConversationSmokeTest is opt-in (`VF_CHAT_DEV_SMOKE=true`) and tests the configured development MySQL and real AI provider. It must only be run against the authorized development environment.

UI: `npm test`, `npm run build`, `npm run test:chat-browser`, and `npm run test:project-flow-browser`. The chat browser test uses isolated API/auth fixtures and tests reloads, independent chats, retries, rename/search/delete, safe Markdown/copy and both themes at desktop/mobile sizes. Captures are in ignored `test-results/chat/`. AI security/health unit tests also run.

Boundaries: search currently covers titles, not message bodies. No pinning or new realtime infrastructure was added; tabs refresh on focus. Only the latest failed assistant turn can be retried. Historical circuit proposals are not re-enabled for execution after reload: generate a fresh revision-checked proposal. Guest chat cannot claim server persistence; sign-in is required. Browser automation does not claim a real Keycloak login/relogin test; persistence across sessions follows the stable server-side owner and is separately exercised through reopen tests.

The database migration is applied. Source and build artifacts are updated; an already running backend without class reload needs its normal development restart to expose the new endpoints. No production deployment was performed.

Executed results: 60 backend tests passed across the full suite and final targeted chat run (11 chat integration tests); the opt-in development smoke is skipped by default and was run separately with success. UI build/type check/bundle budget, the npm test contract suite, chat browser tests and project flow browser tests passed. Lint reported existing warnings outside the new chat files, with no errors. The contract runner also emitted a Vite dependency-scan shutdown warning after its assertions passed. AI security/health tests: 5 passed. No real user conversations were deleted.
