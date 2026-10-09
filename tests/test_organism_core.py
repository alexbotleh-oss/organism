import json
import tempfile
import unittest
from pathlib import Path

from organism_core import OrganismCore


class OrganismCoreTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.core = OrganismCore(Path(self.temp.name) / "organism.db")
        imported = self.core.import_text(
            "chat.txt",
            "Пользователь: запускаем тест\nМодель: попробуйте вариант X\nПользователь: тест прошёл",
            completeness="declared_complete",
        )
        self.source_id = imported["source_id"]
        self.raw_ids = imported["segment_ids"]

    def test_raw_import_keeps_exact_payload_and_hash(self):
        snap = self.core.export_snapshot()
        source = next(x for x in snap["source_documents"] if x["id"] == self.source_id)
        self.assertEqual(source["raw_text"], "Пользователь: запускаем тест\nМодель: попробуйте вариант X\nПользователь: тест прошёл")
        self.assertEqual(len([x for x in snap["raw_segments"] if x["source_id"] == self.source_id]), 3)

    def test_model_generated_content_cannot_self_verify(self):
        with self.assertRaises(ValueError):
            self.core.add_claim(
                "X is true", project_id="P1", source_kind="model",
                origin="organism_generated", verification_status="verified",
            )

    def test_model_review_cannot_verify_real_world_claim(self):
        claim = self.core.add_claim("The build succeeds", project_id="P1", source_kind="model")
        with self.assertRaises(ValueError):
            self.core.verify(
                "claim", claim, method="second model agreed", channel="model_review",
                outcome="verified", description="A second model repeated the claim",
            )

    def test_user_satisfaction_is_not_verification(self):
        claim = self.core.add_claim("The app works", project_id="P1", source_kind="user_report")
        with self.assertRaises(ValueError):
            self.core.verify(
                "claim", claim, method="user said thanks", channel="user_report",
                outcome="verified", description="User said thanks",
            )

    def test_handoff_only_contains_verified_claims_and_matching_environment(self):
        good = self.core.add_claim(
            "Verified fact", project_id="P1", source_kind="artifact",
            claim_status="active", verification_status="verified",
            environment={"python": "3.12"},
        )
        self.core.add_claim(
            "Unverified advice", project_id="P1", source_kind="model",
            claim_status="active", verification_status="unverified",
            environment={"python": "3.12"},
        )
        self.core.add_claim(
            "Fact from different environment", project_id="P1", source_kind="artifact",
            claim_status="active", verification_status="verified",
            environment={"python": "3.8"},
        )
        result = self.core.build_handoff(
            project_id="P1", task="test handoff", environment={"python": "3.12"},
        )
        claims = result["payload"]["verified_claims"]
        self.assertEqual([x["id"] for x in claims], [good])
        self.assertEqual(result["sha256"], self.core.export_snapshot()["applications"][0]["handoff_sha256"])

    def test_experience_requires_context_action_outcome_and_method(self):
        with self.assertRaises(ValueError):
            self.core.add_experience(
                "Incomplete", project_id="P1", conditions={}, action_taken="do X",
                observed_outcome="", verification_method="manual check",
            )

    def test_application_outcome_is_logged(self):
        handoff = self.core.build_handoff(project_id="P1", task="test")
        self.core.record_application_outcome(handoff["application_id"], outcome="partial", notes="one check passed")
        app = self.core.export_snapshot()["applications"][0]
        self.assertEqual(app["outcome"], "partial")
        events = self.core.export_snapshot()["events"]
        self.assertTrue(any(e["event_type"] == "APPLICATION_OUTCOME_RECORDED" for e in events))

    def test_delete_requires_explicit_authorizer_and_redacts_source(self):
        with self.assertRaises(ValueError):
            self.core.authorize_delete("raw_segment", self.raw_ids[0], authorized_by="", reason="delete")
        tombstone = self.core.authorize_delete(
            "raw_segment", self.raw_ids[1], authorized_by="user", reason="user requested deletion",
        )
        snap = self.core.export_snapshot()
        segment = next(x for x in snap["raw_segments"] if x["id"] == self.raw_ids[1])
        source = next(x for x in snap["source_documents"] if x["id"] == self.source_id)
        self.assertIn(tombstone, [x["id"] for x in snap["tombstones"]])
        self.assertEqual(segment["content"], "[REDACTED BY USER REQUEST]")
        self.assertNotIn("попробуйте вариант X", source["raw_text"])
        self.assertTrue(any(e["event_type"] == "USER_AUTHORIZED_REDACTION" for e in snap["events"]))

    def test_event_log_is_append_only(self):
        with self.core._connect() as conn:
            event_id = conn.execute("SELECT id FROM events LIMIT 1").fetchone()["id"]
        with self.assertRaises(Exception):
            with self.core._connect() as conn:
                conn.execute("DELETE FROM events WHERE id=?", (event_id,))

    def test_directive_is_in_handoff(self):
        self.core.add_directive("Do not rewrite the whole file", project_id="P1")
        handoff = self.core.build_handoff(project_id="P1", task="edit file")
        self.assertEqual(handoff["payload"]["directives"][0]["directive"], "Do not rewrite the whole file")


if __name__ == "__main__":
    unittest.main()
