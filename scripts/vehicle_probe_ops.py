"""Operate the disposable server-owned vehicle experiment, without client injection."""
import hashlib
import json
import math
from contextlib import contextmanager
import fcntl
from functools import wraps
from pathlib import Path
import shutil
import sqlite3
import time
import uuid

import scenario_ops as scenario


@contextmanager
def operation_lock(m):
    base = m.ROOT / 'artifacts/vehicle-probe'
    base.mkdir(parents=True, exist_ok=True)
    path = base / '.operations.lock'
    with path.open('a') as lock:
        path.chmod(0o600)
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        try:
            yield
        finally:
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)


def serialized(function):
    @wraps(function)
    def invoke(m, *args, **kwargs):
        with operation_lock(m):
            return function(m, *args, **kwargs)
    return invoke


def current(m):
    receipt = json.loads((m.ROOT / 'artifacts/vehicle-probe/current.json').read_text())
    target = Path(receipt['path']).resolve()
    if not target.is_relative_to((m.ROOT / 'artifacts/vehicle-probe').resolve()):
        raise RuntimeError('Probe path outside project')
    if not receipt['world'].startswith('LofersVehicleProbe_'):
        raise RuntimeError('Not a disposable vehicle-probe world')
    return receipt, target


@serialized
def prepare(m, driver_model=False):
    base = m.ROOT / 'artifacts/vehicle-probe'
    base.mkdir(parents=True, exist_ok=True)
    active = m.run([m.PODMAN, 'ps', '--filter', 'name=^lofers-vehicle-probe$', '--format', '{{.ID}}'], capture=True)
    if active.stdout.strip():
        raise RuntimeError('Stop the previous vehicle probe before preparing another')
    stamp = time.strftime('%Y%m%d_%H%M%S', time.gmtime()) + '_' + uuid.uuid4().hex[:6]
    world = 'LofersVehicleProbe_' + stamp
    target = base / stamp
    server = target / 'Zomboid/Server'
    server.mkdir(parents=True, mode=0o700)
    target.chmod(0o700)
    source = m.ROOT / 'data/Zomboid/Server'
    ini = scenario.replace_ini((source / f'{m.WORLD}.ini').read_text(), {
        'DefaultPort': 16281, 'UDPPort': 16282, 'RCONPort': 27035,
        'Public': 'false', 'PublicName': 'Lofers vehicle physics experiment',
        'SpawnPoint': '10753,9848,0', 'PauseEmpty': 'false',
        'Mods': '\\LofersDriverProbe' if driver_model else '', 'WorkshopItems': '',
    })
    scenario.write_private(server / f'{world}.ini', ini)
    sandbox = scenario.scenario_sandbox((source / f'{m.WORLD}_SandboxVars.lua').read_text())
    sandbox = sandbox.replace('LofersScenario = { Enabled = true }', 'LofersScenario = { Enabled = false }')
    scenario.write_private(server / f'{world}_SandboxVars.lua', sandbox)
    (server / f'{world}_spawnregions.lua').write_text('function SpawnRegions() return { { name="Muldraugh, KY", file="media/maps/Muldraugh, KY/spawnpoints.lua" } } end\n')
    config = json.loads((m.ROOT / 'data/game-files/ProjectZomboid64.json').read_text())
    config['vmArgs'] = [a for a in config['vmArgs'] if not a.startswith('-Xmx')] + ['-Xmx3g']
    (target / 'ProjectZomboid64.json').write_text(json.dumps(config, indent=2) + '\n')
    (target / 'probe').mkdir(mode=0o700)
    (target / 'Zomboid/mods').mkdir()
    if driver_model:
        package_driver_assets(m.ROOT, target / 'Zomboid/mods/LofersDriverProbe')
    (target / 'agent').mkdir()
    accounts = m.ROOT / f'data/Zomboid/db/{m.WORLD}.db'
    if accounts.is_file():
        dbpath = target / 'Zomboid/db' / f'{world}.db'
        dbpath.parent.mkdir(exist_ok=True)
        with sqlite3.connect(f'file:{accounts}?mode=ro', uri=True) as src, sqlite3.connect(dbpath) as dst:
            src.backup(dst)
            dst.execute('UPDATE whitelist SET world=?', (world,))
            dst.execute("UPDATE whitelist SET role=(SELECT id FROM role WHERE name='admin') WHERE username='akr'")
        dbpath.chmod(0o600)
    props = (f'side=server\nscenario.enabled=true\nworld={world}\n'
             'socket=/run/lofers/no-planner.sock\n'
             f'bandits_update_file={scenario.BANDITS_SOURCE}\n'
             'vehicle_probe.enabled=true\nvehicle_probe.directory=/opt/vehicle-probe\n'
             'vehicle_probe.x=10756.5\nvehicle_probe.y=9856.5\nvehicle_probe.z=0\n'
             'vehicle_probe.heading_degrees=90\nvehicle_probe.script='
             + ('Base.LofersSmallCar' if driver_model else 'Base.SmallCar') + '\n')
    scenario.write_private(target / 'scenario.properties', props)
    receipt = {'world': world, 'path': str(target), 'container': 'lofers-vehicle-probe',
               'ports': [16281, 16282, 27035], 'kind': 'disposable-vehicle-probe',
               'vehicle': [10756.5, 9856.5, 0], 'status': 'prepared', 'client_java_required': False,
               'driver_model': driver_model}
    scenario.write_private(target / 'receipt.json', json.dumps(receipt, indent=2) + '\n')
    scenario.write_private(base / 'current.json', json.dumps(receipt, indent=2) + '\n')
    print(f'Prepared isolated vehicle probe {world}. Playable world preserved.')


