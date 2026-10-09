"""ORGANISM CORE v0.4.

Standard-library-only SQLite core. This is a first implementation increment:
it is deliberately separate from the existing PWA UI until API integration is tested.
"""
from __future__ import annotations

import hashlib
import json
import sqlite3
import time
import uuid
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterable


SCHEMA_VERSION = 1


def _now() -> int:
    return int(time.time())


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def _id(prefix: str) -> str:
    return prefix + "_" + uuid.uuid4().hex


class OrganismCore:
    """Persistence and safety rules for ORGANISM's first structured-memory core.

    Keep business operations here; do not let UI code write memory tables directly.
    """

    def __init__(self, db_path: str | Path):
        self.db_path = str(db_path)
        if self.db_path != ":memory:":
            Path(self.db_path).parent.mkdir(parents=True, exist_ok=True)
        self._initialize()

    @contextmanager
    def _connect(self):
        conn = sqlite3.connect(self.db_path, timeout=5.0)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA foreign_keys=ON")
        conn.execute("PRAGMA busy_timeout=5000")
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()

    def _initialize(self) -> None:
        schema = """
        CREATE TABLE IF NOT EXISTS schema_meta (
            version INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS source_documents (
            id TEXT PRIMARY KEY,
            source_name TEXT NOT NULL,
            source_kind TEXT NOT NULL,
            raw_text TEXT,
            sha256 TEXT NOT NULL,
            imported_at INTEGER NOT NULL,
            completeness TEXT NOT NULL DEFAULT 'unknown',
            metadata_json TEXT NOT NULL DEFAULT '{}'
        );
        CREATE TABLE IF NOT EXISTS raw_segments (
            id TEXT PRIMARY KEY,
            source_id TEXT NOT NULL REFERENCES source_documents(id),
            ordinal INTEGER NOT NULL,
            speaker TEXT NOT NULL DEFAULT 'unknown',
            content TEXT,
            content_sha256 TEXT NOT NULL,
            occurred_at INTEGER,
            created_at INTEGER NOT NULL,
            origin TEXT NOT NULL DEFAULT 'external',
            redacted_at INTEGER,
            UNIQUE(source_id, ordinal)
        );
        CREATE TABLE IF NOT EXISTS events (
            id TEXT PRIMARY KEY,
            event_type TEXT NOT NULL,
            actor TEXT NOT NULL,
            object_type TEXT,
            object_id TEXT,
            payload_json TEXT NOT NULL DEFAULT '{}',
            created_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS claims (
            id TEXT PRIMARY KEY,
            project_id TEXT,
            claim_text TEXT NOT NULL,
            claim_type TEXT NOT NULL DEFAULT 'assertion',
            source_kind TEXT NOT NULL,
            origin TEXT NOT NULL DEFAULT 'external',
            claim_status TEXT NOT NULL DEFAULT 'candidate',
            verification_status TEXT NOT NULL DEFAULT 'unverified',
            scope TEXT NOT NULL DEFAULT 'project',
            environment_json TEXT NOT NULL DEFAULT '{}',
            valid_from INTEGER,
            valid_until INTEGER,
            extractor_version TEXT,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS claim_sources (
            claim_id TEXT NOT NULL REFERENCES claims(id),
            raw_segment_id TEXT NOT NULL REFERENCES raw_segments(id),
            relation TEXT NOT NULL DEFAULT 'derived_from',
            PRIMARY KEY(claim_id, raw_segment_id, relation)
        );
        CREATE TABLE IF NOT EXISTS verifications (
            id TEXT PRIMARY KEY,
            object_type TEXT NOT NULL,
            object_id TEXT NOT NULL,
            method TEXT NOT NULL,
            channel TEXT NOT NULL,
            outcome TEXT NOT NULL,
            description TEXT NOT NULL,
            artifact_ref TEXT,
            independent_of_model INTEGER NOT NULL DEFAULT 0,
            verified_at INTEGER NOT NULL,
            actor TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS experiences (
            id TEXT PRIMARY KEY,
            project_id TEXT,
            title TEXT NOT NULL,
            conditions_json TEXT NOT NULL,
            environment_json TEXT NOT NULL DEFAULT '{}',
            action_taken TEXT NOT NULL,
            observed_outcome TEXT NOT NULL,
            verification_method TEXT NOT NULL,
            verification_status TEXT NOT NULL DEFAULT 'unverified',
            experience_status TEXT NOT NULL DEFAULT 'candidate',
            scope TEXT NOT NULL DEFAULT 'project',
            applies_when TEXT NOT NULL DEFAULT '',
            fails_when TEXT NOT NULL DEFAULT '',
            source_kind TEXT NOT NULL DEFAULT 'user_report',
            origin TEXT NOT NULL DEFAULT 'external',
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );
        CREATE TABLE IF NOT EXISTS experience_sources (
            experience_id TEXT NOT NULL REFERENCES experiences(id),
            raw_segment_id TEXT NOT NULL REFERENCES raw_segments(id),
            relation TEXT NOT NULL DEFAULT 'derived_from',
            PRIMARY KEY(experience_id, raw_segment_id, relation)
        );
        CREATE TABLE IF NOT EXISTS user_directives (
            id TEXT PRIMARY KEY,
            project_id TEXT,
            directive TEXT NOT NULL,
            scope TEXT NOT NULL DEFAULT 'project',
            priority INTEGER NOT NULL DEFAULT 100,
            created_at INTEGER NOT NULL,
            expires_at INTEGER,
            active INTEGER NOT NULL DEFAULT 1,
            source_raw_segment_id TEXT REFERENCES raw_segments(id)
        );
        CREATE TABLE IF NOT EXISTS conflicts (
            id TEXT PRIMARY KEY,
            object_a_type TEXT NOT NULL,
            object_a_id TEXT NOT NULL,
            object_b_type TEXT NOT NULL,
            object_b_id TEXT NOT NULL,
            description TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'open',
            created_at INTEGER NOT NULL,
            resolved_at INTEGER
        );
        CREATE TABLE IF NOT EXISTS applications (
            id TEXT PRIMARY KEY,
            project_id TEXT,
            task TEXT NOT NULL,
            model_name TEXT NOT NULL DEFAULT 'unknown',
            model_version TEXT NOT NULL DEFAULT 'unknown',
            environment_json TEXT NOT NULL DEFAULT '{}',
            handoff_json TEXT NOT NULL,
            handoff_sha256 TEXT NOT NULL,
            outcome TEXT NOT NULL DEFAULT 'pending',
            outcome_notes TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL,
            completed_at INTEGER
        );
        CREATE TABLE IF NOT EXISTS tombstones (
            id TEXT PRIMARY KEY,
            object_type TEXT NOT NULL,
            object_id TEXT NOT NULL,
            reason TEXT NOT NULL,
            authorized_by TEXT NOT NULL,
            created_at INTEGER NOT NULL,
            content_sha256 TEXT
        );
        CREATE INDEX IF NOT EXISTS idx_raw_source_ordinal ON raw_segments(source_id, ordinal);
        CREATE INDEX IF NOT EXISTS idx_claim_scope_status ON claims(project_id, scope, claim_status, verification_status);
        CREATE INDEX IF NOT EXISTS idx_experience_scope_status ON experiences(project_id, scope, experience_status, verification_status);
        CREATE INDEX IF NOT EXISTS idx_events_created ON events(created_at);
        CREATE INDEX IF NOT EXISTS idx_directive_active ON user_directives(project_id, active, expires_at);

        CREATE TRIGGER IF NOT EXISTS events_no_update
        BEFORE UPDATE ON events BEGIN SELECT RAISE(ABORT, 'events are append-only'); END;
        CREATE TRIGGER IF NOT EXISTS events_no_delete
        BEFORE DELETE ON events BEGIN SELECT RAISE(ABORT, 'events are append-only'); END;
        """
        with self._connect() as conn:
            conn.executescript(schema)
            # Forward-compatible additive migration for databases created by an earlier CORE build.
            source_columns = {r["name"] for r in conn.execute("PRAGMA table_info(source_documents)")}
            if "raw_text" not in source_columns:
                conn.execute("ALTER TABLE source_documents ADD COLUMN raw_text TEXT")
            row = conn.execute("SELECT version FROM schema_meta LIMIT 1").fetchone()
            if row is None:
                conn.execute("INSERT INTO schema_meta(version) VALUES (?)", (SCHEMA_VERSION,))
            elif row["version"] > SCHEMA_VERSION:
                raise RuntimeError("Database schema is newer than this CORE version")

    def _event(self, conn: sqlite3.Connection, event_type: str, actor: str,
               object_type: str | None = None, object_id: str | None = None,
               payload: Any | None = None) -> str:
        event_id = _id("evt")
        conn.execute(
            "INSERT INTO events(id,event_type,actor,object_type,object_id,payload_json,created_at) VALUES(?,?,?,?,?,?,?)",
            (event_id, event_type, actor, object_type, object_id, _json(payload or {}), _now()),
        )
        return event_id

    def import_text(self, source_name: str, text: str, *, source_kind: str = "chat_export",
                    speaker: str = "unknown", origin: str = "external",
                    completeness: str = "unknown", metadata: dict[str, Any] | None = None) -> dict[str, Any]:
        """Store an imported text file as immutable-by-policy RAW segments.

        The source text is split into non-empty lines for this first increment. This is
        lossless for the original payload at source level; each segment keeps its exact line.
        """
        digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
        source_id = _id("src")
        lines = text.splitlines()
        with self._connect() as conn:
            conn.execute(
                "INSERT INTO source_documents(id,source_name,source_kind,raw_text,sha256,imported_at,completeness,metadata_json) VALUES(?,?,?,?,?,?,?,?)",
                (source_id, source_name, source_kind, text, digest, _now(), completeness, _json(metadata or {})),
            )
            segment_ids = []
            for ordinal, line in enumerate(lines, start=1):
                segment_id = _id("raw")
                line_hash = hashlib.sha256(line.encode("utf-8")).hexdigest()
                conn.execute(
                    "INSERT INTO raw_segments(id,source_id,ordinal,speaker,content,content_sha256,created_at,origin) VALUES(?,?,?,?,?,?,?,?)",
                    (segment_id, source_id, ordinal, speaker, line, line_hash, _now(), origin),
                )
                segment_ids.append(segment_id)
            self._event(conn, "RAW_IMPORTED", "user", "source_document", source_id,
                        {"sha256": digest, "segments": len(segment_ids), "completeness": completeness})
        return {"source_id": source_id, "sha256": digest, "segment_ids": segment_ids, "segments": len(segment_ids)}

    def get_raw_segment(self, segment_id: str) -> dict[str, Any] | None:
        with self._connect() as conn:
            row = conn.execute("SELECT * FROM raw_segments WHERE id=?", (segment_id,)).fetchone()
            return dict(row) if row else None

    def add_claim(self, claim_text: str, *, project_id: str | None,
                  source_kind: str, raw_segment_ids: Iterable[str] = (),
                  claim_type: str = "assertion", claim_status: str = "candidate",
                  verification_status: str = "unverified", scope: str = "project",
                  environment: dict[str, Any] | None = None,
                  origin: str = "external", extractor_version: str | None = None,
                  actor: str = "organism") -> str:
        if not claim_text.strip():
            raise ValueError("claim_text must not be empty")
        if verification_status == "verified":
            raise ValueError("Use verify() to promote a claim; status cannot be assigned during extraction")
        if verification_status == "verified":
            raise ValueError("Use verify() to promote an experience; status cannot be assigned during extraction")
        if origin == "organism_generated" and verification_status == "verified":
            raise ValueError("ORGANISM-generated content cannot self-verify")
        claim_id = _id("clm")
        now = _now()
        with self._connect() as conn:
            conn.execute(
                """INSERT INTO claims(id,project_id,claim_text,claim_type,source_kind,origin,claim_status,
                   verification_status,scope,environment_json,extractor_version,created_at,updated_at)
                   VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                (claim_id, project_id, claim_text, claim_type, source_kind, origin, claim_status,
                 verification_status, scope, _json(environment or {}), extractor_version, now, now),
            )
            for raw_id in raw_segment_ids:
                if not conn.execute("SELECT 1 FROM raw_segments WHERE id=?", (raw_id,)).fetchone():
                    raise ValueError(f"Unknown raw segment: {raw_id}")
                conn.execute("INSERT INTO claim_sources(claim_id,raw_segment_id) VALUES(?,?)", (claim_id, raw_id))
            self._event(conn, "CLAIM_CREATED", actor, "claim", claim_id,
                        {"source_kind": source_kind, "origin": origin, "claim_status": claim_status,
                         "verification_status": verification_status})
        return claim_id

    def verify(self, object_type: str, object_id: str, *, method: str, channel: str,
               outcome: str, description: str, actor: str = "user",
               artifact_ref: str | None = None, independent_of_model: bool = False) -> str:
        """Record a verification event. User satisfaction or model agreement is not verification."""
        allowed_channels = {"test", "execution", "artifact", "documentation", "manual_check", "user_report", "model_review"}
        if channel not in allowed_channels:
            raise ValueError(f"Unsupported verification channel: {channel}")
        if channel == "model_review" and outcome == "verified":
            raise ValueError("A model review cannot independently verify real-world truth")
        if channel == "user_report" and outcome == "verified":
            raise ValueError("A user report is reported evidence; describe what was actually checked")
        if not description.strip():
            raise ValueError("Verification requires a description of what was checked")
        verification_id = _id("ver")
        now = _now()
        with self._connect() as conn:
            if object_type == "claim":
                table = "claims"
            elif object_type == "experience":
                table = "experiences"
            else:
                raise ValueError("object_type must be 'claim' or 'experience'")
            row = conn.execute(f"SELECT id FROM {table} WHERE id=?", (object_id,)).fetchone()
            if not row:
                raise ValueError(f"Unknown {object_type}: {object_id}")
            conn.execute(
                """INSERT INTO verifications(id,object_type,object_id,method,channel,outcome,description,
                   artifact_ref,independent_of_model,verified_at,actor) VALUES(?,?,?,?,?,?,?,?,?,?,?)""",
                (verification_id, object_type, object_id, method, channel, outcome, description,
                 artifact_ref, int(independent_of_model), now, actor),
            )
            if outcome == "verified" and channel in {"test", "execution", "artifact", "documentation", "manual_check"}:
                if object_type == "claim":
                    conn.execute("UPDATE claims SET verification_status='verified',claim_status='active',updated_at=? WHERE id=?",
                                 (now, object_id))
                else:
                    conn.execute("UPDATE experiences SET verification_status='verified',experience_status='validated',updated_at=? WHERE id=?",
                                 (now, object_id))
            elif outcome in {"failed", "contradicted"}:
                if object_type == "claim":
                    conn.execute("UPDATE claims SET verification_status='contradicted',claim_status='refuted',updated_at=? WHERE id=?",
                                 (now, object_id))
                else:
                    conn.execute("UPDATE experiences SET verification_status='contradicted',experience_status='refuted',updated_at=? WHERE id=?",
                                 (now, object_id))
            self._event(conn, "VERIFICATION_RECORDED", actor, object_type, object_id,
                        {"verification_id": verification_id, "method": method, "channel": channel, "outcome": outcome})
        return verification_id

    def add_experience(self, title: str, *, project_id: str | None, conditions: dict[str, Any],
                       action_taken: str, observed_outcome: str, verification_method: str,
                       verification_status: str = "unverified", scope: str = "project",
                       environment: dict[str, Any] | None = None, applies_when: str = "",
                       fails_when: str = "", raw_segment_ids: Iterable[str] = (),
                       source_kind: str = "user_report", origin: str = "external",
                       actor: str = "organism") -> str:
        if not conditions or not action_taken.strip() or not observed_outcome.strip():
            raise ValueError("Experience requires context, action, and observed outcome")
        if not verification_method.strip():
            raise ValueError("Experience requires a verification method, even if not yet verified")
        if origin == "organism_generated" and verification_status == "verified":
            raise ValueError("ORGANISM-generated content cannot self-verify")
        experience_id = _id("exp")
        now = _now()
        status = "validated" if verification_status == "verified" else "candidate"
        with self._connect() as conn:
            conn.execute(
                """INSERT INTO experiences(id,project_id,title,conditions_json,environment_json,action_taken,
                   observed_outcome,verification_method,verification_status,experience_status,scope,applies_when,
                   fails_when,source_kind,origin,created_at,updated_at)
                   VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                (experience_id, project_id, title, _json(conditions), _json(environment or {}), action_taken,
                 observed_outcome, verification_method, verification_status, status, scope, applies_when,
                 fails_when, source_kind, origin, now, now),
            )
            for raw_id in raw_segment_ids:
                if not conn.execute("SELECT 1 FROM raw_segments WHERE id=?", (raw_id,)).fetchone():
                    raise ValueError(f"Unknown raw segment: {raw_id}")
                conn.execute("INSERT INTO experience_sources(experience_id,raw_segment_id) VALUES(?,?)",
                             (experience_id, raw_id))
            self._event(conn, "EXPERIENCE_CREATED", actor, "experience", experience_id,
                        {"verification_status": verification_status, "scope": scope, "origin": origin})
        return experience_id

    def add_directive(self, directive: str, *, project_id: str | None,
                      scope: str = "project", expires_at: int | None = None,
                      source_raw_segment_id: str | None = None, actor: str = "user") -> str:
        if not directive.strip():
            raise ValueError("directive must not be empty")
        directive_id = _id("dir")
        with self._connect() as conn:
            conn.execute(
                "INSERT INTO user_directives(id,project_id,directive,scope,priority,created_at,expires_at,active,source_raw_segment_id) VALUES(?,?,?,?,?,?,?,?,?)",
                (directive_id, project_id, directive, scope, 100, _now(), expires_at, 1, source_raw_segment_id),
            )
            self._event(conn, "USER_DIRECTIVE_ADDED", actor, "directive", directive_id,
                        {"scope": scope, "expires_at": expires_at})
        return directive_id

    def add_conflict(self, object_a_type: str, object_a_id: str, object_b_type: str,
                     object_b_id: str, description: str) -> str:
        conflict_id = _id("cnf")
        with self._connect() as conn:
            conn.execute(
                "INSERT INTO conflicts(id,object_a_type,object_a_id,object_b_type,object_b_id,description,created_at) VALUES(?,?,?,?,?,?,?)",
                (conflict_id, object_a_type, object_a_id, object_b_type, object_b_id, description, _now()),
            )
            self._event(conn, "CONFLICT_OPENED", "organism", "conflict", conflict_id, {"description": description})
        return conflict_id

    @staticmethod
    def _env_compatible(stored: dict[str, Any], current: dict[str, Any]) -> bool:
        """Exact-match known keys; absent keys are unknown, not proof of compatibility."""
        if not stored:
            return True
        if not current:
            return False
        for key, value in stored.items():
            if key not in current or current[key] != value:
                return False
        return True

    def build_handoff(self, *, project_id: str | None, task: str,
                      environment: dict[str, Any] | None = None,
                      model_name: str = "unknown", model_version: str = "unknown",
                      max_chars: int = 9000) -> dict[str, Any]:
        """Build and persist the exact context snapshot sent to an external model."""
        env = environment or {}
        with self._connect() as conn:
            directives = [dict(r) for r in conn.execute(
                """SELECT * FROM user_directives WHERE active=1 AND (project_id=? OR project_id IS NULL)
                   AND (expires_at IS NULL OR expires_at>?) ORDER BY priority DESC,created_at DESC""",
                (project_id, _now()),
            )]
            claims = []
            for row in conn.execute(
                """SELECT * FROM claims WHERE claim_status='active' AND verification_status='verified'
                   AND origin!='organism_generated' AND (project_id=? OR (project_id IS NULL AND scope='global'))
                   ORDER BY updated_at DESC""", (project_id,),
            ):
                item = dict(row)
                if self._env_compatible(json.loads(item["environment_json"]), env):
                    claims.append(item)
            experiences = []
            for row in conn.execute(
                """SELECT * FROM experiences WHERE experience_status='validated' AND verification_status='verified'
                   AND origin!='organism_generated' AND (project_id=? OR (project_id IS NULL AND scope='global'))
                   ORDER BY updated_at DESC""", (project_id,),
            ):
                item = dict(row)
                if self._env_compatible(json.loads(item["environment_json"]), env):
                    experiences.append(item)
            open_conflicts = [dict(r) for r in conn.execute("SELECT * FROM conflicts WHERE status='open'")]
            payload = {
                "core_version": "0.4",
                "project_id": project_id,
                "task": task,
                "environment": env,
                "directives": directives,
                "verified_claims": [],
                "validated_experiences": [],
                "open_conflicts": open_conflicts,
                "safety_note": "Verified claims and experiences only. Model consensus and ORGANISM-generated text are not ground truth.",
                "budget_exceeded": False,
            }
            # Preserve mandatory task/directive/conflict context, then pack evidence one item at a time.
            for key, items in (("verified_claims", claims), ("validated_experiences", experiences)):
                for item in items:
                    payload[key].append(item)
                    if len(_json(payload)) > max_chars:
                        payload[key].pop()
            packed = _json(payload)
            # Mandatory fields may themselves exceed the budget; report rather than silently dropping them.
            payload["budget_exceeded"] = len(packed) > max_chars
            packed = _json(payload)
            application_id = _id("app")
            digest = hashlib.sha256(packed.encode("utf-8")).hexdigest()
            conn.execute(
                """INSERT INTO applications(id,project_id,task,model_name,model_version,environment_json,
                   handoff_json,handoff_sha256,created_at) VALUES(?,?,?,?,?,?,?,?,?)""",
                (application_id, project_id, task, model_name, model_version, _json(env), packed, digest, _now()),
            )
            self._event(conn, "HANDOFF_CREATED", "organism", "application", application_id,
                        {"sha256": digest, "chars": len(packed), "budget_exceeded": payload["budget_exceeded"]})
            return {"application_id": application_id, "sha256": digest, "chars": len(packed), "payload": payload}

    def record_application_outcome(self, application_id: str, *, outcome: str, notes: str = "",
                                   actor: str = "user") -> None:
        allowed = {"success", "failed", "partial", "inapplicable", "neutral", "inconclusive"}
        if outcome not in allowed:
            raise ValueError(f"outcome must be one of {sorted(allowed)}")
        with self._connect() as conn:
            row = conn.execute("SELECT id FROM applications WHERE id=?", (application_id,)).fetchone()
            if not row:
                raise ValueError("Unknown application")
            conn.execute("UPDATE applications SET outcome=?,outcome_notes=?,completed_at=? WHERE id=?",
                         (outcome, notes, _now(), application_id))
            self._event(conn, "APPLICATION_OUTCOME_RECORDED", actor, "application", application_id,
                        {"outcome": outcome, "notes": notes})

    def authorize_delete(self, object_type: str, object_id: str, *, authorized_by: str,
                         reason: str) -> str:
        """Redact user memory only with explicit authorization; keep a tombstone and audit event.

        Source content is scrubbed when the target is a raw segment. References remain so
        dependent claims/experiences can be audited and reviewed instead of silently orphaned.
        """
        if not authorized_by.strip() or not reason.strip():
            raise ValueError("Explicit authorizer and reason are required")
        allowed = {
            "raw_segment": ("raw_segments", "content"),
            "claim": ("claims", "claim_text"),
            "experience": ("experiences", "observed_outcome"),
            "directive": ("user_directives", "directive"),
        }
        if object_type not in allowed:
            raise ValueError("Unsupported deletable object type")
        table, field = allowed[object_type]
        tombstone_id = _id("tmb")
        with self._connect() as conn:
            row = conn.execute(f"SELECT {field} FROM {table} WHERE id=?", (object_id,)).fetchone()
            if not row:
                raise ValueError(f"Unknown {object_type}: {object_id}")
            old = row[field] or ""
            digest = hashlib.sha256(old.encode("utf-8")).hexdigest() if old else None
            # Scrub the requested content; dependent rows remain and are flagged for review.
            conn.execute(f"UPDATE {table} SET {field}=? WHERE id=?", ("[REDACTED BY USER REQUEST]", object_id))
            if object_type == "raw_segment":
                raw_row = conn.execute("SELECT source_id,ordinal FROM raw_segments WHERE id=?", (object_id,)).fetchone()
                conn.execute("UPDATE raw_segments SET redacted_at=? WHERE id=?", (_now(), object_id))
                # The exact imported source is retained unless the user explicitly requests redaction;
                # this authorized operation scrubs the corresponding line in the source payload too.
                source_row = conn.execute("SELECT raw_text FROM source_documents WHERE id=?", (raw_row["source_id"],)).fetchone()
                if source_row and source_row["raw_text"] is not None:
                    original_lines = source_row["raw_text"].splitlines(keepends=True)
                    line_index = int(raw_row["ordinal"]) - 1
                    if 0 <= line_index < len(original_lines):
                        ending = chr(10) if original_lines[line_index].endswith(chr(10)) else ""
                        original_lines[line_index] = "[REDACTED BY USER REQUEST]" + ending
                        conn.execute("UPDATE source_documents SET raw_text=? WHERE id=?",
                                     ("".join(original_lines), raw_row["source_id"]))
                conn.execute("""UPDATE claims SET verification_status='needs_review',claim_status='quarantined',updated_at=?
                                WHERE id IN (SELECT claim_id FROM claim_sources WHERE raw_segment_id=?)""", (_now(), object_id))
                conn.execute("""UPDATE experiences SET verification_status='needs_review',experience_status='quarantined',updated_at=?
                                WHERE id IN (SELECT experience_id FROM experience_sources WHERE raw_segment_id=?)""", (_now(), object_id))
            elif object_type == "claim":
                conn.execute("UPDATE claims SET claim_status='redacted',verification_status='needs_review',updated_at=? WHERE id=?", (_now(), object_id))
            elif object_type == "experience":
                conn.execute("UPDATE experiences SET experience_status='redacted',verification_status='needs_review',updated_at=? WHERE id=?", (_now(), object_id))
            else:
                conn.execute("UPDATE user_directives SET active=0 WHERE id=?", (object_id,))
            conn.execute(
                "INSERT INTO tombstones(id,object_type,object_id,reason,authorized_by,created_at,content_sha256) VALUES(?,?,?,?,?,?,?)",
                (tombstone_id, object_type, object_id, reason, authorized_by, _now(), digest),
            )
            self._event(conn, "USER_AUTHORIZED_REDACTION", authorized_by, object_type, object_id,
                        {"tombstone_id": tombstone_id, "reason": reason, "content_sha256": digest})
        return tombstone_id

    def stats(self) -> dict[str, int]:
        tables = ("source_documents", "raw_segments", "events", "claims", "experiences",
                  "verifications", "conflicts", "applications", "tombstones")
        with self._connect() as conn:
            return {table: conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0] for table in tables}

    def export_snapshot(self) -> dict[str, Any]:
        """Diagnostic export for tests/backups; does not expose provider credentials."""
        with self._connect() as conn:
            result: dict[str, Any] = {"schema_version": SCHEMA_VERSION}
            for table in ("source_documents", "raw_segments", "events", "claims", "claim_sources",
                          "verifications", "experiences", "experience_sources", "user_directives",
                          "conflicts", "applications", "tombstones"):
                result[table] = [dict(r) for r in conn.execute(f"SELECT * FROM {table}")]
            return result
