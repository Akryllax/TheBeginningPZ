package net.akr.scenario;

import java.lang.reflect.*;
import zombie.characters.IsoPlayer;

/**
 * Scoped calls into the pinned player's native endurance calculation, without stock player update.
 * BuildGuard pins IsoPlayer. Reflection never replaces an engine class or copies its formula.
 */
final class NativeActorLocomotion {
  private static final Method endurance;
  private static final Field moving;

  static {
    try {
      endurance = IsoPlayer.class.getDeclaredMethod("updateEndurance");
      endurance.setAccessible(true);
      moving = IsoPlayer.class.getDeclaredField("isPlayerMoving");
      if (moving.getType() != boolean.class)
        throw new IllegalStateException("locomotion_field_contract");
      moving.setAccessible(true);
    } catch (ReflectiveOperationException e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  static void tick(IsoPlayer body, NativeCivilianActors.Gait gait) {
    GameHooks.ownThread();
    if (!NativeCivilianActors.owns(body)) throw new IllegalArgumentException("unowned_locomotion");
    try {
      boolean prior = moving.getBoolean(body);
      float oldSpeed = body.currentSpeed;
      try {
        moving.setBoolean(body, gait != NativeCivilianActors.Gait.IDLE);
        body.currentSpeed =
            gait == NativeCivilianActors.Gait.RUN
                ? 1
                : gait == NativeCivilianActors.Gait.WALK ? .5f : 0;
        endurance.invoke(body);
      } finally {
        moving.setBoolean(body, prior);
        body.currentSpeed = oldSpeed;
      }
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("native_endurance", e);
    }
  }
}
