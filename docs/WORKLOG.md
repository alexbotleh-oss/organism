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
