# ORGANISM — mobile-first event observer decision v0.1

**Date:** 2026-10-09  
**Status:** Architecture direction recorded; implementation not started.

## User-confirmed direction

Start by developing and validating the first usable version on the Android phone. The phone is always with the user and is more convenient than a PC. A PC client or PC-hosted executor is future scope, not a prerequisite.

## Goal

Reduce manual GitHub checking and repeated status questions. ORGANISM should report meaningful development events to the phone, especially GitHub Actions completion and failures, with enough context to decide what to do next.

## Proposed separation

1. **Android client:** notifications, run/task details, event history and user confirmations.
2. **Event/state/memory layer:** links an event to repository, branch, commit, task, timestamp, source and verification status.
3. **GitHub observer/connector:** receives or checks workflow state. Keep it replaceable so other connectors and a future PC client can reuse the same event contract.

## Initial scope

- Notify about relevant GitHub Actions success, failure, cancellation and prolonged/unavailable status.
- Show repository, workflow/run, branch, commit, verified result and a link to the run.
- Deduplicate repeated events and keep an auditable event history.
- Represent unknown/unavailable separately from success.
- Preserve the distinction between CI build success and feature/device verification.

## Open implementation choice

Compare GitHub webhooks plus a small receiving service with periodic polling. Android background execution alone should not be treated as a guaranteed always-on observer. Push delivery (for example, FCM) is a candidate transport, not yet selected or implemented. Choose the simplest option that meets acceptable reliability and makes delivery delays visible.

## Safety boundaries

- Automated observation/reporting is separate from permission to edit code, commit, migrate data or perform destructive actions.
- Never report success without checking the exact workflow result.
- Do not expose secrets or sensitive authentication data in notifications or event logs.
- Do not change/delete existing app data as part of this feature without explicit permission and a recovery plan.

## Verification and next step

This is a documentation decision only. No app code, database, credentials or user data changed; no build or device test was run for this decision.

Next: check CI run 37955125073 for exact OAuth diagnostic commit 8396fc6b29661f14bbe80dd16fbb86f6a91b808f; then prepare acceptance criteria for the observer and choose webhook vs polling before implementation.
