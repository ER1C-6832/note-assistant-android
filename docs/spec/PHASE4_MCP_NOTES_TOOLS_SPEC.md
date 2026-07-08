# Phase 4 MCP Notes Tools Specification

> Status: Draft specification for Phase 4.
> Scope: Real notes/tags/UI MCP tool execution through the trusted Phase 2 command path.
> Phase 4 turns the Phase 3 assistant runtime from "can talk but cannot touch notes" into "can safely operate the notes app".

## 1. Purpose

Phase 4 connects assistant MCP `tools/call` requests to the real notes product.

Phase 1 made the manual notes app usable. Phase 2 made command execution traceable and recoverable. Phase 3 made the Xiaozhi-compatible assistant runtime work while blocking note mutations. Phase 4 answers the next question:

```text
Can the voice assistant control the notes app globally without bypassing safety?
```

The answer must be yes, but only through the existing trusted command boundary:

```text
Xiaozhi server / Fake runtime
    -> assistant-runtime protocol router
        -> assistant-mcp-base tool executor interface
            -> assistant-tools note/tag/ui tools
                -> NoteCommandService / UiCommandBus
                    -> Phase 2 risk, confirmation, log, revision
                        -> Phase 1 UseCases
                            -> Repository
                                -> Room
```

Phase 4 must not make the assistant powerful by making it privileged. It becomes powerful because it can use the same safe product actions as the user.

## 2. Product Contract

From the user's point of view, Phase 4 must satisfy these promises:

- The assistant can create, search, open, update, organize, restore, and delete notes through speech or text.
- The assistant can operate on "the recent note", "the note about customers", "all archived todos", or similar references only after resolving them to concrete note ids.
- Low-risk read and navigation actions happen immediately.
- Medium-risk changes happen with clear feedback and command logs.
- High-risk changes ask for confirmation and show a preview before changing anything.
- The user can confirm or reject a pending operation by voice or in the app.
- Every assistant-triggered mutation is traceable in command logs.
- Every destructive or overwriting assistant-triggered mutation creates revisions before changing notes.
- UI refreshes after assistant operations without manual reload.

From the developer's point of view, Phase 4 must satisfy these constraints:

- `assistant-runtime` still must not import Room DAO, repository implementations, or `notes-data`.
- `assistant-runtime` should not directly depend on `NoteCommandService`; it should call an MCP tool executor/registry interface from `assistant-mcp-base`.
- `assistant-tools` may depend on `notes-domain` and may call `NoteCommandService`.
- Note and tag MCP mutation tools must never call DAO or repository implementations directly.
- Tool names, risk levels, statuses, confirmation states, and error codes must map to strong domain types.
- The Phase 3 blocked MCP path must be replaced by real execution only after the injected executor is available.
- If the executor is unavailable, tools must fail closed with `not_implemented` or `blocked`, not silently succeed.
- Fake runtime and real Xiaozhi runtime must use the same tool execution path.

## 3. Relationship to Earlier Phases

Phase 1 owns:

- Manual note CRUD.
- Tags, todo, pin, archive, trash, colors, filters, search, grid/list, and UI polish.
- UseCases for note and tag behavior.

Phase 2 owns:

- `NoteCommandService`.
- Risk policy.
- Command log.
- Pending confirmation.
- Revision snapshots.
- Local simulator behavior.
- Search UseCase shared outside UI.

Phase 3 owns:

- OTA/activation.
- WebSocket.
- Text conversation.
- Push-to-talk audio.
- Opus encode/decode/playback.
- MCP protocol parsing.
- Runtime state and diagnostics.
- Blocking note mutation tools before Phase 4.

Phase 4 owns:

- Real MCP tool registry.
- Tool descriptors and JSON schemas.
- Mapping MCP `tools/call` to `NoteCommandService`.
- Mapping command results back to MCP results.
- Voice/app confirmation bridge.
- UI navigation tools.
- End-to-end fake and real assistant tool-call acceptance.

## 4. Included

