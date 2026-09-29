package net.akr.scenario;

import java.nio.ByteBuffer;
import zombie.characters.IsoPlayer;
import zombie.core.network.ByteBufferReader;
import zombie.core.network.ByteBufferWriter;
import zombie.network.fields.character.PlayerVariables;

/** Headless fixture only: real stock serialization/application, not a visual assertion. */
final class NativeMovementWireCheck {
  static void run(NativeCivilianActors actors, IsoPlayer body) {
    for (var gait :
        new NativeCivilianActors.Gait[] {
          NativeCivilianActors.Gait.WALK, NativeCivilianActors.Gait.RUN
        }) {
      actors.movementSpeed(body, gait);
      float speed = body.getVariableFloat("WalkSpeed", 0),
          injury = body.getVariableFloat("WalkInjury", 0);
      if (!(speed >= 0) || body.isRunning() != (gait == NativeCivilianActors.Gait.RUN))
        throw new IllegalStateException("native_gait_input");
      var stock = new PlayerVariables();
      stock.set(body);
      if (body.getActionStateName().equals("idle")
          && stock.getDescription().contains("id=WalkSpeed"))
        throw new IllegalStateException("stock_idle_contract_changed");
      for (float testInjury : new float[] {injury, .3f}) {
        body.setVariable("WalkInjury", testInjury);
        var encoded = new PlayerVariables();
        ActorMovementVariables.fill(encoded, body, true);
        var buffer = ByteBuffer.allocate(128);
        encoded.write(new ByteBufferWriter(buffer));
        buffer.flip();
        var decoded = new PlayerVariables();
        decoded.parse(new ByteBufferReader(buffer), null);
        if (buffer.hasRemaining()) throw new IllegalStateException("movement_wire_trailing_bytes");
        body.setVariable("WalkSpeed", 0f);
        body.setVariable("WalkInjury", 0f);
        decoded.apply(body);
        if (body.getVariableFloat("WalkSpeed", 0) != speed
            || body.getVariableFloat("WalkInjury", 0) != testInjury)
          throw new IllegalStateException("movement_wire_lost_blend");
      }
      body.setVariable("WalkInjury", injury);
    }
    actors.stop(body);
    if (body.isRunning()) throw new IllegalStateException("stop_retains_run");
    var idle = new PlayerVariables();
    ActorMovementVariables.fill(idle, body, false);
    if (body.getActionStateName().equals("idle") && !idle.getDescription().contains("id=IdleSpeed"))
      throw new IllegalStateException("idle_wire_changed");
  }
}
