package net.akr.scenario;

import java.lang.reflect.Field;
import java.util.*;
import zombie.characters.*;
import zombie.characters.BodyDamage.Fitness;

/**
 * Pinned reset of a detached living ambient Actor, before binding any resident snapshot.
 * Constructor-owned callbacks, animator, network AI and other engine components are retained.
 * Corpse, vehicle and in-flight native actions are not reset into a new person.
 */
final class NativeActorReset {
  private final List<Field> characterCollections = new ArrayList<>(),
      playerCollections = new ArrayList<>(),
      fitnessCollections = new ArrayList<>();

  NativeActorReset() {
    fields(
        IsoGameCharacter.class,
        characterCollections,
        "readBooks",
        "knownRecipes",
        "readLiterature",
        "knownMediaLines");
    fields(IsoPlayer.class, playerCollections, "mechanicsItem", "alreadyReadBook", "lastSpotted");
    fields(
        Fitness.class,
        fitnessCollections,
        "stiffnessIncMap",
        "stiffnessTimerMap",
        "regularityMap",
        "bodypartToIncStiffness",
        "exeTimer");
  }

  private static void fields(Class<?> type, List<Field> result, String... names) {
    try {
      for (String name : names) {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        if (!Collection.class.isAssignableFrom(field.getType())
            && !Map.class.isAssignableFrom(field.getType()))
          throw new IllegalStateException("reset_field_type:" + name);
        result.add(field);
      }
    } catch (ReflectiveOperationException error) {
      throw new IllegalStateException("actor_reset_contract", error);
    }
  }

  private static void clear(Object owner, List<Field> fields) throws IllegalAccessException {
    for (Field field : fields) {
      Object value = field.get(owner);
      if (value instanceof Map<?, ?> map) map.clear();
      else if (value instanceof Collection<?> collection) collection.clear();
      else throw new IllegalStateException("reset_collection_missing");
    }
  }

  static void requireQuiescent(IsoPlayer body) {
    if (body.isDead()
        || body.getVehicle() != null
        || body.isOnFire()
        || !body.getCharacterActions().isEmpty()
        || body.isPerformingAnAction()
        || body.isClimbing()
        || body.isGrappling()
        || body.isAttacking()
        || body.isGettingUp()
        || body.isKnockedDown()
        || body.isOnFloor()
        || body.isRagdollSimulationActive())
      throw new IllegalStateException("actor_not_resettable");
  }

  static void detachDescriptor(IsoPlayer body) {
    var descriptor = body.getDescriptor();
    if (descriptor != null) {
      if (descriptor.getInstance() == body) descriptor.setInstance(null);
      zombie.iso.IsoWorld.instance.survivorDescriptors.remove(descriptor.getID(), descriptor);
    }
  }

  void clearBeforeLoad(IsoPlayer body) throws Exception {
    GameHooks.ownThread();
    requireQuiescent(body);
    detachDescriptor(body);
    body.setPrimaryHandItem(null);
    body.setSecondaryHandItem(null);
    body.setUseHandWeapon(null);
    body.getWornItems().clear();
    body.getAttachedItems().clear();
    body.getInventory().clear();
    body.getModData().wipe();
    clear(body, characterCollections);
    clear(body, playerCollections);
    clear(body.getFitness(), fitnessCollections);
    body.getBodyDamage().RestoreToFullHealth();
    body.setAttackedBy(null);
    body.setKnockedDown(false);
    body.setOnFloor(false);
    body.setFallOnFront(false);
    body.setRunning(false);
    body.setSprinting(false);
    body.setSneaking(false);
    body.setBumpType("");
    body.setHitReaction("");
    body.setBumpFall(false);
    body.setBumpStaggered(false);
    body.getPathFindBehavior2().reset();
    body.setPath2(null);
    body.getActionContext().clearActionContextEvents();
    body.setDefaultState();
    body.getNetworkCharacterAI().resetState();
    // The following stock load supplies stats, XP/traits, health, descriptor and visuals.
    // Explicitly cleared collections/empty slots above are not reset by stock load.
  }
}
