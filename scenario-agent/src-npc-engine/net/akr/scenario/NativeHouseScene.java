package net.akr.scenario;

import java.util.*;
import net.akr.scenario.npc.core.CivilianNavigation.Tile;
import zombie.characters.IsoPlayer;
import zombie.inventory.InventoryItem;
import zombie.inventory.ItemContainer;
import zombie.iso.*;
import zombie.iso.objects.IsoBarricade;
import zombie.iso.objects.IsoDoor;
import zombie.network.GameServer;
import zombie.network.ServerMap;

/** Bounded survey of an existing house and a different building's real container. */
final class NativeHouseScene {
  static final Tile ORIGIN = new Tile(10753, 9839, 0);
  final List<Tile> homes = new ArrayList<>();
  final List<IsoDoor> exits = new ArrayList<>();
  private final Map<IsoDoor, Boolean> originalLocked = new IdentityHashMap<>();
  private final Map<IsoDoor, Boolean> originalKeyLocked = new IdentityHashMap<>();
  private final Map<IsoDoor, Boolean> originalOpen = new IdentityHashMap<>();
  private final List<IsoBarricade> barriers = new ArrayList<>();
  private final List<InventoryItem> items = new ArrayList<>();
  private final List<IsoPlayer> recipients = new ArrayList<>();
  private zombie.iso.areas.IsoBuilding building;
  private int cursor;
  private boolean ready, activitySelected;

  private record Candidate(ItemContainer container, Tile at, double distance) {}

  private final List<Candidate> candidates = new ArrayList<>();
  private net.akr.scenario.npc.core.CivilianGraphCapture accessCapture;
  private double best = Double.MAX_VALUE;
  ItemContainer container;
  Tile activity;
  int observedOpenings;
  private final Set<IsoDoor> observed = Collections.newSetFromMap(new IdentityHashMap<>());

