#!/usr/bin/env bash
set -euo pipefail
cd /pzserver
# Install/update is an explicit maintenance operation, never a startup side effect.
test -f /pzserver/java/projectzomboid.jar
PZ_ADMIN_PASSWORD="$(cat /run/secrets/admin-password)"
# Match the bundled launcher's library setup, then exec the process directly so
# its exit code reaches Podman and the failure restart policy can work.
export PATH="/pzserver/jre64/bin:$PATH"
PZ_JRE_LIB=/pzserver/jre64/lib
if [[ -d "$PZ_JRE_LIB/amd64" ]]; then PZ_JRE_LIB="$PZ_JRE_LIB/amd64"; fi
export LD_LIBRARY_PATH="/pzserver/linux64:/pzserver:$PZ_JRE_LIB${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export LD_PRELOAD="${LD_PRELOAD:+$LD_PRELOAD:}libjsig.so"
exec /pzserver/ProjectZomboid64 -servername "${PZ_WORLD:-AKR_DayOne}" -cachedir=/home/pzuser/Zomboid -adminusername admin -adminpassword "$PZ_ADMIN_PASSWORD"