- MCP `tools/list` returns full Phase 4 note/tag/ui tool descriptors.
- MCP `tools/call` executes supported tools through real handlers.
- `assistant-tools` implementations for all required tools in this spec.
- A shared `McpToolExecutor` or equivalent interface in `assistant-mcp-base`.
- Runtime integration that delegates MCP tool calls to the injected executor.
- Source mapping: real assistant calls use `CommandSource.Voice`; fake/dev calls may use `CommandSource.LocalToolSimulator` or a documented fake assistant source.
- JSON schema validation for tool arguments before command execution.
- Tool result mapping from `CommandResult` to Xiaozhi/MCP-compatible response JSON.
- Confirmation tools for voice/app confirmation.
- UI command tools for opening notes and showing confirmation/search surfaces.
- Tests for registry, descriptors, execution mapping, confirmation mapping, and architecture boundaries.

## 5. Excluded

- Wake word foreground service. That belongs to Phase 5.
- Background operation while the app is killed. Phase 4 may work while the app process is alive.
- Cloud sync.
- Cross-device conflict resolution.
- General Android device-control MCP tools.
- Arbitrary filesystem, contacts, calendar, notification, or browser tools.
- Letting the assistant execute commands outside the notes app domain.
- Natural language parsing inside the Android app. Phase 4 receives normalized tool calls from the assistant service.

## 6. Global Rules

### 6.1 Tool Execution Boundary

- `McpProtocolClient` must no longer hard-code only Phase 3 safe tools when a real Phase 4 executor is injected.
- Runtime code parses protocol and forwards `tool_name + arguments_json`.
- Tool implementations live outside `assistant-runtime`.
- Tool implementations call either:
  - `NoteCommandService` for note/tag command operations.
  - `UiCommandBus` or equivalent navigation event boundary for UI-only operations.
  - A read-only UseCase only when the operation is explicitly low-risk and does not need command log.
- For consistency, all assistant-sourced note/tag tools should prefer `NoteCommandService` even for reads, because Phase 2 requires future MCP calls to be logged.

### 6.2 Fail-Closed Behavior

If any of these are true, the tool call must not mutate notes:

- Tool name is unknown.
- Arguments are not valid JSON.
- Required argument is missing.
- Target note/tag is ambiguous.
- Target note/tag no longer exists.
- Tool risk is high and confirmation is absent.
- Pending confirmation is expired, rejected, already consumed, or not found.
- Tool executor is not injected or disabled.
- Phase 4 feature flag is off.

The response must be `failed`, `blocked`, `requires_confirmation`, or `not_implemented` with a user-safe message.

### 6.3 Risk and Confirmation

Minimum risk levels:

| Tool | Risk | Confirmation |
| --- | --- | --- |
| `notes.search` | Low | No |
| `notes.list_recent` | Low | No |
| `notes.get` | Low | No |
| `notes.summarize` | Low | No |
| `tags.search` | Low | No |
| `ui.open_note` | Low | No |
| `ui.show_search` | Low | No |
| `ui.show_confirmation` | Low | No mutation |
| `assistant.confirm` | High gateway | Requires valid pending id |
| `assistant.reject` | Low | No mutation |
| `notes.create` | Medium | No |
| `notes.append` | Medium | No |
| `notes.update_title` | Medium | No |
| `notes.toggle_done` | Medium | No |
| `notes.pin` | Medium, High if >5 notes | Required if escalated |
| `notes.archive` | Medium, High if >5 notes | Required if escalated |
| `notes.restore` | Medium, High if >5 notes | Required if escalated |
| `tags.create` | Medium | No |
| `tags.bind.add` | Medium, High if >5 notes | Required if escalated |
| `tags.bind.remove` | Medium, High if >5 notes | Required if escalated |
| `notes.replace_content` | High | Yes |
| `notes.delete` | High | Yes |
| `notes.restore_revision` | High | Yes |
| `notes.clear_done` | High | Yes |
| `tags.bind.replace` | High | Yes |
| `tags.rename` | High when linked notes exist | Yes when high |
| `tags.delete` | High | Yes |

Rules:

- Medium operations affecting more than 5 notes escalate to high risk.
- Any command that overwrites, hides, deletes, restores old state over current state, or removes tag relationships at scale is high risk.
- Ambiguous references must be resolved before mutation. If resolution returns multiple plausible targets, the assistant must ask a clarification question or return candidates instead of mutating.
- Confirmation must consume the persisted Phase 2 pending confirmation, not simply replay a tool call with `confirmed=true`.

### 6.4 Command Source

MCP calls from the real assistant runtime must use:

```text
CommandSource.Voice
```

MCP calls from fake runtime tests may use:

```text
CommandSource.LocalToolSimulator
```

Future wake-word background calls must use:

```text
CommandSource.Wakeword
```

Command logs must preserve source, tool name, arguments JSON, risk, status, confirmation status, result JSON, affected note ids, and affected tag ids.

### 6.5 Result Envelope

Every MCP tool result must expose a stable envelope:

```json
{
  "status": "success",
  "message": "已创建便签：周五寄出样品",
  "risk": "medium",
  "requires_confirmation": false,
  "confirmation_id": null,
  "command_log_id": 42,
  "affected_note_ids": [123],
  "affected_tag_ids": [],
  "result": {}
}
```

Allowed `status` values:

```text
success
failed
blocked
requires_confirmation
partial_success
not_implemented
```

Rules:

- High-risk unconfirmed command returns `requires_confirmation`.
- `requires_confirmation=true` must include `confirmation_id`, summary, preview, and expiry.
- `result` must contain machine-readable fields for tests.
- User-facing `message` must be concise and safe.
- MCP response must also fit Xiaozhi JSON-RPC `tools/call` response shape used in Phase 3.

## 7. Required Tool Set

Phase 4 should expose a broad but notes-scoped tool set so the assistant can globally control the app without leaving the product domain.

### 7.1 Read and Discovery Tools

Required:

```text
notes.search
notes.list_recent
notes.get
notes.list_by_tag
notes.list_archived
notes.list_deleted
notes.list_todos
notes.list_done
tags.search
tags.list
```

Optional but recommended:

```text
notes.summarize
notes.find_candidates
```

### 7.2 Note Mutation Tools

Required:

```text
notes.create
notes.append
notes.update_title
notes.replace_content
notes.toggle_done
notes.pin
notes.archive
notes.delete
notes.restore
notes.restore_revision
notes.clear_done
```

Optional but useful:

```text
notes.update_color
notes.convert_type
```

If implemented, optional mutation tools must still use the Phase 2 command path or receive new command behavior cards before release.

### 7.3 Tag Tools

Required:

```text
tags.create
tags.search
tags.list
tags.rename
tags.delete
tags.bind
```

`tags.bind` must support:

```text
add
remove
replace
```

### 7.4 UI Tools

Required:

```text
ui.open_note
ui.show_search
ui.show_note_list
ui.show_tag
ui.show_archive
ui.show_trash
ui.show_confirmation
```

UI tools must not directly mutate notes. They emit app navigation or presentation events.

### 7.5 Confirmation Tools

Required:

```text
assistant.confirm
assistant.reject
assistant.list_pending_confirmations
```

Rules:

- `assistant.confirm` accepts only a `confirmation_id`.
- `assistant.reject` accepts only a `confirmation_id`.
- Confirmation tools must call Phase 2 confirmation APIs.
- Voice phrases like "确认", "取消", "不要删了" must be normalized by the assistant service into these tools.

## 8. Tool Schemas and Behavior Cards

### TOOL-01 `notes.search`

Purpose: Search active notes by query, tags, type, and status.

Risk: Low.

Arguments:

```json
{
  "query": "客户",
  "tags": ["工作"],
  "type": "normal",
  "include_done": true,
  "include_archived": false,
  "include_deleted": false,
  "limit": 10
}
```

Behavior:

- Use the shared `SearchNotesUseCase` or `NoteCommandService` search path.
- Default excludes deleted notes.
- Default excludes archived notes unless requested.
- Return ids, title, snippet, tags, type, done, pinned, archived, updated_at.

Acceptance:

- Given the user says "搜索客户相关的便签", When server calls `notes.search`, Then matching active notes are returned and a command log is written.
- Given matching deleted notes exist and `include_deleted=false`, Then deleted notes do not appear.

### TOOL-02 `notes.list_recent`

Purpose: Resolve "刚才那条", "最近那条", and "最近记了什么".

Risk: Low.

Arguments:

```json
{
  "limit": 5,
  "include_archived": false,
  "include_deleted": false
}
```

Behavior:

- Return recent notes ordered by updated time, pinned status only if existing product search/list semantics require it.
- Include enough data for a follow-up mutation to target a note id.

Acceptance:

- Given a note was just created by voice, When `notes.list_recent` is called, Then that note is the first result.

### TOOL-03 `notes.get`

Purpose: Retrieve one note by id.

Risk: Low.

Arguments:

```json
{
  "note_id": 123,
  "include_deleted": false
}
```

Behavior:

- Return full note fields except internal-only data.
- If note is deleted and `include_deleted=false`, return `not_found` or blocked with "便签已在回收站".

Acceptance:

- Given note id exists, Then tool returns title, content, type, done, tags, pinned, archived, deleted, color, created_at, updated_at.

### TOOL-04 `notes.create`

Purpose: Create a normal or todo note.

Risk: Medium.

Arguments:

```json
{
  "title": "周五寄出样品",
  "content": "快递单号待同步",
  "type": "todo",
  "tags": ["客户", "样品"],
  "color": "#FFF7CC",
  "pinned": false,
  "open_after_create": true
}
```

Behavior:

- Call `NoteCommandService.execute("notes.create", ...)`.
- If `open_after_create=true`, emit `ui.open_note` after successful creation.
- If `pinned=true` is not supported by existing `notes.create`, either extend the command path safely or execute a follow-up `notes.pin` command and log both operations.

Acceptance:

- Given the user says "帮我创建一个待办，周五寄出样品", Then a todo note is created, command log source is `voice`, and UI refreshes.

### TOOL-05 `notes.append`

Purpose: Add content to an existing note without overwriting current content.

Risk: Medium.

Arguments:

```json
{
  "note_id": 123,
  "content": "补充：快递单号待同步",
  "separator": "newline"
}
```

Behavior:

- Use `NoteCommandService`.
- Write revision before append for assistant-sourced calls.
- Preserve existing content.

Acceptance:

- Given note content is `A`, When appending `B`, Then final content contains both `A` and `B`, and revision contains `A`.

### TOOL-06 `notes.update_title`

Purpose: Rename a note title.

Risk: Medium.

Arguments:

```json
{
  "note_id": 123,
  "title": "客户样品寄送"
}
```

Behavior:

- Use `NoteCommandService`.
- Write revision before mutation.

Acceptance:

- Given a voice title update succeeds, Then command log includes affected note id and revision stores old title.

### TOOL-07 `notes.replace_content`

Purpose: Replace full note body.

Risk: High.

Arguments:

```json
{
  "note_id": 123,
  "content": "新的完整正文",
  "expected_updated_at": 1720000000000
}
```

Behavior:

- Without valid pending confirmation, return `requires_confirmation`.
- Preview old and new content snippets.
- On confirm, revalidate note id and expected timestamp if provided.
- Write revision before overwrite.

Acceptance:

- Given the assistant calls unconfirmed replace content, Then content remains unchanged and pending confirmation is stored.

### TOOL-08 `notes.toggle_done`

Purpose: Mark todo note done or not done.

Risk: Medium.

Arguments:

```json
{
  "note_id": 123,
  "done": true
}
```

Behavior:

- Validate note is todo.
- Use `NoteCommandService`.

Acceptance:

- Given the user says "把刚才那条标记完成", Then the recent note is resolved to id and todo state becomes done.

### TOOL-09 `notes.pin`

Purpose: Pin or unpin one or more notes.

Risk: Medium, high if more than 5 notes.

Arguments:

```json
{
  "note_ids": [123],
  "pinned": true
}
```

Behavior:

- Use `NoteCommandService`.
- Batch above threshold requires confirmation.

Acceptance:

- Given one note is pinned by voice, Then it moves to the pinned area/filter and command log is written.

### TOOL-10 `notes.archive`

Purpose: Archive or unarchive notes.

Risk: Medium, high if more than 5 notes.

Arguments:

```json
{
  "note_ids": [123, 456],
  "archived": true
}
```

Behavior:

- Use `NoteCommandService`.
- Deleted notes must not be archived.
- Batch above threshold requires confirmation.

Acceptance:

- Given active notes are archived, Then they disappear from active list and appear in archive.

### TOOL-11 `notes.delete`

Purpose: Soft delete notes.

Risk: High.

Arguments:

```json
{
  "note_ids": [123]
}
```

Behavior:

- Always requires confirmation for assistant source.
- Return preview with title, snippet, tags, updated_at.
- On confirmation, revalidate targets and soft delete.
- Write revision for each affected note.

Acceptance:

- Given the user says "删除刚才那条便签", Then assistant first gets a confirmation prompt. No note is deleted before confirmation.

### TOOL-12 `notes.restore`

Purpose: Restore notes from trash.

Risk: Medium for small batch, high if more than 5 notes.

Arguments:

