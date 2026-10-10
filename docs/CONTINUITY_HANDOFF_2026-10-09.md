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


## 2026-10-09 — Mobile-first event observer decision (WL-014)

- User confirmed that Android phone is the first target because it is always with them; a PC client or PC-hosted executor is future scope.
- Architecture decision document: docs/MOBILE_FIRST_EVENT_OBSERVER_DECISION_v0.1.md (commit eb190254ff1f25543175f13ba14baa7572568a3d). Work-log entry: WL-014 (commit a997c0fb78f2800120f176569f474c0456f7c7e4).
- Proposed subsystem: event observer/dispatcher connects verified GitHub Actions state to ORGANISM task/memory context and Android notifications. Initial scope includes success/failure/cancelled/unavailable states, run link, branch/commit context, deduplication and durable event history.
- Keep Android client, event/state/memory model and GitHub connector separable for future devices. Do not build PC infrastructure now.
- Implementation decision still open: webhook plus receiver vs periodic polling; FCM/push is a candidate only. Android background execution alone is not a guaranteed always-on observer.
- Safety: observing/reporting does not grant automatic permission to edit code, commit or perform destructive operations. This is a documented direction only; no application code, DB, credentials or user data changed; no tests/build/device test performed for this decision.
- Current next step remains: check CI run 37955125073 for OAuth diagnostic commit 8396fc6b29661f14bbe80dd16fbb86f6a91b808f. Then define observer acceptance criteria and choose the initial GitHub monitoring method before implementation.


## Handoff update — OAuth success confirmed on device (2026-10-09)

- **Diagnostic build:** exact source commit `8396fc6b29661f14bbe80dd16fbb86f6a91b808f`; Android CI run `37955125073` passed and uploaded `organism-debug-apk` (11,348,423 bytes; SHA-256 `ea6cc90aa9e4c0d430063517fe9d413f62318b5ca5bb7dd85d5433d4f3dd029a`). Run: https://github.com/alexbotleh-oss/organism/actions/runs/37955125073.
- **User's real-device trace at 20:41–20:42:** callback received; OAuth state matched; authorization code obtained; token endpoint HTTP 200; token exchange succeeded; ID-token signature/issuer/audience/expiration/nonce validated; required scope granted; credentials saved locally; `AUTH_COMPLETE | SUCCESS` recorded at 20:42:35.259.
- **Confirmed:** this authorization attempt completed the OAuth handshake and persisted credentials on the phone. This is stronger than CI-only evidence and means we should not change the token exchange or redirect configuration speculatively.
- **Still open:** user reports the browser kept spinning and they switched away after roughly three minutes. The trace proves app-side OAuth completion, but does not prove why the browser page's visual loading state persisted. A subsequent authenticated model/chat request has not been demonstrated by this trace.
- **Data safety:** no database/archive reset, migration or credential deletion. Diagnostic log redacts secret values.
- **Work log:** WL-015 appended to `docs/WORKLOG.md`.
- **Next action:** inspect current post-auth UI and the authenticated-request path without clearing app data. Treat browser spinner/return UX as a separate issue; preserve the successful OAuth path unless new evidence identifies a defect.


## Handoff update — Manual ChatGPT Web connector MVP (2026-10-09)

