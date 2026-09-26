#!/usr/bin/env python3
"""Extract a bounded original scenario index from the installed game's map data."""
from __future__ import annotations
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'observer'))
from observer.roads import Roads, cells_on_line
from observer.terrain import read_header

BOUNDS = (10400, 9400, 11200, 10752)
ROOM_ROLES = {
    'home': {'livingroom'},
    'shop': {'grocery', 'grocerystorage', 'conveniencestore', 'gigamart', 'cornerstore', 'supermarket'},
    'work': {'office', 'restaurant', 'restaurantkitchen', 'warehouse', 'factory', 'store', 'gasstore'},
    'mechanic': {'mechanic', 'mechanicstorage', 'autoshop', 'carrepair'},
    'police': {'police', 'policeoffice', 'policestorage'},
    'clinic': {'medical', 'pharmacy', 'hospitalroom', 'hospital', 'clinic'},
}


def generate():
    source = ROOT / 'data/game-files/media/maps/Muldraugh, KY'
    output = ROOT / 'artifacts/scenario-map'
    output.mkdir(parents=True, exist_ok=True)
    generated = output / 'generated'
    generated.mkdir(exist_ok=True)
    subprocess.run([str(ROOT / '.tooling/agent/protoc/bin/protoc'), '-I', str(ROOT / 'protocol'),
                    f'--python_out={generated}', str(ROOT / 'protocol/npc_control.proto')], check=True)
    spec = importlib.util.spec_from_file_location('scenario_map_protocol', generated / 'npc_control_pb2.py')
    protocol = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(protocol)
    x0, y0, x1, y1 = BOUNDS
    places = []
    source_hashes = {}
    for cx in range(x0 // 256, (x1 - 1) // 256 + 1):
        for cy in range(y0 // 256, (y1 - 1) // 256 + 1):
            path = source / f'{cx}_{cy}.lotheader'
            if not path.exists(): continue
            data = path.read_bytes()
            source_hashes[path.name] = hashlib.sha256(data).hexdigest()
            header = read_header(data, cx, cy)
            for index, room_ids in enumerate(header['buildings']):
                rooms = [header['rooms'][i] for i in room_ids]
                residential = any(r['name'] in {'bedroom', 'kidsbedroom', 'bedroom4'} for r in rooms)
                for kind, names in ROOM_ROLES.items():
                    if kind == 'home' and not residential: continue
                    if kind != 'home' and residential: continue
                    matches = [r for r in rooms if r['z'] == 0 and r['name'] in names]
                    if not matches: continue
                    rect = max((rect for room in matches for rect in room['rects']), key=lambda r:r[2]*r[3])
                    x, y = rect[0] + rect[2] // 2 + 0.5, rect[1] + rect[3] // 2 + 0.5
                    if not (x0 <= x < x1 and y0 <= y < y1): continue
                    places.append({'id': f'{cx}:{cy}:{index}:{kind}', 'kind': kind,
                                   'position': {'x': x, 'y': y, 'z': 0}, 'available': True, 'revision': 1})
    roads = Roads(source, ROOT / '.tooling/scenario-roads')
    image_cache = {}
    def cost(x, y):
        x, y = math.floor(x), math.floor(y)
        if not (x0 <= x < x1 and y0 <= y < y1): return 0
        key = x // 256, y // 256
        if key not in image_cache: image_cache[key] = roads.image(*key)
        return image_cache[key][(y % 256) * 256 + x % 256]
    # One ground-validated road point per 8x8 navigation cell. Shift toward the
    # road interior where possible; never draw links across non-road terrain.
    nodes, grid, position_ids = [], {}, {}
    offsets = sorted(((dx,dy) for dx in range(-4,5) for dy in range(-4,5)), key=lambda p:(p[0]*p[0]+p[1]*p[1],p))
    for gx in range(x0 // 8, x1 // 8):
        for gy in range(y0 // 8, y1 // 8):
            candidate = None
            for dx, dy in offsets:
                point = gx*8+4+dx, gy*8+4+dy
                if cost(*point):
                    candidate = point
                    break
            if candidate is None: continue
            if candidate not in position_ids:
                if len(nodes) >= 4096: raise RuntimeError('Scenario road node cap exceeded')
                position_ids[candidate] = len(nodes) + 1
                nodes.append({'id': len(nodes)+1, 'position': {'x': candidate[0]+0.5, 'y':candidate[1]+0.5,'z':0}})
            grid[gx,gy] = position_ids[candidate]
    by_id = {n['id']: n['position'] for n in nodes}
    edges, seen = [], set()
    for (gx,gy), a in grid.items():
        for dx,dy in ((1,0),(0,1),(1,1),(1,-1)):
            b = grid.get((gx+dx,gy+dy))
            if not b or a == b: continue
            pair = min(a,b), max(a,b)
            if pair in seen: continue
            seen.add(pair)
            p,q = by_id[a],by_id[b]
            segment = list(cells_on_line((p['x'],p['y']),(q['x'],q['y'])))
            if not all(cost(x,y) for x,y in segment): continue
            weight=math.hypot(p['x']-q['x'],p['y']-q['y'])
            edges.extend([{'from':a,'to':b,'cost':weight,'blocked':False},
                          {'from':b,'to':a,'cost':weight,'blocked':False}])
    if len(edges)>16384: raise RuntimeError('Scenario road edge cap exceeded')
    batch=protocol.ObservationBatch(revision=1,phase='waiting',seed=1)
    for p in places: batch.places.add(**p)
    for n in nodes: batch.road_nodes.add(**n)
    for e in edges: batch.road_edges.add(**e)
    payload=batch.SerializeToString()
    if len(payload)>1024*1024: raise RuntimeError('Static map index unexpectedly large')
    (output/'map-index.pb').write_bytes(payload)
    (output/'map-index.json').write_text(json.dumps({'bounds':BOUNDS,'places':places,'road_nodes':nodes,'road_edges':edges},separators=(',',':'))+'\n')
    # Server-only derived coordinates; no textures, map binaries, or dependency code.
    lua=['-- Generated by scripts/build_scenario_map.py from the installed map.','return { places = {']
    for p in places:
        q=p['position']
        lua.append('  {id="%s",kind="%s",position={x=%.1f,y=%.1f,z=0},available=true,revision=1},'%(p['id'],p['kind'],q['x'],q['y']))
    lua.extend(['} }',''])
    destination=ROOT/'mods/LofersStoryteller/42/media/lua/server/LofersScenario/MapIndex.lua'
    destination.parent.mkdir(parents=True,exist_ok=True)
    destination.write_text('\n'.join(lua))
    counts={kind:sum(p['kind']==kind for p in places) for kind in ROOM_ROLES}
    manifest={'bounds':BOUNDS,'places':counts,'nodes':len(nodes),'edges':len(edges),'bytes':len(payload),
              'sha256':hashlib.sha256(payload).hexdigest(),'source_headers':source_hashes,'road_revision':roads.revision,
              'limitations':['The initial road graph covers the configured Muldraugh area only.',
                              'Loaded terrain, occupancy, doors and traffic still require live checks.']}
    (output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps({k:v for k,v in manifest.items() if k!='source_headers'},indent=2))

if __name__=='__main__': generate()
