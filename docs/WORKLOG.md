# ORGANISM — chronological work log

Entries are append-only. Newest entries are added at the end. See [WORKLOG_AND_CONTINUITY_PROTOCOL_v1.0.md](WORKLOG_AND_CONTINUITY_PROTOCOL_v1.0.md) for required evidence and handoff rules.

## 2026-10-09 — WL-001 — Establish continuity protocol

- **Lifecycle:** STARTED → CHANGED. Repository documentation change; verification of the app is not implied.
- **Goal:** preserve project context and prevent future assistants from behaving like a new, contextless developer.
- **Baseline:** repository `alexbotleh-oss/organism`; branch `fix/zip-import-crash-and-archive-restore`; prior handoff recorded head `258d0edd70d7fc5a30c78a3ddbafb633c85c61c5`.
- **User requirement:** comment/document all stages of work in the repository so a future assistant can resume with the same continuity and established decisions.
- **Change:** added `docs/WORKLOG_AND_CONTINUITY_PROTOCOL_v1.0.md` in commit `b8cad3184875ed40229f06d45d17bbc9bbdbb15e`. It requires stage lifecycle, baseline SHA, changed files, exact test evidence, failures, data-safety status, current status, and one concrete next action.
- **Verification:** GitHub Contents API returned commit SHA `b8cad3184875ed40229f06d45d17bbc9bbdbb15e`. No app build or device test was run as part of this documentation-only stage.
- **Status:** PARTIAL — protocol created; this work-log entry and handoff update still need to be committed.
- **Data safety:** no application code or user database changed.
- **Next:** add this chronological log and update continuity handoff with the user's real-device findings; then inspect implementation and plan the conversation archive/search feature before changing code.


## 2026-10-09 — WL-002 — Record real-device archive gap and continuity rule

- **Lifecycle:** CHANGED. Documentation-only stage; app behavior not changed.
- **User goal:** ensure every development stage is recorded so a future assistant can resume from repository evidence with continuity.
- **Starting point:** branch `fix/zip-import-crash-and-archive-restore`; protocol commit `b8cad3184875ed40229f06d45d17bbc9bbdbb15e`; initial work-log commit `3c8ae246eb444e8bdc9e97f1d0ec9269b8dbdc1a`.
- **Changes:** added `docs/DEVICE_FEEDBACK_2026-10-09_CHAT_ARCHIVE_GAP.md` in commit `b3553daa3f168c57fda94eef806b8dcccbd7db3b`. Recorded user screenshots and explicitly separated observations from unverified assumptions.
- **Key finding:** current app shows counts for 74 conversations / 10,034 saved messages, but user cannot browse imported conversations or search their contents. ChatGPT message send shows `subscription_sharing_usage_limit_exceeded`. RAW character count shows 0 and needs investigation. Do not re-import the same ZIP; avoid data reset.
- **Verification:** protocol, work log, and device feedback file were fetched back from the branch and their contents/line counts confirmed. No app code changed; no build or device test was run in this documentation stage.
- **Status:** PARTIAL. Process protocol and device feedback are committed. The existing continuity handoff still needs a concise update reflecting the new archive/search blocker and latest docs.
- **Data safety:** no application database or user data touched.
- **Next action:** inspect `Db.java` and `ImportPipeline.java`; design and document the imported-conversation browser/search against the current schema before implementation.


## 2026-10-09 — WL-003 — Add universal project entry instructions

- **Lifecycle:** CHANGED → VERIFIED (documentation only).
- **Goal:** give any assistant entering any ORGANISM project chat a short, clear guide to working style, source-of-truth files, product invariants, safety rules, and current priority.
- **Baseline:** branch `fix/zip-import-crash-and-archive-restore`; prior handoff update commit `713d121ef9f3be215675bb5522be70ef63b38e1b`.
- **Change:** added root `PROJECT_INSTRUCTIONS.md` in commit `0780db0a81e003bc3cdbf99050e6b1b27bfb9868`.
- **Contents:** working style and evidence honesty; ordered navigation to handoff/protocol/work log/feature notes; “one memory, different connectors”; data safety; current imported-conversation archive/search priority; required stage logging.
- **Verification:** GitHub Contents API confirmed file creation and commit SHA. No application code changed; no build or device test run.
- **Status:** DONE for repository entry document. It has not been automatically inserted into ChatGPT Project Settings; user may need to copy its contents into the Project Instructions UI. The tracked file remains the source of truth if that setting is unavailable.
- **Data safety:** no application database or user data touched.
- **Next action:** update the continuity handoff to point to `PROJECT_INSTRUCTIONS.md`, then resume source inspection for imported-chat browsing/search.


## 2026-10-09 — WL-004 — Require continuous progress checks and platform-first connector preference

- **Lifecycle:** CHANGED → VERIFIED (documentation only).
- **User requirement:** at every session, continuously compare what is done and what remains; where feasible, prefer the official ChatGPT/other AI platform web UI inside the app over API calls.
- **Starting point:** branch `fix/zip-import-crash-and-archive-restore`; prior project-instructions commit `0780db0a81e003bc3cdbf99050e6b1b27bfb9868`; latest instruction file fetched before edit.
- **Change:** updated root `PROJECT_INSTRUCTIONS.md` in commit `84526cc52919d487e42602c8994d4e2f69170c0e`. Added a mandatory planned/implemented/verified/device-confirmed progress check and platform-UI-first preference with explicit security, terms, reliability, usage-limit, and transparent-fallback constraints.
- **Verification:** GitHub Contents API returned the update commit SHA. No application code changed; no build or device test run.
- **Status:** DONE for documenting the preference and process. Platform UI connector itself is NOT IMPLEMENTED/NOT VERIFIED by this documentation change.
- **Data safety:** no application database or user data touched.
- **Next:** inspect current Android source and schema, then deliver imported conversation archive/search; in parallel or immediately after, inspect current connector implementation and assess a permitted official-platform WebView interaction path without bypassing access/usage controls.


## 2026-10-09 — WL-005 — Imported conversation archive/search