- **Starting evidence:** WL-015 confirms the Android OAuth callback, token exchange, ID-token validation, and local credential persistence completed successfully. The user's chat request then displayed `subscription_sharing_usage_limit_exceeded`. Source inspection shows the current API chat action calls `GET /models` when loading/selecting models and `POST /responses` when the user taps Send. No evidence currently proves duplicate POSTs or an automatic retry loop.
- **User decision:** try the official ChatGPT web interface as a separate connector path to inspect the prepared prompt and determine whether platform-UI interaction behaves differently.
- **Implemented, not yet verified:** commits `11c27e21c8fbebceaf943999bdf57f6a0e961afa` (MainActivity controls/preparation/manual response import), `e4207b093489fede84263797fcd155e1d4a0e23d` (PlatformWebActivity), `9c1f95d3d42c8c77e831ad9d8cf8f3572d0c940a` (manifest), and `2966d7efe2f78f5c61a27f3a33e9cb0fcaaf6344` (MVP specification). Work-log record: WL-016 in commit `80b442b9b59e0b055d05019c0e0353b3bfb5282f`.
- **Flow:** write a prompt in ORGANISM → prepare a context-bearing prompt and mark it `PREPARED_NOT_SENT` → open `https://chatgpt.com/` in WebView → user taps Copy, pastes and sends manually → user copies the answer → user returns to ORGANISM and explicitly imports the clipboard response. The imported output is saved as `ChatGPT Web (manual copy)` and remains `NOT_VERIFIED`.
- **Important boundary:** this MVP does not inject JavaScript, read the WebView DOM, click Send, extract cookies/session credentials, or bypass platform protections or plan limits. The app cannot independently prove that the exact prepared text was sent; it records preparation and user-confirmed response import separately.
- **Verification status:** source and docs writes are committed, but exact-commit Android CI and physical-device behavior have not yet been checked. Embedded WebView authentication may be restricted; if blocked, use an official browser handoff rather than bypassing controls.
- **Data safety:** no database/archive reset, migration, or credential deletion. Pending prompt/context is kept in app-private preferences until response import or replacement by a new prepared prompt. Do not test by re-sending API prompts while the current usage limit is exhausted.
- **Next concrete action:** inspect/read back the exact changed source files and check CI for the latest branch head. If green, install that exact APK and test WebView login, prompt copy/paste, answer copy/import, and one-time persistence on the existing installation. Record build evidence separately from device results.


## Handoff addendum — Exact prepared prompt preview (2026-10-09)

- Latest application change: `d88a1eca21136b37fe8a25991d25d2d4fbb1b2ab` adds a selectable preview of the exact prompt/context prepared for ChatGPT Web before the user copies it.
- Latest documentation record: WL-017, commit `7977d52d473db78e97c913661866a94f7bfbc9e5`.
- A combined GitHub commit-status query returned no status checks for the latest application commit. This is **not** evidence of a successful build; CI remains pending/unobserved.
- **Next:** confirm an Android build for the latest branch head. If no automatic workflow is running, inspect the repository's workflow configuration and trigger/use the existing build process rather than claiming success. Then test on the existing phone without clearing data.


## CI correction — ChatGPT Web connector (2026-10-09)

The prior handoff said CI was pending because the first status helper returned no checks. That conclusion was incorrect: the helper's workflow-run query only includes pull-request-triggered runs. Directly querying the repository Actions run list found successful push builds.

- Latest successful build run: 37973127262, branch `fix/oauth-diagnostic-trace-20261009`, commit `69efeb2c32a575c43e336a348b8c73feb91963f5`.
- Artifact: `organism-debug-apk`, ID 11638116459, size 11,353,155 bytes, archive digest `sha256:ac28545249d4dfc4b73936e6993b5b818fbf18e9630921729f7d1067aee5737e`.
- Earlier app commit `11c27e21c8fb` had a failed build (run 37972935277); later changes corrected the source and the latest branch-head build passed.
- **Current truth:** code compiles and the debug APK artifact was uploaded. This does not prove WebView login/clipboard flow on a real phone.
- Work-log correction: WL-018, commit `a396d01f4373a0b5bd42558a7083cdcb554b2c45`.
- **Next:** download the artifact from the successful Actions run and install it as an update without clearing app data. Test the WebView login and manual prompt/response flow once. If embedded WebView login is blocked, stop and use an official browser handoff rather than bypassing platform controls.


## Handoff update — ChatGPT Web controls unusable in embedded WebView (2026-10-09)