```json
{
  "note_ids": [123]
}
```

Behavior:

- Use `NoteCommandService` if implemented; otherwise add command support before exposing this tool as executable.
- Restoring non-deleted note returns a safe no-op or validation error.

Acceptance:

- Given note is in trash, When restore succeeds, Then it appears in normal list again.

### TOOL-13 `notes.restore_revision`

Purpose: Restore a previous revision over current note state.

Risk: High.

Arguments:

```json
{
  "note_id": 123,
  "revision_id": 888
}
```

Behavior:

- Always requires confirmation.
- Preview current vs revision title/content/tag/type/done/pin/archive/deleted/color.
- Write a new revision of current state before restore.

Acceptance:

- Given revision restore is confirmed, Then current state matches revision and prior current state is recoverable.

### TOOL-14 `notes.clear_done`

Purpose: Delete, archive, or move completed todos according to product rule.

Risk: High.

Arguments:

```json
{
  "action": "archive",
  "tag_filter": ["工作"],
  "older_than_days": 0
}
```

Behavior:

- Phase 4 must define the product rule before implementation:
  - `archive`: archive completed todos.
  - `delete`: soft delete completed todos.
- Must preview affected notes and require confirmation.
- Must write revisions.

Acceptance:

- Given 6 completed todos match, Then request returns confirmation preview and changes nothing before confirmation.

### TOOL-15 `tags.search`

Purpose: Search tags by name.

Risk: Low.

Arguments:

```json
{
  "query": "客户",
  "limit": 10
}
```

Behavior:

- Return tag id, name, color, linked note count if available.

Acceptance:

- Given existing tag `客户`, Then searching `客` returns it.

### TOOL-16 `tags.list`

Purpose: List all tags for assistant planning.

Risk: Low.

Arguments:

```json
{
  "include_counts": true
}
```

Behavior:

- Return stable ids, names, colors, linked note counts.

Acceptance:

- Given tags exist, Then tool returns them without mutating notes.

### TOOL-17 `tags.create`

Purpose: Create a tag.

Risk: Medium.

Arguments:

```json
{
  "name": "客户",
  "color": "#AABBCC"
}
```

Behavior:

- Use command path if available.
- If `NoteCommandService` does not yet support executable `tags.create`, implement it before exposing the tool as success-capable.
- Duplicate normalized tag name returns existing tag or validation error according to Phase 1 tag rules.

Acceptance:

- Given tag name does not exist, Then tag is created and command log is written.

### TOOL-18 `tags.rename`

Purpose: Rename a tag.

Risk: Medium if unlinked, high if linked notes exist.

Arguments:

```json
{
  "tag_id": 1,
  "name": "客户跟进"
}
```

Behavior:

- If linked notes exist, require confirmation with affected note count.
- Write revisions for affected notes if tag names are stored in note snapshots.
- Use command path.

Acceptance:

- Given tag is linked to notes, Then rename requires confirmation and preview shows affected count.

### TOOL-19 `tags.delete`

Purpose: Delete a tag and remove relationships from notes.

Risk: High.

Arguments:

```json
{
  "tag_id": 1
}
```

Behavior:

- Always requires confirmation.
- Deleting tag never deletes notes.
- Write revisions for affected notes before relationships are removed.

Acceptance:

- Given tag is linked to 3 notes, Then confirmation preview says 3 notes will lose the tag, and notes remain after confirmed deletion.

### TOOL-20 `tags.bind`

Purpose: Add, remove, or replace tags on notes.

Risk: Medium for add/remove, high for replace or batch above threshold.

Arguments:

```json
{
  "note_ids": [123],
  "tags": ["客户", "待办"],
  "mode": "add"
}
```

Behavior:

- `mode` is one of `add`, `remove`, `replace`.
- `replace` always requires confirmation.
- Use `NoteCommandService`.
- Create missing tags only if command path explicitly supports that behavior; otherwise return validation error with suggested `tags.create`.

Acceptance:

- Given note has tag `客户`, When `mode=replace` with `报价`, Then confirmation is required and revision stores original tag set.

### TOOL-21 `ui.open_note`

Purpose: Open a specific note in the app.

Risk: Low.

Arguments:

```json
{
  "note_id": 123,
  "mode": "detail"
}
```

Behavior:

- Emit UI navigation command.
- Do not mutate note data.
- If note does not exist, return `not_found`.

Acceptance:

