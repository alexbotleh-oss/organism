# ORGANISM — continuity handoff (2026-10-09)

## User-facing goal
The user selected a ZIP containing the official ChatGPT data export. The app previously crashed during chat import. ZIP creation/export is already known to work and must not be treated as the problem. The current task is specifically to repair ChatGPT ZIP ingestion and improve the mobile UI without regressing existing features.

## Baseline
- Repository: https://github.com/alexbotleh-oss/organism
- Main branch baseline: 6835e00ba03a3bef3206eb49c924f80604c1da17
- Previous HTML import work is in PR #9. It does not prove ZIP import works on-device.
- No claim of a real-device test until one is actually run.

## Changes on branch `fix/zip-import-crash-and-archive-restore`
- `ImportPipeline.importUri` detects ZIP before reading it into a byte array.
- Selected ZIP is copied to app-private RAW storage in bounded chunks.
- ZIP is read using `ZipInputStream`; `conversations.json` is scanned as a stream and each top-level conversation is parsed independently, avoiding one giant archive buffer and one giant JSONArray.
- Auxiliary TXT/MD/HTML entries have an 8 MiB cap.
- Import reports a specific error when no conversations.json is present or the JSON is truncated.
- Import screen separates ChatGPT ZIP from TXT/MD/PDF and adds audit access.
- Mobile shell updated: smaller, even-width five-item bottom navigation; secondary pages remain available via “Ещё”; refreshed spacing/cards/header.
- Import progress and actionable error dialog added.

## Critical next steps
1. Build via GitHub Actions and inspect logs. Fix all compilation errors before distributing an APK.
2. Review ZIP scanner carefully against the supplied official export ZIP. Verify nested objects, escaped quotes/backslashes, arrays, empty export, malformed/truncated archive, and one very large conversation.
3. Verify source/provenance relationships and import audit counts. Prevent a false success if parsing fails part-way.
4. Test on actual Android device with the same ChatGPT export ZIP; confirm app remains open and chats appear in audit/memory.
5. Only after successful tests, provide APK. Never state the device import is verified based on compilation alone.
6. Keep export/backup code untouched unless a separate, explicit defect is established.

## Important product/process constraints
- Preserve existing user data; do not replace/delete the database automatically.
- Do not restart the project from scratch or re-ask which ZIP the user selected.
- Explain what was actually changed and distinguish build tests from device tests.
- Update this handoff at the end of each substantial work session.


## Update after 2026-10-09 architecture decision

- User-confirmed product invariant: **one memory, different connectors**. Memory belongs to ORGANISM/user, not to a model, platform, API, browser session, or chat.
- Added `docs/MEMORY_CONNECTOR_INVARIANTS_v0.1.md` defining memory ownership, connector responsibilities, lifecycle states, platform/API separation, safety requirements, acceptance criteria, and implementation order.
- Importer correction committed: `f5e35a7e4e8987c285f83fdf99241fa4ba35cd41`. Oversized optional TXT/MD/HTML entries are now drained and skipped with a summary count rather than aborting the entire ChatGPT ZIP import. The required conversations JSON remains separately streamed and parsed.
- Latest branch head after the architecture note: `0ec5f1efaac34a8e255a1cfbbff11105d0e2387b`.
- The architecture note is a target contract, not a claim that platform connector support is already implemented. Next code step: move existing API interaction behind a connector interface while preserving behavior; then prove an allowed platform-session path independently.
- Build workflows were observed running for importer commit `f5e35a7e4e8987c285f83fdf99241fa4ba35cd41`; confirm final status for the latest head before distributing an APK. No on-device ZIP test has yet been confirmed.

- Build check on importer commit failed because the preceding full-screen chat layout used the wrong Java LinearLayout.addView overload in MainActivity lines 73–74. Fixed argument order in commit `cd1009cc1105f9e148ce8c0d8d6d42fae8442cff`. This was a compile-time failure, not an import-pipeline failure. Re-run/confirm CI on the fixed head before distributing an APK.


## Handoff update — 2026-10-09: archive/search gap and work logging

User tested the APK on a real Android phone and reported that imported conversations cannot be browsed or searched; old/new chat history is not usable. Screenshots show 74 conversations, 10,034 saved messages, one RAW ZIP, and RAW characters = 0. These are UI-reported counts, not yet proof of complete/accurate import. Chat send returns `subscription_sharing_usage_limit_exceeded`, separate from local archive access. Do not ask the user to re-import the same ZIP or reset/delete their database.

