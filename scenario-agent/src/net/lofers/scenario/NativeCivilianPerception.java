package net.lofers.scenario;

import static net.lofers.scenario.CivilianNavigation.*;

import java.util.*;
import zombie.characters.*;
import zombie.iso.*;
import zombie.network.ServerMap;

/** LOS against a bounded page of a loaded square, never the world's first N zombies. */
public final class NativeCivilianPerception implements CivilianPerception.Source {
  private final IsoPlayer observer;

  public NativeCivilianPerception(IsoPlayer observer) {
    this.observer = observer;
  }

  @Override
  public CivilianPerception.Page read(Tile tile, int offset, int maximum) {
    GameHooks.ownThread();
    if (maximum > 8 || maximum < 1 || offset < 0)
      throw new IllegalArgumentException("perception_budget");
    IsoGridSquare square = ServerMap.instance.getGridSquare(tile.x(), tile.y(), tile.z());
    if (square == null || observer.getSquare() == null)
      return new CivilianPerception.Page(false, List.of(), false);
    var objects = square.getMovingObjects();
    var threats = new ArrayList<CivilianPerception.Threat>();
    int end = Math.min(objects.size(), offset + maximum);
    for (int i = offset; i < end; i++)
      if (objects.get(i) instanceof IsoZombie zombie) {
        var sight =
            LosUtil.lineClear(
                observer.getCell(),
                (int) Math.floor(observer.getX()),
                (int) Math.floor(observer.getY()),
                (int) Math.floor(observer.getZ()),
                tile.x(),
                tile.y(),
                tile.z(),
                false);
        boolean visible =
            sight != LosUtil.TestResults.Blocked
                && sight != LosUtil.TestResults.ClearThroughClosedDoor;
        // Ignore zombies without a stable network identity; no synthetic identity may alias another
        // body.
        if (zombie.getOnlineID() >= 0)
          threats.add(
              new CivilianPerception.Threat(
                  Integer.toString(zombie.getOnlineID()),
                  new Tile(
                      (int) Math.floor(zombie.getX()),
                      (int) Math.floor(zombie.getY()),
                      (int) Math.floor(zombie.getZ())),
                  visible,
                  zombie.isDead()));
      }
    return new CivilianPerception.Page(true, threats, end < objects.size());
  }
}
