---
type: decision
status: accepted
date: 2026-09-26
---

# 0002 — Isolated workstation deployment

## Context

The existing `.160` server already hosts the active game and Observer. A new world should be independently testable, with no accidental save, port or credential reuse.

## Decision

Use `/var/home/akr/Documents/Projects/ZomboidDayOne` on `192.168.1.132`, with rootless Podman and project-local tools/cache/container storage. Keep fresh game and Observer state, private credentials and dedicated network/port ranges. Use direct router forwarding to `.132`, with no SSH gateway through `.160`.

| Purpose | Port |
| --- | --- |
| Game UDP | 16271 and 16272 |
| HTTPS map | 8453 |
| LAN map | 8099 |
| Loopback app/debug | 8098 |
| Loopback RCON | 27025 |

The initial Java heap is 6 GiB; game container memory limit 9 GiB and CPU quota 6. Observer receives 1 GiB/1.5 CPUs and the gateway 256 MiB/0.5 CPUs. These are initial limits subject to measurement while a local client also runs.

## Consequences

New downloads, images and builds stay within the project. Bind-mounted files require correct rootless UID mapping and SELinux labels. The user forwards only the game UDP pair and public HTTPS TCP port. Detailed debug and RCON stay loopback-only. Existing `.160` services and saves remain unchanged.

See [[Design/Architecture]] and [[Runbooks/Operations]].
