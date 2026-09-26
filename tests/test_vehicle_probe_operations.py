"""Route boundaries and stopped-only reconfiguration; no live game is touched."""
import importlib.util
import json
import hashlib
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import sys
from types import SimpleNamespace
from threading import Event, Lock

import pytest

SCRIPTS = Path(__file__).parents[1] / 'scripts'
sys.path.insert(0, str(SCRIPTS))
spec = importlib.util.spec_from_file_location('vehicle_probe_ops', SCRIPTS / 'vehicle_probe_ops.py')
ops = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ops)


@pytest.fixture
def probe(tmp_path):
    target = tmp_path / 'artifacts/vehicle-probe/test'
    target.mkdir(parents=True)
    config = target / 'scenario.properties'
    config.write_text('world=LofersVehicleProbe_test\nvehicle_probe.x=10\nvehicle_probe.y=10\nprivate_key=unchanged\n')
    receipt = {'world': 'LofersVehicleProbe_test', 'path': str(target), 'container': 'lofers-vehicle-probe'}
    (target.parent / 'current.json').write_text(json.dumps(receipt))
    route = tmp_path / 'artifacts/route.json'
    route.write_text(json.dumps({'waypoints': [{'x': 100, 'y': 100}, {'x': 110, 'y': 100}, {'x': 110, 'y': 90}]}))
    manager = SimpleNamespace(ROOT=tmp_path, PODMAN='test-podman', run=lambda *a, **k: SimpleNamespace(stdout=''))
    return manager, target, route


def test_active_probe_route_change_is_rejected_without_config_mutation(probe):
    manager, target, route = probe
    manager.run = lambda *a, **k: SimpleNamespace(stdout='running-id')
    before = (target / 'scenario.properties').read_bytes()
    with pytest.raises(RuntimeError, match='Stop the vehicle probe'):
        ops.configure_route(manager, route)
    assert (target / 'scenario.properties').read_bytes() == before
    assert not (target / 'routes').exists()


@pytest.mark.parametrize('points', [
    [{'x': 1, 'y': 1}],
    [{'x': 0, 'y': 0}, {'x': 100, 'y': 0}],
    [{'x': 0, 'y': 0}, {'x': float('nan'), 'y': 1}],
    [{'x': True, 'y': 0}, {'x': 10, 'y': 0}],
    [{'x': 0, 'y': 0}, {'x': 0, 'y': 0}],
    [{'x': 0, 'y': 0}, {'x': 10, 'y': 0, 'z': 1}],
    [{'x': 0, 'y': 0}, {'x': 10, 'y': 0, 'z': False}],
])
def test_invalid_route_does_not_modify_configuration(probe, points):
    manager, target, route = probe
    route.write_text(json.dumps({'waypoints': points}))
    before = (target / 'scenario.properties').read_bytes()
    with pytest.raises(ValueError):
        ops.configure_route(manager, route)
    assert (target / 'scenario.properties').read_bytes() == before


def test_route_reconfiguration_keeps_private_settings_and_reviewed_provenance(probe):
    manager, target, route = probe
    before = (target / 'scenario.properties').read_bytes()
    ops.configure_route(manager, route)
    settings = ops.properties(target / 'scenario.properties')
    assert settings['private_key'] == 'unchanged'
    assert settings['world'] == 'LofersVehicleProbe_test'
    assert float(settings['vehicle_probe.heading_degrees']) == 90
    assert settings['vehicle_probe.waypoints'].startswith('100.00000000,100.00000000;')
    record = next((target / 'routes').iterdir())
    assert (record / 'route.json').read_bytes() == route.read_bytes()
    assert (record / 'previous.properties').read_bytes() == before
    assert (target / 'scenario.properties').stat().st_mode & 0o777 == 0o600
    ops.configure_route(manager, route)
    assert (record / 'previous.properties').read_bytes() == before


def test_concurrent_start_and_route_change_cannot_replace_launch_configuration(probe, monkeypatch):
    manager, target, route = probe
    (target / 'agent').mkdir()
    artifact = manager.ROOT / 'artifacts/scenario-agent'
    artifact.mkdir()
    (artifact / 'lofers-scenario-agent.jar').write_bytes(b'fixture-only')
    (artifact / 'manifest.json').write_text(json.dumps({'jar_sha256': hashlib.sha256(b'fixture-only').hexdigest()}))
    monkeypatch.setattr(ops.scenario, 'free_ports', lambda: None)
    launching, release, second_lock = Event(), Event(), Event()
    count_lock = Lock()
    lock_count = 0
    flock = ops.fcntl.flock
    def tracked_flock(fd, operation):
        nonlocal lock_count
        if operation == ops.fcntl.LOCK_EX:
            with count_lock:
                lock_count += 1
                if lock_count == 2:
                    second_lock.set()
        return flock(fd, operation)
    monkeypatch.setattr(ops.fcntl, 'flock', tracked_flock)
    active = False
    def run(command, **kwargs):
        nonlocal active
        if command[1] == 'ps':
            return SimpleNamespace(stdout='running-id' if active else '')
        if command[1] == 'run':
            launching.set()
            assert release.wait(3), 'Fixture launch was not released'
            active = True
        return SimpleNamespace(stdout='')
    manager.run = run
    before = (target / 'scenario.properties').read_bytes()
    with ThreadPoolExecutor(max_workers=2) as pool:
        start = pool.submit(ops.start, manager)
        assert launching.wait(3)
        configure = pool.submit(ops.configure_route, manager, route)
        try:
            assert second_lock.wait(3)
            assert not configure.done()
        finally:
            release.set()
        start.result(timeout=3)
        with pytest.raises(RuntimeError, match='Stop the vehicle probe'):
            configure.result(timeout=3)
    assert (target / 'scenario.properties').read_bytes() == before
