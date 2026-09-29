"""First Week build, disposable-world operations and explicit fresh-world preparation."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import socket
import sqlite3
import time
import uuid

SPAWN = (10753, 9839, 0)  # Ground-floor living room, verified in installed 41_38.lotheader.
BANDITS_SOURCE = '/pzserver/steamapps/workshop/content/108600/3268487204/mods/Bandits/42.20/media/lua/client/BanditUpdate.lua'


def replace_ini(text, changes):
    remaining = dict(changes)
    lines = []
    for line in text.splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key = line.split('=', 1)[0]
            if key in remaining:
                line = f'{key}={remaining.pop(key)}'
        lines.append(line)
    lines.extend(f'{key}={value}' for key, value in remaining.items())
    return '\n'.join(lines) + '\n'


def scenario_sandbox(text):
    values = {
        'PopulationMultiplier': '0.0', 'PopulationStartMultiplier': '0.0',
        'PopulationPeakMultiplier': '0.0', 'RespawnHours': '0.0',
        'Helicopter': '1', 'MetaEvent': '1', 'SleepingEvent': '1',
        'SurvivorHouseChance': '1', 'VehicleStoryChance': '1', 'ZoneStoryChance': '1',
        'Transmission': '4', 'Mortality': '7',
    }
    # Preserve the native spawning mechanism for explicitly permitted civilians.
    for key, value in values.items():
        text, count = re.subn(r'(?m)^(\s*' + re.escape(key) + r'\s*=\s*)[^,\n]+,',
                              lambda m: m[1] + value + ',', text)
        if count != 1:
            raise RuntimeError(f'Expected exactly one sandbox {key}; found {count}')
    if re.search(r'(?m)^\s*LofersScenario\s*=', text):
        raise RuntimeError('Sandbox already contains First Week settings; refusing duplicate insertion')
    end = text.rfind('}')
    if end < 0:
        raise RuntimeError('Malformed sandbox source')
    return text[:end] + ('    LofersScenario = { Enabled = true },\n') + text[end:]


def write_private(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    path.chmod(0o600)


def build(m):
    m.run([m.PYTHON, m.ROOT / 'scripts/build_npc_service.py', '--bundle'])
    m.run([m.PYTHON, m.ROOT / 'scripts/build_scenario_agent.py', '--test'])
    m.run([m.PYTHON, m.ROOT / 'scripts/build_scenario_map.py'])
    (m.ROOT / 'artifacts/scenario-agent/scenario.properties').write_text(
        f'side=server\nscenario.enabled=true\nworld={m.WORLD}\nsocket=/run/lofers/npc.sock\n'
        f'bandits_update_file={BANDITS_SOURCE}\n')
    m.compose('--profile', 'scenario', 'build', 'npc')


def test_receipt(m):
    current = m.ROOT / 'artifacts/scenario-tests/current.json'
    if not current.is_file():
        raise RuntimeError('No disposable scenario exists; run scenario-test-create')
    receipt = json.loads(current.read_text())
    path = Path(receipt['path']).resolve()
    if not path.is_relative_to((m.ROOT / 'artifacts/scenario-tests').resolve()):
        raise RuntimeError('Test path is outside project scenario-tests')
    return receipt, path


def create_test(m):
    base = m.ROOT / 'artifacts/scenario-tests'
    base.mkdir(parents=True, exist_ok=True)
    if (base / 'current.json').exists():
        previous, _ = test_receipt(m)
        result = m.run([m.PODMAN, 'ps', '--filter', f"name={previous['container']}", '--format', '{{.ID}}'], capture=True)
        if result.stdout.strip():
            raise RuntimeError('Stop the current disposable server before preparing another')
    stamp = time.strftime('%Y%m%d-%H%M%S', time.gmtime()) + '-' + uuid.uuid4().hex[:6]
    world = 'AKR_DayOne_Test_' + stamp.replace('-', '_')
    target = base / stamp
    target.mkdir(mode=0o700)
    cachedir = target / 'Zomboid'
    server = cachedir / 'Server'
    server.mkdir(parents=True)
    source = m.ROOT / 'data/Zomboid/Server'
    ini = (source / f'{m.WORLD}.ini').read_text()
    ini = replace_ini(ini, {
        'DefaultPort': 16281, 'UDPPort': 16282, 'RCONPort': 27035,
        'Public': 'false', 'PublicName': 'Lofers First Week validation',
        'SpawnPoint': ','.join(map(str, SPAWN)), 'PauseEmpty': 'true',
    })
    write_private(server / f'{world}.ini', ini)
    write_private(server / f'{world}_SandboxVars.lua', scenario_sandbox((source / f'{m.WORLD}_SandboxVars.lua').read_text()))
    (server / f'{world}_spawnregions.lua').write_text('function SpawnRegions() return { { name="Muldraugh, KY", file="media/maps/Muldraugh, KY/spawnpoints.lua" } } end\n')
    shutil.copytree(m.ROOT / 'mods/LofersStoryteller', cachedir / 'mods/LofersStoryteller')
    config = json.loads((m.ROOT / 'data/game-files/ProjectZomboid64.json').read_text())
    config['vmArgs'] = [a for a in config['vmArgs'] if not a.startswith('-Xmx')] + ['-Xmx3g']
    (target / 'ProjectZomboid64.json').write_text(json.dumps(config, indent=2) + '\n')
    (target / 'ipc').mkdir(mode=0o700)
    (target / 'scenario.properties').write_text(f'side=server\nscenario.enabled=true\nworld={world}\nsocket=/run/lofers/npc.sock\nbandits_update_file={BANDITS_SOURCE}\n')
    accounts = m.ROOT / f'data/Zomboid/db/{m.WORLD}.db'
    if accounts.is_file():
        dbpath = cachedir / 'db' / f'{world}.db'
        dbpath.parent.mkdir(exist_ok=True)
        with sqlite3.connect(f'file:{accounts}?mode=ro', uri=True) as src, sqlite3.connect(dbpath) as dst:
            src.backup(dst)
            dst.execute('UPDATE whitelist SET world=?', (world,))
            dst.execute("UPDATE whitelist SET role=(SELECT id FROM role WHERE name='admin') WHERE username='akr'")
        dbpath.chmod(0o600)
    receipt = {'world': world, 'path': str(target), 'container': 'lofers-scenario-test',
               'worker_container': 'lofers-scenario-test-npc', 'ports': [16281, 16282, 27035],
               'spawn': SPAWN, 'created_at': stamp, 'kind': 'disposable', 'validation': 'not_started'}
    write_private(target / 'receipt.json', json.dumps(receipt, indent=2) + '\n')
    write_private(base / 'current.json', json.dumps(receipt, indent=2) + '\n')
    print(f'Prepared fresh disposable world {world} at {target}; playable prototype untouched.')


def free_ports():
    sockets = []
    try:
        for port, kind in [(16281, socket.SOCK_DGRAM), (16282, socket.SOCK_DGRAM), (27035, socket.SOCK_STREAM)]:
            sock = socket.socket(socket.AF_INET, kind)
            sockets.append(sock)
            if kind == socket.SOCK_STREAM:
                # Ignore closed RCON connections in TIME_WAIT; live listeners still conflict.
                sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.bind(('0.0.0.0', port))
    finally:
        for sock in sockets:
            sock.close()


def start_test(m):
    receipt, target = test_receipt(m)
    jar = m.ROOT / 'artifacts/scenario-agent/lofers-scenario-agent.jar'
    if not jar.is_file():
        raise RuntimeError('Build the scenario agent first')
    free_ports()
    for name in (receipt['container'], receipt['worker_container']):
        result = m.run([m.PODMAN, 'ps', '--filter', f'name={name}', '--format', '{{.ID}}'], capture=True)
        if result.stdout.strip():
            raise RuntimeError(f'{name} already running')
        m.run([m.PODMAN, 'rm', name], capture=True, check=False)
    # Refresh only the stopped disposable world's original mod.
    mod = target / 'Zomboid/mods/LofersStoryteller'
    shutil.rmtree(mod)
    shutil.copytree(m.ROOT / 'mods/LofersStoryteller', mod)
    m.run([m.PODMAN, 'run', '-d', '--name', receipt['worker_container'], '--user', '1000:1000',
           '--userns=keep-id', '--memory=512m', '--cpus=2', '--network=none', '--read-only',
           '-v', f'{target}/ipc:/run/lofers:z',
           '-v', f'{m.ROOT}/artifacts/scenario-map:/opt/lofers/map:ro,z',
           'localhost/zomboid-dayone_npc:latest', '--socket', '/run/lofers/npc.sock',
           '--world', receipt['world'], '--rules', '/opt/lofers/rules', '--workers', '2',
           '--map-index', '/opt/lofers/map/map-index.pb'])
    try:
        m.run([m.PODMAN, 'run', '-d', '--name', receipt['container'], '--userns=keep-id',
               '--memory=5g', '--cpus=4',
               '-p', '192.168.1.132:16281:16281/udp', '-p', '192.168.1.132:16282:16282/udp',
               '-p', '127.0.0.1:27035:27035/tcp',
               '-e', f"PZ_WORLD={receipt['world']}",
               '-e', 'JAVA_TOOL_OPTIONS=-javaagent:/opt/scenario/lofers-scenario-agent.jar=/opt/scenario/scenario.properties',
               '-v', f'{m.ROOT}/data/game-files:/pzserver:z',
               '-v', f'{target}/ProjectZomboid64.json:/pzserver/ProjectZomboid64.json:ro,z',
               '-v', f'{target}/Zomboid:/home/pzuser/Zomboid:z',
               '-v', f'{jar}:/opt/scenario/lofers-scenario-agent.jar:ro,z',
               '-v', f'{target}/scenario.properties:/opt/scenario/scenario.properties:ro,z',
               '-v', f'{target}/ipc:/run/lofers:z',
               '-v', f'{m.ROOT}/secrets/admin-password:/run/secrets/admin-password:ro,z',
               '-v', f'{m.ROOT}/game/entrypoint.sh:/home/pzuser/entrypoint.sh:ro,z',
               'localhost/zomboid-dayone_game:latest'])
    except BaseException:
        m.run([m.PODMAN, 'stop', receipt['worker_container']], capture=True, check=False)
        raise
    print(f"Disposable server starting: 192.168.1.132:16281 ({receipt['world']})")


def test_rcon(m, command):
    import importlib.util
    spec = importlib.util.spec_from_file_location('scenario_rcon', m.ROOT / 'game/rcon.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.rcon_command('127.0.0.1', 27035, m.credentials()['rcon_password'], command, timeout=15)


def stop_test(m):
    receipt, _ = test_receipt(m)
    active = m.run([m.PODMAN, 'ps', '--filter', f"name=^{receipt['container']}$", '--format', '{{.ID}}'], capture=True).stdout.strip()
    if active:
        try:
            print(m.redact(test_rcon(m, 'quit')))
        except ConnectionError:
            pass
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            active = m.run([m.PODMAN, 'inspect', '-f', '{{.State.Running}}', receipt['container']], capture=True).stdout.strip()
            if active == 'false':
                break
            time.sleep(2)
        else:
            raise RuntimeError('Disposable server did not stop gracefully; not killing it')
    m.run([m.PODMAN, 'stop', receipt['worker_container']], capture=True, check=False)


def archive_reset(m):
    """Called only after the recorded playable integration gate has passed."""
    evidence = m.ROOT / 'artifacts/scenario-tests/acceptance.json'
    if not evidence.is_file():
        raise RuntimeError('Missing recorded two-client scenario acceptance; prototype preserved')
    result = json.loads(evidence.read_text())
    required = {'calm_opening', 'civilian_routine', 'civilian_driving', 'emergency_driving',
                'replication', 'handoff', 'restart', 'outbreak'}
    if result.get('passed') is not True or len(set(result.get('clients', []))) < 2 or not required.issubset(result.get('checks_passed', [])):
        raise RuntimeError('Scenario acceptance is incomplete; prototype preserved')
    m.backup()
    stamp = time.strftime('%Y%m%d-%H%M%S', time.gmtime())
    archive = m.ROOT / 'backups' / ('prototype-' + stamp)
    archive.mkdir(mode=0o700)
    save = m.ROOT / 'data/Zomboid/Saves/Multiplayer' / m.WORLD
    if save.exists():
        shutil.move(str(save), archive / 'world')
    observer = m.ROOT / 'data/observer'
    if observer.exists():
        shutil.move(str(observer), archive / 'observer')
    observer.mkdir()
    server = m.ROOT / 'data/Zomboid/Server'
    for suffix in ['.ini', '_SandboxVars.lua', '_spawnregions.lua']:
        path = server / (m.WORLD + suffix)
        shutil.copy2(path, archive / path.name)
    ini = replace_ini((server / f'{m.WORLD}.ini').read_text(),
                      {'SpawnPoint': ','.join(map(str, SPAWN)), 'PublicName': 'Lofers First Week'})
    write_private(server / f'{m.WORLD}.ini', ini)
    write_private(server / f'{m.WORLD}_SandboxVars.lua', scenario_sandbox((server / f'{m.WORLD}_SandboxVars.lua').read_text()))
    (server / f'{m.WORLD}_spawnregions.lua').write_text('function SpawnRegions() return { { name="Muldraugh, KY", file="media/maps/Muldraugh, KY/spawnpoints.lua" } } end\n')
    write_private(m.ROOT / 'game/scenario.json', json.dumps({'enabled': True, 'world': m.WORLD, 'archive': str(archive)}, indent=2) + '\n')
    (m.ROOT / 'data/npc-ipc').mkdir(mode=0o700, exist_ok=True)
    print(f'Archived prototype at {archive}; fresh First Week configured. Accounts retained. Services stopped.')


def dispatch(m, command, args):
    if command == 'scenario-build': build(m)
    elif command == 'scenario-test-create': create_test(m)
    elif command == 'scenario-test-start': start_test(m)
    elif command == 'scenario-test-stop': stop_test(m)
    elif command == 'scenario-test-rcon':
        if not args: raise RuntimeError('scenario-test-rcon requires a command')
        print(m.redact(test_rcon(m, ' '.join(args))))
    elif command == 'scenario-reset': archive_reset(m)
    else: raise RuntimeError('Unknown scenario operation')
