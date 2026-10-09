"""SQLite persistence for ORGANISM Core v0.4.

RAW payloads are append-only during normal operation. A user-authorized deletion is
handled explicitly through tombstones and content redaction; it is not silently ignored.
"""
from __future__ import annotations
import hashlib
import json
import sqlite3
import time
import uuid
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1

SCHEMA = """
PRAGMA foreign_keys = ON;
CREATE TABLE IF NOT EXISTS schema_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS projects (
 id TEXT PRIMARY KEY, name TEXT NOT NULL, created_at REAL NOT NULL, status TEXT NOT NULL DEFAULT 'ACTIVE'
);
CREATE TABLE IF NOT EXISTS raw_sources (
 id TEXT PRIMARY KEY, project_id TEXT, source_name TEXT NOT NULL, source_kind TEXT NOT NULL,
 content TEXT, content_sha256 TEXT NOT NULL, byte_length INTEGER NOT NULL,
 imported_at REAL NOT NULL, completeness TEXT NOT NULL DEFAULT 'UNKNOWN',
 redacted_at REAL, tombstone_id TEXT,
 FOREIGN KEY(project_id) REFERENCES projects(id)
);
CREATE TABLE IF NOT EXISTS events (
 id TEXT PRIMARY KEY, project_id TEXT, event_type TEXT NOT NULL, actor TEXT NOT NULL,
 payload_json TEXT NOT NULL, occurred_at REAL NOT NULL, recorded_at REAL NOT NULL,
 source_id TEXT, origin TEXT NOT NULL DEFAULT 'external',
 FOREIGN KEY(project_id) REFERENCES projects(id),
 FOREIGN KEY(source_id) REFERENCES raw_sources(id)
);
CREATE TABLE IF NOT EXISTS assertions (
 id TEXT PRIMARY KEY, project_id TEXT, source_id TEXT, source_quote TEXT NOT NULL,
 speaker TEXT NOT NULL, assertion_kind TEXT NOT NULL, text TEXT NOT NULL,
 claim_status TEXT NOT NULL DEFAULT 'CANDIDATE',
 verification_status TEXT NOT NULL DEFAULT 'NONE',
 scope TEXT NOT NULL DEFAULT 'project', environment_json TEXT NOT NULL DEFAULT '{}',
 extractor_id TEXT NOT NULL DEFAULT 'manual', valid_from REAL, valid_until REAL,
 origin TEXT NOT NULL DEFAULT 'external', created_at REAL NOT NULL, supersedes_id TEXT,
 FOREIGN KEY(project_id) REFERENCES projects(id),
 FOREIGN KEY(source_id) REFERENCES raw_sources(id),
 FOREIGN KEY(supersedes_id) REFERENCES assertions(id)
);
CREATE TABLE IF NOT EXISTS verifications (
 id TEXT PRIMARY KEY, assertion_id TEXT NOT NULL, method TEXT NOT NULL,
 independence_class TEXT NOT NULL, outcome TEXT NOT NULL, notes TEXT NOT NULL DEFAULT '',
 artifact_source_id TEXT, verified_at REAL NOT NULL, actor TEXT NOT NULL,
 FOREIGN KEY(assertion_id) REFERENCES assertions(id),
 FOREIGN KEY(artifact_source_id) REFERENCES raw_sources(id)
);
CREATE TABLE IF NOT EXISTS experiences (
 id TEXT PRIMARY KEY, project_id TEXT, source_id TEXT, title TEXT NOT NULL,
 conditions_json TEXT NOT NULL, environment_json TEXT NOT NULL DEFAULT '{}',
 action TEXT NOT NULL, observed_outcome TEXT NOT NULL,
 verification_method TEXT NOT NULL, verification_status TEXT NOT NULL DEFAULT 'UNVERIFIED',
 kind TEXT NOT NULL DEFAULT 'CANDIDATE', scope TEXT NOT NULL DEFAULT 'project',
 applies_when TEXT NOT NULL DEFAULT '', fails_when TEXT NOT NULL DEFAULT '',
 status TEXT NOT NULL DEFAULT 'CANDIDATE', origin TEXT NOT NULL DEFAULT 'external',
 created_at REAL NOT NULL, supersedes_id TEXT,
 FOREIGN KEY(project_id) REFERENCES projects(id),
 FOREIGN KEY(source_id) REFERENCES raw_sources(id),
 FOREIGN KEY(supersedes_id) REFERENCES experiences(id)
);
CREATE TABLE IF NOT EXISTS experience_applications (
 id TEXT PRIMARY KEY, experience_id TEXT NOT NULL, project_id TEXT, task TEXT NOT NULL,
 context_json TEXT NOT NULL, handoff_snapshot TEXT NOT NULL, expected_outcome TEXT NOT NULL DEFAULT '',
 actual_outcome TEXT NOT NULL DEFAULT '', outcome TEXT NOT NULL DEFAULT 'PENDING',
 verification_status TEXT NOT NULL DEFAULT 'NONE', notes TEXT NOT NULL DEFAULT '',
 applied_at REAL NOT NULL, completed_at REAL,
 FOREIGN KEY(experience_id) REFERENCES experiences(id),
 FOREIGN KEY(project_id) REFERENCES projects(id)
);
CREATE TABLE IF NOT EXISTS conflicts (
 id TEXT PRIMARY KEY, left_kind TEXT NOT NULL, left_id TEXT NOT NULL,
 right_kind TEXT NOT NULL, right_id TEXT NOT NULL, reason TEXT NOT NULL,
 status TEXT NOT NULL DEFAULT 'OPEN', created_at REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS handoff_snapshots (
 id TEXT PRIMARY KEY, project_id TEXT, task TEXT NOT NULL, model_id TEXT NOT NULL DEFAULT '',
 snapshot_json TEXT NOT NULL, included_ids_json TEXT NOT NULL, excluded_json TEXT NOT NULL,
 created_at REAL NOT NULL, origin TEXT NOT NULL DEFAULT 'organism_generated'
);
CREATE TABLE IF NOT EXISTS tombstones (
 id TEXT PRIMARY KEY, target_kind TEXT NOT NULL, target_id TEXT NOT NULL,
 requested_by TEXT NOT NULL, reason TEXT NOT NULL DEFAULT '',
 content_sha256 TEXT NOT NULL, redacted_at REAL NOT NULL, receipt_json TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_assertions_project_status ON assertions(project_id, claim_status);
CREATE INDEX IF NOT EXISTS idx_experiences_project_status ON experiences(project_id, status);
CREATE INDEX IF NOT EXISTS idx_events_project_time ON events(project_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_applications_experience ON experience_applications(experience_id, applied_at);
"""

