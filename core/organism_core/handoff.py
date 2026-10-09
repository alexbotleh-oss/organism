"""Context selection for a model. Generated text is data, never independent evidence."""
from __future__ import annotations
import json
import time
from .policy import may_enter_handoff
from .store import new_id

VERIFIED = {"EXECUTED", "TEST_PASSED", "ARTIFACT_CONFIRMED", "EXTERNAL_CONFIRMED"}

def build_handoff(store, *, project_id: str, task: str, environment: dict | None = None,
                  model_id: str = "", risk: str = "normal", limit: int = 12,
                  explicit_override_ids: set[str] | None = None) -> dict:
    env = environment or {}
    overrides = explicit_override_ids or set()
    included, excluded = [], []
    rows = store.db.execute("""SELECT * FROM assertions WHERE project_id=? OR scope='global'
      ORDER BY created_at DESC""", (project_id,)).fetchall()
    for row in rows:
        r = dict(row)
        try: saved_env = json.loads(r["environment_json"] or "{}")
        except Exception: saved_env = {}
        env_match = all(env.get(k) == v for k,v in saved_env.items()) if saved_env else True
        allowed, reason = may_enter_handoff(status=r["claim_status"],
            verification_status=r["verification_status"],scope_match=(r["project_id"]==project_id or r["scope"]=="global"),
            environment_match=env_match,risk=risk,explicit_override=(r["id"] in overrides))
        if not allowed:
            excluded.append({"id":r["id"],"kind":"assertion","reason":reason})
            continue
        r["origin_notice"] = "ORGANISM_GENERATED_NOT_EVIDENCE" if r["origin"]=="organism_generated" else "SOURCE_BACKED_ASSERTION"
        r.pop("source_quote",None)
        included.append({"kind":"assertion",**r})
    exps = store.db.execute("""SELECT * FROM experiences WHERE project_id=? OR scope='global'
      ORDER BY created_at DESC""",(project_id,)).fetchall()
    for row in exps:
        r = dict(row)
        try: saved_env = json.loads(r["environment_json"] or "{}")
        except Exception: saved_env = {}
        env_match = all(env.get(k) == v for k,v in saved_env.items()) if saved_env else True
        allowed, reason = may_enter_handoff(status=r["status"],verification_status=r["verification_status"],
            scope_match=(r["project_id"]==project_id or r["scope"]=="global"),environment_match=env_match,
            risk=risk,explicit_override=(r["id"] in overrides))
        if not allowed:
            excluded.append({"id":r["id"],"kind":"experience","reason":reason})
            continue
        r["conditions_json"] = json.loads(r["conditions_json"] or "{}")
        r["environment_json"] = json.loads(r["environment_json"] or "{}")
        r["origin_notice"] = "ORGANISM_GENERATED_NOT_EVIDENCE" if r["origin"]=="organism_generated" else "EXPERIENCE_RECORD"
        included.append({"kind":"experience",**r})
    included = included[:max(0,limit)]
    snapshot = {"core_version":"0.4.0","project_id":project_id,"task":task,"risk":risk,
      "environment":env,"model_id":model_id,"created_at":time.time(),
      "items":included,"excluded":excluded,"policy":"No repetition/model-consensus promotion; scope/environment filters; high-risk requires verification."}
    sid = new_id("HOF")
    with store.db:
        store.db.execute("""INSERT INTO handoff_snapshots(id,project_id,task,model_id,snapshot_json,included_ids_json,excluded_json,created_at)
          VALUES(?,?,?,?,?,?,?,?)""",(sid,project_id,task,model_id,json.dumps(snapshot,ensure_ascii=False,default=str),
          json.dumps([x["id"] for x in included],ensure_ascii=False),json.dumps(excluded,ensure_ascii=False),snapshot["created_at"]))
        store.append_event("HANDOFF_SNAPSHOT_CREATED","organism",{"snapshot_id":sid,"included_count":len(included),
          "excluded_count":len(excluded),"model_id":model_id},project_id=project_id,origin="organism_generated")
    snapshot["snapshot_id"] = sid
    return snapshot
