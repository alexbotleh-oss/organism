import json
import tempfile
import unittest
from pathlib import Path

from organism_core import OrganismStore, build_handoff, may_enter_handoff

class CoreTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.store = OrganismStore(Path(self.tmp.name) / "test.db")
        self.project = self.store.create_project("fixture")
    def tearDown(self):
        self.store.close()
        self.tmp.cleanup()

    def test_model_repetition_does_not_verify_claim(self):
        aid = self.store.add_assertion("Claim X", source_quote="model says X", speaker="model",
            assertion_kind="MODEL_SUGGESTION", project_id=self.project, origin="organism_generated")
        handoff = build_handoff(self.store, project_id=self.project, task="normal task")
        found = [x for x in handoff["items"] if x["id"] == aid]
        self.assertEqual(len(found), 1)
        self.assertEqual(found[0]["verification_status"], "NONE")
        self.assertEqual(found[0]["origin_notice"], "ORGANISM_GENERATED_NOT_EVIDENCE")

    def test_high_risk_blocks_unverified(self):
        allowed, reason = may_enter_handoff(status="CANDIDATE", verification_status="NONE",
            scope_match=True, environment_match=True, risk="critical")
        self.assertFalse(allowed)
        self.assertEqual(reason, "high_risk_requires_verification")

    def test_environment_mismatch_blocks(self):
        aid = self.store.add_assertion("Works on v1", source_quote="Works on v1", speaker="user",
            assertion_kind="OBSERVATION", project_id=self.project, environment={"runtime":"v1"},
            verification_status="TEST_PASSED")
        handoff = build_handoff(self.store, project_id=self.project, task="test", environment={"runtime":"v2"})
        self.assertFalse(any(x["id"] == aid for x in handoff["items"]))
        self.assertTrue(any(x["id"] == aid and x["reason"] == "environment_mismatch" for x in handoff["excluded"]))

    def test_handoff_snapshot_is_persisted(self):
        self.store.add_assertion("A", source_quote="A", speaker="user", assertion_kind="CLAIM",
            project_id=self.project, verification_status="TEST_PASSED")
        result = build_handoff(self.store, project_id=self.project, task="resume", model_id="test-model")
        row = self.store.db.execute("SELECT * FROM handoff_snapshots WHERE id=?", (result["snapshot_id"],)).fetchone()
        self.assertIsNotNone(row)
        self.assertEqual(json.loads(row["snapshot_json"])["task"], "resume")

    def test_delete_requires_user_authorization_and_keeps_tombstone(self):
        sid = self.store.add_raw_source("chat.txt", "private text", project_id=self.project)
        with self.assertRaises(PermissionError):
            self.store.authorized_delete("raw_source", sid, requested_by="model")
        tombstone = self.store.authorized_delete("raw_source", sid, requested_by="user", reason="requested")
        row = self.store.db.execute("SELECT content,tombstone_id FROM raw_sources WHERE id=?", (sid,)).fetchone()
        self.assertIsNone(row["content"])
        self.assertEqual(row["tombstone_id"], tombstone)
        self.assertIsNotNone(self.store.db.execute("SELECT id FROM tombstones WHERE id=?", (tombstone,)).fetchone())

if __name__ == "__main__":
    unittest.main()