- Given note exists and app is foreground, Then note detail opens.

### TOOL-22 `ui.show_search`

Purpose: Show search screen/results for a query.

Risk: Low.

Arguments:

```json
{
  "query": "客户",
  "tags": ["工作"]
}
```

Behavior:

- Emit UI command to show search state.
- May reuse `notes.search` results.

Acceptance:

- Given query is valid, Then UI enters search mode with matching results.

### TOOL-23 `ui.show_confirmation`

Purpose: Show pending confirmation in app.

Risk: Low, no mutation.

Arguments:

```json
{
  "confirmation_id": "abc123"
}
```

Behavior:

- Load pending confirmation from Phase 2 store.
- Show confirmation bottom sheet/dialog with preview.
- If missing or expired, show safe error.

Acceptance:

- Given high-risk delete is pending, Then UI shows affected notes and confirm/reject actions.

### TOOL-24 `assistant.confirm`

Purpose: Confirm a pending command.

Risk: High gateway.

Arguments:

```json
{
  "confirmation_id": "abc123"
}
```

Behavior:

- Call `NoteCommandService.confirmPendingCommand`.
- Do not accept tool name or arguments as a substitute for pending id.
- Return mutation result.

Acceptance:

- Given delete pending confirmation exists, When assistant confirms with id, Then target notes are deleted and original command log is updated.

### TOOL-25 `assistant.reject`

Purpose: Reject a pending command.

Risk: Low.

Arguments:

```json
{
  "confirmation_id": "abc123"
}
```

Behavior:

- Call `NoteCommandService.rejectPendingCommand`.
- Mutate no notes.

Acceptance:

- Given delete pending confirmation exists, When assistant rejects it, Then no notes are deleted and command log is rejected.

### TOOL-26 `assistant.list_pending_confirmations`

Purpose: Let the assistant recover pending confirmation state after interruption.

Risk: Low.

Arguments:

```json
{
  "limit": 5
}
```

Behavior:

- Return unexpired pending confirmations with summary and expiry.
- Do not include full private note content unless preview already contains it by Phase 2 design.

Acceptance:

- Given one delete confirmation is pending, Then tool returns its id, tool name, summary, and expires_at.

## 9. Reference Resolution

The assistant service may call tools with concrete ids. If it cannot, Phase 4 must provide safe discovery tools instead of guessing inside mutation tools.

Rules:

- Mutation tools should prefer `note_ids` or `tag_id`.
- Terms like "刚才那条" should be resolved by `notes.list_recent` before mutation.
- Terms like "客户相关的便签" should be resolved by `notes.search` before mutation.
- If search returns one high-confidence match, the assistant may proceed for medium operations.
- If search returns multiple matches for mutation, the assistant must ask a clarification question or use a high-risk confirmation preview.
- Tool handlers must not silently choose an arbitrary match.

Required candidate result fields:

```json
{
  "note_id": 123,
  "title": "客户样品寄送",
  "snippet": "快递单号待同步",
  "tags": ["客户"],
  "type": "todo",
  "done": false,
  "updated_at": 1720000000000
}
```

## 10. MCP Descriptor Requirements

`tools/list` must return descriptors that include:

- Tool name.
- Description in Chinese or bilingual text suitable for Xiaozhi.
- JSON schema for arguments.
- Risk level.
- Whether confirmation may be required.
- Whether tool mutates notes.
- Short examples.

Descriptor example:

```json
{
  "name": "notes.append",
  "description": "向指定便签追加内容，不覆盖原正文。",
  "inputSchema": {
    "type": "object",
    "properties": {
      "note_id": { "type": "integer" },
      "content": { "type": "string" },
      "separator": { "type": "string", "enum": ["newline", "space"] }
    },
    "required": ["note_id", "content"]
  },
  "risk": "medium",
  "mutates": true,
  "confirmation": "not_required_by_default"
}
```

## 11. UI Synchronization

After successful assistant mutation:

- Active note list updates automatically.
- Detail screen updates if it is showing the affected note.
- Archived/trash/tag filters update if affected.
- A lightweight feedback surface shows assistant operation result.
- High-risk confirmation prompt can be shown from voice or app UI.

Timing:

- Foreground UI should reflect completed local DB mutation within 500 ms after repository flow emits.
- If the app is backgrounded but process is alive, state may update on next foreground resume.

## 12. Errors and User Messages

