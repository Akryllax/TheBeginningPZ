"""Explicit per-start spectator capacity; never inherit extra seats from an earlier test."""


def parse(args):
    args = list(args)
    viewers = 1
    if "--viewers" in args:
        i = args.index("--viewers")
        if i != 1 or len(args) != 3 or args[0] not in {"start", "prepare"}:
            raise ValueError("Use start/prepare --viewers N")
        viewers = int(args[i + 1])
        args = args[:i]
    if not 1 <= viewers <= 4:
        raise ValueError("Viewing spots must be between 1 and 4")
    return args, viewers
