package net.lofers.scenario;

import zombie.GameTime;
import zombie.core.physics.Bullet;
import zombie.core.physics.WorldSimulation;
import zombie.iso.IsoWorld;

/** Build-guarded engine calls. Java remains a dedicated server throughout. */
final class GamePhysicsBackend implements ResidentPhysics.Backend {
  public boolean initialized() {
    return WorldSimulation.instance.created || (Bullet.cmdBuf != null && Bullet.isWorldInit());
  }

  public void initialize() {
    if (Bullet.cmdBuf == null) Bullet.init();
    var simulation = WorldSimulation.instance;
    if (!ProbeRoute.finite(simulation.offsetX, simulation.offsetY)
        || simulation.offsetX != (int) simulation.offsetX
        || simulation.offsetY != (int) simulation.offsetY)
      throw new IllegalStateException("unsupported_native_coordinate_frame");
    var meta = IsoWorld.instance.metaGrid;
    // Preserve the frame of preloaded cars; WorldSimulation.create() rebases it.
    // Ordinary native chunk maps activate obstacle bodies; headless server cells do not.
    Bullet.initWorld(
        meta.getMinX(),
        meta.getMinY(),
        meta.getMaxX(),
        meta.getMaxY(),
        (int) simulation.offsetX,
        (int) simulation.offsetY,
        false);
    simulation.time = GameTime.getServerTimeMills();
    simulation.created = Bullet.isWorldInit();
  }

  public int bodies() {
    return Bullet.getVehicleCount();
  }

  public void activate(int x, int y, int width) {
    Bullet.activateChunkMap(0, x, y, width);
  }

  public void deactivate() {
    Bullet.deactivateChunkMap(0);
  }
}
