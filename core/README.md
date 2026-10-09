# ORGANISM Core v0.4 (first implementation slice)

This package is a modular Python + SQLite core beside the existing PWA prototype. It does not replace the current UI or the ChatGPT bridge yet.

## Modules
- `organism_core/store.py`: SQLite schema, source provenance, append-only events, claims, verification events, experience applications, conflicts, authorized redaction/tombstones.
- `organism_core/policy.py`: explicit conservative handoff policy; no trust increases from repetition or model consensus.
- `organism_core/handoff.py`: scoped/environment-aware context selection and persisted exact handoff snapshot.
- `tests/test_core.py`: focused safety regression tests.

## Run tests
From the repository root:

```bash
python -m unittest discover -s core/tests -v
```

No third-party Python dependencies are required for this core slice. It uses Python's standard `sqlite3`.

## Deliberate limits
- This is a first implementation slice, not a completed production memory system.
- Existing browser `localStorage` data is not silently migrated or overwritten. A dedicated, tested importer is required.
- No semantic extractor is included yet; imported text must not be turned into experiences by keyword counting.
- Model review and repeated text do not constitute external verification.
- User-authorized redaction removes stored content and leaves a hash tombstone/receipt. It cannot recall data already sent to an external provider.
- A passing unit test is not proof of Android/mobile runtime correctness.