- **Latest user observation:** typing works inside the embedded ChatGPT page, but tapping the right-side control does not send and appears to change/refresh the starter question; the left pale-brown control does not respond. The exact cause is not yet established.
- **Source change:** `4d30ca7a61edbbe3c6346b46368141e67d134df6` adds an explicit «Открыть в браузере» action in `PlatformWebActivity.java`, opening the official `https://chatgpt.com/` URL via Android's external-browser intent. Existing in-WebView controls remain available; prompt/response copy-paste remains manual.
- **Work log:** WL-019 appended in commit `bfe5581891d5982a53d820285e7415c8ea649403`.
- **Verification:** source update and read-back succeeded. CI for the app change is not yet verified; the fallback button has not been tested on the phone.
- **Safety:** no database, imported chat archive, app preferences, OAuth credentials, or user data was cleared or migrated. No JS injection, DOM/cookie reading, automated Send action, or usage-limit bypass was added.
- **Current status:** PARTIAL — fallback implemented in source; build/device test pending.
- **Next action:** inspect Actions for the latest push build. If green, install the artifact as an update without clearing app data, test «Открыть в браузере», then manually send one harmless test message in the official browser and report the result.


## Handoff — Web-first multi-provider model connections (2026-10-09)

- **User's explicit decision:** connect models primarily through official web interfaces and retain API as an optional alternative. User-supplied URLs: Claude https://claude.ai/ ; DeepSeek https://chat.deepseek.com/ ; Qwen https://chat.qwen.ai/ ; Gemini https://gemini.google.com/ ; Yandex Alice https://alice.yandex.ru/ .
- **Spec:** `docs/PLATFORM_WEB_CONNECTOR_MVP_v0.1.md` updated in commit `db9fb17144762d2caaad7f5175c49e885901c06f` with provider-neutral architecture, manual transfer boundaries, provenance/status tracking, and staged implementation.
- **Work log:** WL-020 appended in commit `ac8793ae51b293dec9dbc1c73cffef3f3b31acea`.
- **Implementation is NOT done yet:** no multi-provider picker/adapters were added by the documentation commits. Existing API/OAuth connector remains untouched. No claims that the five providers work in WebView have been verified.
- **Latest known successful Android build:** run `37977490305`, commit `e872fa9b8eaecc4a8291ce9eef5ff4af7db76de7`, conclusion success; it includes the external-browser fallback button, but predates the multi-provider specification decision. Device test still pending.
- **Next implementation sequence:** (1) validate the fallback build on the phone without clearing app data; (2) implement provider registry + selection UI; (3) shared manual web connector flow for Claude and DeepSeek; (4) verify copy/import provenance and session isolation; (5) add Qwen, Gemini and Alice via the same contract; (6) preserve API as a separate selectable transport.
- **Safety constraints:** no DOM/script injection, cookie/session extraction, automated Send, usage-limit bypass, credential deletion, or user-data reset. A provider is not marked connected just because its landing page opens.

## Handoff — Imported chat search navigation (2026-10-09)

- **Latest code change:** `e670155838794084939eba546a225b705cdde541` (`MainActivity.java`). The existing imported-chat archive now shows a matching message snippet and opens the paginated conversation view at the 100-message page containing the first body match. Empty search opens page 1. User text remains SQL-bound.
- **Acceptance criteria:** appended to `docs/DEVICE_FEEDBACK_2026-10-09_CHAT_ARCHIVE_GAP.md` in commit `fe98bbad140411e3894bd4fe3a825d8978a3eae5`.
- **Work log:** WL-021 recorded in commit `2fc313611d8e326197c354841bf5a229e1df9ffb`.
- **Verification:** source read-back and static review completed. GitHub Actions run `37980569567` for code SHA `e670155838794084939eba546a225b705cdde541` was still in progress at the last check. No device test. The later docs-only work-log commit does not change app code.
- **Safety:** read-only archive UI/query update. No database schema or imported rows changed; no re-import, reset, credential change, or RAW deletion.
- **Known limits:** title-only match opens page 1; result count cap remains 500; Android SQLite runtime and on-device navigation are unverified.
- **Next action:** check run `https://github.com/alexbotleh-oss/organism/actions/runs/37980569567`. If successful, use the matching APK to test body search and a match beyond message 100, as well as title-only/no-match. Then record actual device feedback before moving to the multi-provider web connector.

