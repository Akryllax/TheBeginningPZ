#!/usr/bin/env python3
"""Real Unix-socket protocol, overload, reconnect, and deterministic-worker tests."""
from __future__ import annotations
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import socket
import statistics
import struct
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parents[2]
TOOLS = ROOT / ".tooling/npc-service"
PINNED_VENV = ROOT / ".tooling/venv"
if Path(sys.prefix) != PINNED_VENV:
    if not (PINNED_VENV / "bin/python").exists():
        raise RuntimeError("Run project bootstrap first: wire tests need the project protobuf7.36.1 Python runtime")
    os.execv(str(PINNED_VENV / "bin/python"), [str(PINNED_VENV / "bin/python"), __file__, *sys.argv[1:]])
GENERATED = TOOLS / "wire-python"
GENERATED.mkdir(parents=True, exist_ok=True)
subprocess.run([str(ROOT / ".tooling/agent/protoc/bin/protoc"), f"--proto_path={ROOT / 'protocol'}", f"--python_out={GENERATED}", str(ROOT / "protocol/npc_control.proto")], check=True)
sys.path.insert(0, str(GENERATED))
os.environ["PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION"] = "python"
import google.protobuf
assert google.protobuf.__version__ == "7.36.1", "wire fixtures require the pinned runtime"
import npc_control_pb2 as pb  # noqa: E402

MAX_FRAME = 256 * 1024


def envelope(request, epoch="test-epoch"):
    return pb.Envelope(protocol_version=1, world="AKR_DayOne", server_epoch=epoch, request_id=request)


def packed(message):
    data = message.SerializeToString()
    return struct.pack("!I", len(data)) + data


def receive(sock):
    def exactly(length):
        chunks = bytearray()
        while len(chunks) < length:
            data = sock.recv(length - len(chunks))
            if not data:
                raise EOFError("service closed connection")
            chunks.extend(data)
        return bytes(chunks)
    length, = struct.unpack("!I", exactly(4))
    assert 0 < length <= MAX_FRAME
    return pb.Envelope.FromString(exactly(length))


def connect(path, epoch="test-epoch"):
    sock = socket.socket(socket.AF_UNIX)
    sock.settimeout(2)
    sock.connect(str(path))
    hello = envelope(1, epoch)
    hello.hello.build = "independent-python-wire-fixture"
    hello.hello.max_frame_bytes = MAX_FRAME
    hello.hello.max_residents = 64
    sock.sendall(packed(hello))
    result = receive(sock)
    assert result.HasField("hello") and result.request_id == 1
    assert result.world == "AKR_DayOne" and result.server_epoch == epoch
    assert result.hello.max_frame_bytes == MAX_FRAME and result.hello.max_residents == 64
    return sock


def observation(request=2, revision=1, count=1, prefix="resident-", epoch="test-epoch"):
    msg = envelope(request, epoch)
    b = msg.observations
    b.revision = revision
    b.world_hour = 10
    b.phase = "calm"
    b.online_players = 2
    b.seed = 7
    for i in range(count):
        r = b.residents.add(id=f"{prefix}{i}", revision=revision, generation=1, name="Civilian", role="mechanic", health=100, has_food=True, home_safe=True, work_available=True)
        r.position.x = r.home.x = 100
        r.position.y = r.home.y = 100
        r.work.x = 400
        r.work.y = 100
        r.shop.x = 130
        r.shop.y = 100
    return msg


def normalized(batch):
    copy = pb.PlanBatch()
    copy.CopyFrom(batch)
    copy.compute_ms = 0
    for plan in copy.plans:
        plan.compute_ms = 0
    return copy.SerializeToString()


def closed(sock):
    try:
        assert sock.recv(1) == b""
    except ConnectionResetError:
        pass