Minimum error codes:

```text
invalid_json
validation_error
not_found
ambiguous_target
already_deleted
already_archived
conflict
requires_confirmation
confirmation_expired
confirmation_rejected
executor_unavailable
unsupported_tool
storage_error
partial_failure
```

Rules:

- Do not expose stack traces.
- Do not claim success if mutation did not happen.
- Do not return raw SQL/Room errors.
- For ambiguous target, return candidates and ask for clarification.
- For high-risk unconfirmed operation, return confirmation preview instead of error.

## 13. Architecture Requirements

Required structure:

```text
assistant-mcp-base
    McpTool
    McpToolRegistry
    McpToolExecutor
    McpToolDescriptor
    McpToolResult

assistant-tools
    notes/*.kt
    tags/*.kt
    ui/*.kt
    confirmation/*.kt
    depends on notes-domain and assistant-mcp-base

assistant-runtime
    parses protocol
    injects McpToolExecutor
    does not know note business implementation
```

Forbidden dependencies:

```text
assistant-runtime -> notes-data
assistant-runtime -> Room DAO
assistant-runtime -> NoteRepositoryImpl
assistant-runtime -> NoteCommandService
assistant-tools -> notes-data DAO
assistant-tools -> Room DAO
MCP tool -> direct Activity navigation
MCP tool -> direct DB write
```

Allowed dependencies:

```text
assistant-tools -> notes-domain
assistant-tools -> assistant-mcp-base
assistant-tools -> app UI command boundary
assistant-runtime -> assistant-mcp-base
assistant-runtime -> app-settings/core-common
```

## 14. Rollout Gates

### Gate A: Local Executor Wiring

Goal: Replace Phase 3 blocked MCP path with an injected executor while keeping fake runtime.

Acceptance:

- `tools/list` returns full Phase 4 descriptors.
- Fake `tools/call notes.search` returns real search results.
- Fake `tools/call notes.create` creates a note through `NoteCommandService`.
- Fake `tools/call notes.delete` returns `requires_confirmation`.
- `assistant-runtime` still has no forbidden imports.

### Gate B: Core Voice Notes Tools

Goal: Real Xiaozhi runtime can execute common low/medium tools.

Required tools:

```text
notes.create
notes.search
notes.list_recent
notes.get
notes.append
notes.update_title
notes.toggle_done
notes.pin
ui.open_note
tags.search
tags.bind.add
```

Acceptance utterances:

```text
小智，帮我记一下明天上午十点联系客户
小智，帮我创建一个待办，周五寄出样品
小智，搜索客户相关的便签
小智，打开刚才那条便签
小智，给刚才那条补充一句，快递单号待同步
小智，把刚才那条标记完成
小智，把客户样品寄送置顶
```

### Gate C: High-Risk Confirmation

Goal: Real assistant can request high-risk tools but cannot execute them without confirmation.

Required tools:

```text
notes.delete
notes.replace_content
notes.restore_revision
tags.bind.replace
tags.delete
assistant.confirm
assistant.reject
ui.show_confirmation
```

Acceptance utterances:

```text
小智，删除刚才那条便签
小智，确认
小智，取消刚才的删除
小智，把这条便签正文替换成……
小智，删除客户这个标签
```

Acceptance:

- Delete request creates pending confirmation and does not delete before confirm.
- Voice confirmation consumes the pending confirmation.
- Voice rejection marks it rejected.
- Expired confirmation cannot execute.
- Revision exists after confirmed destructive operation.

### Gate D: Full Notes App Control

Goal: Assistant can operate the complete Phase 1 notes feature set through safe tools.

Required additions:

```text
notes.archive
notes.restore
notes.clear_done
notes.list_archived
notes.list_deleted
notes.list_todos
notes.list_done
tags.create
tags.rename
tags.list
ui.show_search
ui.show_note_list
ui.show_tag
ui.show_archive
ui.show_trash
assistant.list_pending_confirmations
```

Acceptance:

- Assistant can navigate the app's major note views.
- Assistant can organize tags and archive/trash safely.
- Batch operations above threshold require confirmation.

## 15. Test Acceptance Suite

Minimum tests:

