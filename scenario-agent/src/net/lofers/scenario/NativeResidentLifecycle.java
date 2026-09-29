package net.lofers.scenario;

import java.util.*;
import se.krka.kahlua.vm.*;
import zombie.Lua.LuaManager;
import zombie.characters.*;
import zombie.inventory.*;
import zombie.inventory.types.InventoryContainer;
import zombie.iso.objects.IsoDeadBody;
import zombie.network.ServerMap;

/** Game-thread lifecycle. Corpses/zombies outlive the reusable Actor; no authority override. */
public final class NativeResidentLifecycle {
  static final class Aftermath {
    final IsoDeadBody corpse;
    IsoZombie zombie;
    String failure = "";

    Aftermath(IsoDeadBody corpse) {
      this.corpse = corpse;
    }
  }

  private static final IdentityHashMap<IsoDeadBody, Aftermath> tracked = new IdentityHashMap<>();

  static void watch(IsoDeadBody corpse) {
    if (tracked.containsKey(corpse)) return;
    if (tracked.size() >= 64) throw new IllegalStateException("aftermath_tracking_budget");
    tracked.put(corpse, new Aftermath(corpse));
  }

  /** Called after the exact stock method returns. Never change its return or throw into it. */
  public static void reanimated(IsoGameCharacter result, IsoDeadBody corpse) {
    Aftermath a = tracked.get(corpse);
    if (a == null || result == null) return;
    if (!(result instanceof IsoZombie z)) {
      a.failure = "unexpected_reanimation_type";
      return;
    }
    if (a.zombie != null && a.zombie != z) {
      a.failure = "duplicate_reanimation";
      return;
    }
    a.zombie = z;
  }

  static IsoZombie zombie(IsoDeadBody corpse) {
    Aftermath a = tracked.get(corpse);
    return a == null ? null : a.zombie;
  }

  static void forget(IsoDeadBody corpse) {
    tracked.remove(corpse);
  }

  private final String world;
  private final TerminalJournal journal;
  private final CivilianPool<IsoPlayer> pool;
  private final NativeCivilianActors actors;
  final CivilianPool.Token token;
  final IsoPlayer body;
  final KahluaTable resident;
  private Aftermath aftermath;
  IsoDeadBody corpse;
  ItemContainer inventory;
  String fingerprint, corpseId = "", failure = "";
  TerminalJournal.Receipt receipt;
  boolean durable, released, reanimationRecorded;

  NativeResidentLifecycle(
      String world,
      TerminalJournal journal,
      CivilianPool<IsoPlayer> pool,
      NativeCivilianActors actors,
      CivilianPool.Token token,
      IsoPlayer body,
      KahluaTable resident) {
    this.world = world;
    this.journal = journal;
    this.pool = pool;
    this.actors = actors;
    this.token = token;
    this.body = body;
    this.resident = resident;
    bind(resident, token);
  }

  boolean tick(boolean releaseAllowed) {
    GameHooks.ownThread();
    if (!failure.isEmpty() || released) return released;
    try {
      var dead = pool.pollDead();
      if (dead == null || !dead.token().equals(token)) return false;
      if (corpse == null) {
        corpse = Objects.requireNonNull(actors.corpseFor(token, body));
        aftermath = tracked.get(corpse);
        corpseId = dead.corpseId();
        inventory = corpse.getContainer();
        if (inventory == null && zombie() != null) inventory = zombie().getInventory();
        fingerprint = inventoryDigest(Objects.requireNonNull(inventory));
        receipt = new TerminalJournal.Receipt(world, token, corpseId, fingerprint);
        terminalLedger();
      }
      verifyTransfer();
      var state = journal.submit(receipt);
      if (state == TerminalJournal.State.UNRESOLVED)
        throw new IllegalStateException("terminal_persistence_failed");
      durable = state == TerminalJournal.State.DURABLE;
      if (durable && releaseAllowed && pool.acknowledgeDead(token, corpseId)) {
        released = true;
        journal.forget(receipt);
        verifyTransfer();
      }
      return released;
    } catch (Exception error) {
      failure = error.getClass().getSimpleName() + ":" + error.getMessage();
      return false;
    }
  }

