package net.akr.scenario;

import zombie.characters.IsoPlayer;
import zombie.characters.NetworkPlayerVariables;

/** Encodes Actor gait using the reviewed 42.21 native flag API. */
final class ActorMovementEncoding {
  private ActorMovementEncoding() {}

  /** Preserve unrelated native flags while replacing our authoritative movement state. */
  static short flags(IsoPlayer actor, boolean moving, boolean running, boolean onFloor) {
    var flags = NetworkPlayerVariables.getBooleanVariables(actor);
    flags.set(NetworkPlayerVariables.Flags.isRunning, moving && running);
    flags.clear(NetworkPlayerVariables.Flags.isSprinting);
    flags.set(NetworkPlayerVariables.Flags.hasDeferredMovement, moving);
    if (onFloor) flags.set(NetworkPlayerVariables.Flags.isOnFloor);
    return flags.asShort();
  }
}
