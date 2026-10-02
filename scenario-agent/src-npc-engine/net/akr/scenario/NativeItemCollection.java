package net.akr.scenario;

import zombie.characters.IsoPlayer;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.iso.IsoGridSquare;
import zombie.network.GameServer;

/** Exact-item server transfer with range, ownership, interruption and duplicate-effect checks. */
final class NativeItemCollection {
  private final ItemContainer source;
  private final InventoryItem item;
  private final IsoPlayer recipient;
  private long started;
  private String active = "", completed = "";
  int transfers;
  long extraDelayNanos;

  NativeItemCollection(ItemContainer source, InventoryItem item, IsoPlayer recipient) {
    this.source = source;
    this.item = item;
    this.recipient = recipient;
    if (source == null || item == null || recipient == null || !source.contains(item))
      throw new IllegalArgumentException("collection_source");
  }

  /** Completion is acknowledged only after the same object belongs solely to this Actor. */
  boolean tick(IsoPlayer actor, String action, long now, boolean permitted) {
    GameHooks.ownThread();
    if (actor != recipient || actor.isDead())
      throw new IllegalStateException("collection_actor_changed");
    if (!completed.isEmpty()) {
      if (item.getContainer() != actor.getInventory()
          || !actor.getInventory().contains(item)
          || source.contains(item))
        throw new IllegalStateException("collection_completed_item_lost");
      return completed.equals(action);
    }
    IsoGridSquare square = source.getSourceGrid();
    boolean near =
        square != null
            && actor.getSquare() != null
            && (int) actor.getZ() == square.z
            && Math.hypot(actor.getX() - square.x - .5, actor.getY() - square.y - .5) <= 1.6;
    if (!permitted || action.isEmpty() || !near) {
      started = 0;
      active = "";
      return false;
    }
    // Adjacent does not mean reachable through a wall. Require the selected access square.
    var sight =
        zombie.iso.LosUtil.lineClear(
            actor.getCell(),
            actor.getSquare().x,
            actor.getSquare().y,
            actor.getSquare().z,
            square.x,
            square.y,
            square.z,
            false);
    if (sight == zombie.iso.LosUtil.TestResults.Blocked
        || sight == zombie.iso.LosUtil.TestResults.ClearThroughClosedDoor)
      throw new IllegalStateException("collection_obstructed");
    if (item.getContainer() != source || !source.contains(item))
      throw new IllegalStateException("collection_item_moved");
    if (!action.equals(active)) {
      active = action;
      started = now;
      return false;
    }
    if (now - started < 1_500_000_000L + extraDelayNanos) return false;
    if (actor.getInventory().getFreeCapacity(actor) < item.getActualWeight())
      throw new IllegalStateException("collection_capacity");
    source.Remove(item);
    try {
      actor.getInventory().AddItem(item);
      if (item.getContainer() != actor.getInventory()
          || !actor.getInventory().contains(item)
          || source.contains(item))
        throw new IllegalStateException("collection_transfer_unconfirmed");
    } catch (RuntimeException error) {
      if (!actor.getInventory().contains(item) && !source.contains(item)) source.AddItem(item);
      throw error;
    }
    // Commit before network publication so an exception cannot cause a duplicate transfer.
    completed = action;
    transfers++;
    GameServer.sendRemoveItemFromContainer(source, item);
    return true;
  }
}