  /** Examine at most 32 squares per call; never scan the loaded world. */
  boolean survey() {
    GameHooks.ownThread();
    if (ready) return true;
    ServerMap.instance.characterIn(Math.floorDiv(ORIGIN.x(), 8), Math.floorDiv(ORIGIN.y(), 8), 7);
    IsoGridSquare origin = square(ORIGIN);
    if (origin == null) return false;
    if (building == null) {
      building = origin.getBuilding();
      if (building == null) throw new IllegalStateException("house_origin_not_in_building");
    }
    for (int count = 0; count < 32 && cursor < 49 * 49; count++, cursor++) {
      int x = ORIGIN.x() - 24 + cursor % 49, y = ORIGIN.y() - 24 + cursor / 49;
      var s = ServerMap.instance.getGridSquare(x, y, 0);
      if (s == null) return false;
      if (s.getBuilding() == building && s.isFree(false) && !s.HasStairs())
        homes.add(new Tile(x, y, 0));
      for (int objectIndex = 0; objectIndex < s.getObjects().size(); objectIndex++) {
        var object = s.getObjects().get(objectIndex);
        if (object instanceof IsoDoor door) {
          var opposite =
              ServerMap.instance.getGridSquare(
                  x - (door.getNorth() ? 0 : 1), y - (door.getNorth() ? 1 : 0), 0);
          if (opposite != null
              && (s.getBuilding() == building && opposite.getBuilding() == null
                  || opposite.getBuilding() == building && s.getBuilding() == null))
            exits.add(door);
        }
        var candidate = object.getContainer();
        double distance = Math.hypot(x - ORIGIN.x(), y - ORIGIN.y());
        if (candidate == null || s.getBuilding() == building || distance < 10 || distance > 28)
          continue;
        for (int[] delta : new int[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
          var a = ServerMap.instance.getGridSquare(x + delta[0], y + delta[1], 0);
          if (a == null || !a.isFree(false) || a.HasStairs()) continue;
          var los = LosUtil.lineClear(IsoWorld.instance.currentCell, a.x, a.y, 0, x, y, 0, false);
          if (los == LosUtil.TestResults.Blocked
              || los == LosUtil.TestResults.ClearThroughClosedDoor) continue;
          candidates.add(new Candidate(candidate, new Tile(a.x, a.y, 0), distance));
          if (distance < best) {
            best = distance;
            container = candidate;
            activity = new Tile(a.x, a.y, 0);
          }
        }
      }
    }
    if (cursor < 49 * 49) return false;
    System.out.println(
        "[HouseScene] room="
            + origin.getRoom().getName()
            + " container="
            + container.getType()
            + " at="
            + activity);
    homes.sort(Comparator.comparingDouble((Tile t) -> t.distance(ORIGIN)).thenComparing(t -> t));
    if (homes.size() < 4 || exits.isEmpty() || container == null)
      throw new IllegalStateException(
          "house_scene_missing_home_exit_or_container:"
              + homes.size()
              + ":"
              + exits.size()
              + ":"
              + (container != null));
    return true;
  }

  /** Choose a real container access square reachable by supported ground-floor actions. */
  boolean selectActivity(IsoPlayer actor) {
    if (activitySelected) return true;
    if (accessCapture == null) {
      prepareDoors(actor);
      var from = new Tile((int) actor.getX(), (int) actor.getY(), 0);
      accessCapture =
          new net.akr.scenario.npc.core.CivilianGraphCapture(
              new NativeCivilianGraphSource(actor, () -> 1L, List.of(), List.of()), from, 16);
    }
    accessCapture.advance(32);
    if (!accessCapture.complete()) return false;
    if (accessCapture.limited() || accessCapture.unknown())
      throw new IllegalStateException("house_access_unknown");
    var graph = accessCapture.result();
    var seen = new HashSet<Tile>();
    var pending = new ArrayDeque<Tile>();
    var start = new Tile((int) actor.getX(), (int) actor.getY(), 0);
    pending.add(start);
    seen.add(start);
    while (!pending.isEmpty()) {
      for (var edge : graph.edges().getOrDefault(pending.removeFirst(), List.of())) {
        if (NativeCivilianGeometry.executableActions().contains(edge.action())
            && seen.add(edge.to())) pending.add(edge.to());
      }
    }
    var chosen =
        candidates.stream()
            .filter(c -> seen.contains(c.at()))
            .min(Comparator.comparingDouble(Candidate::distance).thenComparing(Candidate::at))
            .orElseThrow(() -> new IllegalStateException("house_no_supported_container_access"));
    container = chosen.container();
    activity = chosen.at();
    activitySelected = true;
    System.out.println(
        "[HouseScene] walk/door access=" + activity + " container=" + container.getType());
    accessCapture = null;
    return true;
  }

  /**
   * Find a loaded, unobstructed spectator row in the Actor's room, never a coordinate-only hint.
   */
  Tile viewing(IsoPlayer focus, int seats) {
    var at = focus.getSquare();
    if (at == null) throw new IllegalStateException("house_view_actor_unloaded");
    for (int radius = 2; radius <= 5; radius++) {
      for (int dy = -radius; dy <= radius; dy++)
        for (int dx = -radius; dx <= radius; dx++) {
          if (Math.max(Math.abs(dx), Math.abs(dy)) != radius) continue;
          boolean valid = true;
          for (int seat = 0; seat < seats; seat++) {
            var s = ServerMap.instance.getGridSquare(at.x + dx + (int) (seat * 1.5), at.y + dy, 0);
            if (s == null
                || !s.isFree(false)
                || s.HasStairs()
                || s.getRoom() != at.getRoom()
                || LosUtil.lineClear(
                        IsoWorld.instance.currentCell, s.x, s.y, 0, at.x, at.y, 0, false)
                    != LosUtil.TestResults.Clear) {
              valid = false;
              break;
            }
          }
          if (valid) return new Tile(at.x + dx, at.y + dy, 0);
        }
    }
    throw new IllegalStateException("house_clear_viewing_spot_missing");
  }

  static IsoGridSquare square(Tile tile) {
    return ServerMap.instance.getGridSquare(tile.x(), tile.y(), tile.z());
  }

  boolean outside(IsoPlayer actor) {
    return actor.getSquare() != null && actor.getSquare().getBuilding() != building;
  }

  void prepareDoors(IsoPlayer actor) {
    for (var door : exits) {
      originalOpen.putIfAbsent(door, door.isOpen());
      originalLocked.putIfAbsent(door, door.isLocked());
      originalKeyLocked.putIfAbsent(door, door.isLockedByKey());
      door.setLocked(false);
      door.setLockedByKey(false);
      if (door.isOpen()) door.ToggleDoor(actor);
      if (door.isOpen()) throw new IllegalStateException("house_door_did_not_close");
      var at = door.getSquare();
      var a = new Tile(at.x, at.y, at.z);
      var b = new Tile(at.x - (door.getNorth() ? 0 : 1), at.y - (door.getNorth() ? 1 : 0), at.z);
      System.out.println(
          "[HouseDoor] " + a + " to " + b + " " + NativeCivilianGeometry.classify(actor, a, b));
    }
  }

  /** Real fixture barricades: door locks alone do not prevent opening from inside. */
  void blockExits(boolean value) {
    if (value) {
      if (!barriers.isEmpty()) throw new IllegalStateException("house_barrier_already_owned");
      for (var door : exits) {
        if (door.isBarricaded()) throw new IllegalStateException("house_existing_barricade");
        var barricade = IsoBarricade.AddBarricadeToObject(door, false);
        if (barricade == null) throw new IllegalStateException("house_barricade_creation");
        barriers.add(barricade);
        barricade.addPlank(null, null);
        barricade.transmitCompleteItemToClients();
        if (!door.isBarricaded()) throw new IllegalStateException("house_barricade_unconfirmed");
      }
    } else {
      while (!barriers.isEmpty()) {
        var barricade = barriers.getFirst();
        var square = barricade.getSquare();
        if (square == null || !square.getObjects().contains(barricade))
          throw new IllegalStateException("house_barricade_identity_lost");
        square.transmitRemoveItemFromSquare(barricade);
        if (square.getObjects().contains(barricade)
            || square.getSpecialObjects().contains(barricade))
          throw new IllegalStateException("house_barricade_cleanup_unconfirmed");
        barriers.removeFirst();
      }
    }
  }

  void observe() {
    for (var door : exits) if (door.isOpen() && observed.add(door)) observedOpenings++;
  }

  NativeItemCollection collection(IsoPlayer actor, String tag) {
    InventoryItem item = container.AddItem("Base.Pen");
    if (item == null) throw new IllegalStateException("house_fixture_item_creation");
    item.getModData().rawset("AKRHouseFixture", tag);
    items.add(item);
    recipients.add(actor);
    GameServer.sendAddItemToContainer(container, item);
    return new NativeItemCollection(container, item, actor);
  }

  /** Remove only exact tagged fixture items; unknown ownership retains the scene. */
  void cleanup(IsoPlayer actor) {
    while (!items.isEmpty()) {
      var item = items.getFirst();
      var owner = item.getContainer();
      if (owner != container && owner != recipients.getFirst().getInventory())
        throw new IllegalStateException("house_fixture_item_owner_unknown");
      if (!owner.contains(item)) throw new IllegalStateException("house_fixture_item_missing");
      if (owner == container) GameServer.sendRemoveItemFromContainer(container, item);
      owner.Remove(item);
      if (owner.contains(item)) throw new IllegalStateException("house_fixture_item_cleanup");
      items.removeFirst();
      recipients.removeFirst();
    }
    items.clear();
    recipients.clear();
    blockExits(false);
    for (var entry : originalOpen.entrySet()) {
      var door = entry.getKey();
      if (!door.getSquare().getObjects().contains(door))
        throw new IllegalStateException("house_door_identity_lost");
      if (door.isOpen() != entry.getValue()) door.ToggleDoor(actor);
      if (door.isOpen() != entry.getValue()) throw new IllegalStateException("house_door_restore");
      door.setLocked(originalLocked.get(door));
      door.setLockedByKey(originalKeyLocked.get(door));
    }
    originalOpen.clear();
    originalLocked.clear();
    originalKeyLocked.clear();
    observed.clear();
    observedOpenings = 0;
  }
}
