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


## CI update — 2026-10-09

The archive/search code commit `fc3acbe57d745de04953b55cac8e5035d0357f1c` passed Android CI. Run `37943562566` successfully assembled and uploaded the debug APK: https://github.com/alexbotleh-oss/organism/actions/runs/37943562566. Independent run `37943562807` also passed and uploaded its APK: https://github.com/alexbotleh-oss/organism/actions/runs/37943562807.

This confirms CI build only. No Android device validation has happened yet. Next: inspect the uploaded artifact, then test archive search and transcript view against the existing installation/database without re-importing the ZIP. Do not automatically remove duplicate records from previous imports.


## Follow-up — filter empty source rows in archive

- Commit `bd0653fb2195997cfb7df8915d6cac8ebd86fc41` updates the archive query to list only conversation sources with at least one linked `CHAT_MESSAGE`. This prevents empty duplicate source records from older builds appearing as blank conversations; it does not delete or alter stored records.
- Work-log entry: `429ab25f9a4bdf2df13161631c9b370694b3fff8`.
- The prior app commit `fc3acbe57d745de04953b55cac8e5035d0357f1c` passed CI. This additional query change has not yet been build-verified. Next: check CI for `bd0653fb2195997cfb7df8915d6cac8ebd86fc41`, then validate on the phone without re-importing the ZIP.


## CI confirmation — follow-up archive filter

The latest app commit `bd0653fb2195997cfb7df8915d6cac8ebd86fc41` now has both successful CI workflows: Android build `37943950470` and APK build/upload `37943950487`. APK artifact `organism-debug-apk` is 11,345,011 bytes, SHA-256 `77b3ecc9d02a8a6c784b754cde53b0758af0b47a5ce7abb42a2b54ed0ce20f9c`. Artifact ZIP: https://api.github.com/repos/alexbotleh-oss/organism/actions/artifacts/11622002797/zip (may require GitHub authentication). Work-log evidence commit: `5f378d1f5bc34e0ad1dab78fe1d32f93072519ae`.

The build is verified; device behavior is not. Next action is install/test this build on the existing Android app/database without re-importing the ZIP, then record what is observed. No old rows were deleted.


## Handoff update — OAuth browser return fix (2026-10-09)

