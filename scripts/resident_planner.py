"""Run the project-local worker inside its disposable game's security/IPC namespace."""
import json
import os
from pathlib import Path
import signal
import subprocess
import time


def _podman(root, *args, check=True):
    return subprocess.run([str(root/'scripts/podman-local'), *map(str,args)], check=check, capture_output=True, text=True)


def start(root, target, world):
    receipt=json.loads((target/'receipt.json').read_text());container=receipt['container']
    identity=_podman(root,'inspect','--format','{{.Id}}',container).stdout.strip()
    manifest=root/'artifacts/npc-service-runtime/opt/lofers/runtime-manifest.json'
    if not manifest.exists():raise RuntimeError('Build and bundle the project C++ planner first')
    if (target/'ipc/npc.sock').exists():raise RuntimeError('Existing planner socket retained; use a fresh disposable world')
    command=['/opt/npc-runtime/lib64/ld-linux-x86-64.so.2','--library-path','/opt/npc-runtime/lib64:/opt/npc-runtime/usr/lib64',
        '/opt/npc-runtime/usr/local/bin/lofers-npc-service','--socket','/run/lofers/npc.sock','--world',world,
        '--rules','/opt/npc-runtime/opt/lofers/rules','--workers','2']
    _podman(root,'exec','-d',container,'sh','-c','echo $$ > /run/lofers/planner.pid; exec "$@" > /run/lofers/planner.log 2>&1','planner',*command)
    (target/'planner-process.json').write_text(json.dumps({'container':container,'identity':identity,'world':world,'root':str(root),
        'manifest':json.loads(manifest.read_text())},indent=2)+'\n')
    deadline=time.monotonic()+5
    while not (target/'ipc/npc.sock').exists():
        if time.monotonic()>deadline:raise RuntimeError('Disposable planner failed to start; inspect ipc/planner.log')
        time.sleep(.05)


def stop(target):
    receipt=target/'planner-process.json'
    if not receipt.exists():return
    data=json.loads(receipt.read_text())
    if 'pid' in data: # Reconcile the earlier host-process experiment without touching other workers.
        try:
            pid=data['pid'];same=(Path(f'/proc/{pid}/stat').read_text().split()[21]==data['start'] and
                str(Path(f'/proc/{pid}/exe').resolve()).removesuffix(' (deleted)')==data['binary'])
            if same:os.kill(pid,signal.SIGTERM)
        except (OSError,IndexError):pass
        return
    root=Path(data['root']);state=_podman(root,'inspect','--format','{{.Id}} {{.State.Running}}',data['container'],check=False)
    if state.returncode or state.stdout.strip()!=data['identity']+' true':return
    # Normal stop follows game shutdown, which stops all processes in this namespace.
    # If explicitly asked while it still runs, retain ownership instead of PID guessing.
    raise RuntimeError('Stop the disposable game before its attached planner')