def new_id(prefix: str) -> str:
    return prefix + "_" + uuid.uuid4().hex

def sha256_text(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()

class OrganismStore:
    def __init__(self, path: str | Path):
        self.path = str(path)
        Path(self.path).parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(self.path)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.execute("PRAGMA busy_timeout=5000")
        self.db.executescript(SCHEMA)
        self.db.execute("INSERT OR REPLACE INTO schema_meta(key,value) VALUES('schema_version',?)", (str(SCHEMA_VERSION),))
        self.db.commit()

    def close(self) -> None:
        self.db.close()

    def create_project(self, name: str, project_id: str | None = None) -> str:
        pid = project_id or new_id("PRJ")
        now = time.time()
        with self.db:
            self.db.execute("INSERT INTO projects(id,name,created_at) VALUES(?,?,?)", (pid,name,now))
            self.append_event("PROJECT_CREATED", "user", {"name": name}, project_id=pid)
        return pid

    def add_raw_source(self, source_name: str, content: str, *, project_id: str | None = None,
                       source_kind: str = "CHAT_EXPORT", completeness: str = "UNKNOWN") -> str:
        sid, now = new_id("SRC"), time.time()
        digest = sha256_text(content)
        with self.db:
            self.db.execute("""INSERT INTO raw_sources
              (id,project_id,source_name,source_kind,content,content_sha256,byte_length,imported_at,completeness)
              VALUES(?,?,?,?,?,?,?,?,?)""",
              (sid,project_id,source_name,source_kind,content,digest,len(content.encode("utf-8")),now,completeness))
            self.append_event("RAW_IMPORTED", "user", {"source_name":source_name,"sha256":digest,
                "byte_length":len(content.encode("utf-8")),"completeness":completeness},
                project_id=project_id, source_id=sid)
        return sid

    def append_event(self, event_type: str, actor: str, payload: dict[str, Any], *,
                     project_id: str | None = None, source_id: str | None = None,
                     origin: str = "external", occurred_at: float | None = None) -> str:
        eid, now = new_id("EVT"), time.time()
        self.db.execute("""INSERT INTO events(id,project_id,event_type,actor,payload_json,occurred_at,recorded_at,source_id,origin)
          VALUES(?,?,?,?,?,?,?,?,?)""", (eid,project_id,event_type,actor,json.dumps(payload,ensure_ascii=False),
          occurred_at or now,now,source_id,origin))
        return eid

    def add_assertion(self, text: str, *, source_quote: str, speaker: str, assertion_kind: str,
                      project_id: str | None = None, source_id: str | None = None,
                      claim_status: str = "CANDIDATE", verification_status: str = "NONE",
                      scope: str = "project", environment: dict[str, Any] | None = None,
                      extractor_id: str = "manual", origin: str = "external") -> str:
        aid, now = new_id("CLM"), time.time()
        with self.db:
            self.db.execute("""INSERT INTO assertions(id,project_id,source_id,source_quote,speaker,assertion_kind,text,
              claim_status,verification_status,scope,environment_json,extractor_id,origin,created_at)
              VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
              (aid,project_id,source_id,source_quote,speaker,assertion_kind,text,claim_status,verification_status,
               scope,json.dumps(environment or {},ensure_ascii=False),extractor_id,origin,now))
            self.append_event("ASSERTION_ADDED",speaker,{"assertion_id":aid,"kind":assertion_kind,
              "claim_status":claim_status,"verification_status":verification_status},project_id=project_id,
              source_id=source_id,origin=origin)
        return aid

    def add_verification(self, assertion_id: str, *, method: str, independence_class: str,
                         outcome: str, actor: str, notes: str = "", artifact_source_id: str | None = None) -> str:
        vid, now = new_id("VRF"), time.time()
        with self.db:
            self.db.execute("""INSERT INTO verifications(id,assertion_id,method,independence_class,outcome,notes,
              artifact_source_id,verified_at,actor) VALUES(?,?,?,?,?,?,?,?,?)""",
              (vid,assertion_id,method,independence_class,outcome,notes,artifact_source_id,now,actor))
            if outcome.upper() in {"PASS","CONFIRMED","SUCCESS"}:
                self.db.execute("UPDATE assertions SET verification_status=? WHERE id=?",
                    ({"EXECUTION":"EXECUTED","TEST":"TEST_PASSED","ARTIFACT":"ARTIFACT_CONFIRMED",
                      "EXTERNAL_SOURCE":"EXTERNAL_CONFIRMED"}.get(method.upper(),"USER_REPORTED"),assertion_id))
            self.append_event("ASSERTION_VERIFIED",actor,{"assertion_id":assertion_id,"method":method,
              "independence_class":independence_class,"outcome":outcome},source_id=artifact_source_id)
        return vid

    def add_experience(self, *, title: str, conditions: dict[str, Any], action: str,
                       observed_outcome: str, verification_method: str, project_id: str | None = None,
                       source_id: str | None = None, environment: dict[str, Any] | None = None,
                       verification_status: str = "UNVERIFIED", kind: str = "CANDIDATE",
                       scope: str = "project", applies_when: str = "", fails_when: str = "",
                       origin: str = "external") -> str:
        xid, now = new_id("EXP"), time.time()
        status = "CANDIDATE" if verification_status.upper() in {"NONE","UNVERIFIED","REPORTED"} else kind.upper()
        with self.db:
            self.db.execute("""INSERT INTO experiences(id,project_id,source_id,title,conditions_json,environment_json,
              action,observed_outcome,verification_method,verification_status,kind,scope,applies_when,fails_when,status,origin,created_at)
              VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
              (xid,project_id,source_id,title,json.dumps(conditions,ensure_ascii=False),
               json.dumps(environment or {},ensure_ascii=False),action,observed_outcome,verification_method,
               verification_status,kind,scope,applies_when,fails_when,status,origin,now))
            self.append_event("EXPERIENCE_CANDIDATE_ADDED", "organism" if origin=="organism_generated" else "user",
              {"experience_id":xid,"status":status,"verification_status":verification_status},
              project_id=project_id,source_id=source_id,origin=origin)
        return xid

    def record_application(self, experience_id: str, *, task: str, context: dict[str, Any],
                           handoff_snapshot: str, project_id: str | None = None,
                           expected_outcome: str = "") -> str:
        aid, now = new_id("APP"), time.time()
        with self.db:
            self.db.execute("""INSERT INTO experience_applications(id,experience_id,project_id,task,context_json,
              handoff_snapshot,expected_outcome,applied_at) VALUES(?,?,?,?,?,?,?,?)""",
              (aid,experience_id,project_id,task,json.dumps(context,ensure_ascii=False),handoff_snapshot,expected_outcome,now))
            self.append_event("EXPERIENCE_APPLIED", "organism", {"application_id":aid,"experience_id":experience_id},
              project_id=project_id,origin="organism_generated")
        return aid

    def complete_application(self, application_id: str, *, outcome: str, verification_status: str,
                             actual_outcome: str, notes: str = "") -> None:
        with self.db:
            self.db.execute("""UPDATE experience_applications SET outcome=?,verification_status=?,
              actual_outcome=?,notes=?,completed_at=? WHERE id=?""",
              (outcome,verification_status,actual_outcome,notes,time.time(),application_id))
            self.append_event("EXPERIENCE_APPLICATION_COMPLETED","user",{"application_id":application_id,
              "outcome":outcome,"verification_status":verification_status,"notes":notes})

    def add_conflict(self, left_kind: str, left_id: str, right_kind: str, right_id: str, reason: str) -> str:
        cid, now = new_id("CNF"), time.time()
        with self.db:
            self.db.execute("INSERT INTO conflicts(id,left_kind,left_id,right_kind,right_id,reason,created_at) VALUES(?,?,?,?,?,?,?)",
              (cid,left_kind,left_id,right_kind,right_id,reason,now))
            self.append_event("CONFLICT_OPENED","organism",{"conflict_id":cid,"reason":reason},origin="organism_generated")
        return cid

    def authorized_delete(self, target_kind: str, target_id: str, *, requested_by: str,
                          reason: str = "") -> str:
        """Redact user-requested content and preserve only a hash tombstone/reference skeleton."""
        if requested_by.lower() not in {"user", "owner", "data_subject"}:
            raise PermissionError("Deletion requires explicit user authorization.")
        table = {"raw_source":"raw_sources","assertion":"assertions","experience":"experiences"}.get(target_kind)
        if not table:
            raise ValueError("Unsupported target_kind")
        row = self.db.execute(f"SELECT * FROM {table} WHERE id=?", (target_id,)).fetchone()
        if row is None:
            raise KeyError(target_id)
        payload = dict(row)
        content = str(payload.get("content") or payload.get("text") or payload.get("source_quote") or
                      payload.get("observed_outcome") or "")
        digest = payload.get("content_sha256") or sha256_text(content)
        tid, now = new_id("TMB"), time.time()
        receipt = {"target_kind":target_kind,"target_id":target_id,"sha256":digest,
                   "derived_records_need_review":True,"external_disclosures_recallable":False}
        with self.db:
            self.db.execute("INSERT INTO tombstones(id,target_kind,target_id,requested_by,reason,content_sha256,redacted_at,receipt_json) VALUES(?,?,?,?,?,?,?,?)",
              (tid,target_kind,target_id,requested_by,reason,digest,now,json.dumps(receipt,ensure_ascii=False)))
            if target_kind == "raw_source":
                self.db.execute("UPDATE raw_sources SET content=NULL,redacted_at=?,tombstone_id=? WHERE id=?", (now,tid,target_id))
            elif target_kind == "assertion":
                self.db.execute("UPDATE assertions SET text='[REDACTED BY USER]',source_quote='[REDACTED BY USER]',claim_status='TOMBSTONED' WHERE id=?", (target_id,))
            else:
                self.db.execute("UPDATE experiences SET title='[REDACTED BY USER]',action='[REDACTED BY USER]',observed_outcome='[REDACTED BY USER]',status='TOMBSTONED' WHERE id=?", (target_id,))
            self.append_event("USER_AUTHORIZED_REDACTION",requested_by,receipt,origin="external")
        return tid
