"""Exercise release gates with temporary Git repositories and mocked publishing."""

import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from zipfile import ZipFile

SCRIPTS = Path(__file__).resolve().parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / (name + ".py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


detector = load("detect-release")
verifier = load("verify-release-jar")


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Release Test")
        self.git("config", "user.email", "release@example.invalid")
        self.properties = self.root / "gradle.properties"
        self.properties.write_text("mod_version=1.0.0\narchives_base_name=VillagerVerdict\n")
        self.git("add", ".")
        self.git("commit", "-m", "Initial version")
        self.before = self.git("rev-parse", "HEAD").strip()

    def git(self, *args):
        return detector.git(self.root, *args)

    def detect(self, event="push", before=None, ref="refs/heads/main"):
        return detector.detect(self.root, event, self.before if before is None else before, ref)

    def test_unchanged_version_and_dependency_only_change_skip(self):
        self.properties.write_text(self.properties.read_text() + "loader_version=0.19.5\n")
        self.assertEqual(self.detect()["should_release"], "false")

    def test_version_bump_in_multiple_commit_push(self):
        self.properties.write_text(self.properties.read_text().replace("1.0.0", "1.1.0-beta.1"))
        self.git("commit", "-am", "Bump version")
        self.git("commit", "--allow-empty", "-m", "Another commit")
        result = self.detect()
        self.assertEqual(result["should_release"], "true")
        self.assertEqual(result["tag"], "v1.1.0-beta.1")
        self.assertEqual(result["artifact"], "VillagerVerdict-1.1.0-beta.1.jar")

    def test_manual_and_initial_push_release(self):
        self.assertEqual(self.detect("workflow_dispatch")["should_release"], "true")
        self.assertEqual(self.detect(before="0" * 40)["should_release"], "true")

    def test_missing_previous_properties_is_initial_release(self):
        self.git("rm", "gradle.properties")
        self.git("commit", "-m", "No properties")
        before = self.git("rev-parse", "HEAD").strip()
        self.properties.write_text("mod_version=1.0.0\narchives_base_name=VillagerVerdict\n")
        self.assertEqual(self.detect(before=before)["should_release"], "true")

    def test_reject_non_main_invalid_sha_and_unsafe_metadata(self):
        for event in ("push", "workflow_dispatch"):
            with self.assertRaises(ValueError):
                self.detect(event, ref="refs/heads/feature")
        with self.assertRaises(ValueError):
            self.detect(before="not-a-sha")
        with self.assertRaises(subprocess.CalledProcessError):
            self.detect(before="f" * 40)
        for text in ("mod_version=1.0.0\nmod_version=2.0.0\n",
                     "mod_version=../bad\n", "mod_version=1.0.0\narchives_base_name=../bad\n"):
            self.properties.write_text(text)
            with self.assertRaises(ValueError):
                self.detect()

    def test_verify_jar_metadata_classes_and_test_isolation(self):
        jar_path = self.root / "release.jar"
        metadata = {"id": "villagerverdict", "version": "1.0.0", "environment": "client",
                    "entrypoints": {"main": ["example.Main"], "client": ["example.Client"]}}
        with ZipFile(jar_path, "w") as jar:
            jar.writestr("fabric.mod.json", json.dumps(metadata))
            jar.writestr("example/Main.class", b"main")
            jar.writestr("example/Client.class", b"client")
        verifier.verify(jar_path, "1.0.0")
        with self.assertRaises(ValueError):
            verifier.verify(jar_path, "2.0.0")
        with ZipFile(jar_path, "a") as jar:
            jar.writestr("cn/ericcraft/villagerverdict/ClientIntegrationTest.class", b"test")
        with self.assertRaises(ValueError):
            verifier.verify(jar_path, "1.0.0")

    def test_publish_checks_tags_digest_and_prerelease(self):
        # These executables record calls; no network access or real Release is possible.
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        calls = self.root / "calls"
        for name, script in {
            "git": '#!/bin/bash\nexit "${TAG_STATUS:-2}"\n',
            "gh": '#!/bin/bash\nprintf "%s\\n" "$@" > "$CALLS"\n',
        }.items():
            executable = bin_dir / name
            executable.write_text(script)
            executable.chmod(0o755)
        artifact = "VillagerVerdict-1.0.0.jar"
        content = b"verified build"
        digest = hashlib.sha256(content).hexdigest()
        (self.root / artifact).write_bytes(content)
        (self.root / (artifact + ".sha256")).write_text(digest + "  " + artifact + "\n")
        env = {**os.environ, "PATH": str(bin_dir) + os.pathsep + os.environ["PATH"],
               "GITHUB_SHA": self.before, "CALLS": str(calls)}

        def publish(version="1.0.0", status="2"):
            return subprocess.run(["bash", str(SCRIPTS / "create-release.sh"), version,
                                   str(self.root), artifact, digest], env={**env, "TAG_STATUS": status},
                                  capture_output=True, text=True)

        self.assertEqual(publish().returncode, 0)
        self.assertIn(self.before, calls.read_text())
        self.assertNotIn("--prerelease", calls.read_text())
        self.assertEqual(publish("1.1.0-beta.1").returncode, 0)
        self.assertIn("--latest=false", calls.read_text())
        for status in ("0", "128"):
            calls.unlink(missing_ok=True)
            self.assertNotEqual(publish(status=status).returncode, 0)
            self.assertFalse(calls.exists())
        (self.root / artifact).write_bytes(b"tampered")
        self.assertNotEqual(publish().returncode, 0)
        self.assertFalse(calls.exists())
        tampered_digest = hashlib.sha256(b"tampered").hexdigest()
        (self.root / (artifact + ".sha256")).write_text(tampered_digest + "  " + artifact + "\n")
        self.assertNotEqual(publish().returncode, 0)
        self.assertFalse(calls.exists())


if __name__ == "__main__":
    unittest.main()
