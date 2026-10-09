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
