"""Build the manually installable mod archive (no Workshop publishing)."""

import sys
import zipfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
output = Path(sys.argv[1]) if len(sys.argv) > 1 else root / "artifacts/ZomboidObserver.zip"
output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
    for path in sorted((root / "mod").rglob("*")):
        if path.is_file():
            archive.write(path, path.relative_to(root / "mod"))
    archive.writestr(
        "INSTALL.txt",
        "Extract ZomboidObserver into your Zomboid/mods directory.\n"
        "Linux: ~/Zomboid/mods/ZomboidObserver/42/mod.info\n"
        "Windows: %UserProfile%\\Zomboid\\mods\\ZomboidObserver\\42\\mod.info\n"
        "Restart the game after installation, then join the server.\n"
        "This mod publishes observed world state, selected world-container contents,\n"
        "inspected vehicle details, and online player locations to the public viewer.\n"
        "Personal inventories and private/group-only map annotations are excluded.\n",
    )
print(output)