def package_driver_assets(root, destination):
    """An isolated original visual test mod with disposable-world visibility setup."""
    media = root / 'mods/LofersStoryteller/42/media'
    (destination / 'common').mkdir(parents=True, exist_ok=True)
    version = destination / '42'
    version.mkdir(exist_ok=True)
    for scope in ('common', '42'):
        for name in ('AnimSets', 'actiongroups'):
            (destination / scope / 'media' / name).mkdir(parents=True, exist_ok=True)
    (version / 'mod.info').write_text('name=Lofers original driver test\nid=LofersDriverProbe\nversionMin=42.20\n'
                                    'description=Original static seated driver; disposable visual test only.\n')
    files = ['models_X/Lofers/SeatedDriver.x', 'textures/Lofers/DriverPalette.png', 'scripts/lofers_driver.txt']
    for name in files:
        output = version / 'media' / name
        output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(media / name, output)
    weather = version / 'media/lua/server/LofersDriverProbeWeather.lua'
    weather.parent.mkdir(parents=True, exist_ok=True)
    weather.write_text('''-- Original visibility setup, restricted to disposable driver tests.
local ticks=0
local function clearFog()
    if not isServer() or not string.find(getServerName(), "^LofersVehicleProbe_") then
        Events.OnTick.Remove(clearFog);return
    end
    local cm=getClimateManager()
    if not cm then return end
    ticks=ticks+1
    if ticks==1 then
        cm:transmitServerStopWeather()
        local fog=cm:getClimateFloat(5)
        fog:setAdminValue(0);fog:setEnableAdmin(true)
    elseif cm:getFogIntensity()==0 then
        print("[LofersDriverProbe] confirmed server fog intensity=0")
        Events.OnTick.Remove(clearFog)
    elseif ticks>=300 then
        print("[LofersDriverProbe] fog override not confirmed")
        Events.OnTick.Remove(clearFog)
    end
end
Events.OnTick.Add(clearFog)
''')


