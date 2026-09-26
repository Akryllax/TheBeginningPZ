# Game mod paused — saved-map web mode active

Observer was disabled because Eric could not connect with the manually distributed required mod. Do not re-enable the game mod unless requested. The user subsequently authorized the independent 2D saved-map web mode; it does not use the mod.

The real-client test found two bugs: server headings are already degrees; IsoCell.getVehicles() returns a Set, not an indexed list. Source fixes are prepared locally (heading normalization, vehicles captured from visible squares moving-object lists, error retry cooldown), but are NOT deployed or validated in a real client. Do not describe the exporter as production-ready. There are no detailed world observations yet. Historical coverage and public markers imported successfully.

Local source: /var/home/akr/Projects/zomboid-observer. Remote project: /home/akr/projects/zomboid-observer. Viewer LAN port 8089, HTTPS 8443. Router TCP8443 forwarding was not working. HTTPS certificate issued successfully using Porkbun DNS TXT; placeholder _acme-challenge.map.lofers.net prevents wildcard ALIAS challenge interference.

Before resuming: fix/update the Lua harness to include a visible vehicle; validate against actual Build 42 APIs and client. Package the fixed mod and arrange installation for every connecting player before enabling it on the server again. Earlier tests: 15 Python and 2 Playwright cases passed, mocked Lua tests passed but missed actual engine API differences. Retained collector warning/gap reflects rejected bad heading records; address this after corrections, preserving an audit.
