package net.akr.scenario;

import java.lang.classfile.*;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.*;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Set;

final class ScenarioTransformer implements ClassFileTransformer {
  static final ClassDesc AGENT = ClassDesc.of("net.akr.scenario.ScenarioAgent");
  static final ClassDesc NATIVE = ClassDesc.of("net.akr.scenario.GameHooks");
  static final ClassDesc CRASH = ClassDesc.of("net.akr.scenario.ProbeCrashFeedback");
  static final ClassDesc PEDESTRIANS = ClassDesc.of("net.akr.scenario.ServerPedestrians");
  static final ClassDesc ACTORS = ClassDesc.of("net.akr.scenario.ServerActors");
  static final Set<String> TARGETS =
      Set.of(
          "zombie/network/RCONServer",
          "zombie/Lua/LuaManager",
          "zombie/Lua/Event",
          "zombie/VirtualZombieManager",
          "zombie/iso/IsoWorld",
          "zombie/core/physics/CarController",
          "zombie/vehicles/BaseVehicle",
          "zombie/iso/IsoChunk",
          "zombie/popman/NetworkZombieManager",
          "zombie/popman/NetworkZombiePacker",
          "zombie/characters/IsoGameCharacter",
          "zombie/MovingObjectUpdateScheduler",
          "zombie/characters/IsoZombie",
          "zombie/characters/IsoPlayer",
          "zombie/core/skinnedmodel/animation/AnimationPlayer",
          "zombie/CombatManager",
          "zombie/iso/objects/IsoDeadBody");

  public byte[] transform(
      ClassLoader loader, String name, Class<?> redefining, ProtectionDomain domain, byte[] bytes) {
    if (!TARGETS.contains(name) || redefining != null) return null;
    try {
      byte[] result = instrument(name, bytes, loader);
      net.akr.scenario.compat.HookContract.verify(name, result);
      ScenarioAgent.hooks.add(name);
      return result;
    } catch (Throwable ex) {
      ScenarioAgent.hookFailure = name + ":" + ex.getClass().getSimpleName();
      return null;
    }
  }

