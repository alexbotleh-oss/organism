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