## Handoff — WebView attachment picker (2026-10-09)

- **User report:** ChatGPT Web's attachment button inside ORGANISM did not open a file-selection window even after a long wait. The screenshot was successfully sent later from the normal browser, distinguishing this from a general inability to access files on the phone.
- **Implementation:** `0a37c6785645bdb80a2b855a2235526f2116e749` adds `WebChromeClient.onShowFileChooser()` and passes selected URI results back to WebView via `FileChooserParams.parseResult()`. Pending callbacks are cancelled safely; no broad storage permission was added.
- **Current branch:** `fix/oauth-diagnostic-trace-20261009`; the implementation commit is followed by the work-log commit. Check the live branch head and Actions before distributing an APK.
- **Verification boundary:** source change only so far. Android CI/build and actual device selection/cancellation are not yet verified. Do not tell the user it is fixed until device-tested.
- **Data safety:** no database or user data modified; no archive re-import or reset.
- **Next concrete action:** check CI for the latest head, then test selecting an image, selecting a document, and cancelling the picker on the existing app installation without clearing data.


## Handoff — Dedicated ORGANISM web composer (2026-10-09)

- **Latest UI source commit:** `855a31ede1491fffe3e403b90cbed679a2d5b0f2`, file `app/src/main/java/com/organism/app/PlatformWebActivity.java`.
- **User-confirmed context:** the normal browser worked after the home internet connection was fixed; the embedded site itself now works but its usable area is cramped by the large app controls and keyboard.
- **Implemented UI slice:** compact header, official ChatGPT Web in the central area, multiline ORGANISM composer at the bottom, keyboard resize mode, and prepared prompt prefill. Composer action copies the current text and explicitly states manual paste/send is still required.
- **Not implemented:** automatic insertion into ChatGPT's page, Send triggering, response-completion detection, answer retrieval/import, unified model history. Do not imply these are complete.
- **External browser button:** removed from this screen in favor of the composer. If external handoff is needed, add it as a compact overflow action rather than restoring a large strip.
- **Verification:** no CI result or on-device keyboard/attachment test yet for this commit.
- **Data safety:** no database/history/archive/credential reset or migration.
- **Next action:** inspect CI for the latest branch head; if successful, test composer/keyboard/scroll and attachment chooser on phone without clearing existing data. Then implement only provider interaction that can be supported and reliably verified.

## Handoff — Mandatory Android UI standard (2026-10-09)

- User explicitly requires Android design standards to apply across the **whole application**, not only the current WebView: safe margins, top/bottom spacing, usable button sizes, and correct composition are baseline requirements.
- New standard: `docs/ANDROID_UI_STANDARD_v1.0.md` (commit `43ce4ef35b697cb60d0fce690841a402c8ffd847`). Project instructions now require reading and applying it before every Android UI change/release (commit `c8530c0b4d3a76e79aaf78a99a9cc763f81fc916`).
- The standard is based on official Android guidance: handle window insets and edge-to-edge system bars, preserve reachability under keyboard/system UI, use at least 48×48 dp touch targets and consistent dp/sp spacing, test adaptive states, and keep build evidence separate from device verification.
- This is a durable project rule, not a claim that every existing screen has already been audited. The latest WebView safe-area code commit `08cb9dafdbc2eae918808886adfa310b178d5a6e` still needs its CI result and real-device validation.
- Next action: check CI for latest source/UI commit; then conduct a screen-by-screen audit of the app using the standard and consolidate necessary fixes into a coherent UI pass. Do not ship based only on compilation; do not reset or alter user data during UI work.


## Handoff update — APK artifact recovered (2026-10-10)

