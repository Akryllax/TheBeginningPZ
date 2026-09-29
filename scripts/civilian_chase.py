"""One native off-screen chase, using the disposable deployment and prewarmed Actor pool."""
import json
from pathlib import Path
import shutil
import time
import civilian_watch as watch
import pedestrian_ops as pedestrian
import scenario_ops as scenario


def dispatch(m, args):
    action = args[0] if args else 'status'
    if action not in {'create','start','join','status','stop'} or len(args)>1:
        raise RuntimeError('Usage: ./dayone civilian-chase [create|start|join|status|stop]')
    if action=='create':
        watch.create(m)
        receipt,target=scenario.test_receipt(m)
        receipt.pop('watched_pool',None)
        receipt['offscreen_chase']={'actors':1,'hunters':1,'prewarmed':4,'observer':[10596.5,10060.5,0]}
        props=target/'scenario.properties'
        scenario.write_private(props,'\n'.join(line for line in props.read_text().splitlines() if not line.startswith('watched.'))+'\nchase.enabled=true\nchase.directory=/run/lofers\n')
        ini=target/'Zomboid/Server'/f'{receipt["world"]}.ini'
        scenario.write_private(ini,scenario.replace_ini(ini.read_text(),{'Mods':'Bandits2;AKRCore;AKRDevTools;AKRPopulation;AKRResidents'}))
        for name in ('AKRPopulation','AKRResidents'):
            shutil.copytree(m.ROOT/'mods'/name,target/'Zomboid/mods'/name)
        for path in (target/'receipt.json',m.ROOT/'artifacts/scenario-tests/current.json'):
            scenario.write_private(path,json.dumps(receipt,indent=2)+'\n')
        print('Prepared one off-screen chase. No ambient controller activated.');return
    receipt,target=scenario.test_receipt(m)
    if not receipt.get('offscreen_chase'):raise RuntimeError('Current disposable world is not the chase test')
    report=target/'ipc/chase-report.json'
    if action=='start':watch.install_client(m,target);pedestrian.start(m)
    elif action=='stop':scenario.stop_test(m)
    elif action=='join':
        deadline=time.monotonic()+300
        while True:
            r=json.loads(report.read_text()) if report.exists() else {}
            if r.get('status') not in (None,'running'):raise RuntimeError('Chase test already finished')
            if r.get('phase',0)>=1:break
            if time.monotonic()>deadline:raise RuntimeError('Chase warmup timeout')
            time.sleep(2)
        pedestrian.join(m)
    else:print(report.read_text() if report.exists() else '{"status":"waiting_for_boot"}')