Next stage: inspect `Db.java` and `ImportPipeline.java`; map stored conversation IDs, titles, timestamps, roles, message nodes, and provenance. Specify and implement a searchable imported-conversation archive with full ordered transcript viewing and a clear distinction between imported ChatGPT chats and ORGANISM sessions. Verify idempotency and preserve existing data. Investigate RAW metadata and replace the misleading character count with accurate file metadata. Build evidence and device validation must be recorded separately.

User requirement: document every meaningful development stage so a future assistant can resume with continuity. Follow `docs/WORKLOG_AND_CONTINUITY_PROTOCOL_v1.0.md` and append to `docs/WORKLOG.md`; also update this handoff at the end of each substantial session. Protocol commit: `b8cad3184875ed40229f06d45d17bbc9bbdbb15e`. Device-feedback report: `docs/DEVICE_FEEDBACK_2026-10-09_CHAT_ARCHIVE_GAP.md`. Latest work-log commit before this handoff update: `b9e631f5591927f8cfb44150e50cd5a09c6b973a`. No application code was changed during these documentation updates; no new build was run.


## 2026-10-09 — Universal project entry instructions added

- Added root `PROJECT_INSTRUCTIONS.md` in commit `0780db0a81e003bc3cdbf99050e6b1b27bfb9868`. It is a concise entry point for any assistant/chat: working style, where to look first, invariant “one memory, different connectors”, data-safety rules, current imported-chat archive/search priority, and mandatory work-stage logging.
- Work-log entry added in commit `5493bb4dddccb0672dd1a4e2f89c96483f265cab`.
- Important limitation: repository file creation does not automatically modify ChatGPT Project Settings. If the user wants these rules available from any chat in the Project, copy the contents of `PROJECT_INSTRUCTIONS.md` into the Project Instructions UI. The repository copy is durable and should be maintained as the source of truth.
- No app code changed; no build/device test performed in this documentation stage.
- Next: inspect the current database/import pipeline and implement a usable imported-conversation archive/search without resetting existing data.


## 2026-10-09 — Progress tracking and platform-first preference

- User explicitly requests ongoing comparison of what is done vs. remaining work at each session, and a preference for using the official platform web UI in an in-app browser/WebView instead of API where feasible.
- Updated root `PROJECT_INSTRUCTIONS.md` in commit `84526cc52919d487e42602c8994d4e2f69170c0e`: mandatory planned/implemented/verified/device-confirmed tracking; platform UI first where safe, permitted, and reliable; no bypassing authentication/security/usage limits; no silent API fallback; connector must be tested before being called functional.
- Logged the stage in `docs/WORKLOG.md` commit `8c4b989fc99270eaeb96e7fabc38cd1b0d1e0d4d`.
- This is a documentation change only. Platform WebView connector is not implemented or verified. Existing issue `subscription_sharing_usage_limit_exceeded` must not be bypassed.
- Next ordered work: (1) inspect Db/import pipeline and implement archive/search without data loss; (2) inspect existing connector code and assess a supported official-platform WebView flow, documenting capabilities and limits; (3) run exact-commit CI and device checks and record each result separately.


## 2026-10-09 — Archive/search code stage

- Code: `aa35dfd53a9e489ba2fcc8624c3efd646abaccad` adds imported conversation search and transcript viewing; `fc3acbe57d745de04953b55cac8e5035d0357f1c` fixes duplicate conversation-source creation during ZIP parsing.
- Search matches conversation titles and message content. The archive screen opens a selectable/copyable transcript; limits are 500 search results and 3,000 messages per transcript view.
- Work log: `a2f3c00e973f9d44e544c017e01dd21dd5d448fe`.
- CI run `37943562566` is queued for `fc3acbe57d745de04953b55cac8e5035d0357f1c`: https://github.com/alexbotleh-oss/organism/actions/runs/37943562566. Build and device behavior are not yet verified.
- No user database was reset or migrated. Any duplicate rows from a prior version remain untouched; do not re-import or clean them automatically.
- Next: review CI result, fix failures, then validate the archive on the existing Android installation without re-importing the ZIP. Investigate RAW ZIP metadata and supported official-platform WebView interaction after this check.