def run(binary):
    runs = TOOLS / "tests"
    runs.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="wire-", dir=runs) as directory:
        path = Path(directory) / "s"
        process = subprocess.Popen([str(binary), "--socket", str(path), "--world", "AKR_DayOne", "--rules", str(ROOT / "npc-service/rules")], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        try:
            until = time.monotonic() + 5
            while not path.exists() and time.monotonic() < until:
                assert process.poll() is None, process.communicate()
                time.sleep(0.02)
            assert path.exists()
            # A competing process must not unlink or steal the active socket.
            duplicate = subprocess.run([str(binary), "--socket", str(path), "--world", "AKR_DayOne", "--rules", str(ROOT / "npc-service/rules")], capture_output=True, text=True)
            assert duplicate.returncode != 0 and path.exists()
            time.sleep(0.05)  # competing process's probe is accepted then closed
            with connect(path) as sock:
                request = observation()
                # Exercise arbitrary transport fragmentation including the prefix.
                data = packed(request)
                for begin in range(0, len(data), 7):
                    sock.sendall(data[begin:begin+7])
                result = receive(sock)
                assert result.request_id == 2 and result.plans.observation_revision == 1
                assert [a.kind for a in result.plans.plans[0].actions] == [pb.WALK, pb.WORK]
                assert result.plans.plans[0].expansions <= 64
                expected = normalized(result.plans)
                sock.sendall(packed(observation(3, 1)))
                assert receive(sock).status.health == "stale"
                bad = observation(4, 2)
                bad.observations.residents[0].position.x = float("nan")
                sock.sendall(packed(bad))
                assert receive(sock).status.detail == "resident_position"
                receipt = envelope(5)
                receipt.receipt.resident_id = "resident-0"
                receipt.receipt.action_id = "resident-0:1:1:1:0"
                receipt.receipt.state = "completed"
                sock.sendall(packed(receipt))
                assert receive(sock).status.detail == "receipt_observed"
                wrong_epoch = observation(6, 3, epoch="stale-server")
                sock.sendall(packed(wrong_epoch))
                closed(sock)
            time.sleep(0.03)
            with connect(path, "new-epoch") as sock:
                sock.sendall(packed(observation(epoch="new-epoch")))
                result = receive(sock)
                assert normalized(result.plans) == expected
                # Pipelined observations replace obsolete queued work per NPC.
                combined = b"".join(packed(observation(3+i, 2+i, 64, epoch="new-epoch")) for i in range(4))
                sock.sendall(combined)
                responses = [receive(sock) for _ in range(4)]
                assert {x.request_id for x in responses} == {3,4,5,6}
                for response in responses:
                    assert response.HasField("plans")
                    assert len(response.plans.plans) + response.plans.deferred == 64
                    for plan in response.plans.plans:
                        assert len(plan.actions) <= 6 and plan.expansions <= 64
                # Run a sustained workload through the actual two-worker queue.
                timings = []
                for i in range(30):
                    sent = time.perf_counter()
                    sock.sendall(packed(observation(7+i, 6+i, 32, epoch="new-epoch")))
                    answer = receive(sock)
                    assert len(answer.plans.plans) == 32 and answer.plans.deferred == 0
                    timings.append((time.perf_counter()-sent)*1000)
                assert max(timings) < 1000, timings
            time.sleep(0.03)
            for bad_prefix in (0, MAX_FRAME+1, 0xffffffff):
                with connect(path) as sock:
                    sock.sendall(struct.pack("!I", bad_prefix))
                    closed(sock)
                time.sleep(0.03)
            with connect(path) as sock:
                sock.sendall(struct.pack("!I", 1)+b"\xff")
                closed(sock)
            time.sleep(0.03)
            with connect(path) as sock:
                message = envelope(2)
                message.status.health = "heartbeat"
                sock.sendall(packed(message))
                assert receive(sock).status.workers == 2
            report = {"scenarios": ["handshake", "fragmentation", "C++ protobuf plans", "stale revision", "NaN validation", "receipt ack", "epoch rejection", "reconnect", "coalescing", "queue bounds", "malformed frame", "heartbeat", "active socket protection"],
                      "batch_residents": 32, "batches": len(timings), "round_trip_ms_p50": statistics.median(timings), "round_trip_ms_p95": sorted(timings)[int(len(timings)*.95)-1], "round_trip_ms_max": max(timings)}
            (TOOLS / "wire-test-report.json").write_text(json.dumps(report, indent=2)+"\n")
            print(json.dumps(report))
        finally:
            process.terminate()
            try:
                stdout, stderr = process.communicate(timeout=5)
            except subprocess.TimeoutExpired:
                process.kill()
                stdout, stderr = process.communicate()
                raise AssertionError("service did not stop promptly")
            assert process.returncode == 0, (stdout, stderr)
            assert not path.exists(), "socket removed on graceful shutdown"


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--binary", type=Path, default=TOOLS/"build/lofers-npc-service")
    run(parser.parse_args().binary.resolve())
