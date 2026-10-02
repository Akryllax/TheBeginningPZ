# AKR client installation

This is an experimental private test bundle, not the completed First Week scenario.
Use **Project Zomboid 42.20.4** matching the server. 42.21 is not compatible with this
runtime. `manifest.json` records the build and companion mods; the server must use the
same bundle revision. No Java agent or replacement engine files go on the client.

1. Exit Project Zomboid completely.
2. Obtain Bandits NPC separately through Steam Workshop, item **3268487204**, Mod ID
   **Bandits2**. The server operator must verify its pinned version; Workshop's latest
   download is not guaranteed to match. Other mods required by that server are separate
   dependencies too. They are not bundled here.
3. Copy the **folders inside `mods/`**, preserving their `common/` and `42/` subfolders,
   into your Zomboid user-data mod directory:
   - Linux: `~/Zomboid/mods/`
   - Windows: `%USERPROFILE%\Zomboid\mods\`
   - macOS: `~/Zomboid/mods/` (client behavior on macOS has not been tested here).
   With a custom `-cachedir`, use that directory's `mods/` instead.
   Example result: `~/Zomboid/mods/AKRCore/42/mod.info`.
   Do not copy into Steam's game installation or nest the entire `client/` folder there.
4. When updating, move old copies of these AKR folders **outside** the mod directory
   before copying; merging can leave stale Lua files. Keep the old copy for rollback.
   Remove old Lofers variants only if the server operator confirms they are superseded.
5. Start the matching ordinary client and join the address/port supplied by the operator.
   The server selects the active mod set. If testing locally, select the matching profile
   in the Mods menu and restart when prompted. Do not enable every test module globally.
   Current NPC watched tests use `Bandits2`, `AKRCore`, `AKRDevTools`; the isolated
   vehicle probe uses `AKRDriverProbe`. Follow the operator's exact list for other scenes.
6. If the game reports a version/mod mismatch, stop and compare `manifest.json` with
   the server operator. Do not disable checksum checks or edit base game files.

`AKRResidents`, `AKRPopulation` and `AKRStoryteller` are included for the experimental
scenario profile; copying them does not activate ambient NPCs. `AKRDevTools` supplies
watched-test presentation and feedback. `AKRDriverProbe` is an original asset-only
vehicle test mod with disposable-world server weather setup. The automatic connection
helper `AKRDevConnect` is intentionally excluded: connect manually, with your own account.

To uninstall, close the game and remove the installed AKR folders after leaving servers
that require them. No save, password, server address, or player account is included.

Checksums are in `SHA256SUMS`. On Linux, from this directory: `sha256sum -c SHA256SUMS`.
A checksum verifies the package bytes, not that multiplayer behavior has been qualified.