- Registry lists all required Phase 4 tools.
- Descriptor schemas include required fields.
- Unknown tool fails closed.
- Invalid JSON returns `invalid_json`.
- `notes.search` calls real command/search path.
- `notes.create` creates command log with source `voice`.
- `notes.append` creates revision before mutation.
- `notes.delete` returns `requires_confirmation` and does not mutate.
- `assistant.confirm` executes the stored pending command.
- `assistant.reject` mutates no notes.
- Expired confirmation does not execute.
- `tags.delete` deletes tag only after confirmation and does not delete notes.
- `tags.bind.replace` requires confirmation.
- `ui.open_note` emits UI command and does not mutate DB.
- Fake runtime `tools/call` and real runtime `tools/call` share the same executor.
- Architecture test: `assistant-runtime` has no forbidden imports.
- Architecture test: tool implementations do not import DAO or Room classes.
- Result mapper preserves command status, command log id, affected ids, and confirmation id.

Manual acceptance:

- Real Xiaozhi endpoint can call `tools/list`.
- Real Xiaozhi endpoint can call `notes.create`.
- Real Xiaozhi endpoint can call `notes.search`.
- Real Xiaozhi endpoint can call a high-risk tool and receive confirmation result.
- Real voice "确认" can execute a pending high-risk operation.
- UI updates after assistant mutation.

## 16. Phase Completion Definition

Phase 4 is complete when:

- Full Phase 4 tool descriptors are available through MCP `tools/list`.
- Required Gate A, B, and C tools are implemented through `assistant-tools`.
- Gate D tools are implemented or explicitly deferred with documented reason.
- All assistant note/tag mutations go through `NoteCommandService`.
- High-risk operations require persisted confirmation.
- Confirmation can be accepted or rejected through voice and app UI.
- Mutations write command logs with source `voice`.
- Destructive or overwriting mutations write revisions.
- Fake runtime and real Xiaozhi runtime use the same executor.
- UI refreshes after assistant mutations.
- Architecture boundaries pass static checks.
- The project remains ready for Phase 5 wake word without moving note safety into background service code.

## 17. Phase4 前代码清理清单

Phase 4 会增加很多工具类。进入实现前建议先清理这些点，否则工具层很容易膨胀成难维护结构：

1. 合并重复 command model。
   - 当前存在 `notes-domain/.../model/PendingConfirmation.kt` 和 `notes-domain/.../model/command/PendingConfirmation.kt`。
   - 当前存在 `notes-domain/.../model/NoteRevision.kt` 和 `notes-domain/.../model/command/NoteRevision.kt`。
   - 选择一个包作为唯一 domain model 来源，删除未使用副本或迁移引用。

2. 清理旧 AssistantState。
   - 当前存在 `assistant-runtime/.../state/AssistantState.kt` 和 `assistant-runtime/.../controller/AssistantState.kt`。
   - `controller/AssistantState.kt` 看起来像早期占位状态，默认值还是 connected。Phase4 前应确认是否未使用，未使用就删除。

3. 统一 MCP result/model。
   - 当前 `assistant-runtime` 内部有 Phase3 专用 `McpToolResult/McpToolStatus`，`assistant-mcp-base` 也有基础 `McpToolResult`。
   - Phase4 应把可复用的 tool result、descriptor、executor 放到 `assistant-mcp-base`，runtime 只做协议包装。

4. 扩展 `NoteToolRegistry` 前先升级接口。
   - 现在 registry 只能列 tool names。
   - Phase4 需要 `listDescriptors()`、`execute(name, argumentsJson, source/context)`、`findTool()`。

5. 不要为每个工具复制 JSON 解析模板。
   - 建议建立轻量 `ToolArgumentParser` / schema validator / result mapper。
   - 各工具只负责声明 schema 和调用 command boundary。

6. 先补齐 `NoteCommandService` 缺口再暴露工具。
   - `tags.create`、`tags.rename`、`notes.restore`、`notes.clear_done` 等如果 command path 尚未支持，不允许工具直接绕到 UseCase/Repository。
   - 可以先返回 `not_implemented`，但不能假装成功。

## 18. Traceability

This spec refines:

- `docs/DEVELOPMENT_PLAN.md` Phase 4.
- `docs/spec/PHASE1_NOTES_SPEC.md`.
- `docs/spec/PHASE2_TRUST_AND_TRACEABILITY_SPEC.md`.
- `docs/spec/PHASE3_ASSISTANT_RUNTIME_SPEC.md`.

It should be updated whenever Phase 4 tool behavior, risk classification, confirmation workflow, or architecture boundary changes.
