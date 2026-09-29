package net.akr.scenario;

import net.akr.scenario.protocol.RuntimeControl.*;
import se.krka.kahlua.vm.*;
import zombie.Lua.LuaManager;

/** Server-local entry point into the same scheduler as the private operator socket. */
final class RuntimeLua {
  static KahluaTable install(RuntimeSession runtime, ServerPedestrians pedestrians) {
    KahluaTable api = LuaManager.platform.newTable();
    api.rawset("world", runtime.world);
    api.rawset("epoch", runtime.epoch);
    api.rawset("capability", "pedestrian-experiment-unvalidated");
    api.rawset(
        "register",
        (JavaFunction)
            (frame, count) -> {
              try {
                if (!(frame.get(0) instanceof LuaClosure spawn)
                    || !(frame.get(1) instanceof LuaClosure retire))
                  return frame.push(false, "closures_required");
                pedestrians.register(spawn, retire);
                return frame.push(true);
              } catch (RuntimeException failure) {
                return frame.push(false, failure.getMessage());
              }
            });
    api.rawset(
        "status",
        (JavaFunction)
            (frame, count) ->
                frame.push(
                    view(
                        runtime.handle(
                            base(runtime, "lua-status")
                                .setOperation(Request.Operation.STATUS)
                                .setEventId(string(frame.get(0)))
                                .build()))));
    api.rawset(
        "cancel",
        (JavaFunction)
            (frame, count) ->
                frame.push(
                    view(
                        runtime.handle(
                            base(runtime, "lua-cancel")
                                .setOperation(Request.Operation.CANCEL)
                                .setEventId(string(frame.get(0)))
                                .build()))));
    api.rawset(
        "submit",
        (JavaFunction)
            (frame, count) -> {
              try {
                if (!(frame.get(1) instanceof KahluaTable args)
                    || !(args.rawget("route") instanceof KahluaTable route))
                  return frame.push(false, "definition_required");
                var definition =
                    PedestrianCase.newBuilder()
                        .setActors(integer(args.rawget("actors")))
                        .setTimeoutSeconds(integer(args.rawget("timeout_seconds")))
                        .setHoldSeconds(integer(args.rawget("hold_seconds")));
                if ("actor".equals(args.rawget("entity")))
                  definition.setEntity(PedestrianCase.Entity.ACTOR);
                else if (args.rawget("entity") != null) return frame.push(false, "entity");
                if (args.rawget("reassign_after_seconds") != null)
                  definition.setReassignAfterSeconds(
                      integer(args.rawget("reassign_after_seconds")));
                for (int i = 1; i <= 9; i++) {
                  Object value = route.rawget((double) i);
                  if (value == null) break;
                  if (!(value instanceof KahluaTable point))
                    return frame.push(false, "point_required");
                  definition.addRoute(
                      Point.newBuilder()
                          .setX(number(point.rawget("x")))
                          .setY(number(point.rawget("y")))
                          .setZ(integer(point.rawget("z"))));
                }
                return frame.push(
                    view(
                        runtime.handle(
                            base(runtime, string(frame.get(0)))
                                .setOperation(Request.Operation.SUBMIT)
                                .setPedestrian(definition)
                                .build())));
              } catch (RuntimeException failure) {
                return frame.push(false, failure.getMessage());
              }
            });
    return api;
  }

  private static Request.Builder base(RuntimeSession runtime, String id) {
    return Request.newBuilder()
        .setVersion(1)
        .setWorld(runtime.world)
        .setEpoch(runtime.epoch)
        .setRequestId(id);
  }

  private static String string(Object value) {
    return value instanceof String s ? s : "";
  }

  private static double number(Object value) {
    if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue()))
      throw new IllegalArgumentException("finite_number_required");
    return n.doubleValue();
  }

  private static int integer(Object value) {
    double n = number(value);
    if (n != Math.rint(n) || n < 0 || n > 180) throw new IllegalArgumentException("integer_range");
    return (int) n;
  }

  private static KahluaTable view(Reply reply) {
    var result = LuaManager.platform.newTable();
    result.rawset("accepted", reply.getAccepted());
    result.rawset("code", reply.getCode());
    result.rawset("event_id", reply.getEventId());
    result.rawset("phase", reply.getPhase());
    result.rawset("reason", reply.getReason());
    var actors = LuaManager.platform.newTable();
    int index = 0;
    for (var actor : reply.getActorsList()) {
      var value = LuaManager.platform.newTable();
      value.rawset("id", actor.getId());
      value.rawset("action", actor.getAction());
      value.rawset("x", actor.getPosition().getX());
      value.rawset("y", actor.getPosition().getY());
      actors.rawset((double) ++index, value);
    }
    result.rawset("actors", actors);
    return result;
  }
}
