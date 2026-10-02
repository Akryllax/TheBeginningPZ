"""Read class-file contracts without loading game classes or executing game code.

Fingerprints normalize constant-pool references and omit debug/stack-map metadata.
Unknown attributes are retained as hashes and explicitly require human review.
"""

from __future__ import annotations

import hashlib
import json


def fingerprint(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True).encode()).hexdigest()


class Reader:
    """Bounds-checked big-endian class-file reader."""

    def __init__(self, data):
        self.data, self.pos = data, 0

    def take(self, size):
        if size < 0 or self.pos + size > len(self.data):
            raise ValueError("Truncated class file")
        result = self.data[self.pos : self.pos + size]
        self.pos += size
        return result

    def number(self, size=2, signed=False):
        return int.from_bytes(self.take(size), "big", signed=signed)


def parse_class(data):
    """Return API/body hashes and references; never emit raw bytecode in a snapshot."""
    r = Reader(data)
    if r.take(4) != b"\xca\xfe\xba\xbe":
        raise ValueError("Not a Java class")
    version = [r.number(), r.number()]
    pool = [None] * r.number()
    i = 1
    while i < len(pool):
        tag = r.number(1)
        if tag == 1:
            value = r.take(r.number()).decode("utf-8", errors="surrogateescape")
        elif tag in (3, 4):
            value = r.take(4).hex()
        elif tag in (5, 6):
            value = r.take(8).hex()
        elif tag in (7, 8, 16, 19, 20):
            value = r.number()
        elif tag in (9, 10, 11, 12, 17, 18):
            value = [r.number(), r.number()]
        elif tag == 15:
            value = [r.number(1), r.number()]
        else:
            raise ValueError(f"Unknown constant-pool tag {tag}")
        pool[i] = (tag, value)
        i += 2 if tag in (5, 6) else 1

    def ref(index):
        if index == 0:
            return None
        tag, value = pool[index]
        if tag in (1, 3, 4, 5, 6):
            return [tag, value]
        if tag in (7, 8, 16, 19, 20):
            return [tag, ref(value)]
        if tag in (17, 18):
            # Bootstrap indices are separately fingerprinted. Never ignore them.
            return [tag, value[0], ref(value[1])]
        if tag == 15:
            return [tag, value[0], ref(value[1])]
        return [tag, *[ref(v) for v in value]]

    def utf(index):
        tag, value = pool[index]
        if tag != 1:
            raise ValueError("Expected UTF8 constant")
        return value

    def name(index):
        return utf(pool[index][1]) if index else None

    def attrs(reader):
        return [
            (utf(reader.number()), reader.take(reader.number(4))) for _ in range(reader.number())
        ]

    def attribute_hashes(attributes):
        known, unknown = {}, {}
        for key, raw in attributes:
            a = Reader(raw)
            if key in {
                "SourceFile",
                "LineNumberTable",
                "LocalVariableTable",
                "LocalVariableTypeTable",
                "StackMapTable",
                "SourceDebugExtension",
            }:
                continue
            if key in {"Signature", "ConstantValue", "NestHost"}:
                known[key] = ref(a.number())
            elif key in {"Exceptions", "NestMembers", "PermittedSubclasses"}:
                known[key] = [ref(a.number()) for _ in range(a.number())]
            elif key == "BootstrapMethods":
                known[key] = [
                    [ref(a.number()), [ref(a.number()) for _ in range(a.number())]]
                    for _ in range(a.number())
                ]
            elif key in {"Synthetic", "Deprecated"}:
                known[key] = True
            else:
                unknown[key] = hashlib.sha256(raw).hexdigest()
        return known, unknown

    def code(raw):
        c = Reader(raw)
        c.take(4)  # max stack/locals are verifier metadata, not behavior.
        b = Reader(c.take(c.number(4)))
        instructions = []
        calls = []
        while b.pos < len(b.data):
            offset = b.pos
            op = b.number(1)
            arg = None
            if op in (18, 19, 20, 178, 179, 180, 181, 182, 183, 184, 187, 189, 192, 193):
                index = b.number(1 if op == 18 else 2)
                arg = ref(index)
                if op in (182, 183, 184):
                    calls.append(arg)
            elif op in (185, 186, 197):
                arg = [ref(b.number()), b.take(1 if op == 197 else 2).hex()]
                if op != 197:
                    calls.append(arg[0])
            elif 153 <= op <= 168 or op in (198, 199, 200, 201):
                arg = ["target", offset + b.number(4 if op in (200, 201) else 2, True)]
            elif op in (170, 171):
                b.take((-b.pos) % 4)
                default = offset + b.number(4, True)
                if op == 170:
                    low, high = b.number(4, True), b.number(4, True)
                    if high < low or high - low > len(b.data):
                        raise ValueError("Invalid tableswitch")
                    pairs = [[v, offset + b.number(4, True)] for v in range(low, high + 1)]
                else:
                    count = b.number(4)
                    if count > len(b.data):
                        raise ValueError("Invalid lookupswitch")
                    pairs = [[b.number(4, True), offset + b.number(4, True)] for _ in range(count)]
                arg = ["switch", default, pairs]
            elif op == 196:
                sub = b.number(1)
                arg = [sub, b.take(4 if sub == 132 else 2).hex()]
            elif op in (16, 21, 22, 23, 24, 25, 54, 55, 56, 57, 58, 169, 188):
                arg = b.take(1).hex()
            elif op in (17, 132):
                arg = b.take(2).hex()
            elif op > 201:
                raise ValueError(f"Unknown opcode {op}")
            instructions.append([offset, 18 if op == 19 else op, arg])
        positions = {row[0]: n for n, row in enumerate(instructions)}
        positions[len(b.data)] = len(instructions)
        for row in instructions:
            arg = row[2]
            if isinstance(arg, list) and arg and arg[0] == "target":
                arg[1] = positions[arg[1]]
            elif isinstance(arg, list) and arg and arg[0] == "switch":
                arg[1] = positions[arg[1]]
                for pair in arg[2]:
                    pair[1] = positions[pair[1]]
        exceptions = [
            [positions[c.number()], positions[c.number()], positions[c.number()], ref(c.number())]
            for _ in range(c.number())
        ]
        known, unknown = attribute_hashes(attrs(c))
        body = {
            "instructions": [row[1:] for row in instructions],
            "exceptions": exceptions,
            "attributes": known,
        }
        return {"body": fingerprint(body), "unknown": unknown, "calls": calls}

    access, this, parent = r.number(), name(r.number()), name(r.number())
    interfaces = [name(r.number()) for _ in range(r.number())]
    members = {}
    for kind in ("fields", "methods"):
        result = {}
        for _ in range(r.number()):
            flags, member, descriptor = r.number(), utf(r.number()), utf(r.number())
            attributes = attrs(r)
            known, unknown = attribute_hashes([(k, v) for k, v in attributes if k != "Code"])
            entry = {
                "name": member,
                "descriptor": descriptor,
                "access": flags,
                "attributes": known,
                "unknown": unknown,
            }
            for key, raw in attributes:
                if key == "Code":
                    body = code(raw)
                    entry["unknown"].update(body.pop("unknown"))
                    entry.update(body)
            result[member + descriptor] = entry
        members[kind] = result
    known, unknown = attribute_hashes(attrs(r))
    if r.pos != len(data):
        raise ValueError("Trailing class-file bytes")
    return {
        "references": sorted(
            [
                {
                    "class": name(value[0]),
                    "kind": "fields" if tag == 9 else "methods",
                    "member": utf(pool[value[1]][1][0]) + utf(pool[value[1]][1][1]),
                }
                for entry in pool[1:]
                if entry is not None
                for tag, value in [entry]
                if tag in (9, 10, 11)
            ],
            key=lambda item: (item["class"], item["kind"], item["member"]),
        ),
        "name": this,
        "version": version,
        "access": access,
        "parent": parent,
        "interfaces": interfaces,
        **members,
        "attributes": known,
        "unknown": unknown,
        "sha256": hashlib.sha256(data).hexdigest(),
    }
