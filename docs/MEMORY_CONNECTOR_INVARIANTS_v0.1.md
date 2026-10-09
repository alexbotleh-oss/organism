# ORGANISM — Memory and Connector Invariants v0.1

**Status:** accepted architectural direction; implementation in progress  
**Date:** 2026-10-09

## 1. Foundational invariant

**One memory. Different connectors. Continuity belongs to ORGANISM and the user, not to a model, platform, API, browser session, or chat.**

A connector transports requests and responses. It does not own, replace, or define the lifetime of memory.

## 2. Separation of responsibilities

- **Memory core:** local source archive, normalized events, projects, tasks, claims and verification status, relations, artifacts, experience, audit trail, and recoverable task state.
- **Context/resume engine:** selects relevant records and creates a bounded context package with source references, decisions, uncertainty, current task state, and next step.
- **Connector adapter:** implements one interaction mechanism and reports its capabilities, lifecycle, errors, and evidence. Examples: an official API connector; a user-session platform connector where the platform permits it.
- **Execution controller:** tracks progress, no progress, regression, blocked state, and need to re-plan. It must not treat a transport retry as proof that a request was not sent.
- **Safety/reflex layer:** protects memory and user data, enforces permissions, and prevents an unverified model response from becoming a verified fact automatically.

## 3. Connector contract

All connectors should eventually implement the same application-level contract:

1. capabilities() — report supported operations and known limitations.
2. connect() / disconnect() — report explicit connection state.
3. send(requestId, message, context) — accept a stable request ID and the selected context.
4. receive() or an equivalent completion callback — return response content and transport metadata.
5. status(requestId) — distinguish prepared, sent, response pending, response received, failed, interrupted, and unknown outcomes when the channel allows it.
6. cancel(requestId) — best-effort cancellation where supported.

Connector states are not memory states. A connector failure must not delete conversation records or task state. When the delivery outcome is unknown, ORGANISM must not blindly resend; it should reconcile or ask the user.

## 4. Platform and API modes

- **Platform mode:** uses a user-authorized session and only supported interaction paths. A WebView alone does not guarantee that login, message sending, or response reading is permitted or technically stable. Do not bypass platform protections or extract credentials from browser storage.
- **API mode:** uses an official API and separately configured credentials. API access and user subscription entitlements are distinct.
- **Future connectors:** may be added without changing the canonical memory schema or task lifecycle.

## 5. Memory ownership and continuity

- Raw imported/source material is preserved with provenance and checksums where available.
- Derived summaries and context packages remain rebuildable from source-linked records where practical.
- Claims keep separate claim and verification statuses; agreement between models is not proof.
- Switching connectors must not fork canonical project/task memory.
- Session-specific transcripts can remain distinct, while project/task state and validated knowledge are shared.
- Deletion, compaction, and cleanup must be reversible or quarantined where feasible; protected records and relations require explicit safeguards.

## 6. Acceptance criteria

1. Start a task through connector A, store a decision and a verified artifact, then switch to connector B.
2. The resume package for connector B includes the same project/task state, decision, source links, uncertainty, and next step without importing a duplicate canonical memory.
3. A connector error or app restart does not erase the task state or raw source.
4. A request with unknown delivery outcome is marked UNKNOWN/reconciliation-required rather than automatically resent.
5. Model output remains unverified until a defined check supplies evidence.
6. Connector capability limitations are shown honestly; unsupported platform automation is not simulated as success.

## 7. Implementation order

1. Preserve and validate the current local memory and import pipeline.
2. Define the connector contract and request lifecycle without changing canonical memory.
3. Move current API interaction behind an API connector with behavior parity.
4. Add a platform-session connector only after the chosen platform's supported interaction path is proven.
5. Add connector switching and resume-package integration tests.
6. Expand failure recovery, audit views, and on-device acceptance tests.

## 8. Current implementation note

The current Android app already has local SQLite memory, context and experience engines, and an API-oriented interaction path. This is not yet proof that connector abstraction or platform mode is implemented. Treat the above as the target contract until code and tests demonstrate each capability.
