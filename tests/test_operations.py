"""Exercise consistency and failure behavior of the maintenance CLI."""

import importlib.util
from pathlib import Path
import sqlite3

import pytest

spec = importlib.util.spec_from_file_location(
    "manage", Path(__file__).parents[1] / "scripts/manage.py"
)
manage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(manage)


@pytest.fixture
def project(tmp_path, monkeypatch):
    for name in ["data/Zomboid", "data/observer", "secrets", "mods", "references", "backups"]:
        (tmp_path / name).mkdir(parents=True)
    with sqlite3.connect(tmp_path / "data/observer/saved-map.sqlite") as db:
        db.execute("create table metadata(key text primary key, value text)")
        db.execute("insert into metadata values('world', 'AKR_DayOne')")
    (tmp_path / "secrets/positions.token").write_text("test-only")
    monkeypatch.setattr(manage, "ROOT", tmp_path)
    monkeypatch.setattr(manage, "stop", lambda: None)
    return tmp_path


def test_backup_roundtrip_checks_observer_sqlite(project):
    manage.backup()
    archive = next((project / "backups").glob("*.tar.gz"))
    assert archive.stat().st_mode & 0o777 == 0o600
    manage.restore_test(archive)
    restored = next((project / "artifacts/restore-tests").glob("*/data/observer/saved-map.sqlite"))
    with sqlite3.connect(restored) as db:
        assert db.execute("select value from metadata").fetchone() == ("AKR_DayOne",)


def test_failed_archive_is_not_finalized(project, monkeypatch):
    def failure(*args, **kwargs):
        raise OSError("simulated interrupted write")

    monkeypatch.setattr(manage.tarfile, "open", failure)
    with pytest.raises(OSError, match="interrupted"):
        manage.backup()
    assert not list((project / "backups").glob("*.tar.gz"))
    assert not list((project / "backups").glob("*.sha256"))


def test_retention_counts_only_complete_archives(project):
    for i in range(7):
        name = project / "backups" / f"AKR_DayOne-20200101-00000{i}.tar.gz"
        name.write_bytes(b"older-backup")
        name.with_suffix(".gz.sha256").write_text("existing checksum")
    incomplete = project / "backups/AKR_DayOne-20990101-000000.tar.gz"
    incomplete.write_bytes(b"partial")
    manage.backup()
    completed = [
        p for p in (project / "backups").glob("*.tar.gz") if p.with_suffix(".gz.sha256").exists()
    ]
    assert len(completed) == 7
    assert incomplete.exists()


def test_failed_graceful_stop_prevents_backup(project, monkeypatch):
    def fail_stop():
        raise RuntimeError("still running")

    monkeypatch.setattr(manage, "stop", fail_stop)
    with pytest.raises(RuntimeError, match="still running"):
        manage.backup()
    assert not list((project / "backups").iterdir())