- **User goal:** after completing ChatGPT sign-in in the browser, return automatically to ORGANISM rather than leaving the browser open with a loading indicator.
- **Source finding:** the loopback callback handler listened on \`127.0.0.1\`, received the OAuth callback, and returned a static HTML page saying “Авторизация завершена. Вернитесь в приложение.” It contained no app-launch action. This is consistent with the manual return the user described, but does not independently explain the browser's persistent spinner.
- **Changes committed on \`fix/zip-import-crash-and-archive-restore\`:**
  - \`7996c271ce66027e9f728be54ab1f7970b260595\` — callback page attempts to launch the installed ORGANISM app via an Android intent and provides a visible fallback button.
  - \`4005260fb17dee451caabd5c372146b03c44af1c\` — MainActivity is marked \`singleTask\` to reuse the existing activity on return.
  - \`a4ef7e81d1f10bfc7a15d92e2b6808031ab2cbfd\` — append-only work-log record for this stage.
- **Planned vs implemented vs verified:** browser return attempt implemented; no CI result observed yet; no device test performed. This is not yet a confirmed fix.
- **Limitations:** Android browser policy may block automatic external-app launch, so the callback page has a manual return link. OAuth token exchange/validation logic was not altered. The reported \`subscription_sharing_usage_limit_exceeded\` remains a separate usage-limit issue and is not bypassed.
- **Data safety:** no database or saved credentials were reset, deleted, or migrated.
- **Next action:** check CI for the new branch head; if green, install the exact build on the existing phone and test browser → callback → app, verifying that the OAuth token exchange finishes and no duplicate activity appears. Record the device result separately.


## Handoff update — OAuth diagnostics first (2026-10-09)

- The user proposes the correct next step: instrument the phone so a diagnostic trace shows which commands/events the app initiates and which responses/events it receives, exposing the exact stage that stalls instead of guessing from screenshots.
- Screenshot evidence: the authorization page at auth.openai.com remains on a spinner after the user approves ChatGPT plan sharing. The user reports that manually switching back to ORGANISM then causes the app to show connected. This is evidence of a stalled/unfinished visible browser flow, not proof that the OAuth exchange itself succeeded or failed.
- Work-log: added WL-010 in docs/WORKLOG.md; commit fac44d4c29c17df6447789dc8a69c1f1cbd3216a.
- Implementation status: diagnostics UI/log has NOT yet been implemented, built, or tested on device. Do not claim otherwise.
- Instrumentation requirements: ordered timestamps and named stages for browser launch, callback listener readiness, callback arrival/timeout, state/PKCE validation status, token exchange outcome, persisted auth state, activity resume/deep-link events; redacted errors/statuses; copy/export trace. Never log auth codes, tokens, cookies, passwords, or unnecessary personal information.
- Next action: inspect current branch HEAD and the exact OAuth code path (AndroidManifest.xml, MainActivity, callback listener/server, token exchange and connection-state persistence); then implement diagnostics before changing behavior further. Add tests and verify CI for the exact commit. Have the user run one authorization attempt on the phone and share the trace; distinguish app/browser/callback/token-exchange failure from the separate subscription_sharing_usage_limit_exceeded service limit.
- Data safety: no database, imported archive, saved credentials, or current auth state was reset or migrated.


## Handoff update — diagnostic trace implementation (2026-10-09)

- Working branch: `fix/oauth-diagnostic-trace-20261009`, based on `fix/zip-import-crash-and-archive-restore`.
- Implemented a bounded, local, redacted trace in `app/src/main/java/com/organism/app/AuthTrace.java` and instrumented `MainActivity.java`. Settings now exposes “Журнал диагностики авторизации” with copy and clear controls.
- Trace stages include auth start, loopback listener ready, browser launch request, callback wait/arrival, state match status, code presence without its value, token exchange begin/success, ID-token validation, scope status, credential persistence, generic failure class/stage and activity resume.
- Privacy/data safety: do not log auth codes, tokens, cookies, passwords, full callback URLs or raw server response bodies. The trace is limited to 120 lines. No database, imported archive, saved credentials or authentication state was reset/migrated.
- Commits: `c3a41f38a82150c3e2789f61bc032e7a52706e66` and `0ccf0c0baef1d25546b0df94ef179a9bb93db62a`.
- Not yet verified: Android build/CI, APK artifact, and device test. This is observability instrumentation, not a confirmed authorization fix.
- Next: add/verify safe token-endpoint HTTP status breadcrumbs if possible; run Android CI on the exact branch head; inspect artifact and compile result; then install that build on the user's phone and run one login attempt. Use the copied trace to locate the first failed/missing stage before changing OAuth behavior.


## Verification checkpoint — OAuth diagnostic branch (2026-10-09)

- Branch head at this checkpoint: `126aafab640c066957104ee1620ec54665a15aee` (`ci(android): build OAuth diagnostic branch`).
- Android CI runs were triggered for the diagnostic branch; at the last poll, build jobs were still in progress. Do not report build success until the run reaches a final conclusion and the artifact is checked.
- Draft PR #11 targeting `main` was closed without merging because the source branch includes 45 commits / 10 changed files relative to `main`; avoid merging this broad history as part of the diagnostic task. The working branch remains based on the existing OAuth implementation branch.
- Next: poll run `37953051552` (Android build) and the companion Android APK workflow; inspect job conclusions and uploaded APK artifact. If compilation fails, fix only the concrete diagnostic-code issue, then rerun CI. If green, test the diagnostic screen and one real authorization attempt on the phone.


## Handoff update — OAuth diagnostic APK build passed (2026-10-09)

- Exact branch head: `fix/oauth-diagnostic-trace-20261009`, commit `6d5fd748260fedaa5bff5fc9a66b82c6c5246a8b`.
- Android CI run `37953256844` completed successfully, including `Build debug APK` and `Upload debug APK`: https://github.com/alexbotleh-oss/organism/actions/runs/37953256844.
- Artifact `organism-debug-apk` is available and not expired (11,348,150 bytes). Artifact ZIP URL: https://api.github.com/repos/alexbotleh-oss/organism/actions/artifacts/11626284579/zip; GitHub may require authentication.
- The earlier Java compilation regression caused by literal escaped newline separators was corrected; this exact commit is now green. Keep the failure history in the work log; do not rewrite it as if the first build passed.
- **Boundary:** this verifies CI compilation and upload only. The APK has not yet been installed/tested on a real phone; OAuth success is not established.
- **Next step:** install this exact artifact, open Settings → “Журнал диагностики авторизации”, perform one login attempt, copy the trace, and inspect the first missing/failed stage before changing OAuth behavior. Do not clear app data or touch the existing DB/archive. Never share a trace containing tokens, authorization codes, cookies or passwords; the implemented trace is intended to redact these.
- Detailed stage record: WL-012 in `docs/WORKLOG.md`.


## Handoff — OAuth timeout evidence and next diagnostic build (2026-10-09)

- Device trace supplied by user: browser at `auth.openai.com` displays `Operation timed out`. App trace shows loopback listener READY, browser launch REQUESTED, callback wait started at 18:46:57, no `CALLBACK_RECEIVED` in the supplied excerpt, and app foreground event at 18:48:02.
- Interpret narrowly: no callback had been recorded by the time of the excerpt. Because the app waits for 120 seconds and the excerpt ends around 65 seconds after launch, it does not show the final local socket timeout. Root cause remains unproven; do not guess that OAuth is fixed or change redirect/scopes speculatively.
- Commit `8396fc6b29661f14bbe80dd16fbb86f6a91b808f` on `fix/oauth-diagnostic-trace-20261009` adds a specific safe `CALLBACK_WAIT TIMEOUT` record and token endpoint HTTP status-only breadcrumbs. It does not log response bodies, secrets or full callback URLs and does not alter OAuth behavior.
- Work-log: WL-013. Existing DB, imported archive, saved credentials and auth state were not reset or migrated.
- **Current status:** source committed; CI for this exact commit pending; no device verification of this change.
- **Next:** check Android CI run for exact commit `8396fc6b29661f14bbe80dd16fbb86f6a91b808f`. If green, provide its artifact and perform one controlled attempt, allowing the complete 120-second wait; copy the full trace. Use the first terminal diagnostic event to decide whether the next investigation is callback delivery or token exchange.
