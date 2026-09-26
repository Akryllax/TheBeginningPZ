#!/usr/bin/env python3
"""Launch the installed client with the reversible First Week Java helper."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import zipfile

BANDITS_HASH='fb9bd559da4e0faabd2c35c41cd7d2cd74d85510ef642a7ba6e3776cb8a02192'


def game_directory(explicit):
    if explicit:
        roots=[Path(explicit).expanduser()]
    else:
        roots=[Path('/run/media/system/DATA/SteamLibrary/steamapps/common/ProjectZomboid'),
               Path.home()/'.local/share/Steam/steamapps/common/ProjectZomboid',
               Path.home()/'.steam/steam/steamapps/common/ProjectZomboid',
               Path(os.environ.get('PROGRAMFILES(X86)','C:/Program Files (x86)'))/'Steam/steamapps/common/ProjectZomboid']
    for base in roots:
        for folder in [base/'projectzomboid',base]:
            if (folder/'ProjectZomboid64.json').is_file(): return folder.resolve()
    raise RuntimeError('Game installation not found. Supply --game-dir PATH.')


def bandits_file(game, explicit):
    if explicit: candidates=[Path(explicit).expanduser()]
    else:
        candidates=[]
        for parent in game.parents:
            if parent.name=='steamapps':
                candidates.append(parent/'workshop/content/108600/3268487204/mods/Bandits/42.20/media/lua/client/BanditUpdate.lua')
        for root in [Path.home()/'.local/share/Steam/steamapps',Path.home()/'.steam/steam/steamapps']:
            candidates.append(root/'workshop/content/108600/3268487204/mods/Bandits/42.20/media/lua/client/BanditUpdate.lua')
    for path in candidates:
        if path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest()==BANDITS_HASH:
            return path.resolve()
    raise RuntimeError('The verified Bandits 42.20 update file was not found. Check the installed dependency; do not bypass its hash guard.')


def assets():
    here=Path(__file__).resolve().parent
    if (here/'lofers-scenario-agent.jar').is_file():
        return here,here/'LofersStoryteller'
    return here.parent/'artifacts/scenario-agent',here.parent/'mods/LofersStoryteller'


def verify_game(game, manifest):
    jar=next((p for p in [game/'java/projectzomboid.jar',game/'projectzomboid.jar'] if p.is_file()),None)
    if jar is None: raise RuntimeError('Installed game JAR missing')
    data=json.loads(manifest.read_text())
    expected=data.get('class_hashes') or data.get('game_class_hashes') or data.get('hashes')
    if not isinstance(expected,dict) or not expected:
        raise RuntimeError('The helper build has no class compatibility manifest')
    with zipfile.ZipFile(jar) as archive:
        for name,digest in expected.items():
            entry=name if name.endswith('.class') else name+'.class'
            if hashlib.sha256(archive.read(entry)).hexdigest()!=digest:
                raise RuntimeError('Unsupported installed game class: '+entry)


def running_client(game):
    if sys.platform.startswith('linux'):
        for proc in Path('/proc').iterdir():
            if not proc.name.isdigit(): continue
            try:
                if (proc/'exe').resolve()==game/'ProjectZomboid64': return True
            except OSError: pass
    elif os.name=='nt':
        result=subprocess.run(['tasklist','/FI','IMAGENAME eq ProjectZomboid64.exe','/FO','CSV','/NH'],capture_output=True,text=True)
        return 'ProjectZomboid64.exe' in result.stdout
    return False


def install_mod(source,cachedir,game):
    if running_client(game): raise RuntimeError('Close the local game before updating its companion mod')
    target=cachedir/'mods/LofersStoryteller'
    if target.exists():
        backup=cachedir/'mods-backups'/('LofersStoryteller-'+time.strftime('%Y%m%d-%H%M%S'))
        backup.parent.mkdir(parents=True,exist_ok=True)
        shutil.move(str(target),backup)
    target.parent.mkdir(parents=True,exist_ok=True)
    shutil.copytree(source,target)
    print('Installed original companion mod: '+str(target))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game-dir')
    parser.add_argument('--bandits-file')
    parser.add_argument('--cache-dir',type=Path,default=Path.home()/'Zomboid')
    parser.add_argument('--check',action='store_true')
    parser.add_argument('--install-mod',action='store_true')
    parser.add_argument('game_args',nargs=argparse.REMAINDER)
    args=parser.parse_args()
    game=game_directory(args.game_dir)
    directory,mod=assets()
    jar=directory/'lofers-scenario-agent.jar'
    manifest=directory/'manifest.json'
    if not jar.is_file() or not manifest.is_file(): raise RuntimeError('Build/package the scenario agent first')
    verify_game(game,manifest)
    bandits=bandits_file(game,args.bandits_file)
    if args.install_mod: install_mod(mod,args.cache_dir,game)
    if args.check:
        print('Game and Bandits hashes verified; helper is compatible with the installed files.')
        return
    if running_client(game): raise RuntimeError('The local game is already running')
    properties=directory/'client.properties'
    # Java properties parse backslashes; forward slashes also work on Windows.
    properties.write_text('side=client\nscenario.enabled=true\nbandits_update_file='+bandits.as_posix()+'\n')
    env=os.environ.copy()
    if 'lofers-scenario-agent' in env.get('JAVA_TOOL_OPTIONS',''):
        raise RuntimeError('A scenario helper is already present in JAVA_TOOL_OPTIONS')
    agent='-javaagent:'+jar.as_posix()+'='+properties.as_posix()
    if '"' in agent or '\n' in agent: raise RuntimeError('Unsupported quote/newline in helper path')
    env['JAVA_TOOL_OPTIONS']=(env.get('JAVA_TOOL_OPTIONS','')+' "'+agent+'"').strip()
    extra=args.game_args[1:] if args.game_args[:1]==['--'] else args.game_args
    if args.cache_dir!=Path.home()/'Zomboid': extra=['-cachedir='+str(args.cache_dir),*extra]
    if os.name=='nt':
        command=[str(game/'ProjectZomboid64.exe'),*extra]
    else:
        launch=game.parent/'projectzomboid.sh'
        if not launch.is_file(): raise RuntimeError('The installed Linux client launch script is missing')
        command=['bash',str(launch),*extra]
    raise SystemExit(subprocess.call(command,cwd=game,env=env))

if __name__=='__main__':
    try: main()
    except (RuntimeError,OSError,ValueError,KeyError,zipfile.BadZipFile) as error:
        print('First Week launcher: '+str(error),file=sys.stderr)
        raise SystemExit(1)
