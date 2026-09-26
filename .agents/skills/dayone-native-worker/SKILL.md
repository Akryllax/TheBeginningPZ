---
name: dayone-native-worker
description: Implement and profile this project's C++ NPC planning service, embedded Lua behavior rules, and Protobuf IPC. Use for worker changes, not unrelated native projects.
---

# Native NPC worker

Read `protocol/npc_control.proto` and `vault/Design/First Week.md` before changing contracts.
The service proposes plans from immutable observations. The game owns accepted resident
state and effects. Worker caches are reconstructible and must not become a second save.

Use C++20 with bounded GOAP/A* jobs and a fixed worker pool. Keep one pending request per
resident and deterministic tie-breaking independent of completion order. Cancellation is
checked by operation count; a queue limit must not run work on the game thread.

Each worker owns an isolated Lua state. Apply instruction and allocator budgets, remove
IO/OS/uncontrolled random access, bound host functions, and reset per-request script state.
Return typed actions, never executable code. Worker Lua is not the game's Kahlua runtime.

Pin native dependencies and checksums under `.tooling/`, including the exact Protobuf
generator/runtime match. Keep generated files and builds under `artifacts/`. Use the
project build script; do not borrow unrelated project CMake skills or install global tools.

For IPC, enforce frame/count limits before allocation, reject mismatched world/boot
generations, and separate replaceable observations from acknowledged lifecycle receipts.
Test actual socket framing and C++/Java interoperability as well as pure planner behavior.
Measure copy, encode, queue, planning, decode and apply separately before changing formats.
