# AKR NPC planning service

The service computes proposals from detached observations. The game remains the
authority for identities, action execution, inventory, infection, persistence,
and replication. No game objects or Observer endpoints are accessed here.

Build with `python3 scripts/build_npc_service.py --test --bundle`. Everything
downloaded or generated stays in `.tooling/npc-service`; the exact existing
`.tooling/agent/protoc/bin/protoc` 36.1 is required. Lua 5.4.9, protobuf C++7.36.1,
and Abseil 20250512.1 source archives are SHA-256 pinned. Protobuf and Lua compile
statically; the private container bundle includes the build machine's dynamic
loader and runtime libraries with a manifest. It is for this local deployment,
not a portable public distribution.

Run:

```sh
.tooling/npc-service/build/akr-npc-service \
  --socket data/npc-ipc/npc.sock --world AKR_DayOne \
  --rules npc-service/rules --workers 2 \
  --map-index artifacts/scenario-map/map-index.pb
```

The optional map index is a serialized `ObservationBatch` containing static
places and directed roads, at most 4 MiB, 4096 nodes and 16384 edges. It supplements
empty observation collections inside the service; it is never echoed over IPC.
Dynamic place observations override the static place collection when supplied.
Road edges must include both directions when traffic is allowed both ways.

## Behavior

Each worker owns one independent, bounded Lua state. Original civilian,
emergency, and survival rule functions produce GOAP facts/operators. The native
planner searches state space deterministically (maximum six actions and 64
expansions), with action costs and stable tie breaking. Rules cover home/work
routines, eating, shopping, rest, observed emergencies, fleeing to known refuges,
and scavenging during collapse. Stable resident/seed variation staggers shifts.

Driving is a proposed enter → drive → park → exit → walk → interact chain.
Drive, park, and exit target the actual road endpoint; the final walk and
interaction target the building. Actual `in_vehicle` observations permit safe
park/exit recovery after interrupted driving. A bounded directed A* search uses mapped roads (2048 expansions,
128 route points, endpoints no farther than 40 tiles from a road). Blocked,
unmapped, disconnected, or over-budget routes fall back to walking proposals.
Walking still needs the game executor's collision/path validation; a map index
cannot prove a square or driveway is currently free.

Lua has a 2 MiB allocator budget per worker and a 100000-instruction budget per
rule function/module invocation. Only base, math, table, and string libraries
are loaded. I/O, OS, package loading, debug, coroutines, dynamic code loading,
ambient randomness, and protected calls that could swallow budget errors are
unavailable. Audited rule functions are pure; their source fingerprint appears
in the handshake and every batch. The FNV-1a fingerprint detects local rule
changes; it is not a cryptographic trust boundary. Build artifacts have SHA-256
manifests separately.

## Protocol and limits

The socket is private, mode 0660, and supports one connected game. Configure
matching UID/group access in both containers. Startup refuses to steal an active
socket or unlink a non-socket path. The initial `Envelope` must carry protocol 1,
the configured world, a nonempty server epoch, and `hello`. The server echoes the
identity and handshake. Later request IDs must increase and carry the same epoch.
Observation revisions must increase within that connection.

Frames are a four-byte big-endian unsigned length followed by one Protobuf
`Envelope`, maximum 262144 bytes. Invalid lengths/messages/epochs close the
connection; valid but invalid observations receive a rejection status. Partial
frames time out after two seconds; handshakes after five. Reconnect begins a new
session and discards obsolete work. Heartbeat/status and action-receipt messages
receive a status response with the matching request ID. Receipt acknowledgment
does not commit an effect or create durable service-side state.

Two fixed workers admit at most 64 resident jobs including running jobs. Newer
observations coalesce queued work per resident and cancel stale results. At most
four batches and eight outgoing frames are retained. Overflow is explicitly
deferred/rejected. A batch result retains input order regardless of worker
completion order. Plans carry the input resident revision/generation, a new plan
revision, and deterministic action IDs. Game-side revision/lease validation is
still mandatory because a completed proposal can become stale during transport.

The test suite exercises actual GOAP chains, directed/blocked roads, bounded Lua
loops/allocation, invalid observations, deterministic parallel planning, socket
framing, session restart, queue coalescing, and malformed input. These are service
tests, not evidence of two-client Bandits or vehicle replication.
