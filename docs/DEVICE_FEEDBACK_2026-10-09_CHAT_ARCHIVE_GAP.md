# ORGANISM — real-device feedback, 2026-10-09

**Status:** user-reported device observations; not independently reproduced by a developer test. This document records evidence and the next implementation target.

## Observed in user screenshots

- Import audit shows RAW ChatGPT files: 1; conversations: 74; saved messages: 10,034; message import events: 10,034; RAW characters: 0.
- Memory screen displays individual `CHAT_MESSAGE` objects and an imported CARD111 context index.
- Database overview shows Events: 10,109; Memory: 10,071; Relations: 0; Experiences: 0; Tasks: 0; Verifications: 0; Loss coverage: 0.
- User reports they cannot browse/open imported conversations, search inside conversations, or manage imported data. The visible chat does not provide access to old imported conversations or a usable history of new/old chats.
- Sending a test message returns `subscription_sharing_usage_limit_exceeded`. This is a connector/subscription-sharing limit, separate from local ZIP import/archive access.

## Interpretation — separate facts from hypotheses

- The displayed record counts prove only that the app reports records; they do not prove every exported message was imported correctly or can be retrieved.
- `RAW characters: 0` requires code inspection. Since the RAW source is a ZIP file, character count may be the wrong metric. Show byte size, SHA-256, and retention status instead.
- The current chat UI uses the active ORGANISM session and is not an archive browser for imported ChatGPT conversations.
- Re-importing the same ZIP is not an acceptable workaround. Existing UI warns that repeat import can create duplicates.

## Required next stage

1. Inspect `Db.java` and `ImportPipeline.java`: schemas, conversation IDs, titles, timestamps, message nodes, roles, and source/provenance relationships.
2. Write a concise feature spec and acceptance checks before implementation.
3. Implement an imported-conversation archive: list conversations; search titles and message bodies; open the full conversation with messages in order and roles/timestamps; open a conversation from a search result at the matching message; clearly separate imported ChatGPT conversations from ORGANISM's own sessions.
4. Verify deduplication/idempotency before enabling repeat import. Do not delete/reset/replace existing database data. Any migration must be backward-compatible with a recovery plan.
5. Fix RAW metadata presentation.
6. Build/test and record exact commit SHA and CI run. Then distinguish CI results from the user's subsequent real-device test.

## Data-safety constraints

- Do not ask the user to re-import the same ZIP.
- Do not delete or reset the current database.
- Do not claim all 74 conversations or 10,034 messages are correct until compared against the source export and tested through the UI.
- Keep this report and all subsequent work stages in the repository work log and continuity handoff.

## Follow-up acceptance criteria — archive navigation and search

Source inspection on 2026-10-09 found that an archive list/search screen and a transcript viewer already exist in `MainActivity.java`. Therefore, do not duplicate the screen; improve and verify the actual navigation gap.

- [ ] Search accepts a phrase and returns matching conversation title plus a readable snippet from a matching message.
- [ ] Selecting a search result opens the imported conversation at the page containing the first matching message, not merely at the start of a long transcript.
- [ ] Selecting a conversation without a search query opens its transcript and retains existing pagination for long conversations.
- [ ] Imported archive stays separate from ORGANISM session history.
- [ ] Search is read-only; no imported records, RAW source, or credentials are deleted or rewritten.
- [ ] SQL uses bound parameters for user text; no concatenated user input in SQL predicates.
- [ ] Android CI is checked against the exact code commit; CI does not count as real-device verification.
- [ ] Test query cases: empty query, title-only match, body-only match, no match, and a match beyond the first 100 messages.

## Implementation note

The current archive list searches `source_name` and `memory_objects.content` but only displays conversation title/count, and selecting a result opens a transcript dialog from the beginning. The next minimal change should expose a matching-message snippet and navigate to the matching 100-message page while preserving the full archive and existing database.