- **Lifecycle:** STARTED → CHANGED → PARTIAL; CI queued, not yet verified; device test not performed.
- **Goal:** make imported ChatGPT conversations browsable and searchable without resetting or rewriting the user's existing database.
- **Starting point:** branch `fix/zip-import-crash-and-archive-restore`; prior handoff commit `fa7b297bcdb3e9e8cf57397e9a1069c54422353d`. Inspected `Db.java`, `ImportPipeline.java`, and `MainActivity.java`; conversation messages are stored as `CHAT_MESSAGE` memory objects linked to a `CHAT_EXPORT_CONVERSATION` source.
- **Changes:** commit `aa35dfd53a9e489ba2fcc8624c3efd646abaccad` adds an imported-chat archive entry from the import screen and extra menu, searches conversation titles/message bodies, displays matching conversations and opens a selectable/copyable ordered transcript (up to 3,000 messages per opened conversation; 500 conversation results maximum).
- **Import integrity correction:** while tracing the archive query, found `parseChatJsonStream` created a conversation source before calling `parseChatConversation`, which creates its own source. This produced duplicate source records and empty archive entries. Fixed in commit `fc3acbe57d745de04953b55cac8e5035d0357f1c` so each parsed conversation gets its source from `parseChatConversation` only.
- **Verification:** both changed files were fetched back from GitHub. Android build workflow run `37943562566` is queued for exact commit `fc3acbe57d745de04953b55cac8e5035d0357f1c` (https://github.com/alexbotleh-oss/organism/actions/runs/37943562566). No green build result yet. No device test performed.
- **Risks/limits:** existing DB is not reset or migrated by these changes. Previously imported duplicate source rows, if already present in the user's database, are not deleted or automatically deduplicated; preserve them until a safe, auditable repair plan is designed. Transcript ordering currently follows insertion ID, which is the parser's import order; unusual branching conversations need device/data validation.
- **Status:** PARTIAL — archive/search UI and duplicate-source creation fix committed; build pending; imported ZIP completeness and Android behavior unverified on device. Official-platform WebView connector remains unimplemented/unverified, and API subscription limits are not bypassed.
- **Next action:** wait for workflow run `37943562566`; if green, inspect the exact build artifact and logs, then validate search/open/transcript and data counts on the user's existing device database without re-importing the ZIP. If CI fails, fix the exact failure before distributing an APK.


## 2026-10-09 — WL-006 — CI verification for archive/search

- **Lifecycle:** VERIFIED (CI build only) → HANDED_OFF; device validation remains outstanding.
- **Commit tested:** `fc3acbe57d745de04953b55cac8e5035d0357f1c`.
- **Evidence:** Android build workflow `37943562566` completed successfully; job `assemble-debug` and step `Build debug APK` succeeded, then `Upload debug APK` succeeded. https://github.com/alexbotleh-oss/organism/actions/runs/37943562566. Second APK workflow `37943562807` also completed successfully; job `build` and APK upload succeeded. https://github.com/alexbotleh-oss/organism/actions/runs/37943562807.
- **Boundary:** this proves CI compilation and artifact upload for the exact code commit, not that the feature works on the user's phone or that previously imported records are complete. No real-device test or database inspection was performed.
- **Data safety:** no user database touched; no archive re-import requested or performed.
- **Next:** obtain/inspect the generated APK artifact, then perform device validation using the existing database; do not claim device confirmation until the user sees the archive search and transcript work.


## 2026-10-09 — WL-007 — Keep empty duplicate rows out of archive UI

- **Lifecycle:** CHANGED; CI for this follow-up commit not yet observed.
- **Goal:** avoid showing empty duplicate source records in the new conversation browser if an older app build already created them.
- **Starting point:** application code commit `fc3acbe57d745de04953b55cac8e5035d0357f1c`, which passed CI; branch head before this patch was the documentation handoff commit.
- **Change:** commit `bd0653fb2195997cfb7df8915d6cac8ebd86fc41` adds `HAVING COUNT(m.id)>0` to the conversation archive query. It hides source rows that have no linked chat messages without deleting or changing any database records.
- **Verification:** fetched the file back from GitHub and confirmed the query predicate is present. No build result yet for this follow-up commit; no device test.
- **Data safety:** read-only query/UI change; no user records are modified or deleted.
- **Status:** PARTIAL. Previous commit build passed, but this additional UI change still needs its own CI result. Existing records are preserved.
- **Next:** confirm Android CI for `bd0653fb2195997cfb7df8915d6cac8ebd86fc41`; then test the updated APK on the existing phone/database without re-importing the ZIP.


## 2026-10-09 — WL-008 — CI verification after archive empty-row filter

- **Lifecycle:** VERIFIED (CI only) → HANDED_OFF.
- **Commit tested:** `bd0653fb2195997cfb7df8915d6cac8ebd86fc41`.
- **Evidence:** Android build run `37943950470` succeeded: https://github.com/alexbotleh-oss/organism/actions/runs/37943950470. APK workflow run `37943950487` succeeded, including `Build debug APK` and `Upload APK`: https://github.com/alexbotleh-oss/organism/actions/runs/37943950487.
- **Artifact:** `organism-debug-apk`, 11,345,011 bytes, SHA-256 `77b3ecc9d02a8a6c784b754cde53b0758af0b47a5ce7abb42a2b54ed0ce20f9c`, artifact ZIP URL https://api.github.com/repos/alexbotleh-oss/organism/actions/artifacts/11622002797/zip (GitHub authentication may be required).
- **Boundary:** CI confirms build and artifact upload only. No on-device validation or database inspection was performed.
- **Data safety:** no user data changed; empty source rows are hidden from archive results, not deleted.
- **Next:** test this APK on the existing phone and database; validate search by title and message text, open/copy transcript, and verify imported conversation/message counts without re-importing the ZIP.


## 2026-10-09 — WL-009 — OAuth browser return to ORGANISM

- **Lifecycle:** STARTED → CHANGED; CI/device verification pending.
- **Goal:** address the user's report that OAuth completes in the browser but the browser remains open/loading and the user must manually switch back to ORGANISM.
- **Starting point:** branch \`fix/zip-import-crash-and-archive-restore\`; preceding application changes were committed as \`7996c271ce66027e9f728be54ab1f7970b260595\` (callback response) and \`4005260fb17dee451caabd5c372146b03c44af1c\` (activity reuse).
- **Evidence from source:** the loopback callback handler returned a static page instructing the user to return to the app, but did not attempt to launch it. This matches the reported manual-switch step; it does not by itself prove why the browser's loading indicator remained visible.
- **Changes:** callback page now attempts an Android package-targeted launcher intent after showing a clear completion message, and provides a visible fallback button if automatic launch is blocked by the browser. MainActivity uses \`singleTask\` so the existing activity/task is reused rather than needlessly creating a second screen.
- **Verification:** GitHub Contents API writes succeeded for \`MainActivity.java\` (commit \`7996c271ce66027e9f728be54ab1f7970b260595\`) and \`AndroidManifest.xml\` (commit \`4005260fb17dee451caabd5c372146b03c44af1c\`). No Android build result has been observed for this change yet. No device test has been performed.
- **Known limitation/risk:** Android browsers may block automatic external-app launch; the page therefore includes a manual return link. The OAuth callback/token-exchange flow itself has not been changed. Browser spinner behavior and end-to-end OAuth success still require testing on the user's phone.
- **Data safety:** no app database, stored credentials, or user data was reset or migrated.
- **Status:** PARTIAL — implementation committed; CI and device behavior unverified.
- **Next:** confirm the exact branch head and CI result, inspect build logs/artifact, then test sign-in from browser through return to ORGANISM on device. Confirm successful token exchange and that no duplicate MainActivity screen appears.


## 2026-10-09 — WL-010 — OAuth callback observability plan from device evidence

- **Lifecycle:** STARTED → HANDED_OFF; diagnostic instrumentation not implemented yet.
- **User goal:** stop inferring the OAuth failure from screenshots. Add an on-device diagnostic journal that records what ORGANISM sends, what it receives, and the exact stage where the browser/app handoff stalls.
- **Starting point:** branch `fix/zip-import-crash-and-archive-restore`; known OAuth-related commits `7996c271ce66027e9f728be54ab1f7970b260595` (callback page launch attempt) and `4005260fb17dee451caabd5c372146b03c44af1c` (MainActivity reuse). The previous handoff marked CI/device verification pending.
- **New device evidence:** screenshot at `auth.openai.com` shows the ChatGPT plan-sharing authorization screen with its primary action still showing a spinner. User reports the browser does not return automatically; manually switching back to ORGANISM makes it report successful connection. This indicates a lifecycle/observability gap but does not by itself identify the failing step.
- **Decision:** instrument the existing OAuth path before making another speculative behavioral change. The diagnostic view/log should capture timestamp, stage/event, direction (app→browser / browser→callback / callback→app / token exchange), safe status/result, callback arrival, timeout/error, and activity resume/deep-link events. Record redacted metadata only; never log authorization codes, access/refresh tokens, cookies, passwords, or full sensitive URLs. Do not log profile email or other unnecessary personal data.
- **Acceptance evidence:** one attempt produces an ordered trace showing launch request, callback listener state, callback arrival or timeout, state/PKCE validation result (boolean/status only), token-exchange outcome, persisted connection state, and app foreground/resume event. The screen must make the last successful stage and first missing/failed stage obvious and allow the user to copy/export the diagnostic trace.
- **Verification:** no code changed for this instrumentation request yet; no build or device test performed. Existing database and saved authentication state were not touched.
- **Status:** PARTIAL / instrumentation pending. Do not describe the OAuth fix as verified.
- **Next:** inspect the latest branch head, Android manifest, OAuth callback/server code and auth state persistence; then implement redacted stage-by-stage diagnostics, add focused tests, update this handoff and build CI. Test one real authorization attempt on the phone and use the captured trace to locate the fault before changing the OAuth behavior again.


## 2026-10-09 — WL-011 — Redacted OAuth diagnostic trace

- **Lifecycle:** IMPLEMENTED / CI PENDING / DEVICE TEST PENDING.
- **Starting point:** `fix/zip-import-crash-and-archive-restore` at `c201c730604575bd799971accff486ff0e51bb22`; OAuth implementation in `app/src/main/java/com/organism/app/MainActivity.java`.
- **Changes:** created `app/src/main/java/com/organism/app/AuthTrace.java`, a bounded local trace (maximum 120 lines) in existing app preferences; instrumented auth start, loopback listener readiness, browser launch request, callback wait/arrival, state check, code presence (boolean only), token exchange start/success, ID-token validation, scope grant/denial, credential persistence, app resume and safe failure class; added a settings dialog with copy and clear actions.
- **Privacy:** diagnostic entries do not contain OAuth codes, token values, cookies, passwords, raw callback URLs or response bodies. Error records use exception class/stage; the existing user-facing error behavior is unchanged. No DB or stored credentials are reset or migrated.
- **Commits:** `c3a41f38a82150c3e2789f61bc032e7a52706e66` (AuthTrace class); `0ccf0c0baef1d25546b0df94ef179a9bb93db62a` (MainActivity instrumentation/UI).
- **Verification:** source changes are committed. Android compilation, CI, APK artifact inspection and real-device behavior have not yet been verified. No claim of an OAuth fix is made; this change adds observability only.
- **Risk / follow-up:** token endpoint failures currently need a safe HTTP-status/error-code breadcrumb to distinguish service-side errors from transport/lifecycle failures; add only if source update can be applied safely. Then run Android CI and inspect the exact APK artifact. On-device, perform one authorization attempt and copy the diagnostic trace.


### WL-011 verification update — compile regression found and corrected

- First CI run for the diagnostic implementation failed at Java compilation: two literal `\\n` sequences had been inserted between method declarations in `MainActivity.java`.
- Corrected both separators in commit `78558c8b5bf67bb0dcd226b6a04f41dc06c53520`. This failure is recorded rather than hidden.
- A new Android build run was triggered for the corrected branch. Result remains pending at this update; inspect the final job result before calling the source buildable.


## 2026-10-09 — WL-012 — OAuth diagnostic APK build verified

- **Lifecycle:** VERIFIED (CI build/artifact only) → READY FOR DEVICE DIAGNOSTIC; OAuth behavior remains unverified.
- **Exact source commit:** `6d5fd748260fedaa5bff5fc9a66b82c6c5246a8b` on `fix/oauth-diagnostic-trace-20261009`.
- **Build evidence:** Android workflow run `37953256844` completed with conclusion `success`; job `assemble-debug`, step `Build debug APK`, and step `Upload debug APK` all succeeded. https://github.com/alexbotleh-oss/organism/actions/runs/37953256844
- **Artifact evidence:** `organism-debug-apk` is present, not expired, size 11,348,150 bytes. Artifact ZIP: https://api.github.com/repos/alexbotleh-oss/organism/actions/artifacts/11626284579/zip (GitHub may require authentication).
- **What this proves:** CI successfully compiled and uploaded a debug APK for the exact diagnostic branch head. It does not prove OAuth succeeds or that the diagnostic screen works on a physical device.
- **Known regression history:** earlier CI runs failed due to literal escaped newline separators in `MainActivity.java`; the separators were corrected in the source commit above and the exact commit now passes CI. The failures remain recorded in this log.
- **Data safety/privacy:** no database or saved user data was changed. Diagnostic trace is bounded to 120 lines and must not include authorization codes, tokens, cookies, passwords, raw callback URLs or response bodies.
- **Status:** APK artifact available; no device installation or login attempt has yet been confirmed.
- **Next action:** install the artifact on the user's phone, open Settings → “Журнал диагностики авторизации”, perform exactly one login attempt, then copy the trace back for analysis. Do not clear app data, re-import archives, or claim OAuth fixed before examining device evidence.


## 2026-10-09 — WL-013 — Add safe OAuth timeout/status breadcrumbs

- **Lifecycle:** CHANGED → CI PENDING → DEVICE TEST PENDING.
- **User evidence:** on the Android phone, the browser displayed `auth.openai.com` with `Operation timed out`. The trace from 18:46:57 records loopback listener readiness, browser launch request and callback wait, but no `CALLBACK_RECEIVED`; a foreground event occurred at 18:48:02. This establishes that the app had not recorded a callback by the time of that trace, but does not prove whether the platform page, network, browser handoff or callback delivery caused the timeout.
- **Starting point:** branch `fix/oauth-diagnostic-trace-20261009`, source commit `6d5fd748260fedaa5bff5fc9a66b82c6c5246a8b` (CI passed for that diagnostic APK); edited `app/src/main/java/com/organism/app/MainActivity.java` after fetching its current blob SHA.
- **Change:** commit `8396fc6b29661f14bbe80dd16fbb86f6a91b808f` adds a specific `CALLBACK_WAIT | TIMEOUT` entry when the loopback accept reaches its 120-second timeout, and records only HTTP status codes for token endpoint success/failure. Error bodies remain excluded from the diagnostic trace. No OAuth behavior, scopes, redirect URI, or credentials were changed.
- **Reason:** the supplied trace was captured before the 120-second local callback wait had expired, so it cannot distinguish a delayed callback from a final local timeout. Token HTTP status is useful only if the callback arrives and token exchange begins.
- **Verification:** source commit recorded; Android CI has not yet been polled for this exact commit. No device test has been performed on this new build.
- **Privacy/data safety:** no codes, tokens, cookies, passwords, full callback URLs or response bodies are added to the trace. No database, archive, stored credentials or app data was reset or migrated.
- **Status:** PARTIAL. This is additional diagnostic instrumentation, not an OAuth fix. The current evidence points to a missing callback at the time of capture; root cause remains unknown.
- **Next action:** confirm CI for commit `8396fc6b29661f14bbe80dd16fbb86f6a91b808f`; if successful, share the new APK artifact. Then one controlled device attempt should be allowed to finish (up to 120 seconds) and the full updated trace copied, including any `CALLBACK_WAIT TIMEOUT` or HTTP status event.


## 2026-10-09 — WL-014 — Mobile-first event observer decision

- **Status:** DECISION RECORDED; implementation NOT STARTED.
- **User-confirmed direction:** start on Android phone because it is always with the user; PC client or PC-hosted executor is future scope, not a prerequisite.
- **Goal:** reduce manual GitHub checking by delivering meaningful GitHub Actions state changes to the phone with task/branch/commit context.
- **Architecture:** separate Android client, event/state/memory layer, and replaceable GitHub observer/connector. Keep event contracts portable for a future PC client.
- **Initial scope:** relevant workflow completion/failure/cancellation and unavailable states; run link, branch and commit; event deduplication and durable history; never confuse CI success with device/feature verification.
- **Open choice:** compare webhooks plus a small receiver with periodic polling. Android background execution alone is not guaranteed always-on. Push delivery such as FCM is a candidate, not implemented or selected.
- **Safety:** observing/reporting is distinct from permission to edit, commit or perform destructive actions. No app code, database, credentials or user data changed. No tests/build/device validation were run for this decision.
- **Decision document:** docs/MOBILE_FIRST_EVENT_OBSERVER_DECISION_v0.1.md, commit eb190254ff1f25543175f13ba14baa7572568a3d.
- **Next:** check CI run 37955125073 for commit 8396fc6b29661f14bbe80dd16fbb86f6a91b808f, then define acceptance criteria and choose webhook vs polling before implementation.


## 2026-10-09 — WL-015 — OAuth success confirmed on Android device

- **Lifecycle:** VERIFIED (OAuth flow on user's device) → PARTIAL (visible browser completion/return UX still imperfect).
- **Goal:** use the latest redacted trace to identify the actual OAuth stopping point instead of inferring from the browser spinner.
- **Starting point:** branch `fix/oauth-diagnostic-trace-20261009`; diagnostic source commit `8396fc6b29661f14bbe80dd16fbb86f6a91b808f`; Android CI run `37955125073` passed and uploaded `organism-debug-apk` (11,348,423 bytes; SHA-256 `ea6cc90aa9e4c0d430063517fe9d413f62318b5ca5bb7dd85d5433d4f3dd029a`).
- **User device evidence:** user waited about three minutes and then switched away from the browser; ORGANISM reported that authorization had succeeded. Trace shows listener READY; callback received at 20:42:32.601; OAuth state matched; authorization code present (value redacted); token endpoint HTTP 200; token exchange success; ID token signature/issuer/audience/exp/nonce validation success; required scope granted; credentials saved locally; `AUTH_COMPLETE | SUCCESS` at 20:42:35.259.
- **Interpretation:** the OAuth authorization/code callback/token exchange/ID-token validation/persistence path completed successfully on the user's device. The supplied trace does not show an OAuth failure. Browser spinner/slow visual completion and the need to switch away remain a UX/handoff issue; this trace alone does not prove whether the browser page itself stopped loading, nor does it prove that a subsequent authenticated model request works.
- **Verification:** real-device OAuth flow confirmed by user-provided trace. This is distinct from CI; CI for the exact diagnostic commit also passed at https://github.com/alexbotleh-oss/organism/actions/runs/37955125073. No independent authenticated chat/model request was reported in this trace.
- **Data safety/privacy:** trace contains no exposed code/token values. No DB, imported archive, saved credentials, or app data was reset/migrated. OAuth scopes, redirect URI and auth behavior were not changed during this diagnostic step.
- **Status:** OAuth handshake and local credential persistence are device-confirmed for this attempt. Browser completion/return UX and post-authenticated-request behavior remain unverified.
- **Next action:** inspect the post-auth app state and authenticated-request path without clearing app data; separately reproduce/observe browser completion/return behavior only if it remains a user-facing problem. Do not alter the successful OAuth exchange speculatively.


## 2026-10-09 — WL-016 — Manual ChatGPT Web connector MVP

- **Lifecycle:** CHANGED → CI PENDING → DEVICE TEST PENDING.
- **User goal:** try the official ChatGPT website from ORGANISM instead of relying only on the current API-oriented send path, and make the prepared request inspectable so we can distinguish what ORGANISM prepared from what was actually sent.
- **Starting point:** branch `fix/oauth-diagnostic-trace-20261009`; OAuth handshake and credential persistence were device-confirmed in WL-015, but a subsequent API request displayed `subscription_sharing_usage_limit_exceeded`. The API send path uses `GET /models` and `POST /responses`; no evidence currently shows duplicate API sends for a single tap.
- **Changes:**
  - `11c27e21c8fbebceaf943999bdf57f6a0e961afa` — MainActivity adds an explicit ChatGPT Web preparation action and manual response import into the active session. The prepared event is marked `PREPARED_NOT_SENT`; response import is explicitly user-triggered and saved as `ChatGPT Web (manual copy)` with `NOT_VERIFIED` status.
  - `e4207b093489fede84263797fcd155e1d4a0e23d` — adds `PlatformWebActivity`, which opens `https://chatgpt.com/` in a WebView and offers a button to copy the prepared prompt.
  - `9c1f95d3d42c8c77e831ad9d8cf8f3572d0c940a` — registers the WebView activity as non-exported.
  - `2966d7efe2f78f5c61a27f3a33e9cb0fcaaf6344` — documents the MVP contract, limits, data handling, and acceptance checks in `docs/PLATFORM_WEB_CONNECTOR_MVP_v0.1.md`.
- **Connector boundary:** no JavaScript injection, DOM reading, automated Send click, cookie/session extraction, or credential access is implemented. The user must paste/send the prompt and copy/import the answer manually. This does not bypass usage limits. Embedded WebView authentication compatibility is unknown until device-tested.
- **Verification:** GitHub Contents API confirmed the source/doc writes. No Android build result has yet been checked for this code, and no physical-device test has been performed. Do not claim the WebView connector works until exact-commit CI and on-device checks pass.
- **Failure/regression risk:** login may be blocked or unsupported in an embedded WebView. If so, do not bypass platform controls; consider an official browser handoff while retaining manual copy/paste. The API connector remains unchanged.
- **Data safety:** no database, imported archive, OAuth credentials, or app data was reset or migrated. Pending prompt/context is held in app-private preferences until response import or replacement by another prepared prompt. The user explicitly triggers response import; it is stored as unverified model output.
- **Status:** PARTIAL — MVP source committed; CI/device verification pending.
- **Next action:** inspect the exact updated source and CI result. If build passes, install the resulting APK and test one non-sensitive prompt through ChatGPT Web; record whether sign-in works, whether clipboard copy/paste works, and whether one response is saved exactly once. Do not repeat API sends while the plan limit is exhausted.


## 2026-10-09 — WL-017 — Make prepared web prompt inspectable

- **Lifecycle:** CHANGED → CI PENDING → DEVICE TEST PENDING.
- **Goal:** let the user see the exact context-bearing text before manually sending it to ChatGPT Web, instead of relying on the clipboard alone.
- **Starting point:** WL-016 manual WebView connector MVP; activity already opened the official HTTPS site and supported explicit clipboard copy.
- **Change:** commit `d88a1eca21136b37fe8a25991d25d2d4fbb1b2ab` adds a selectable preview dialog for the exact prepared prompt and a copy action. This is read-only display of the prepared text; it does not send it or read the page.
- **Verification:** source fetched back from GitHub after update; combined commit status query returned no checks/statuses for this commit, so build verification is still pending. No device test has been run.
- **Data safety:** no database/archive/credential changes. Prompt includes selected Context Snapshot and user text, so the preview makes clear what the user is about to copy into ChatGPT Web. No automatic page interaction was added.
- **Status:** PARTIAL. Exact prompt preview implemented; compile and WebView/device behavior unverified.
- **Next action:** obtain a successful Android build for the latest branch commit; then install that exact APK and test the preview/copy/manual send/answer import flow once.


## 2026-10-09 — WL-018 — Corrected CI verification status

- **Status:** Android CI VERIFIED for latest branch head; device test PENDING.
- **Correction:** An earlier query returned no runs because the helper used only exposes pull-request-triggered runs. I incorrectly treated the empty result as no build. Direct inspection of the Actions run list shows the latest branch build succeeded.
- **Exact evidence:** run 37973127262; branch `fix/oauth-diagnostic-trace-20261009`; commit `69efeb2c32a575c43e336a348b8c73feb91963f5`; conclusion `success`. The `assemble-debug` job built and uploaded `organism-debug-apk`.
- **Artifact:** ID 11638116459; size 11,353,155 bytes; archive digest `sha256:ac28545249d4dfc4b73936e6993b5b818fbf18e9630921729f7d1067aee5737e`.
- **Failure history:** app commit `11c27e21c8fb` had a failed build (run 37972935277); later application commits and the latest head passed CI. Preserve this failure in the record.
- **Boundary:** CI proves compilation and artifact upload, not WebView login or phone behavior. No user data, database, archive, or saved credentials were changed.
- **Next:** user installs the artifact from the successful Actions run and tests WebView login plus manual prompt/response once. If embedded login is blocked, do not bypass platform protections; use official browser handoff.


## 2026-10-09 — WL-019 — Official browser fallback after WebView interaction failure

- **Lifecycle:** CHANGED → CI PENDING → DEVICE TEST PENDING.
- **User device feedback:** ChatGPT page loads in the embedded WebView and accepts typed text, but tapping the right-hand control does not send the message and appears to change/refresh the starter prompt; the left pale-brown control does not respond. This is evidence of broken/unusable interaction in the embedded page, not proof of the precise platform cause.
- **Starting point:** branch `fix/oauth-diagnostic-trace-20261009`; preceding branch head `0999e9f4bd6ee8168419548684102617ecd33ed3`; source `PlatformWebActivity.java`.
- **Change:** commit `4d30ca7a61edbbe3c6346b46368141e67d134df6` adds a visible **«Открыть в браузере»** button that opens the official `https://chatgpt.com/` URL using Android's normal external-browser intent, plus a short explanation that prompt/response transfer remains manual. Existing WebView, clipboard, preview, and API/OAuth code remain unchanged.
- **Reason:** provide a safe supported fallback when ChatGPT's own interactive controls do not work inside this embedded WebView. Do not inject JavaScript, click site controls, read DOM/cookies, or bypass account/usage limits.
- **Verification:** GitHub source update succeeded; file content was fetched back and includes the fallback button/handler. CI for this new commit has not yet been checked. No device test of the new button has occurred.
- **Data safety:** no database, imported archive, saved credentials, or app preferences were reset, migrated, or deleted. The external browser opens only the official HTTPS URL; the user must copy/paste manually.
- **Status:** PARTIAL. Source change committed; build and device behavior pending.
- **Next action:** confirm CI for the updated branch head. If successful, install the new APK over the existing app without clearing data; tap «Открыть в браузере» and try one harmless test message. Record browser behavior separately from CI.


## 2026-10-09 — WL-020 — Multi-provider web-first decision

- **User decision:** use provider web interfaces as the primary way to connect models; keep API as an optional parallel transport.
- **Initial provider list supplied by user:** Claude (`https://claude.ai/`), DeepSeek (`https://chat.deepseek.com/`), Qwen (`https://chat.qwen.ai/`), Gemini (`https://gemini.google.com/`), Yandex Alice (`https://alice.yandex.ru/`).
- **Spec updated:** `docs/PLATFORM_WEB_CONNECTOR_MVP_v0.1.md`, commit `db9fb17144762d2caaad7f5175c49e885901c06f`, records provider-independent memory/history, separate provider adapters, manual copy/paste safety boundaries, provenance/status states, and a phased rollout beginning with Claude + DeepSeek.
- **Implementation status:** no multi-provider code or UI implemented in this step. This is an architecture/continuity decision only. All five providers' device compatibility and sign-in flows remain untested.
- **Build status at this point:** latest Android CI run discovered in Actions list is run `37977490305`, commit `e872fa9b8eaecc4a8291ce9eef5ff4af7db76de7`, conclusion `success`; that build predates this documentation-only decision commit and includes the external-browser fallback source change. Device verification remains pending.
- **Safety:** existing API/OAuth code, saved credentials, imported history, database and app preferences unchanged. No site automation, DOM/cookie access or limit bypass.
- **Next step:** after the user validates the current browser fallback build, implement provider registry + provider picker and the shared manual web connector for Claude and DeepSeek first; add the other three through the same interface after the first vertical slice passes build and device checks.

## 2026-10-09 — WL-021 — Imported-chat search navigation

- **Lifecycle:** STARTED → CHANGED → CI PENDING → DEVICE TEST PENDING.
- **Baseline:** branch `fix/oauth-diagnostic-trace-20261009`; pre-change app source at `c8834abf0573db688cff4b0db5bdbb1f6b40963e`; baseline branch head before this stage `fe98bbad140411e3894bd4fe3a825d8978a3eae5` (acceptance-check documentation).
- **Observed gap from user feedback:** imported conversations were reported as difficult to find/open. Code review found an archive/search screen already existed, but search results displayed only conversation title/count and opened a long transcript from its beginning.
- **Spec/acceptance:** `docs/DEVICE_FEEDBACK_2026-10-09_CHAT_ARCHIVE_GAP.md` updated in commit `fe98bbad140411e3894bd4fe3a825d8978a3eae5` with search/snippet/jump-to-match acceptance checks.
- **Code change:** commit `e670155838794084939eba546a225b705cdde541` updates `MainActivity.java`: results now include a short matching-message snippet; selecting a search result opens the paginated conversation view on the 100-message page containing the first match; empty-query archive opens at page 1. Search text remains bound through SQL parameters.
- **Static review:** read back the modified source and confirmed query bindings and page-offset navigation. No local Gradle test was run in this tool session. GitHub Actions run `37980569567` was `in_progress` at last check: https://github.com/alexbotleh-oss/organism/actions/runs/37980569567.
- **Data safety:** read-only UI/query changes only. No schema migration, imported record rewrite, archive re-import, database reset, RAW deletion, or credential change.
- **Known limits:** title-only search opens page 1 because there is no matching message body; large result sets remain capped at 500; page navigation and SQL runtime behavior still need CI and device verification.
- **Status:** PARTIAL — source committed, build not yet verified, no device test.
- **Next action:** verify Actions for exact code SHA `e670155838794084939eba546a225b705cdde541`; if green, inspect artifact availability and test on phone with title-only/body-only/no-match and a body match beyond the first 100 messages. Do not claim device success from CI.

## 2026-10-09 — WL-022 — Restore Android WebView file selection

- **Lifecycle:** STARTED → CHANGED; CI and device verification pending.
- **User-observed symptom:** in ChatGPT Web inside ORGANISM, tapping the attachment control never opened Android's file picker even after waiting. The user successfully attached the screenshot later from a normal browser, so the in-app WebView file chooser is the scoped issue.
- **Baseline:** branch `fix/oauth-diagnostic-trace-20261009`, prior head `34c12bb7d8593472a68275e3e3b671a701a4b2dc`; file `app/src/main/java/com/organism/app/PlatformWebActivity.java`.
- **Change:** commit `0a37c6785645bdb80a2b855a2235526f2116e749` overrides `WebChromeClient.onShowFileChooser()`, launches the chooser intent provided by WebView, returns selected URI(s) using `FileChooserParams.parseResult()`, cancels any previous pending chooser callback, and resolves a pending callback with null if the activity is destroyed. No broad storage permission was added.
- **Verification:** source change committed through GitHub and requires read-back plus Android CI. No local Gradle build or real-device attachment test has yet been run. This is a targeted implementation, not yet a confirmed fix.
- **Data safety:** no database, imported archives, chat history, credentials, or app preferences touched. The system picker grants access only to the selected attachment URI.
- **Status:** PARTIAL — implementation committed; compile/CI and device behavior pending.
- **Next action:** verify the exact commit's Android Actions run; if green, install that exact APK as an update without clearing app data and test image attachment, document attachment, and picker cancellation in ChatGPT Web.


## 2026-10-09 — WL-023 — Dedicated web composer layout

- **User feedback:** the embedded ChatGPT page works after the home internet connection was fixed, but the current screen is awkward on a phone: the site input is squeezed by the app's upper controls and Android keyboard. User asked to move toward the previously discussed single ORGANISM input.
- **Baseline:** branch `fix/oauth-diagnostic-trace-20261009`; file `app/src/main/java/com/organism/app/PlatformWebActivity.java`; attachment-picker fix is in the prior history.
- **Change:** commit `855a31ede1491fffe3e403b90cbed679a2d5b0f2` removes the large manual-mode notice/action strip/external-browser button from the top of the WebView screen and adds a compact header plus a dedicated multiline ORGANISM composer docked below the web page. Keyboard resize is explicitly requested. The existing prepared prompt is prefilled when provided. Pressing the composer action copies its current text and clearly tells the user that manual paste/send is still required.
- **Important boundary:** this is the first UI slice, NOT the completed unified conversation transport. It does not yet send into the site's DOM, detect completion, read the answer, or import responses automatically. No hidden web-page automation or session/cookie access was added. The previous external-browser fallback button is removed from this screen to make space for the single composer layout; official external browser remains available through Android/app navigation only if another route exists.
- **Verification:** GitHub source commit created; exact source read-back, Android CI, and device keyboard/scroll testing are pending. Do not call the unified input/send/receive flow complete.
- **Data safety:** no database, archive, OAuth credentials, or saved preferences reset. Existing prompt is only prefilled into the composer.
- **Status:** PARTIAL.
- **Next action:** inspect the exact source and build result; install on the existing app without clearing data and verify the composer stays visible above the keyboard, the web page remains scrollable, and attachment picker still opens. Then continue with a provider adapter design for supported send/response handling.


## 2026-10-09 — WL-024 — Respect Android safe areas in web composer

- **User report:** screenshot shows header/status elements and bottom composer/send button too close to system bars; the user cannot comfortably reach controls. Cookie dialog is rendered by ChatGPT's own website.
- **Change:** commit `08cb9dafdbc2eae918808886adfa310b178d5a6e` applies Android window insets to the root view, adds safer horizontal/bottom composer padding, and gives the send action a stable width with spacing.
- **Scope:** layout accessibility only. Does not change provider transport, site DOM, authentication, memory/archive data, or permissions.
- **Verification:** source committed. CI and on-device test still pending; do not call the layout fixed until tested on the user's device with system navigation and keyboard visible.
- **Next:** check exact commit's Actions build, then test that header and send button are fully visible/tappable in portrait and with keyboard open. Cookie consent remains a site-owned modal and must be dismissed on the page.

## 2026-10-09 — WL-025 — Make Android UI quality a project-wide release gate

- **User directive:** Android layout standards are mandatory for the entire app, like a spoon with soup: consistent margins, safe spacing top/bottom, usable button sizes, and proper composition must not be forgotten when designing APKs.
- **Official references reviewed:** Android Developers guidance on window insets and edge-to-edge layout; Android accessibility guidance recommends touch targets of at least 48×48 dp. Sources are listed in `docs/ANDROID_UI_STANDARD_v1.0.md`.
- **Changes:** `docs/ANDROID_UI_STANDARD_v1.0.md` added in commit `43ce4ef35b697cb60d0fce690841a402c8ffd847`; `PROJECT_INSTRUCTIONS.md` updated in commit `c8530c0b4d3a76e79aaf78a99a9cc763f81fc916` to make the standard mandatory before any Android UI work and any APK release.
- **Acceptance gate:** all screens must account for system bars/cutouts/keyboard, keep primary actions reachable, use 48×48 dp minimum touch targets (or verified equivalent), maintain consistent dp/sp spacing, support scrolling/adaptive layouts, and have recorded screen-by-screen checks. CI compilation is not UI verification.
- **Current UI defect context:** screenshot showed WebView header/system indicators overlapping visually and bottom composer/action too close to navigation area; exact device layout remains to be verified after the safe-area change. Cookie consent is site-owned.
- **Data safety:** documentation-only stage; no app data, database, imported archive, OAuth credentials, or preferences changed.
- **Status:** standard documented and linked from project entry instructions; not yet audited against every existing screen.
- **Next action:** inspect the latest UI commit's CI result, then audit all app screens against this checklist and fix the screen-level issues as one coherent UI pass before treating the APK as release-ready.


## 2026-10-10 — WL-026 — Recover and verify the WebView safe-area APK artifact

- **User goal:** provide the actual installable ORGANISM APK rather than another status-only response.
- **Starting point:** branch `fix/oauth-diagnostic-trace-20261009`; safe-area source commit `08cb9dafdbc2eae918808886adfa310b178d5a6e`. Earlier checks incorrectly treated empty commit status / PR-filtered workflow-run results as evidence that no build existed.
- **Investigation/correction:** queried workflow artifacts directly for recorded run IDs. Found run `37987076157`, job `assemble-debug` = `success`, artifact `organism-debug-apk` ID `11642818226`, tied to exact source SHA `08cb9dafdbc2eae918808886adfa310b178d5a6e`. Downloaded artifact and confirmed it contains `app-debug.apk` (11,460,069 bytes).
- **Artifact integrity:** extracted APK SHA-256 `ccadeea02297103d2f4f3c385a7f3b6d0910889e7fd09901f0e74d22376a3298`. The artifact is a debug build, not a signed production release.
- **Branch/source relation:** GitHub compare confirms three commits after `08cb9da` through `c8530c0` modify only `PROJECT_INSTRUCTIONS.md`, `docs/ANDROID_UI_STANDARD_v1.0.md`, and `docs/WORKLOG.md`; no Android app source changed after the APK's code SHA in that comparison.
- **Verification boundary:** CI build passed for the exact app source SHA. No physical-device/emulator usability test has been performed here; UI safe areas, keyboard reachability, file chooser, and other screens remain unverified against the mandatory UI checklist.
- **Data safety:** APK retrieval only. No app database, imported archive, credentials, preferences, or user data were modified.
- **Status:** APK recovered and checksum verified; device-level release gate remains PARTIAL.
- **Next action:** install over the existing app without clearing storage; verify launch, existing archive/session retention, WebView top/bottom safe areas with keyboard open, and attachment picker. Report any visible defect before calling it release-ready.


## 2026-10-10 — WL-027 — WebView attachment and overlapping composer investigation

- **Lifecycle:** INVESTIGATION; implementation and device verification pending.
- **User goal:** Preserve ORGANISM's in-app WebView and its planned control path, while fixing image attachment failures, UI lag/overlap, and touch conflicts between the ORGANISM composer and ChatGPT's native composer.
- **Starting point:** branch `fix/oauth-diagnostic-trace-20261009`; previously verified app-source SHA `08cb9dafdbc2eae918808886adfa310b178d5a6e`; current source file `app/src/main/java/com/organism/app/PlatformWebActivity.java` blob SHA `eb8c7810dc4905ced2b0dbcb6c17dfed3c89384f`. Latest known APK is a CI debug build only; not device-verified.
- **Observed device evidence (user screenshots):** ChatGPT's native composer is visible alongside a second fixed ORGANISM composer; site content and consent dialog compete with the fixed panel; some site controls are hard to reach. User reports a document attached successfully but a photo did not. This suggests a format/path-specific attachment issue but does not establish the exact failure point.
- **Source inspection:** `PlatformWebActivity` has a `WebChromeClient.onShowFileChooser` handler and returns `FileChooserParams.parseResult(...)` to the pending callback. It sets `WebSettings.setAllowContentAccess(false)`. The latter is a plausible reason selected `content://` URIs cannot be consumed by the WebView, but it is not yet confirmed by a device trace. The ORGANISM button only copies text to clipboard; it does not send messages or upload files.
- **Plan:** (1) allow only Android's explicitly selected content URIs through the WebView content provider path while keeping `file://` access disabled; (2) add file chooser cancellation/error diagnostics without logging sensitive paths; (3) redesign the ORGANISM composer as a collapsible/secondary control so the native ChatGPT composer remains unobstructed by default; (4) preserve a deliberate entry point for ORGANISM-prepared prompts and future approved automation; (5) build and install over the existing app, then test image/document attachment, cancellation, keyboard, cookie dialog, touch reachability, scrolling and responsiveness.
- **Safety/data:** no user data, app database, imported archive, credentials or preferences were modified during this investigation.
- **Verification:** source inspection and screenshots only. No code fix, CI build for a fix, or device confirmation is claimed by this entry.
- **Next step:** implement the narrow content-URI fix and responsive composer redesign; run CI and update this log/handoff with the exact resulting commit and artifact before requesting device validation.


## 2026-10-10 — WL-028 — WebView composer toggle

- Status: source change committed; CI and device verification pending.
- Branch: fix/oauth-diagnostic-trace-20261009.
- Code commit: 8c10635347d4fa97b3e6a83538d9b6db69b55a1b.
- PlatformWebActivity now enables WebView content access for user-selected Android content:// attachment URIs while keeping file:// access disabled.
- Added a visible header control to hide/show the ORGANISM composer and helper text; hiding also dismisses the keyboard. This is an interim debugging control, not native composer integration and not automatic send/receive.
- Photo failure remains unconfirmed; document-success/photo-failure report suggests checking format-specific behavior on device.
- No user database, chat history, imported archive, credentials, or preferences were modified.
- Next: verify CI on this code commit, retrieve its exact APK, then test image and document attachment plus hide/show behavior on the phone.

## 2026-10-10 — WL-030 — WebView teardown resource cleanup

- **Lifecycle:** CHANGED; CI/device verification pending.
- **Research:** Android Developers documents WebView as a Chromium-based component with native/renderer-process memory outside the app's Java heap; unmanaged instances can cause leaks and degraded device performance. Official teardown guidance explicitly removes WebView from its parent before `destroy()`. References: https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory and https://developer.android.com/topic/performance/memory/guide/webview-memory.
- **Source change:** commit `39a63b2c979e1eec0e80578b2720200b3cea9eaf` updates `PlatformWebActivity.onDestroy()` to remove WebView from its parent ViewGroup before stopping loading, detaching clients, destroying the instance, and clearing the reference.
- **Important limits:** This is a narrowly justified lifecycle cleanup, not proof of the user's reported browser slowdown root cause. Android may retain renderer/native cache memory after destruction; no device profile is available in this session. Avoid global `WebView.pauseTimers()` because the API pauses timers for all WebViews, not just this instance.
- **Existing controls:** Source already has a Show/Hide composer toggle and allows selected `content://` access while keeping `file://` access disabled. The user-visible APK previously tested may not contain the latest source; device behavior remains unverified.
- **Data safety:** no app data, cookies, history, archives, or credentials cleared or modified.
- **Verification:** source patch committed; no CI result or device test claimed yet.
- **Next:** run the Android CI workflow for this exact commit; if successful, distribute the resulting debug APK and test repeated WebView open/close, native browser responsiveness, photo/document attachments, and composer toggle on the user's device. If slowdown persists, collect Android Studio/Perfetto or `dumpsys meminfo` evidence rather than guessing.


## 2026-10-10 — WL-031 — Suspend embedded WebView when ORGANISM is backgrounded

- **Lifecycle:** STARTED → CHANGED; CI/device verification pending.
- **User goal:** reduce the reported lingering phone/browser slowdown after leaving or closing ORGANISM's embedded ChatGPT view.
- **Starting point:** branch `fix/oauth-diagnostic-trace-20261009`; source baseline `39a63b2c9791e1eec0e80578b2720200b3cea9eaf`; current branch head before this change `7faa1ca42be4cefbaf3b6f3b793b03515508a992`. Existing `onDestroy()` detaches, stops, and destroys WebView; composer toggle already exists; `setAllowContentAccess(true)` already allows user-selected `content://` URIs while `file://` remains disabled.
- **Change:** `PlatformWebActivity.onPause()` now calls the instance-scoped `webView.onPause()`, and `onResume()` restores it with `webView.onResume()`. This avoids using `WebView.pauseTimers()`, which is global and could affect unrelated WebViews. Source commit: `1ca67afd8f12c47ee2a1dd57ef60268672de5f4b`.
- **Rationale / limits:** this is a narrow lifecycle improvement intended to suspend WebView work while the Activity is backgrounded. It does not prove the renderer is the cause of device-wide slowdown, and it cannot guarantee that all Chromium/native memory is immediately returned. The existing teardown fix remains in place.
- **Verification:** source was read from GitHub before editing and updated through the Contents API. No CI result or physical-device test is yet confirmed for this exact source commit. The prior wrapper's empty workflow-run response is not evidence of a failed build; query Actions directly and inspect exact-run jobs/artifacts.
- **Data safety:** no application data, database, cookies, browsing history, or imported archive was changed. Reversible source-only change.
- **Status:** PARTIAL / NOT DEVICE-TESTED.
- **Next action:** inspect exact-commit CI and obtain a debug APK only if the build succeeds; then compare ordinary-browser responsiveness after opening/closing the embedded view. Separately investigate photo attachment with a device test; do not log selected file URIs or contents.


## 2026-10-10 — WL-032 — WebView composer usability

- Goal: unblock practical testing by preventing the ORGANISM composer from competing with ChatGPT's native composer.
- Branch: `fix/oauth-diagnostic-trace-20261009`. Source commits: `7589a870abeab2c799b9010be49cc21f1a3d3210` (composer hidden by default; toggle label corrected), then `b99ae679f996e47df8b2370bce1fa0f03b9cafee` (composer collapses and keyboard hides after copying prompt).
- Current flow remains explicit/manual: prepare prompt in ORGANISM → copy → paste and send in ChatGPT Web → copy answer → use “Вставить ответ из ChatGPT Web” in ORGANISM. Existing response import associates question and answer with the same turn/session. No DOM scraping, automatic send, or limit bypass added.
- Read-back: current source fetched after commits. CI run `38028105547` passed only for prior SHA `70915b0bc66972247dc98aa522e11b8b3e655167`; its APK does not contain these changes. CI for `b99ae679f996e47df8b2370bce1fa0f03b9cafee` and physical-device behavior remain unverified.
- Data safety: no database, archive, cookies, history, credentials, or preferences cleared or modified.
- Status: PARTIAL. Next: verify exact-head Actions build, then test the round trip, attachments, keyboard/touch layout, and WebView reopen behavior on the existing installation.


## 2026-10-10 — WL-033 — Record every new user requirement in repository work log

- **Lifecycle:** STARTED → CHANGED; documentation updated, enforcement in the development workflow remains to be demonstrated across future tasks.
- **User requirement:** “Все новые требования должны быть отражены в журнале работы над проектом в репозитории на который ссылаешься ты каждый раз” — every new requirement must be reflected in the project work log in the repository referenced during work.
- **Baseline:** branch `fix/oauth-diagnostic-trace-20261009`; the existing work log and project instructions were fetched before editing. Work-log blob SHA: `a5c7a367a660b43252e47974af3b6e892f64c7b2`; project-instructions blob SHA before this change: `6e77c60cbedf633b87cf2f3a2aa8bc85e8b7891b`.
- **Change:** add this entry and make the requirement a durable rule in `PROJECT_INSTRUCTIONS.md`. The rule requires updating the handoff too when priorities, acceptance criteria, safety constraints, or next steps change; a recorded requirement remains open until implemented and verified.
- **Verification:** source contents fetched from GitHub before editing. Post-write read-back is still required after the sequential documentation commits. No application code/build/device test was changed or run in this documentation-only stage.
- **Data safety:** no app data, database, imported archive, browser history, credentials, or user preferences modified.
- **Status:** PARTIAL until the work-log and handoff updates are read back and verified. This entry records the rule; it does not claim the process has already been enforced perfectly.
- **Next action:** update the continuity handoff with this requirement and the exact documentation commit chain; read back all changed files and confirm the final branch head.


## 2026-10-10 — WL-034 — Screenshot attachment still fails in the in-app ChatGPT WebView

- **Lifecycle:** STARTED / BLOCKED on reproduction details; no fix claimed.
- **User report:** “Пишу тебе с нашего приложения, добавление скриншота так и не работает” — the user is writing from ORGANISM and screenshot attachment still does not work.
- **Relevant history:** WL-022 records the WebView file chooser implementation (onShowFileChooser); WL-027 records a previous report that a document attached but a photo did not; WL-028 enabled WebView content access for selected Android content:// URIs while keeping file:// disabled. Those changes were not confirmed as fixing photo attachment on the physical device.
- **Baseline inspected:** branch fix/oauth-diagnostic-trace-20261009; app/src/main/java/com/organism/app/PlatformWebActivity.java currently uses FileChooserParams.createIntent(), startActivityForResult(), and FileChooserParams.parseResult(). It shows an error only if launching the picker throws; it does not distinguish picker cancellation, an empty URI result, and a successful selection in user-visible diagnostics. setAllowContentAccess(true) is already present. Manifest declares INTERNET and the FileProvider, but no broad storage permission.
- **Important uncertainty:** the report does not yet identify whether the picker fails to open, the image cannot be selected, or the selected image fails to attach/upload in ChatGPT. Do not assume the root cause and do not add broad storage permissions without evidence.
- **Acceptance:** from the existing install and existing data, open the ChatGPT attachment picker; select a screenshot; confirm it appears as an attachment preview and can be sent (or capture the exact error). Also test picker cancellation and a document as controls. Preserve the current session/database/cookies/history.
- **Data safety:** no app data, database, imported archive, credentials, browser session, or preferences changed during this inspection.
- **Status:** OPEN / NOT FIXED / NOT DEVICE-VERIFIED.
- **Next action:** establish the exact failure point with the user, then make the narrowest evidence-based fix; build the exact commit and test on the phone before marking resolved.