- **Correction to prior handoff wording:** the safe-area UI build was not missing. The earlier check used commit statuses and a workflow-run helper that only returns PR-triggered runs, so it missed the successful push build artifact.
- **Exact build evidence:** GitHub Actions run [37987076157](https://github.com/alexbotleh-oss/organism/actions/runs/37987076157), job `assemble-debug` completed successfully; artifact `organism-debug-apk` ID `11642818226`; artifact records source `head_sha=08cb9dafdbc2eae918808886adfa310b178d5a6e`.
- **Artifact contents:** `app-debug.apk`, 11,460,069 bytes. SHA-256: `ccadeea02297103d2f4f3c385a7f3b6d0910889e7fd09901f0e74d22376a3298`. This is a CI debug APK, not a signed production release.
- **Source alignment:** compare `08cb9da...c8530c0` reports only documentation changes (`PROJECT_INSTRUCTIONS.md`, `docs/ANDROID_UI_STANDARD_v1.0.md`, `docs/WORKLOG.md`), no app-source changes after the APK source SHA in that range.
- **Current status:** APK artifact recovered and downloaded; successful CI compilation verified for the exact app code SHA. Physical-device behavior and project-wide UI audit are still NOT TESTED. Do not call the app release-ready solely from CI.
- **Data safety:** artifact retrieval only; no app data, imported archive, database, credentials, or preferences changed.
- **Next action:** user installs APK over existing app without clearing storage; check launch and retained history, WebView safe areas and keyboard reachability, plus image/document selection and cancellation. Then record actual device findings and fix any blockers.


## Handoff update — WebView attachments and composer UX (2026-10-10)

- User confirmed the product requirement: do not remove ORGANISM's planned input/control path, but stop it from obstructing ChatGPT's native composer and attachment controls.
- User's screenshots show the ChatGPT site composer and a second fixed ORGANISM composer simultaneously. The cookie dialog also competes with the fixed footer; touch targets and usable viewport are compromised. User reports a document attached successfully but a photo did not.
- Source inspection found that `PlatformWebActivity` implements `WebChromeClient.onShowFileChooser` and forwards the selected URI result, but `WebSettings.setAllowContentAccess(false)` is set. This is a plausible contributor to image attachment failure, not a confirmed root cause yet.
- The ORGANISM "Отправить" button currently copies prompt text to the clipboard and explicitly does not send it automatically. Do not describe this as integrated send/receive.
- Work-log entry `WL-027` recorded in commit `49a3487cb5465bd9f19871227a19a7ae74e8fcf1`.
- Current stage is investigation/planned implementation. No code fix, new build, or device validation is claimed yet. No user data was altered.
- Next: implement narrowly scoped content URI access for user-selected attachments while retaining disabled `file://` access; add non-sensitive chooser failure/cancel diagnostics; redesign ORGANISM composer to be collapsible/secondary by default; preserve the control entry point; run CI; distribute only the exact built artifact; then device-test photo/document attachment and UI reachability.


## Handoff update — visible composer toggle (2026-10-10)

- Implemented in source commit `8c10635347d4fa97b3e6a83538d9b6db69b55a1b` on `fix/oauth-diagnostic-trace-20261009`.
- `PlatformWebActivity` now allows WebView content access for Android user-selected `content://` attachments; `file://` access remains disabled. This is a plausible attachment fix, not yet device-confirmed.
- Added a visible header button `Скрыть ввод` / `Показать ввод`. It hides/shows the ORGANISM composer and helper, and hides the keyboard when the composer is hidden. This allows debugging with the site's native composer unobstructed, while preserving an entry point to ORGANISM's own input.
- Not yet implemented: automatic insertion into the site's native composer, automatic send/receive, and a true overlay bound to the native composer. Current button still copies text to clipboard.
- CI build and device tests are pending; do not distribute or claim the fix works until the exact commit's build is retrieved and the phone confirms photo/document attachment and toggle behavior.
- Work-log entry WL-028 recorded in commit `f4d0dd575e4a4ee7c6ebf6a45196015b699057ed`.
- No user data or local app storage was modified.


## 2026-10-10 WebView performance handoff

Android Developers documents WebView as Chromium-based with native memory and renderer processes; memory pressure can affect other apps, and RSS may not immediately drop after destruction. Official cleanup guidance says to detach the WebView from its parent before destroying it. Narrow cleanup committed as 39a63b2c979e1eec0e80578b2720200b3cea9eaf; WL-030 recorded in 0d76a4eacc4078217a680d37ac2af8f876fca24f.

This does not prove the root cause of the user's phone slowdown; no profiler trace or device test is available. Do not use global WebView.pauseTimers() as a quick fix because it pauses timers across all WebViews. Existing source has the composer Show/Hide toggle and selected content URI access; behavior on the latest installed APK remains unverified. Next: confirm CI for the exact source commit, then test repeated open/close and browser responsiveness. If lag persists, collect Android Studio Profiler, Perfetto, or dumpsys meminfo evidence. Do not clear cookies, site data, archives, or app history for diagnosis.


## 2026-10-10 — WebView lifecycle follow-up

- Immediate user priority in this conversation: address reported sluggishness after leaving/closing ORGANISM's embedded ChatGPT WebView, plus the overlapping composer and image-attachment issue.
- Branch: `fix/oauth-diagnostic-trace-20261009`.
- Existing code already had a visible `Скрыть ввод / Показать ввод` toggle, `setAllowContentAccess(true)` for selected `content://` attachments, and `onDestroy()` cleanup that detaches/stops/destroys WebView. `file://` remains disabled. The exact photo failure is not yet reproduced.
- Added instance-scoped WebView lifecycle suspension: `onPause()` calls `webView.onPause()`; `onResume()` calls `webView.onResume()`. Commit: `1ca67afd8f12c47ee2a1dd57ef60268672de5f4b`. Work-log entry WL-031: commit `a55a845a0343fb8a67d08259cff00dffc4178cd8`.
- No use of global `WebView.pauseTimers()`; that could affect unrelated WebViews. No user data or site session was touched.
- **Verification boundary:** no confirmed CI build or device test for the new source commit yet. Do not present the lifecycle patch as a proven performance fix until an APK built from this change is tested on the user's phone. WebView may retain native/renderer memory after destruction; the symptom may also originate in the browser, Android System WebView, ChatGPT page, or device memory pressure.
- **Next action:** find the exact GitHub Actions run for source commit `1ca67afd8f12c47ee2a1dd57ef60268672de5f4b`; inspect job status and download a debug APK only if successful. Then test repeated open → hide composer → leave/close → use normal browser. If slowdown persists, gather device model/Android version and use a memory/renderer trace rather than changing experimental WebView flags blindly. Photo picker diagnosis remains a separate device-validation item.


## 2026-10-10 — WebView composer follow-up

Active blocker: reliable dialogue/capture between user, ChatGPT Web, and ORGANISM. Source commits: `7589a870abeab2c799b9010be49cc21f1a3d3210` hides the secondary composer by default; `b99ae679f996e47df8b2370bce1fa0f03b9cafee` collapses it and hides the keyboard after copying a prompt. Current source was read back.

Existing manual round trip: `MainActivity.startPlatformWeb()` prepares a context-bearing prompt; user copies it, pastes/sends in ChatGPT Web, copies the answer, then uses `MainActivity.importPlatformWebAnswer()`. The response importer stores question and answer under the same turn/session IDs. Automatic send/receive is not implemented and must not be claimed.

CI run `38028105547` passed only for prior SHA `70915b0bc66972247dc98aa522e11b8b3e655167`; current exact-head CI and device testing remain pending. No local DB, archives, cookies, history, credentials, or preferences were cleared.

Next: locate CI for `b99ae679f996e47df8b2370bce1fa0f03b9cafee`; if green, test its APK on the existing installation: manual round trip, photo/document attachments, keyboard/touch reachability, and close/reopen responsiveness.


## 2026-10-10 — New mandatory continuity rule: log every user requirement

- User directive: every new requirement must be reflected in `docs/WORKLOG.md` in this repository, not left only in chat context.
- Durable rule added to `PROJECT_INSTRUCTIONS.md`; chronological entry added to `docs/WORKLOG.md` as WL-033.
- Documentation commit chain so far: instructions `909f4f441eeeda2b3ed3995e5862fbbf053412ac`; work log `3fa227f175810a00566732f78c7cf9d9d2f0377b`.
- The rule applies to new requirements, corrections, constraints, and priority changes. Update this handoff as well when the requirement changes priorities, acceptance criteria, safety boundaries, or the next action. Keep each requirement open until implementation and verification evidence support closure.
- Verification status at handoff writing: write API returned commits; final read-back of all three files is the next required check. This is a documentation/process change only; no app code, build, or device test was performed.
- Data safety: no application/user data changed.
- **Next action:** read back `PROJECT_INSTRUCTIONS.md`, the end of `docs/WORKLOG.md`, and this handoff on the same branch; confirm exact final head and ensure WL-033 and the rule are present.


## Handoff update — screenshot attachment remains broken (2026-10-10)

The user reports from the ORGANISM app that adding a screenshot still does not work. This is an active blocker for practical in-app ChatGPT Web use. Work-log entry WL-034 was appended in commit 9065aba6291ba46e895583f5b150b037681f8ef4.

Source inspection of PlatformWebActivity.java confirms the file chooser callback exists, uses WebChromeClient.FileChooserParams.createIntent()/parseResult(), and selected content URI access is already enabled while file:// access stays disabled. The manifest has INTERNET and a FileProvider but no broad storage permission. Prior WL-027/WL-028 already noted photo-vs-document behavior and the tentative content-access fix; the user/device has not confirmed that fix works. Do not repeat the same unverified assumption or add broad storage permissions without evidence.

**Next step:** determine the precise failure point (picker does not open / screenshot cannot be selected / selected screenshot never appears as a ChatGPT attachment / upload or send fails), then implement a narrow fix, build the exact source commit, and test on the existing phone installation. Acceptance requires screenshot preview and successful send, plus picker-cancel and document-control checks. Do not clear app data, cookies, chat history, or database.

**Status:** OPEN / NOT FIXED / NOT DEVICE-VERIFIED. No app code or user data was changed in this stage; no new build or device test was performed.


## Handoff update — diagnostic instrumentation for attachment failures (2026-10-10)

The user explicitly rejected guessing by trial and error and expects ORGANISM to include useful diagnostics. Work-log entry WL-035 records this requirement and implementation.

Source commits on `fix/oauth-diagnostic-trace-20261009`: `e2b8f86695fe1745235abe6c9d6041644ae2ee56`, `db70f8189e63cf63e9b389eaeeacc63e6dbcd0cf`, and `6fe2a5716fc87cb425b8a8d159aca88f6ded1eec`. `PlatformWebActivity` now exposes a `Log` button and copyable report, keeps up to 80 timestamped events, and records WebView main-frame errors, chooser request/mode/accepted MIME patterns, picker launch outcomes, Android activity result/cancel/empty-result, returned MIME types, and lifecycle cancellation. The report includes Android/WebView versions where supported. It intentionally does not record selected content URI/path or file contents. `file://` remains disabled; no broad storage permission or data reset was added.

This instrumentation narrows the failure stage but cannot alone prove that ChatGPT displayed an attachment preview or completed upload. Exact-source CI and real-device validation are pending. **Next:** read back source/log/handoff, inspect exact CI build for the latest source SHA, obtain its artifact if successful, then test screenshot, document, and cancellation on the existing phone installation and copy the diagnostic report after reproducing the issue.

**Status:** PARTIAL / NOT RELEASE-VERIFIED / NOT DEVICE-TESTED. No user data was changed.
