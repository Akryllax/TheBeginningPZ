package net.akr.scenario;

import java.io.IOException;
import java.nio.ByteBuffer;
import net.akr.scenario.npc.core.CivilianPool;
import zombie.characters.IsoPlayer;

/** Stock player byte-buffer codec; native fidelity remains a separate in-game round-trip gate. */
public final class NativeActorBodyCodec {
  public static final String BUILD = net.akr.scenario.compat.BuildProfile.BUILD;
  private final int worldVersion;

  public NativeActorBodyCodec(int worldVersion) {
    if (worldVersion <= 0) throw new IllegalArgumentException("world_version");
    this.worldVersion = worldVersion;
  }

  public CivilianPool.Snapshot capture(IsoPlayer body) throws IOException {
    GameHooks.ownThread();
    if (body.isDead() || body.getVehicle() != null)
      throw new IllegalStateException("body_not_dematerializable");
    // One bounded attempt. Retrying save after overflow could repeat engine side effects.
    ByteBuffer output = ByteBuffer.allocate(CivilianPool.Snapshot.MAX_BYTES);
    body.save(output, false);
    byte[] data = new byte[output.position()];
    output.flip();
    output.get(data);
    return CivilianPool.Snapshot.capture(worldVersion, BUILD, data);
  }

  public void restore(IsoPlayer body, CivilianPool.Snapshot saved) throws IOException {
    GameHooks.ownThread();
    if (saved.codec() != 1 || saved.worldVersion() != worldVersion || !BUILD.equals(saved.build()))
      throw new IllegalArgumentException("incompatible_body");
    ByteBuffer input = ByteBuffer.wrap(saved.bytes());
    body.load(input, worldVersion, false);
    if (input.hasRemaining()) throw new IllegalArgumentException("body_trailing_data");
    // Caller must reapply only binding/position metadata, never default clothing/inventory.
  }
}
