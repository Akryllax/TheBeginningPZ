#!/usr/bin/env python3
"""Minimal Source RCON client for sending a single command to the PZ server."""

import os
import socket
import struct
import sys

SERVERDATA_AUTH = 3
SERVERDATA_EXECCOMMAND = 2
SERVERDATA_RESPONSE_VALUE = 0


def _send_packet(sock, pkt_id, pkt_type, body):
    payload = struct.pack("<ii", pkt_id, pkt_type) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(payload)) + payload)


def _recv_exact(sock, n):
    buf = b""
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("RCON connection closed unexpectedly")
        buf += chunk
    return buf


def _read_packet(sock):
    size = struct.unpack("<i", _recv_exact(sock, 4))[0]
    data = _recv_exact(sock, size)
    pkt_id, pkt_type = struct.unpack("<ii", data[:8])
    body = data[8:-2].decode("utf-8", errors="replace")
    return pkt_id, pkt_type, body


def rcon_command(host, port, password, command, timeout=10):
    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.settimeout(timeout)
        _send_packet(sock, 1, SERVERDATA_AUTH, password)
        pkt_id, pkt_type, _ = _read_packet(sock)
        if pkt_type == SERVERDATA_RESPONSE_VALUE:
            # Some servers send an empty response-value packet before the
            # real auth response.
            pkt_id, pkt_type, _ = _read_packet(sock)
        if pkt_id == -1:
            raise PermissionError("RCON authentication failed (wrong password?)")

        _send_packet(sock, 2, SERVERDATA_EXECCOMMAND, command)
        _, _, body = _read_packet(sock)
        return body


def main():
    if len(sys.argv) < 2:
        print("usage: rcon.py <command...>", file=sys.stderr)
        sys.exit(2)

    host = os.environ.get("PZ_RCON_HOST", "127.0.0.1")
    port = int(os.environ.get("PZ_RCON_PORT", "27015"))
    password = os.environ.get("PZ_RCON_PASSWORD")
    if not password:
        print("PZ_RCON_PASSWORD is not set", file=sys.stderr)
        sys.exit(2)

    command = " ".join(sys.argv[1:])
    try:
        print(rcon_command(host, port, password, command))
    except (ConnectionError, PermissionError, OSError) as e:
        print(f"rcon error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