  static byte[] instrument(String name, byte[] bytes, ClassLoader loader) {
    var cf =
        ClassFile.of(
            ClassFile.ClassHierarchyResolverOption.of(
                ClassHierarchyResolver.ofResourceParsing(loader)));
    return cf.transformClass(
        cf.parse(bytes),
        (cb, element) -> {
          if (!(element instanceof MethodModel m) || m.code().isEmpty()) {
            cb.with(element);
            return;
          }
          String method = m.methodName().stringValue(), descriptor = m.methodType().stringValue();
          int clientReads = 0;
          if (name.equals("zombie/CombatManager")
              && (method.equals("attackCollisionCheck") || method.equals("processClientHit"))) {
            for (CodeElement instruction : m.code().get()) {
              if (instruction instanceof FieldInstruction field
                  && field.opcode() == Opcode.GETSTATIC
                  && field.owner().asInternalName().equals("zombie/network/GameClient")
                  && field.name().equalsString("client")) clientReads++;
            }
          }
          final int collectRead = clientReads;
          cb.transformMethod(
              m,
              MethodTransform.transformingCode(
                  new CodeTransform() {
                    int clientReadsSeen;

                    public void atStart(CodeBuilder b) {
                      if (name.equals("zombie/CombatManager")
                          && method.equals("CheckObjectHit")
                          && descriptor.equals(
                              "(Lzombie/characters/IsoGameCharacter;Lzombie/inventory/types/HandWeapon;)Z")) {
                        b.aload(1)
                            .invokestatic(
                                ClassDesc.of("net.akr.scenario.NativeCombatRelay"),
                                "scoped",
                                MethodTypeDesc.ofDescriptor(
                                    "(Lzombie/characters/IsoGameCharacter;)Z"));
                        b.ifThen(
                            x ->
                                x.aload(0)
                                    .aconst_null()
                                    .putfield(
                                        ClassDesc.of("zombie.CombatManager"),
                                        "objHit",
                                        ClassDesc.of("zombie.iso.IsoObject"))
                                    .aload(0)
                                    .aconst_null()
                                    .putfield(
                                        ClassDesc.of("zombie.CombatManager"),
                                        "treeHit",
                                        ClassDesc.of("zombie.iso.objects.IsoTree"))
                                    .iconst_0()
                                    .ireturn());
                      }
                      if (name.equals("zombie/characters/IsoGameCharacter")
                          && method.equals("CanUsePathfindState")
                          && descriptor.equals("()Z")) {
                        b.aload(0)
                            .invokestatic(
                                PEDESTRIANS,
                                "ownsCharacter",
                                MethodTypeDesc.ofDescriptor(
                                    "(Lzombie/characters/IsoGameCharacter;)Z"));
                        b.ifThen(x -> x.iconst_1().ireturn());
                      }
                      if (name.equals("zombie/characters/IsoZombie")) {
                        if ((Set.of("spotted", "spottedNew", "spottedOld").contains(method)
                                && descriptor.equals("(Lzombie/iso/IsoMovingObject;Z)V"))
                            || (Set.of("RespondToSound", "Wander").contains(method)
                                && descriptor.equals("()V"))) {
                          b.aload(0)
                              .invokestatic(
                                  PEDESTRIANS,
                                  "owns",
                                  MethodTypeDesc.ofDescriptor("(Lzombie/characters/IsoZombie;)Z"));
                          b.ifThen(x -> x.return_());
                        }
                        if (Set.of("update", "postupdate").contains(method)
                            && descriptor.equals("()V"))
                          b.aload(0)
                              .invokestatic(
                                  PEDESTRIANS,
                                  "beginNativeStep",
                                  MethodTypeDesc.ofDescriptor("(Lzombie/characters/IsoZombie;)V"));
                        if (method.equals("isTargetVisible") && descriptor.equals("()Z")) {
                          b.aload(0)
                              .invokestatic(
                                  PEDESTRIANS,
                                  "overridesTargetVisibility",
                                  MethodTypeDesc.ofDescriptor("(Lzombie/characters/IsoZombie;)Z"));
                          b.ifThen(
                              x ->
                                  x.aload(0)
                                      .invokestatic(
                                          PEDESTRIANS,
                                          "actorTargetVisible",
                                          MethodTypeDesc.ofDescriptor(
                                              "(Lzombie/characters/IsoZombie;)Z"))
                                      .ireturn());
                        }
                        if (method.equals("isRemoteZombie") && descriptor.equals("()Z")) {
                          b.aload(0)
                              .invokestatic(
                                  PEDESTRIANS,
                                  "hunts",
                                  MethodTypeDesc.ofDescriptor("(Lzombie/characters/IsoZombie;)Z"));
                          b.ifThen(x -> x.iconst_0().ireturn());
                        }
                      }

                      // Server-simulated hunters only: the dedicated server's non-visual animation
                      // update
                      // computes root translation but never root rotation, so turn-in-place
                      // animations
                      // (deferred rotation) freeze the facing angle. Fall back to the stock
                      // procedural
                      // rotation branch by clearing this frame's deferred-rotation weight.
                      if (name.equals("zombie/core/skinnedmodel/animation/AnimationPlayer")
                          && method.equals("DoAngles")
                          && descriptor.equals("(F)V")) {
                        ClassDesc anim =
                            ClassDesc.of("zombie.core.skinnedmodel.animation.AnimationPlayer");
                        b.aload(0)
                            .getfield(
                                anim,
                                "character",
                                ClassDesc.of("zombie.characters.IsoGameCharacter"));
                        b.invokestatic(
                            PEDESTRIANS,
                            "serverTurnFallback",
                            MethodTypeDesc.ofDescriptor("(Lzombie/characters/IsoGameCharacter;)Z"));
                        b.ifThen(
                            x ->
                                x.aload(0)
                                    .fconst_0()
                                    .putfield(
                                        anim, "deferredRotationWeight", ConstantDescs.CD_float));
                      }
                      // Actor pool timing only: a map lookup for every other server player.
                      if (name.equals("zombie/characters/IsoPlayer")
                          && Set.of("update", "postupdate").contains(method)
                          && descriptor.equals("()V"))
                        b.aload(0)
                            .invokestatic(
                                ACTORS,
                                "beginNativeStep",
                                MethodTypeDesc.ofDescriptor("(Lzombie/characters/IsoPlayer;)V"));

                      if (name.equals("zombie/characters/IsoGameCharacter")
                          && method.equals("setVariable")
                          && Set.of(
                                  "(Lzombie/core/skinnedmodel/advancedanimation/IAnimationVariableSlot;)V",
                                  "(Ljava/lang/String;Ljava/lang/String;)Lzombie/core/skinnedmodel/advancedanimation/IAnimationVariableSlot;",
                                  "(Ljava/lang/String;Z)Lzombie/core/skinnedmodel/advancedanimation/IAnimationVariableSlot;",
                                  "(Ljava/lang/String;F)Lzombie/core/skinnedmodel/advancedanimation/IAnimationVariableSlot;",
                                  "(Lzombie/core/skinnedmodel/advancedanimation/AnimationVariableHandle;Z)Lzombie/core/skinnedmodel/advancedanimation/IAnimationVariableSlot;")
                              .contains(descriptor)) {
                        b.aload(0)
                            .invokestatic(
                                PEDESTRIANS,
                                "ownsCharacter",
                                MethodTypeDesc.ofDescriptor(
                                    "(Lzombie/characters/IsoGameCharacter;)Z"));
                        b.ifThen(
                            x -> {
                              x.aload(0)
                                  .invokevirtual(
                                      ClassDesc.of("zombie.characters.IsoGameCharacter"),
                                      "getGameVariablesInternal",
                                      MethodTypeDesc.ofDescriptor(
                                          "()Lzombie/core/skinnedmodel/advancedanimation/AnimationVariableSource;"));
                              x.aload(1);
                              if (descriptor.contains(";Z)")) x.iload(2);
                              else if (descriptor.contains(";F)")) x.fload(2);
                              else if (descriptor.startsWith(
                                  "(Ljava/lang/String;Ljava/lang/String;")) x.aload(2);
                              x.invokevirtual(
                                  ClassDesc.of(
                                      "zombie.core.skinnedmodel.advancedanimation.AnimationVariableSource"),
                                  "setVariable",
                                  MethodTypeDesc.ofDescriptor(descriptor));
                              if (descriptor.endsWith(")V")) x.return_();
                              else x.areturn();
                            });
                      }
                      if (name.equals("zombie/popman/NetworkZombieManager")
                          && method.equals("updateAuth")
                          && descriptor.equals("(Lzombie/characters/IsoZombie;)V")) {
                        b.aload(1)
                            .invokestatic(
                                ClassDesc.of("net.akr.scenario.OffscreenZombieLease"),
                                "maintain",
                                MethodTypeDesc.ofDescriptor("(Lzombie/characters/IsoZombie;)Z"));
                        b.ifThen(x -> x.return_());
                        b.aload(1)
                            .invokestatic(
                                PEDESTRIANS,
                                "simulated",
                                MethodTypeDesc.ofDescriptor("(Lzombie/characters/IsoZombie;)Z"));
                        b.ifThen(x -> x.return_());
                      }
                      if (name.equals("zombie/vehicles/BaseVehicle")
                          && method.equals("crash")
                          && descriptor.equals("(FZ)V"))
                        b.aload(0)
                            .fload(1)
                            .iload(2)
                            .invokestatic(
                                CRASH,
                                "onCrash",
                                MethodTypeDesc.ofDescriptor("(Lzombie/vehicles/BaseVehicle;FZ)V"));
                      if (name.equals("zombie/network/RCONServer")
                          && method.equals("update")
                          && descriptor.equals("()V"))
                        b.invokestatic(AGENT, "tick", MethodTypeDesc.ofDescriptor("()V"));
                      if (name.equals("zombie/Lua/Event") && method.equals("trigger"))
                        b.invokestatic(AGENT, "bootstrap", MethodTypeDesc.ofDescriptor("()V"));
                      if (name.equals("zombie/iso/IsoWorld")
                          && method.equals("getZombiesDisabled")) {
                        b.invokestatic(NATIVE, "inSpawnPermit", MethodTypeDesc.ofDescriptor("()Z"));
                        b.ifThen(x -> x.iconst_0().ireturn());
                        b.invokestatic(
                            NATIVE, "populationBlocked", MethodTypeDesc.ofDescriptor("()Z"));
                        b.ifThen(x -> x.iconst_1().ireturn());
                      }
                      if (name.equals("zombie/VirtualZombieManager")
                          && method.equals("createRealZombieAlways")
                          && descriptor.equals(
                              "(Lzombie/iso/IsoDirections;ZII)Lzombie/characters/IsoZombie;")) {
                        b.invokestatic(
                            NATIVE, "factoryBlocked", MethodTypeDesc.ofDescriptor("()Z"));
                        b.ifThen(x -> x.aconst_null().areturn());
                      }
                      if (name.equals("zombie/core/physics/CarController")
                          && method.equals("update")
                          && descriptor.equals("()V")) {
                        b.aload(0)
                            .invokestatic(
                                NATIVE,
                                "vehicleStep",
                                MethodTypeDesc.ofDescriptor(
                                    "(Lzombie/core/physics/CarController;)Z"));
                        b.ifThen(x -> x.return_());
                      }
                    }

                    public void accept(CodeBuilder b, CodeElement e) {
                      if (collectRead > 0
                          && e instanceof FieldInstruction field
                          && field.opcode() == Opcode.GETSTATIC
                          && field.owner().asInternalName().equals("zombie/network/GameClient")
                          && field.name().equalsString("client")
                          && ++clientReadsSeen == collectRead) {
                        b.invokestatic(
                            ClassDesc.of("net.akr.scenario.NativeCombatRelay"),
                            "collectingHits",
                            MethodTypeDesc.ofDescriptor("()Z"));
                        return;
                      }

                      if (name.equals("zombie/iso/objects/IsoDeadBody")
                          && method.equals("reanimate")
                          && descriptor.equals("()Lzombie/characters/IsoGameCharacter;")
                          && e instanceof ReturnInstruction) {
                        b.dup()
                            .aload(0)
                            .invokestatic(
                                ClassDesc.of("net.akr.scenario.NativeResidentLifecycle"),
                                "reanimated",
                                MethodTypeDesc.ofDescriptor(
                                    "(Lzombie/characters/IsoGameCharacter;Lzombie/iso/objects/IsoDeadBody;)V"));
                      }
                      if (name.equals("zombie/CombatManager")
                          && method.equals("attackCollisionCheck")
                          && e instanceof InvokeInstruction ui
                          && ui.owner().asInternalName().equals("zombie/ui/MoodlesUI")
                          && ui.name().equalsString("wiggle")
                          && ui.type().equalsString("(Lzombie/scripting/objects/MoodleType;)V")) {
                        b.invokestatic(
                            ClassDesc.of("net.akr.scenario.NativeCombatRelay"),
                            "wiggle",
                            MethodTypeDesc.ofDescriptor(
                                "(Lzombie/ui/MoodlesUI;Lzombie/scripting/objects/MoodleType;)V"));
                        return;
                      }
                      if (name.equals("zombie/CombatManager")
                          && method.equals("calculateHitInfoList")
                          && descriptor.equals("(Lzombie/characters/IsoGameCharacter;)V")
                          && e instanceof ReturnInstruction)
                        b.aload(1)
                            .invokestatic(
                                ClassDesc.of("net.akr.scenario.NativeCombatRelay"),
                                "filterTargets",
                                MethodTypeDesc.ofDescriptor(
                                    "(Lzombie/characters/IsoGameCharacter;)V"));
                      if (name.equals("zombie/CombatManager")
                          && e instanceof InvokeInstruction hit
                          && hit.owner().asInternalName().equals("zombie/network/GameClient")
                          && hit.name().equalsString("sendPlayerHit")
                          && hit.type()
                              .equalsString(
                                  "(Lzombie/characters/IsoGameCharacter;Lzombie/iso/IsoObject;Lzombie/inventory/types/HandWeapon;ZLjava/util/List;Ljava/util/List;Z)V")) {
                        b.invokestatic(
                            ClassDesc.of("net.akr.scenario.NativeCombatRelay"),
                            "sendPlayerHit",
                            MethodTypeDesc.ofDescriptor(hit.type().stringValue()));
                        return;
                      }
                      if (name.equals("zombie/characters/IsoZombie")
                          && Set.of("update", "postupdate").contains(method)
                          && descriptor.equals("()V")
                          && e instanceof ReturnInstruction)
                        b.aload(0)
                            .invokestatic(
                                PEDESTRIANS,
                                "endNativeStep",
                                MethodTypeDesc.ofDescriptor("(Lzombie/characters/IsoZombie;)V"));
                      if (name.equals("zombie/characters/IsoPlayer")
                          && Set.of("update", "postupdate").contains(method)
                          && descriptor.equals("()V")
                          && e instanceof ReturnInstruction)
                        b.aload(0)
                            .invokestatic(
                                ACTORS,
                                "endNativeStep",
                                MethodTypeDesc.ofDescriptor("(Lzombie/characters/IsoPlayer;)V"));
                      if (name.equals("zombie/MovingObjectUpdateScheduler")
                          && method.equals("startFrame")
                          && descriptor.equals("()V")
                          && e instanceof ReturnInstruction)
                        b.aload(0)
                            .invokestatic(
                                PEDESTRIANS,
                                "scheduleActors",
                                MethodTypeDesc.ofDescriptor(
                                    "(Lzombie/MovingObjectUpdateScheduler;)V"));
                      if (name.equals("zombie/popman/NetworkZombiePacker")
                          && method.equals("getZombieData")
                          && descriptor.equals(
                              "(Lzombie/core/raknet/UdpConnection;Lzombie/network/packets/character/ZombieSynchronizationPacket;)I")
                          && e instanceof ReturnInstruction)
                        b.aload(1)
                            .aload(2)
                            .invokestatic(
                                PEDESTRIANS,
                                "appendSnapshots",
                                MethodTypeDesc.ofDescriptor(
                                    "(ILzombie/core/raknet/UdpConnection;Lzombie/network/packets/character/ZombieSynchronizationPacket;)I"));
                      if (name.equals("zombie/iso/IsoChunk")
                          && method.equals("calcPhysics")
                          && descriptor.equals("(III[I)V")
                          && e instanceof ReturnInstruction)
                        b.aload(0)
                            .iload(1)
                            .iload(2)
                            .iload(3)
                            .aload(4)
                            .invokestatic(
                                ClassDesc.of("net.akr.scenario.ProbeRockCollider"),
                                "apply",
                                MethodTypeDesc.ofDescriptor("(Lzombie/iso/IsoChunk;III[I)V"));
                      if (name.equals("zombie/Lua/LuaManager")
                          && method.equals("init")
                          && e instanceof ReturnInstruction)
                        b.invokestatic(AGENT, "bootstrap", MethodTypeDesc.ofDescriptor("()V"));
                      // Actor hunters only: stock tells an owner client to report its zombies every
                      // 4 s
                      // unless another player is near; an Actor is that player (200 ms, stock
                      // cadence).
                      if (name.equals("zombie/popman/NetworkZombiePacker")
                          && method.equals("send")
                          && descriptor.equals("(Lzombie/core/raknet/UdpConnection;)V")
                          && e instanceof InvokeInstruction neighbor
                          && neighbor
                              .owner()
                              .asInternalName()
                              .equals("zombie/core/raknet/UdpConnection")
                          && neighbor.name().equalsString("isNeighborPlayer")
                          && neighbor.type().equalsString("()Z")) {
                        b.with(e);
                        b.aload(1)
                            .invokestatic(
                                ACTORS,
                                "neighborPlayer",
                                MethodTypeDesc.ofDescriptor(
                                    "(ZLzombie/core/raknet/UdpConnection;)Z"));
                      } else if (name.equals("zombie/Lua/Event")
                          && method.equals("trigger")
                          && e instanceof InvokeInstruction call
                          && call.owner()
                              .asInternalName()
                              .equals("se/krka/kahlua/integration/LuaCaller")
                          && call.name().equalsString("protectedCallVoid")
                          && call.type()
                              .equalsString(
                                  "(Lse/krka/kahlua/vm/KahluaThread;Ljava/lang/Object;[Ljava/lang/Object;)V")) {
                        b.invokestatic(
                            NATIVE,
                            "eventCallback",
                            MethodTypeDesc.ofDescriptor(
                                "(Lse/krka/kahlua/integration/LuaCaller;Lse/krka/kahlua/vm/KahluaThread;Ljava/lang/Object;[Ljava/lang/Object;)V"));
                      } else b.with(e);
                    }
                  }));
        });
  }
}
