"""Distribution boundaries and corruption checks, independent of the game runtime."""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import zipfile

SPEC = importlib.util.spec_from_file_location(
    "dist_packager", Path(__file__).parents[1] / "scripts/dist.py"
)
dist = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(dist)


class DistributionTests(unittest.TestCase):
    def test_symlink_cannot_import_secret(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "private").write_text("secret")
            (root / "source").symlink_to(root / "private")
            with self.assertRaises(ValueError):
                dist.copy_file(root, Path("source"), root / "out")
            self.assertFalse((root / "out").exists())

    def test_parent_symlink_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "private").mkdir()
            (root / "private/value").write_text("secret")
            (root / "link").symlink_to(root / "private", target_is_directory=True)
            with self.assertRaises(ValueError):
                dist.copy_file(root, Path("link/value"), root / "out")

    def fixture(self, root):
        for side in ("server", "client"):
            folder = root / side
            folder.mkdir()
            (folder / "file").write_text("original")
            dist.write_checksums(folder)
        dist.write_checksums(root)

    def test_verify_detects_changed_and_unlisted_payload(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.fixture(root)
            dist.verify(root)
            (root / "client/file").write_text("changed")
            with self.assertRaises(ValueError):
                dist.verify(root)
            (root / "client/file").write_text("original")
            (root / "client/secret").write_text("must not sneak in")
            with self.assertRaises(ValueError):
                dist.verify(root)

    def test_zip_is_repeatable_and_keeps_executable(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "server"
            folder.mkdir()
            (folder / "common").mkdir()
            (folder / "dayone").write_text("#!/bin/sh\n")
            (folder / "dayone").chmod(0o755)
            dist.archive(folder, root / "one.zip")
            (folder / "dayone").touch()
            dist.archive(folder, root / "two.zip")
            self.assertEqual(dist.digest(root / "one.zip"), dist.digest(root / "two.zip"))
            with zipfile.ZipFile(root / "one.zip") as archive:
                self.assertTrue(archive.getinfo("server/common/").is_dir())
                self.assertEqual(
                    (archive.getinfo("server/dayone").external_attr >> 16) & 0o777, 0o755
                )

    def test_mixed_build_and_scope_rejected(self):
        source = Path(__file__).parents[1]
        profile = json.loads((source / "compatibility/profiles/pz42.21.json").read_text())
        manifest = {
            "compatibility_profile": profile["id"],
            "game_build": profile["build"],
            "game_jar_sha256": profile["jar_sha256"],
            "guard_scope": "disposable-worlds-only",
            "bandits_update_sha256": profile["dependency_pins"]["BanditUpdate.lua"],
        }
        self.assertEqual(dist.validate_profile(source, manifest)["id"], "pz42.21")
        for key in manifest:
            with self.subTest(key=key), self.assertRaises(ValueError):
                dist.validate_profile(source, {**manifest, key: "stale"})

    def test_unknown_dist_not_overwritten(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "dist").mkdir()
            (root / "dist/important").write_text("keep")
            with self.assertRaises(ValueError):
                dist.build(root)
            self.assertEqual((root / "dist/important").read_text(), "keep")


if __name__ == "__main__":
    unittest.main()
