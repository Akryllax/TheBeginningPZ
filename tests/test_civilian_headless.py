"""Deployment boundary checks; the separate native run supplies actual engine evidence."""
import hashlib
import json
from pathlib import Path
from types import SimpleNamespace

import pytest
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'scripts'))
import civilian_headless as headless


def test_prepare_keeps_existing_world_receipt_and_pins_isolated_config(tmp_path, monkeypatch):
    root = tmp_path
    source = root / 'data/Zomboid/Server'
    source.mkdir(parents=True)
    (source / 'normal.ini').write_text('DefaultPort=16271\nRCONPort=27025\nMods=Original\nPauseEmpty=true\n')
    keys = ['PopulationMultiplier', 'PopulationStartMultiplier', 'PopulationPeakMultiplier', 'RespawnHours',
            'Helicopter', 'MetaEvent', 'SleepingEvent', 'SurvivorHouseChance', 'VehicleStoryChance',
            'ZoneStoryChance', 'Transmission', 'Mortality']
    (source / 'normal_SandboxVars.lua').write_text('SandboxVars={\n' + ''.join(f' {k}=1,\n' for k in keys) + '}\n')
    game = root / 'data/game-files'
    game.mkdir(parents=True)
    (game / 'ProjectZomboid64.json').write_text(json.dumps({'vmArgs': ['-Xmx8g', '-Xms2g']}))
    for name in ('AKRCore', 'AKRPopulation', 'AKRResidents'):
        (root / 'mods' / name).mkdir(parents=True)
    agent = root / 'artifacts/scenario-agent'
    agent.mkdir(parents=True)
    (agent / 'lofers-scenario-agent.jar').write_bytes(b'fixture')
    (agent / 'manifest.json').write_text(json.dumps({'jar_sha256': hashlib.sha256(b'fixture').hexdigest()}))
    old = root / 'artifacts/scenario-tests/current.json'
    old.parent.mkdir(parents=True)
    old.write_text('existing-test-session')
    class Socket:
        def __enter__(self): return self
        def __exit__(self, *args): pass
        def setsockopt(self, *args): pass
        def bind(self, address): assert address == ('127.0.0.1', 27045)
    monkeypatch.setattr(headless.socket, 'socket', Socket)
    m = SimpleNamespace(ROOT=root, WORLD='normal', PODMAN='podman', run=lambda *a, **k: SimpleNamespace(stdout=''))
    data, target = headless.prepare(m)
    assert old.read_text() == 'existing-test-session'
    assert (source / 'normal.ini').read_text().startswith('DefaultPort=16271')
    ini = (target / 'Zomboid/Server' / (data['world'] + '.ini')).read_text()
    assert 'PauseEmpty=false' in ini and 'DefaultPort=16291' in ini and 'Open=false' in ini
    assert data['container'] == 'akr-civilian-headless' and data['clients'] == 0
    assert 'headless.enabled=true' in (target / 'scenario.properties').read_text()
    assert json.loads((target / 'ProjectZomboid64.json').read_text())['vmArgs'] == ['-Xms512m', '-Xmx3g']


def test_invalid_command_does_not_operate_services():
    with pytest.raises(RuntimeError, match='Usage'):
        headless.dispatch(None, ['deploy'])


def test_receipt_cannot_target_a_different_container(tmp_path):
    base = tmp_path / 'artifacts/civilian-headless'
    base.mkdir(parents=True)
    (base / 'current.json').write_text(json.dumps({'path': str(base / 'x'), 'container': 'lofers-scenario-test'}))
    with pytest.raises(RuntimeError, match='Invalid headless receipt'):
        headless.receipt(SimpleNamespace(ROOT=tmp_path))
