# ORGANISM — project instructions for any assistant/chat

> Read this first when entering any new chat about ORGANISM. This is the short entry point; the repository and current handoff are the source of truth.

## Working style
- Work as a continuing project partner: warm, direct, practical, and consistent with recorded decisions. Do not restart from scratch or ask the user to repeat information already documented.
- Be honest about evidence. Clearly separate **done**, **planned**, **built**, **tested in CI**, and **tested on the user's device**. Never claim success without proof for the exact commit.
- Do not pretend to work in the background after a reply. Do useful work during the active session and leave a durable handoff before stopping.
- Ask only when a necessary decision is genuinely missing. Otherwise inspect the repository and proceed with the safest well-supported next step.
- Preserve failures, regressions, and the reasons for decisions. Do not hide or rewrite history to make progress look better.

## Where to look first (in this order)
1. `docs/CONTINUITY_HANDOFF_2026-10-09.md` — current state, decisions, blockers, and the next concrete step. Check the latest version/branch before acting.
2. `docs/WORKLOG_AND_CONTINUITY_PROTOCOL_v1.0.md` — mandatory process and safety rules.
3. `docs/WORKLOG.md` — chronological append-only history, including failures and verification evidence.
4. Relevant feature specifications, architecture notes, tests, source code, and GitHub PR/commit/Actions history. For the current imported-chat problem, also read `docs/DEVICE_FEEDBACK_2026-10-09_CHAT_ARCHIVE_GAP.md` and `docs/MEMORY_CONNECTOR_INVARIANTS_v0.1.md`.

Repository: https://github.com/alexbotleh-oss/organism
Current working branch recorded in the handoff: `fix/zip-import-crash-and-archive-restore`. Verify the actual latest head before editing; do not assume an old SHA is still current.


## Continuous progress control (required)
At the beginning of every work session and before choosing a new task:
1. Read the current handoff and the latest work-log entries.
2. Compare **planned → implemented → verified → device-confirmed**. Mark each item separately; do not infer completion from a commit or build.
3. Choose the single next step from the handoff, check current branch/head and related code/tests, and avoid repeating completed work.
4. At the end of every meaningful stage, update the work log and handoff with evidence, unresolved items, data risks, and the next step. Re-check the list at the start of the next session.

## Connector preference: platform UI first where feasible
The user's preference is to use the official ChatGPT (and other supported AI platforms) web interface in an in-app browser/WebView where permitted, rather than defaulting to API calls and token billing.
- Treat this as a design preference to investigate and validate, not as proof that the platform UI can be automated.
- First test what the official website supports in a normal authenticated WebView/browser and what its terms/security protections allow. Never bypass login, CAPTCHA, anti-automation controls, usage limits, or extract session cookies/credentials.
- Keep connector adapters replaceable and the memory core independent. Prefer the platform UI connector when it is supported and reliable; retain API or manual/browser interaction as explicit alternatives when the platform UI cannot provide a safe, permitted, reliable path.
- Do not silently send a request through the API when the user selected platform mode. Show which connector is active and explain any fallback and its cost/requirements before using it.
- Do not claim that a platform connector works until the exact flow has been tested. Subscription limits such as `subscription_sharing_usage_limit_exceeded` must be reported honestly, not treated as something to bypass.

## Product principles that must not drift
- **One memory, different connectors.** The canonical memory belongs to the user/ORGANISM, not to a model, platform, API key, browser session, or chat.
- Imported source conversations are not the same thing as extracted memory objects, and neither is the same thing as ORGANISM's own chat sessions. Keep them distinguishable and traceable to their source.
- Preserve RAW files and provenance. Treat extracted claims as unverified until there is evidence. Model agreement is not proof.
- Protect user data: never delete/reset/overwrite or migrate existing data without explicit permission and a recovery/rollback plan. Do not ask the user to re-import the same archive as a workaround while duplicate safety is unresolved.
- Platform subscription access and API access are separate. Do not imply that one bypasses the other's limits or that a browser/WebView connector works until it is verified and permitted.

## Current priority
The user tested the Android app and reports that imported ChatGPT conversations cannot be browsed or searched and old/new chat history is not usable. Screenshots show the UI reporting 74 conversations and 10,034 saved messages, but those counts alone do not prove completeness. Sending a message displayed `subscription_sharing_usage_limit_exceeded`. RAW characters shows 0 and needs code-level investigation; for a ZIP, use meaningful file metadata such as size/hash/status.

Next step:
1. Inspect current `Db.java` and `ImportPipeline.java`, mapping stored conversation IDs, titles, timestamps, message roles/order, and provenance.
2. Write acceptance checks, then implement a browsable imported-conversation archive, search across titles and message bodies, and ordered transcript view with navigation from search results.
3. Keep imported ChatGPT chats separate from ORGANISM-created sessions. Check import idempotency and preserve the existing database.
4. Build and test against the exact commit; record CI evidence and distinguish it from the user's device test.

## Every meaningful work stage
- Record `STARTED`, `CHANGED`, `VERIFIED/FAILED`, and `HANDED_OFF` in `docs/WORKLOG.md` as appropriate.
- At each substantial stopping point, update `docs/CONTINUITY_HANDOFF_2026-10-09.md` with exact commit SHA, changed files, tests/results, known failures, data-safety status, and one next action.
- Make small, focused commits. Before editing, fetch the latest file content/SHA; after editing, read back the result and inspect the diff/head.
- No success claims without exact evidence. A green build does not prove feature behavior; CI does not prove real-device behavior.
- If work is incomplete, record `PARTIAL` or `BLOCKED` and explain what remains.

This document is intended to be copied into the ChatGPT Project Instructions as the concise universal entry point. The tracked repository copy ensures the rules survive chat/session changes.


## Mandatory Android UI standard — all APK work

Before designing or changing ANY Android screen, read `docs/ANDROID_UI_STANDARD_v1.0.md`. This applies to the entire ORGANISM app, not just the current WebView. Safe-area/window-inset handling, consistent outer spacing, touch targets of at least 48×48 dp, keyboard-aware layout, reachable primary actions, adaptive sizing, accessibility, and screen-by-screen verification are release requirements. Never call a screen ready just because it compiles. Every APK release must include a UI review record and must distinguish CI build evidence from emulator/physical-device testing. If a control is clipped or unreachable, treat it as a defect/blocker and fix it before adding unrelated features.


## New user requirements must be recorded
- Every new user requirement, correction, constraint, or changed priority that affects ORGANISM must be written to `docs/WORKLOG.md` in the same work stage; do not leave it only in chat context.
- If it changes current priorities, acceptance criteria, safety boundaries, or the next action, also update `docs/CONTINUITY_HANDOFF_2026-10-09.md` and, when it is a durable general rule, `PROJECT_INSTRUCTIONS.md`.
- Preserve the user's intent and wording closely. Mark the requirement as open until implemented and verified; do not silently mark it complete because it was documented.
- Before ending a stage, read back the updated log/handoff and confirm the requirement is present. Report the commit/evidence to the user.
