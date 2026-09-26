#!/usr/bin/env python3
"""Project-local tools and operations. No production-host mutations."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import tarfile
import time
import urllib.request
import zipfile


def scenario_enabled():
    config = ROOT / 'game/scenario.json'
    return config.is_file() and json.loads(config.read_text()).get('enabled') is True

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tooling'
PYTHON = TOOLS / 'venv/bin/python'
PODMAN = ROOT / 'scripts/podman-local'
COMPOSE = ['podman-compose', '--podman-path', str(PODMAN), '--in-pod', 'false',
           '-p', 'zomboid-dayone', '-f', str(ROOT / 'compose.yaml')]
WORLD = 'AKR_DayOne'


def environment():
    env = os.environ.copy()
    env.update({
        'PATH': f'{TOOLS}/node/bin:{TOOLS}/bin:{env["PATH"]}',
        'UV_CACHE_DIR': str(TOOLS / 'uv-cache'),
        'UV_PYTHON_INSTALL_DIR': str(TOOLS / 'python'),
        'UV_PYTHON_BIN_DIR': str(TOOLS / 'bin'),
        'UV_TOOL_DIR': str(TOOLS / 'uv-tools'),
        'npm_config_cache': str(TOOLS / 'npm-cache'),
        'PLAYWRIGHT_BROWSERS_PATH': str(TOOLS / 'browsers'),
        'TMPDIR': str(TOOLS / 'tmp'),
        'LOFERS_SCENARIO_AGENT_OPTIONS': (
            '-javaagent:/opt/scenario/lofers-scenario-agent.jar=/opt/scenario/scenario.properties'
            if scenario_enabled() else ''),
    })
    (TOOLS / 'tmp').mkdir(parents=True, exist_ok=True)
    return env


def run(args, *, cwd=ROOT, capture=False, check=True):
    return subprocess.run(list(map(str, args)), cwd=cwd, env=environment(),
                          text=True, capture_output=capture, check=check)


def compose(*args, **kwargs):
    return run([*COMPOSE, *args], **kwargs)


def credentials():
    return json.loads((ROOT / 'secrets/credentials.json').read_text())


def redact(value):
    for secret in credentials().values():
        value = value.replace(secret, '[REDACTED]')
    return value


def rcon(command):
    spec = importlib.util.spec_from_file_location('dayone_rcon', ROOT / 'game/rcon.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.rcon_command('127.0.0.1', 27025, credentials()['rcon_password'], command, timeout=15)


def container_id(service):
    return run([PODMAN, 'ps', '-a', '--filter', 'label=com.docker.compose.project=zomboid-dayone',
                '--filter', f'label=com.docker.compose.service={service}',
                '--format', '{{.ID}}'], capture=True).stdout.strip()


def running(service):
    cid = container_id(service)
    if not cid:
        return False
    return run([PODMAN, 'inspect', '-f', '{{.State.Running}}', cid], capture=True).stdout.strip() == 'true'


def bootstrap():
    downloads = json.loads((ROOT / 'references/tool-downloads.json').read_text())
    for name, metadata in downloads.items():
        target = TOOLS / 'downloads' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        if not target.exists():
            with urllib.request.urlopen(metadata['url'], timeout=90) as src, target.open('wb') as dst:
                shutil.copyfileobj(src, dst)
        if hashlib.file_digest(target.open('rb'), 'sha256').hexdigest() != metadata['sha256']:
            raise RuntimeError(f'Tool archive checksum mismatch: {name}')
        if name.startswith('node-') and not (TOOLS / 'node/bin/node').exists():
            with tarfile.open(target) as archive:
                prefix = archive.getmembers()[0].name.split('/')[0]
                archive.extractall(TOOLS / 'tmp', filter='data')
            shutil.move(TOOLS / 'tmp' / prefix, TOOLS / 'node')
        elif name.startswith('uv-') and not (TOOLS / 'bin/uv').exists():
            with tarfile.open(target) as archive:
                archive.extractall(TOOLS / 'tmp', filter='data')
            (TOOLS / 'bin').mkdir(exist_ok=True)
            for tool in ['uv', 'uvx']:
                shutil.copy2(TOOLS / 'tmp/uv-x86_64-unknown-linux-gnu' / tool, TOOLS / 'bin' / tool)
        elif name == 'steamcmd_linux.tar.gz':
            (ROOT / 'game/vendor').mkdir(exist_ok=True)
            shutil.copy2(target, ROOT / 'game/vendor' / name)
            if not (TOOLS / 'steamcmd/steamcmd.sh').exists():
                (TOOLS / 'steamcmd').mkdir(exist_ok=True)
                with tarfile.open(target) as archive:
                    archive.extractall(TOOLS / 'steamcmd', filter='data')
    uv = TOOLS / 'bin/uv'
    if not uv.exists() or not (TOOLS / 'node/bin/node').exists():
        raise RuntimeError('Project-local uv and Node are missing; restore the recorded toolchain from references/toolchain.json.')
    if not PYTHON.exists():
        run([uv, 'venv', '--python', '3.12.12', TOOLS / 'venv'])
    run([uv, 'pip', 'sync', '--python', PYTHON, ROOT / 'requirements-dev.lock'])
    run(['npm', 'ci'], cwd=ROOT / 'observer/web')
    run(['npx', 'playwright', 'install', 'chromium'], cwd=ROOT / 'observer/web')
    manifest()


def manifest():
    entries = {}
    for relative in ['.tooling/bin/uv', '.tooling/node/bin/node',
                     'data/game-files/java/projectzomboid.jar',
                     '.tooling/agent/jdk25.tar.gz', '.tooling/agent/protoc-36.1-linux-x86_64.zip',
                     '.tooling/agent/protobuf-javalite-4.36.1.jar']:
        path = ROOT / relative
        if path.exists():
            with path.open('rb') as stream:
                entries[relative] = hashlib.file_digest(stream, 'sha256').hexdigest()
    info = {'recorded_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
            'python': run([PYTHON, '--version'], capture=True).stdout.strip(),
            'node': run([TOOLS / 'node/bin/node', '--version'], capture=True).stdout.strip(),
            'uv': run([TOOLS / 'bin/uv', '--version'], capture=True).stdout.strip(),
            'sha256': entries,
            'origins': {'game': 'Read-only copy of validated .160 dedicated server, B42.20.4',
                        'uv_node': 'Copied installed local toolchains; hashes lock this workspace',
                        'java_protobuf': 'URLs and SHA256 pinned in observer/scripts/build_position_agent.py'}}
    (ROOT / 'references/toolchain.json').write_text(json.dumps(info, indent=2) + '\n')
    images = run([PODMAN, 'images', '--format', 'json'], capture=True)
    (ROOT / 'references/container-images.json').write_text(images.stdout)


def agent():
    if running('game'):
        raise RuntimeError('Stop the game before replacing its mounted exporter JAR')
    run([PYTHON, ROOT / 'observer/scripts/build_position_agent.py', '--cache', TOOLS / 'agent'])
    out = ROOT / 'artifacts/agent'
    out.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / 'observer/artifacts/position-agent/observer-position-agent.jar', out)
    (out / 'positions.properties').write_text(
        f'world={WORLD}\nendpoint=http://observer-positions:8000/internal/v1/positions\n'
        'token_file=/run/secrets/observer-positions-token\nstoryteller_enabled=true\n')


def check():
    required = ['data/game-files/java/projectzomboid.jar', 'artifacts/agent/observer-position-agent.jar',
                'artifacts/agent/positions.properties', f'data/Zomboid/Server/{WORLD}.ini',
                f'data/Zomboid/Server/{WORLD}_SandboxVars.lua', 'secrets/positions.token',
                'secrets/admin-password', 'secrets/porkbun.env']
    for relative in required:
        if not (ROOT / relative).is_file():
            raise RuntimeError(f'Missing {relative}')
    ini = dict(line.split('=', 1) for line in (ROOT / required[3]).read_text().splitlines()
               if '=' in line and not line.startswith('#'))
    for key, expected in {'DefaultPort': '16271', 'UDPPort': '16272', 'RCONPort': '27025',
                          'MaxPlayers': '4', 'PauseEmpty': 'true'}.items():
        if ini.get(key) != expected:
            raise RuntimeError(f'Unexpected {key}; expected {expected}')
    flags = json.loads((ROOT / 'data/game-files/ProjectZomboid64.json').read_text())['vmArgs']
    if '-Xmx6g' not in flags:
        raise RuntimeError('Expected 6 GiB Java heap')
    for path in (ROOT / 'secrets').iterdir():
        if path.is_file() and path.stat().st_mode & 0o077:
            raise RuntimeError(f'Secret file permissions too broad: {path.name}')
    compose('config', capture=True)
    print('Configuration, credentials permissions, ports, heap and required files checked.')


def start():
    check()
    if running('game'):
        raise RuntimeError('Game is already running. Stop it before applying new mod or image builds.')
    install_mod()
    if scenario_enabled():
        for relative in ['artifacts/scenario-agent/lofers-scenario-agent.jar', 'artifacts/scenario-agent/scenario.properties',
                         'artifacts/scenario-map/map-index.pb']:
            if not (ROOT / relative).is_file():
                raise RuntimeError(f'Missing First Week build: {relative}')
        (ROOT / 'data/npc-ipc').mkdir(mode=0o700, exist_ok=True)
        compose('--profile', 'scenario', 'up', '-d', '--force-recreate')
    else:
        compose('up', '-d', '--force-recreate')


def install_mod():
    if running('game'):
        raise RuntimeError('Stop the game before installing a companion mod build')
    src = ROOT / 'mods/LofersStoryteller'
    dst = ROOT / 'data/Zomboid/mods/LofersStoryteller'
    if dst.exists():
        shutil.rmtree(dst)
    shutil.copytree(src, dst)
    bandits = ROOT / 'data/game-files/steamapps/workshop/content/108600/3268487204/mods'
    for alias, source in [(bandits / 'bandits', 'Bandits'),
                          (bandits / 'Bandits/common/media/animsets', 'AnimSets')]:
        if (alias.parent / source).exists() and not alias.exists():
            alias.symlink_to(source, target_is_directory=True)
    files = {}
    for file in sorted(src.rglob('*')):
        if file.is_file():
            files[str(file.relative_to(src))] = hashlib.file_digest(file.open('rb'), 'sha256').hexdigest()
    (ROOT / 'artifacts/installed-mod.json').write_text(json.dumps(files, indent=2) + '\n')
    print('Installed the current companion mod source into the stopped new world.')


def stop():
    if running('game'):
        print('Saving and shutting down the new world through RCON…')
        response = rcon('players')
        if response.strip() != 'Players connected (0):':
            print(redact(response))
        try:
            print(redact(rcon('quit')))
        except ConnectionError:
            pass  # quit may close the RCON socket before its acknowledgement
        deadline = time.monotonic() + 180
        while running('game') and time.monotonic() < deadline:
            time.sleep(2)
        if running('game'):
            raise RuntimeError('Game did not stop cleanly; left it running. Inspect logs before intervention.')
    for service in ('observer', 'gateway', 'npc'):
        if running(service):
            compose('stop', service)


def backup():
    stop()
    stamp = time.strftime('%Y%m%d-%H%M%S', time.gmtime()) + f'-{time.time_ns() % 1000000000:09d}'
    path = ROOT / 'backups' / f'{WORLD}-{stamp}.tar.gz'
    partial = path.with_name(path.name + '.partial')
    with partial.open('xb') as stream:
        os.chmod(partial, 0o600)
        with tarfile.open(fileobj=stream, mode='w:gz') as archive:
            for relative in ['data/Zomboid', 'data/observer', 'secrets', 'mods', 'references',
                             'npc-service', 'scenario-agent', 'protocol', 'client', 'scripts',
                             'game/scenario.json', 'game/entrypoint.sh', 'compose.yaml',
                             'artifacts/scenario-agent', 'artifacts/scenario-map',
                             'artifacts/agent', 'artifacts/scenario-tests/acceptance.json']:
                if (ROOT / relative).exists():
                    archive.add(ROOT / relative, arcname=relative)
    digest = hashlib.file_digest(partial.open('rb'), 'sha256').hexdigest()
    sidecar = path.with_suffix(path.suffix + '.sha256')
    partial_sidecar = sidecar.with_name(sidecar.name + '.partial')
    partial_sidecar.write_text(f'{digest}  {path.name}\n')
    os.replace(partial, path)
    os.replace(partial_sidecar, sidecar)
    completed = sorted(old for old in (ROOT / 'backups').glob(f'{WORLD}-*.tar.gz')
                       if old.with_suffix(old.suffix + '.sha256').exists())
    for old in completed[:-7]:
        old.unlink()
        old.with_suffix(old.suffix + '.sha256').unlink(missing_ok=True)
    print(f'Consistent stopped backup: {path}. Services remain stopped; use ./dayone start.')


def restore_test(path):
    path = Path(path).resolve()
    expected = path.with_suffix(path.suffix + '.sha256').read_text().split()[0]
    if hashlib.file_digest(path.open('rb'), 'sha256').hexdigest() != expected:
        raise RuntimeError('Backup checksum mismatch')
    target = ROOT / 'artifacts/restore-tests' / time.strftime('%Y%m%d-%H%M%S', time.gmtime())
    target.mkdir(parents=True, mode=0o700)
    with tarfile.open(path) as archive:
        archive.extractall(target, filter='data')
    databases = (file for file in (target / 'data').rglob('*')
                 if file.suffix in {'.db', '.sqlite', '.sqlite3'} and file.is_file())
    for database in databases:
        with sqlite3.connect(f'file:{database}?mode=ro', uri=True) as connection:
            result = connection.execute('PRAGMA quick_check').fetchall()
            if result != [('ok',)]:
                raise RuntimeError(f'SQLite integrity failed for {database.name}: {result}')
    print(f'Checksum, isolated extraction and SQLite integrity passed: {target}')
    print('A gameplay restore still needs an isolated server boot and client check.')


def package():
    mod = ROOT / 'mods/LofersStoryteller'
    version = (mod / 'VERSION').read_text().strip()
    out = ROOT / 'artifacts' / f'LofersStoryteller-{version}.zip'
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as archive:
        for file in sorted(mod.rglob('*')):
            if file.is_file() and '__pycache__' not in file.parts:
                archive.write(file, file.relative_to(mod.parent))
    digest = hashlib.file_digest(out.open('rb'), 'sha256').hexdigest()
    out.with_suffix('.zip.sha256').write_text(f'{digest}  {out.name}\n')
    print(out)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['bootstrap', 'manifest', 'agent', 'check', 'build', 'start',
        'stop', 'status', 'logs', 'rcon', 'backup', 'restore-test', 'test', 'package', 'install-mod',
        'scenario-build', 'scenario-test-create', 'scenario-test-start', 'scenario-test-stop',
        'scenario-test-rcon', 'scenario-reset', 'vehicle-probe-create', 'vehicle-probe-start',
        'vehicle-probe-stop', 'vehicle-probe-status', 'vehicle-probe-control'])
    parser.add_argument('args', nargs='*')
    args = parser.parse_args()
    if args.command.startswith('vehicle-probe-'):
        import vehicle_probe_ops
        vehicle_probe_ops.dispatch(sys.modules[__name__], args.command, args.args)
    elif args.command.startswith('scenario-'):
        import scenario_ops
        scenario_ops.dispatch(sys.modules[__name__], args.command, args.args)
    elif args.command == 'build':
        compose('build', *args.args)
        manifest()
    elif args.command == 'status':
        compose('ps')
        if running('game'):
            try:
                print(redact(rcon('players')))
            except (OSError, ConnectionError) as error:
                print(f'RCON not ready: {error}')
    elif args.command == 'logs':
        result = compose('logs', '--tail=120', *(args.args or ['game']), capture=True)
        print(redact(result.stdout + result.stderr))
    elif args.command == 'rcon':
        if not args.args:
            parser.error('rcon requires a command')
        print(redact(rcon(' '.join(args.args))))
    elif args.command == 'restore-test':
        if len(args.args) != 1:
            parser.error('restore-test requires one backup path')
        restore_test(args.args[0])
    elif args.command == 'test':
        run([PYTHON, '-m', 'pytest', '-q', ROOT / 'tests'])
        run([PYTHON, '-m', 'pytest', '-q'], cwd=ROOT / 'observer')
        run(['npm', 'run', 'build'], cwd=ROOT / 'observer/web')
    else:
        globals()[args.command.replace('-', '_')]()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError) as error:
        print(f'Error: {error}', file=sys.stderr)
        sys.exit(1)
