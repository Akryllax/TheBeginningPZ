"""Cross-language test against the real installed hook class and controlled players.

The optional isolated dedicated-server smoke test is documented separately.
"""

import argparse
import subprocess
import tempfile
import threading
import time
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from observer.proto.positions_pb2 import ExplorationSnapshot, PositionSnapshot, StorytellerSnapshot

ROOT = Path(__file__).resolve().parents[1]


def test(jdk, game_jar):
    agent = ROOT / "artifacts/position-agent/observer-position-agent.jar"
    with tempfile.TemporaryDirectory(prefix="observer-agent-test-") as directory:
        directory = Path(directory)
        classes = directory / "classes"
        classes.mkdir()
        sources = sorted(ROOT.glob("server-agent/test/**/*.java"))
        subprocess.run(
            [
                str(jdk / "bin/javac"),
                "--release",
                "25",
                "-cp",
                str(agent),
                "-d",
                str(classes),
                *map(str, sources),
            ],
            check=True,
        )
        for mode in (
            "normal",
            "slow_positions",
            "slow_exploration",
            "slow_storyteller",
            "broken_storyteller",
        ):
            frames = []
            maps = []
            diagnostics = []

            class Receiver(BaseHTTPRequestHandler):
                def do_POST(self, frames=frames, maps=maps, diagnostics=diagnostics, mode=mode):
                    assert self.path in (
                        "/internal/v1/positions",
                        "/internal/v1/exploration",
                        "/internal/v1/storyteller",
                    )
                    assert self.headers["Authorization"] == "Bearer " + "b" * 64
                    assert self.headers["Content-Type"] == "application/x-protobuf"
                    n = int(self.headers["Content-Length"])
                    assert n < 65536  # Realistic sparse masks compress below this too.
                    body = self.rfile.read(n)
                    if self.path.endswith("positions"):
                        frames.append(PositionSnapshot.FromString(body))
                    elif self.path.endswith("exploration"):
                        maps.append(ExplorationSnapshot.FromString(body))
                    else:
                        diagnostics.append(StorytellerSnapshot.FromString(body))
                    if mode == "slow_positions" and self.path.endswith("positions"):
                        time.sleep(3)  # Longer than HTTP timeout; game loop must keep moving.
                    if mode == "slow_exploration" and self.path.endswith("exploration"):
                        time.sleep(4)  # Map upload must not block the separate position sender.
                    if mode == "slow_storyteller" and self.path.endswith("storyteller"):
                        time.sleep(4)
                    try:
                        self.send_response(204)
                        self.end_headers()
                    except (BrokenPipeError, ConnectionResetError):
                        pass

                def log_message(self, *_):
                    pass

            server = ThreadingHTTPServer(("127.0.0.1", 0), Receiver)
            server.daemon_threads = True
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            token = directory / "token"
            token.write_text("b" * 64)
            config = directory / "positions.properties"
            config.write_text(
                f"world=ObserverFixture\nendpoint=http://127.0.0.1:{server.server_port}/internal/v1/positions\ntoken_file={token}\nstoryteller_enabled=true\n"
            )
            try:
                run = subprocess.run(
                    [
                        str(jdk / "bin/java"),
                        "-Xverify:all",
                        f"-javaagent:{agent}={config}",
                        "-cp",
                        f"{classes}:{game_jar}:{agent}",
                        "net.lofers.observer.FixtureMain",
                        mode,
                    ],
                    check=False,
                    capture_output=True,
                    text=True,
                    timeout=15,
                )
                print(mode.upper(), run.stdout, run.stderr)
                assert run.returncode == 0
                assert "Installed position hook" in run.stdout
                assert len(frames) >= (3 if mode == "slow_positions" else 6)
                assert all(f.protocol_version == 1 and f.world == "ObserverFixture" for f in frames)
                assert [f.sequence for f in frames] == sorted({f.sequence for f in frames})
                assert any(f.players for f in frames)
                assert all(f.players[0].x > 10632 for f in frames if f.players)
                if mode != "slow_positions":
                    assert any(f.players and f.players[0].dead for f in frames)
                    assert not frames[-1].players
                else:
                    assert frames[-1].sequence > len(frames)  # Replaced snapshots, no backlog.
                assert maps and any(f.players for f in maps)
                assert [f.sequence for f in maps] == sorted({f.sequence for f in maps})
                assert "Exploration adapter failed" not in run.stdout
                masks = [p for f in maps for p in f.players]
                assert all(
                    p.min_cell_x == -250 and p.max_cell_y == 250 and p.world_version == 249
                    for p in masks
                )
                raw = [zlib.decompress(p.visited_zlib) for p in masks]
                assert all(len(r) == 501 * 501 * 16 and r[0] == 3 and not any(r[2:]) for r in raw)
                assert any(r[1] == 3 for r in raw)
                if mode != "slow_exploration":
                    assert not maps[-1].players  # Empty heartbeat retains existing web masks.
                if mode == "broken_storyteller":
                    assert not diagnostics
                    assert "Storyteller adapter failed" in run.stdout
                else:
                    assert diagnostics and all(
                        d.mode == "observe" and d.npc_count == -1 for d in diagnostics
                    )
                    assert any(d.cells and d.cells[0].wealth == 99 for d in diagnostics)
                    assert "Storyteller adapter failed" not in run.stdout
            finally:
                server.shutdown()
                server.server_close()
                worker.join()
        print(
            "Position/exploration/storyteller contracts, detached data, thread isolation, independent timeouts, failure isolation and version guards passed."
        )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--game-jar", type=Path, required=True)
    args = parser.parse_args()
    test(args.jdk, args.game_jar)
