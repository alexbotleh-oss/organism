import importlib
import json
import os
import sys
import tempfile
import threading
import unittest
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path


class CoreApiTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.old_db_path = os.environ.get("ORGANISM_DB_PATH")
        os.environ["ORGANISM_DB_PATH"] = str(Path(self.temp.name) / "api-organism.db")
        sys.modules.pop("organism_server", None)
        self.app = importlib.import_module("organism_server")
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), self.app.Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = "http://127.0.0.1:%d" % self.server.server_address[1]

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)
        sys.modules.pop("organism_server", None)
        if self.old_db_path is None:
            os.environ.pop("ORGANISM_DB_PATH", None)
        else:
            os.environ["ORGANISM_DB_PATH"] = self.old_db_path
        self.temp.cleanup()

    def request_json(self, path, payload=None):
        if payload is None:
            request = urllib.request.Request(self.base + path, method="GET")
        else:
            request = urllib.request.Request(
                self.base + path,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json"},
                method="POST",
            )
        with urllib.request.urlopen(request, timeout=5) as response:
            return json.loads(response.read().decode("utf-8"))

    def test_core_status_endpoint(self):
        result = self.request_json("/api/core/status")
        self.assertTrue(result["ok"])
        self.assertEqual(result["core_version"], "0.4")
        self.assertIn("events", result["stats"])

    def test_raw_import_and_handoff_endpoints(self):
        imported = self.request_json("/api/core/import-text", {
            "source_name": "api-test.txt",
            "text": "User checked this manually",
            "source_kind": "test",
            "speaker": "user",
            "origin": "external",
        })
        self.assertTrue(imported["ok"])
        handoff = self.request_json("/api/core/handoff", {
            "project_id": "PRJ_API",
            "task": "continue integration",
            "environment": {"platform": "test"},
            "model_name": "test-model",
        })
        self.assertTrue(handoff["ok"])
        self.assertEqual(handoff["result"]["payload"]["task"], "continue integration")
        self.assertTrue(handoff["result"]["application_id"])


if __name__ == "__main__":
    unittest.main()