  IsoZombie zombie() {
    return aftermath == null ? null : aftermath.zombie;
  }

  void verifyTransfer() {
    var a = tracked.get(corpse);
    if (a == null || !a.failure.isEmpty())
      throw new IllegalStateException("aftermath_hook_unconfirmed");
    IsoZombie z = a.zombie;
    if (z == null) {
      if (corpse.getContainer() != inventory)
        throw new IllegalStateException("corpse_container_lost");
    } else if (corpse.getContainer() != null
        || z.getInventory() != inventory
        || ServerMap.instance.zombieMap.get(z.getOnlineID()) != z
        || !token.resident().equals(z.getModData().rawget("AKRResidentId")))
      throw new IllegalStateException("reanimation_transfer_unconfirmed");
    if (z != null && !reanimationRecorded) {
      ledger("reanimated", resident, token, corpseId, (double) z.getOnlineID());
      reanimationRecorded = true;
    }
    if (!fingerprint.equals(inventoryDigest(inventory)))
      throw new IllegalStateException("aftermath_inventory_changed");
  }

  private void terminalLedger() {
    resident.rawset("life", "DEAD");
    resident.rawset("state", "TERMINAL");
    resident.rawset("body", null);
    resident.rawset("newBody", false);
    ledger("deceased", resident, token, corpseId, fingerprint);
  }

  static void bind(KahluaTable resident, CivilianPool.Token token) {
    ledger("bind", resident, token);
  }

  private static void ledger(
      String method, KahluaTable resident, CivilianPool.Token token, Object... extra) {
    if (!(LuaManager.env.rawget("AKRPopulationLifecycle") instanceof KahluaTable api))
      throw new IllegalStateException("population_lifecycle_missing");
    var t = LuaManager.platform.newTable();
    t.rawset("epoch", token.epoch());
    t.rawset("slot", (double) token.slot());
    t.rawset("generation", (double) token.generation());
    t.rawset("resident", token.resident());
    t.rawset("residentGeneration", (double) token.residentGeneration());
    var args = new Object[extra.length + 2];
    args[0] = resident;
    args[1] = t;
    System.arraycopy(extra, 0, args, 2, extra.length);
    var result =
        LuaManager.caller.protectedCall(LuaManager.thread, (LuaClosure) api.rawget(method), args);
    if (!result.isSuccess() || !Boolean.TRUE.equals(result.getFirst()))
      throw new IllegalStateException(
          "population_" + method + "_rejected:" + result.getErrorString());
  }

  static boolean sharesItem(ItemContainer left, ItemContainer right) {
    var a = new TreeMap<Integer, String>();
    var b = new TreeMap<Integer, String>();
    scan(left, a, Collections.newSetFromMap(new IdentityHashMap<ItemContainer, Boolean>()), 0, 0);
    scan(right, b, Collections.newSetFromMap(new IdentityHashMap<ItemContainer, Boolean>()), 0, 0);
    return a.keySet().stream().anyMatch(b::containsKey);
  }

  static String inventoryDigest(ItemContainer container) {
    var ids = new TreeMap<Integer, String>();
    var seen = Collections.newSetFromMap(new IdentityHashMap<ItemContainer, Boolean>());
    scan(container, ids, seen, 0, 0);
    return TerminalJournal.digest(ids.toString());
  }

  private static void scan(
      ItemContainer c, Map<Integer, String> ids, Set<ItemContainer> seen, int depth, int parent) {
    if (depth > 8 || !seen.add(c)) throw new IllegalStateException("inventory_depth_or_cycle");
    for (InventoryItem item : c.getItems()) {
      if (ids.size() >= 512 || ids.put(item.getID(), parent + ":" + item.getFullType()) != null)
        throw new IllegalStateException("inventory_budget_or_duplicate");
      if (item instanceof InventoryContainer bag)
        scan(bag.getInventory(), ids, seen, depth + 1, item.getID());
    }
  }
}
