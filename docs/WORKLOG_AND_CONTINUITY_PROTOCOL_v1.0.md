# ORGANISM — work log and continuity protocol v1.0

**Purpose:** let a future assistant resume the project from repository evidence instead of reconstructing intent from memory or improvising from partial chat context.

## Mandatory rule

Every substantial work stage MUST be documented in the repository as it happens. A stage is not considered handed off until its code/docs, verification status, commit reference, and next step are recorded.

## Files of record

- `docs/CONTINUITY_HANDOFF_2026-10-09.md` — current project state, active task, important decisions, verified/unverified claims, and the exact next action. Update at the end of every substantial work session and whenever a material result or blocker appears.
- `docs/WORKLOG_AND_CONTINUITY_PROTOCOL_v1.0.md` — this process contract.
- `docs/WORKLOG.md` — chronological append-only log of meaningful stages. Create if absent; do not rewrite old entries to make history look cleaner.
- Feature-specific architecture/spec/test docs — record the contract and acceptance tests close to the implementation.

## What every work-log entry must contain

1. **Timestamp/date** and a short stage ID/title.
2. **User goal / why** this stage exists.
3. **Starting point:** branch, base/head commit SHA, relevant files, prior known state.
4. **Decisions and constraints:** preserve user decisions verbatim where wording matters; distinguish decisions from assumptions.
5. **Actions taken:** files changed, behavior changed, and relevant commit SHA(s).
6. **Verification evidence:** exact build/test command or CI run URL, result, and tested commit SHA. For device tests, record device-visible result and who tested it. Never label a build as a device test.
7. **Failures/regressions:** observed symptom, likely cause only when evidenced, correction, and remaining risk. Record failed attempts too; do not silently erase them.
8. **Data safety:** whether existing user data/database was touched, migration/backup/rollback behavior, and whether the action is reversible.
9. **Current status:** DONE / PARTIAL / BLOCKED / NOT TESTED, with explicit boundaries.
10. **Next action:** one concrete, ordered step and what evidence will count as success.

## Stage lifecycle

Use a concise lifecycle entry for each stage:
- `STARTED`: objective, baseline SHA, planned files/tests, and risk.
- `CHANGED`: actual changes and commit SHA.
- `VERIFIED` or `FAILED`: evidence tied to exact SHA; include CI URL/log or reproducible test details.
- `HANDED_OFF`: current state, unresolved issues, and next action.

These can be one entry when the work is small, but all applicable facts must be present. A planned test is not a passed test. A green compile is not proof of feature correctness. A successful import message is not proof that every conversation/message was imported and can be retrieved.

## Safety and integrity rules

- Never delete, overwrite, reset, or migrate user data without explicit authorization and a recovery plan.
- Keep raw imported sources and provenance. Derived indexes/summaries must be rebuildable where feasible.
- Prefer small, reviewable commits with specific messages; do not mix unrelated fixes.
- Before editing an existing file, fetch its current content and SHA. Avoid parallel writes to the same path.
- After edits, read back the changed file or inspect the diff; verify the exact resulting commit/head.
- Do not claim a fix, build, artifact, or device behavior unless evidence exists for that exact version.
- If a tool/session ends before work is complete, leave a truthful PARTIAL/BLOCKED entry and the next concrete step.
- Never present speculative implementation plans as completed work.

## Assistant behavior continuity

A future assistant must read this protocol and the current handoff before changing code. It should preserve established product decisions, avoid asking the user to repeat facts already recorded, and explicitly say when the repository does not contain enough evidence. Project memory must be recoverable from tracked files and commit history, not dependent on a particular model/personality/session.

## Required end-of-session checklist

- [ ] Work-log entry updated with status and exact commit SHA.
- [ ] Handoff updated with current head and next step.
- [ ] Tests/build status stated accurately, including failures.
- [ ] User data safety and outstanding risks noted.
- [ ] No unverified APK/link or feature claim presented as complete.
