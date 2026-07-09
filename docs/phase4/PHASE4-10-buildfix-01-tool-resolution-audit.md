# Phase4-10 buildfix-01 - tool resolution audit

## Problem

The assistant could loop through notes.search, notes.list_recent, notes.list_pinned, notes.list_archived and notes.list_deleted when the user said something like:

- delete notes related to 王总
- delete the note titled 1

The root cause was not the database. The root cause was the MCP tool surface:

1. notes.search could still treat natural speech such as 王总相关便签 as a literal query. If no note contained the whole phrase, it returned zero even when notes contained 王总.
2. notes.delete could resolve title references, but it did not resolve full-text or tag related references such as 王总相关.
3. notes.list_recent was described as a context tool, but the model could keep using it as a search substitute.
4. There was no single resolver tool that told the model when to stop searching and ask for clarification.

## Fix

Added:

- NoteReferenceText.kt
- NoteReferenceResolver.kt
- notes.resolve

Updated:

- notes.search
- notes.delete
- notes.list_recent descriptor
- AssistantToolsModule registration

## Behavior

### Query cleaning

Natural phrases are normalized before search. Examples:

- 王总相关便签 -> 王总
- 王总相关记录 -> 王总
- 关于王总的便签 -> 王总的 (search still has original fallback)

### notes.search

notes.search now tries the original query first, then an intent-cleaned query. It returns:

- normalized_query
- match_strategy
- total_matching_count
- result_is_limited
- full note result payloads
- assistant_next_step_hint

### notes.resolve

notes.resolve is a low-risk resolver for voice grounding. It should be used once before a mutation when the target is vague. It returns candidate notes and a next step hint:

- zero matches: ask user to clarify, do not loop
- one match: safe to target the item
- multiple matches: if user asked for all related notes, use a high-risk tool with allow_multiple=true; otherwise ask for clarification

### notes.delete

notes.delete now resolves user-visible references itself. It supports:

- query
- note_ref
- note_title
- title
- exact_title
- note_id / note_ids from tool results

For a request like 删除王总相关便签, the expected call is:

```json
{"query":"王总相关便签","allow_multiple":true}
```

The tool searches active and archived notes, resolves all matching candidates, rewrites them to internal note_ids, then lets the command service create the normal high-risk pending confirmation preview. It does not delete anything without confirmation.

## Safety

- Deleted notes are not deleted again.
- Too many matches fail closed.
- Multiple matches require allow_multiple=true or explicit user phrasing such as 全部/所有/相关.
- Internal note_id remains supported when it comes from a tool result.

## Manual acceptance

1. Create or keep a note whose title/content contains 王总.
2. Say: 小智，删除王总相关便签.
3. Expected: assistant should call notes.delete with query or call notes.resolve once, not loop search/list tools.
4. Expected: App shows one confirmation dialog with matched notes preview.
5. Confirm: matching active/archived notes move to recently deleted.
6. Reject: no notes are changed.