@serialized
def start(m):
    receipt, target = current(m)
    scenario.free_ports()
    active = m.run([m.PODMAN, 'ps', '--filter', 'name=^lofers-vehicle-probe$', '--format', '{{.ID}}'], capture=True)
    if active.stdout.strip():
        raise RuntimeError('Vehicle probe already running')
    jar = m.ROOT / 'artifacts/scenario-agent/lofers-scenario-agent.jar'
    manifest = json.loads((jar.parent / 'manifest.json').read_text())
    if hashlib.sha256(jar.read_bytes()).hexdigest() != manifest['jar_sha256']:
        raise RuntimeError('Scenario agent artifact does not match its build manifest')
    # Each test runs a private immutable copy; rebuilding source cannot replace a loaded JAR.
    shutil.copy2(jar, target / 'agent/lofers-scenario-agent.jar')
    shutil.copy2(jar.parent / 'manifest.json', target / 'agent/manifest.json')
    m.run([m.PODMAN, 'rm', receipt['container']], capture=True, check=False)
    m.run([m.PODMAN, 'run', '-d', '--name', receipt['container'], '--userns=keep-id',
           '--memory=5g', '--cpus=4', '--ulimit', 'core=0:0',
           '-p', '192.168.1.132:16281:16281/udp', '-p', '192.168.1.132:16282:16282/udp',
           '-p', '127.0.0.1:27035:27035/tcp', '-e', f"PZ_WORLD={receipt['world']}",
           '-e', 'JAVA_TOOL_OPTIONS=-javaagent:/opt/scenario/lofers-scenario-agent.jar=/opt/scenario/scenario.properties',
           '-v', f'{m.ROOT}/data/game-files:/pzserver:ro,z',
           '-v', f'{target}/ProjectZomboid64.json:/pzserver/ProjectZomboid64.json:ro,z',
           '-v', f'{target}/Zomboid:/home/pzuser/Zomboid:z',
           '-v', f'{target}/agent/lofers-scenario-agent.jar:/opt/scenario/lofers-scenario-agent.jar:ro,z',
           '-v', f'{target}/scenario.properties:/opt/scenario/scenario.properties:ro,z',
           '-v', f'{target}/probe:/opt/vehicle-probe:z',
           '-v', f'{m.ROOT}/secrets/admin-password:/run/secrets/admin-password:ro,z',
           '-v', f'{m.ROOT}/game/entrypoint.sh:/home/pzuser/entrypoint.sh:ro,z',
           'localhost/zomboid-dayone_game:latest'])
    detail = 'original LofersDriverProbe asset mod required' if receipt.get('driver_model') else 'no game mods'
    print(f'Vehicle probe server starting on 192.168.1.132:16281; ordinary clients, {detail}.')


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if '=' in line and not line.startswith(('#', '!')))


def route_points(payload):
    """Validate detached coordinates before modifying the stopped experiment."""
    points = payload.get('waypoints') if isinstance(payload, dict) else None
    if not isinstance(points, list) or not 2 <= len(points) <= 16:
        raise ValueError('A probe route requires 2 to 16 waypoints')
    normalized = []
    for point in points:
        if not isinstance(point, dict):
            raise ValueError('Waypoint must contain numeric x/y coordinates')
        xy = [point.get('x'), point.get('y')]
        if any(type(v) not in (int, float) or not math.isfinite(v) or not -20000 <= v <= 60000 for v in xy):
            raise ValueError('Waypoint coordinate is not finite or within the probe bounds')
        z = point.get('z', 0)
        if type(z) not in (int, float) or z != 0:
            raise ValueError('Probe waypoints must be at ground level')
        normalized.append(tuple(float(v) for v in xy))
    lengths = [math.dist(a, b) for a, b in zip(normalized, normalized[1:])]
    if min(lengths) < 0.5 or max(lengths) > 40 or not 2 <= sum(lengths) <= 60:
        raise ValueError('Probe route needs segments 0.5 to 40 tiles and total length 2 to 60 tiles')
    return normalized


