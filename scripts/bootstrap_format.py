"""Fetch the two stand-alone formatter binaries into the project cache."""

import gzip
import hashlib
import shutil
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / ".tooling/format"
DOWNLOADS = {
    "google-java-format-1.36.1-all-deps.jar": (
        "https://repo.maven.apache.org/maven2/com/google/googlejavaformat/"
        "google-java-format/1.36.1/google-java-format-1.36.1-all-deps.jar",
        "25b400f003089d23cc5320cdaf1a16cabee19b8aa3434d0ff021b3d9f42154b4",
    ),
    "taplo-0.10.0.gz": (
        "https://github.com/tamasfe/taplo/releases/download/0.10.0/taplo-linux-x86_64.gz",
        "8fe196b894ccf9072f98d4e1013a180306e17d244830b03986ee5e8eabeb6156",
    ),
    "shfmt": (
        "https://github.com/mvdan/sh/releases/download/v3.13.1/shfmt_v3.13.1_linux_amd64",
        "fb096c5d1ac6beabbdbaa2874d025badb03ee07929f0c9ff67563ce8c75398b1",
    ),
}


def main() -> None:
    """Verify cached downloads before exposing them as runnable project tools."""
    CACHE.mkdir(parents=True, exist_ok=True)
    for name, (url, digest) in DOWNLOADS.items():
        target = CACHE / name
        if not target.exists():
            with urllib.request.urlopen(url, timeout=120) as response, target.open("wb") as output:
                shutil.copyfileobj(response, output)
        with target.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != digest:
                raise ValueError(f"Formatter checksum mismatch: {name}")
    binary = CACHE / "taplo"
    if not binary.exists():
        binary.write_bytes(gzip.decompress((CACHE / "taplo-0.10.0.gz").read_bytes()))
        binary.chmod(0o755)
    (CACHE / "shfmt").chmod(0o755)


if __name__ == "__main__":
    main()
