"""Private, typed pedestrian runtime operator client. No game administration via RCON."""
from __future__ import annotations

import json
import os
from pathlib import Path
import socket
import struct
import sys
import uuid

MAX_FRAME = 16384


def protocol(root):
    generated = root / 'artifacts/scenario-agent/generated'
    if not (generated / 'runtime_control_pb2.py').is_file():
        raise RuntimeError('Build the scenario agent to generate runtime protocol bindings')
    sys.path.insert(0, str(generated))
    import runtime_control_pb2
    return runtime_control_pb2


def receive(sock, size):
    output = bytearray()
    while len(output) < size:
        chunk = sock.recv(size - len(output))
        if not chunk:
            raise ConnectionError('Runtime disconnected before reply completed')
        output.extend(chunk)
    return bytes(output)


def exchange(path, request, pb):
    payload = request.SerializeToString()
    if not 0 < len(payload) <= MAX_FRAME:
        raise ValueError('Request frame exceeds runtime limit')
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as channel:
        channel.settimeout(5)
        address = str(path)
        if len(os.fsencode(address)) > 100:
            # Linux pathname sockets limit the supplied address, not the resolved inode path.
            # A directory descriptor avoids chdir and supports deep project-local artifacts.
            parent = os.open(Path(path).parent, os.O_RDONLY | os.O_DIRECTORY)
            try:
                channel.connect(f'/proc/self/fd/{parent}/{Path(path).name}')
            finally:
                os.close(parent)
        else:
            channel.connect(address)
        channel.sendall(struct.pack('!I', len(payload)) + payload)
        size, = struct.unpack('!I', receive(channel, 4))
        if not 0 < size <= MAX_FRAME:
            raise ValueError('Reply frame exceeds runtime limit')
        reply = pb.Reply.FromString(receive(channel, size))
    if reply.version != 1 or reply.request_id != request.request_id:
        raise ValueError('Unexpected runtime response')
    if request.operation != pb.Request.HELLO and (reply.world != request.world or reply.epoch != request.epoch):
        raise ValueError('Runtime identity changed; command outcome must be reconciled')
    return reply


def dispatch(root: Path, args):
    if len(args) < 2 or args[0] not in {'hello', 'submit', 'status', 'cancel'}:
        raise ValueError('Usage: ./dayone runtime <hello|submit|status|cancel> SOCKET [definition.json|event-id] [request-id]')
    action, path = args[:2]
    if len(args) != (2 if action == 'hello' else 3) and not (action == 'submit' and len(args) == 4):
        raise ValueError('Unexpected runtime arguments')
    pb = protocol(root)
    request_id = args[3] if len(args) == 4 else uuid.uuid4().hex
    hello = exchange(path, pb.Request(version=1, request_id='hello', operation=pb.Request.HELLO), pb)
    if action == 'hello':
        result = hello
    else:
        request = pb.Request(version=1, world=hello.world, epoch=hello.epoch, request_id=request_id,
                             operation=getattr(pb.Request, action.upper()))
        if action == 'submit':
            from google.protobuf.json_format import ParseDict
            source = Path(args[2])
            if source.stat().st_size > MAX_FRAME:
                raise ValueError('Definition exceeds runtime limit')
            definition = json.loads(source.read_text())
            if 'civilian_encounter' in definition:
                if set(definition) != {'civilian_encounter'}:
                    raise ValueError('Encounter definition must have one typed payload')
                if 'civilian-encounter-v1' not in hello.capability:
                    raise ValueError('Server does not expose encounter capability')
                ParseDict(definition['civilian_encounter'], request.civilian_encounter)
            else:
                ParseDict(definition, request.pedestrian)
        else:
            request.event_id = args[2]
        result = exchange(path, request, pb)
    from google.protobuf.json_format import MessageToJson
    print(MessageToJson(result, preserving_proto_field_name=True))
    if not result.accepted:
        raise RuntimeError(f'Runtime rejected request: {result.code}')


if __name__ == '__main__':
    dispatch(Path(__file__).resolve().parents[1], sys.argv[1:])
