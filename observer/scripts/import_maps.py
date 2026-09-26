"""Bootstrap coarse exploration using bounds exported by the same game build/map.

Run with the collector stopped. This only writes the viewer's own database.
"""
import argparse
import json
from pathlib import Path

from observer.collector import Collector
from observer.models import Batch
from observer.store import Store

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--metadata-journal', type=Path, required=True)
parser.add_argument('--save', type=Path, required=True)
parser.add_argument('--data', type=Path, required=True)
parser.add_argument('--world', required=True)
args = parser.parse_args()
bounds = None
for line in args.metadata_journal.read_text().splitlines()[1:]:
    batch = Batch.model_validate_json(line)
    if batch.bounds:
        bounds = batch.bounds
        break
if bounds is None:
    raise SystemExit('No runtime world bounds in journal')
store = Store(args.data / 'observer.sqlite', args.world)
with store.lock, store.db:
    store.set_meta('bounds', bounds.model_dump())
    store.set_meta('bounds_source', 'Bootstrap from isolated server using the same game build and Map setting')
Collector(store, None, args.save).read_saves()
print(json.dumps({'observers': store.status()['observers'], 'known_blocks': len(store.coverage()), 'public_markers': len(store.status()['markers'])}))
store.close()