@serialized
def configure_route(m, source):
    receipt, target = current(m)
    active = m.run([m.PODMAN, 'ps', '--filter', f"name=^{receipt['container']}$", '--format', '{{.ID}}'], capture=True)
    if active.stdout.strip():
        raise RuntimeError('Stop the vehicle probe before changing its route')
    source = Path(source).resolve()
    if not source.is_relative_to((m.ROOT / 'artifacts').resolve()):
        raise ValueError('Use a reviewed route artifact inside this project artifacts directory')
    with source.open('rb') as stream:
        data = stream.read(65537)
    if len(data) > 65536:
        raise ValueError('Probe route artifact exceeds 64 KiB')
    points = route_points(json.loads(data))
    dx, dy = points[1][0] - points[0][0], points[1][1] - points[0][1]
    changes = {'vehicle_probe.x': points[0][0], 'vehicle_probe.y': points[0][1],
               'vehicle_probe.heading_degrees': math.degrees(math.atan2(dx, dy)) % 360,
               'vehicle_probe.waypoints': ';'.join(f'{x:.8f},{y:.8f}' for x, y in points)}
    config = target / 'scenario.properties'
    before = config.read_text()
    # Retain exact geometry/provenance and the previous private configuration.
    digest = hashlib.sha256(data).hexdigest()
    record = target / 'routes' / digest
    scenario.write_private(record / 'route.json', data.decode('utf-8'))
    if not (record / 'previous.properties').exists():
        scenario.write_private(record / 'previous.properties', before)
    pending = config.with_suffix('.pending')
    scenario.write_private(pending, scenario.replace_ini(before, changes))
    pending.replace(config)
    print(f'Configured {len(points)} waypoints; route SHA256 {digest}. Runtime road/obstacle checks still apply.')


def control(m, action):
    if action not in {'start', 'stop'}:
        raise ValueError('Probe control must be start or stop')
    _, target = current(m)
    status = properties(target / 'probe/status.properties')
    epoch = status.get('server_epoch', '')
    if not epoch or '\n' in epoch:
        raise RuntimeError('Probe has no current server epoch')
    path = target / 'probe/control.properties'
    scenario.write_private(path.with_suffix('.pending'),
        f'server_epoch={epoch}\ncommand_id={time.time_ns() // 1000000}\naction={action}\n')
    path.with_suffix('.pending').replace(path)
    print(f'Submitted {action} to the current disposable probe epoch.')


@serialized
def stop(m):
    receipt, target = current(m)
    active = m.run([m.PODMAN, 'ps', '--filter', 'name=^lofers-vehicle-probe$', '--format', '{{.ID}}'], capture=True)
    if active.stdout.strip():
        if (target / 'probe/status.properties').is_file():
            control(m, 'stop')
            time.sleep(2)
        try:
            print(m.redact(scenario.test_rcon(m, 'quit')))
        except ConnectionError:
            pass
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            state = m.run([m.PODMAN, 'inspect', '-f', '{{.State.Running}}', receipt['container']], capture=True).stdout.strip()
            if state == 'false':
                return
            time.sleep(2)
        raise RuntimeError('Probe did not stop gracefully; not killing it')


def dispatch(m, command, args):
    if command == 'vehicle-probe-create':
        if args not in ([], ['driver-model']): raise ValueError('Expected optional driver-model')
        prepare(m, driver_model=bool(args))
    elif command == 'vehicle-probe-start': start(m)
    elif command == 'vehicle-probe-stop': stop(m)
    elif command == 'vehicle-probe-status':
        _, target = current(m)
        path = target / 'probe/status.properties'
        print(path.read_text() if path.exists() else 'Probe status not yet available.')
    elif command == 'vehicle-probe-control':
        if len(args) != 1: raise ValueError('Expected start or stop')
        control(m, args[0])
    elif command == 'vehicle-probe-route':
        if len(args) != 1: raise ValueError('Expected one reviewed route JSON artifact')
        configure_route(m, args[0])
    else: raise ValueError('Unknown vehicle probe command')
