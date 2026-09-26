---
name: dayone-local-deployment
description: Build, operate, back up, restore-test and privately package the isolated ZomboidDayOne services on workstation 192.168.1.132 using project-local tooling.
---

# Local deployment

Read `vault/Runbooks/Operations.md` and `vault/Runbooks/Backup and Restore.md`. Use `./dayone --help` for the implemented interface; run commands from the project root.

Use the host's rootless Podman through `scripts/podman-local`. New images, downloaded tools and caches belong under `.tooling/`; runtime sockets may use `/run/user`. Keep SELinux labels and UID mappings compatible with the host-owned bind mounts. Do not fall back to global image storage or install global language packages.

Target `AKR_DayOne` on `.132` only. The `.160` production game/Observer, its saves and its ports are separate. Preserve real game ports 16271/16272, loopback RCON 27025, LAN Observer 8099, public HTTPS 8453 and loopback debug 8098. A host port remap alone does not change advertised Zomboid ports.

Keep credentials in protected `secrets/` files and out of output, command traces, source control and distribution ZIPs. Startup must not silently upgrade the game beneath the build-specific Java agent. Record exact source and dependency manifests for updates.

Stop gracefully and verify the process has exited before making a consistent game backup. Stop Observer or use SQLite's backup API for its database. Test restores under isolated paths and ports; never restore over the running world to test a backup.

Package original `LofersStoryteller` files with a version, checksum and dependency/install notes. Upstream Workshop assets, game binaries and credentials are excluded. No Workshop publication is part of this workflow. Report startup checks, automated tests and real client/WAN checks separately.
