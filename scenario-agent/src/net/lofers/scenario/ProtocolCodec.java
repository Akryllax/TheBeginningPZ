package net.lofers.scenario;

import com.google.protobuf.MessageLite;
import java.lang.reflect.*;
import java.util.*;

/** Generated schema metadata supplies only known fields; reflection happens off the game thread. */
final class ProtocolCodec {
  record Field(String name, String type, boolean repeated) {}

  static final Map<String, Field[]> SCHEMA = ProtocolSchema.FIELDS;

  static String camel(String s) {
    var out = new StringBuilder();
    for (String p : s.split("_"))
      out.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
    return out.toString();
  }

  static MessageLite encode(String type, Map<?, ?> map) throws Exception {
    Class<?> clazz = Class.forName("net.lofers.scenario.protocol.NpcControl$" + type);
    Object builder = clazz.getMethod("newBuilder").invoke(null);
    for (Field f : SCHEMA.get(type)) {
      Object value = map.get(f.name);
      if (value == null) continue;
      if (f.repeated) {
        if (!(value instanceof Map<?, ?> values))
          throw new IllegalArgumentException("Array required " + f.name);
        int cap =
            switch (f.name) {
              case "residents", "plans", "road_closures" -> 128;
              case "places", "road_nodes" -> 1024;
              case "road_edges" -> 4096;
              case "actions" -> 24;
              case "threats" -> 32;
              case "route" -> 256;
              default -> 128;
            };
        if (values.size() > cap) throw new IllegalArgumentException("Array limit " + f.name);
        for (int i = 1; i <= values.size(); i++) {
          Object item = values.get((double) i);
          if (item == null) throw new IllegalArgumentException("Sparse array");
          set(builder, f, item, "add");
        }
      } else set(builder, f, value, "set");
    }
    return (MessageLite) builder.getClass().getMethod("build").invoke(builder);
  }

  static void set(Object builder, Field f, Object value, String prefix) throws Exception {
    Class<?> arg;
    Object converted;
    String name = prefix + camel(f.name);
    switch (f.type) {
      case "string" -> {
        arg = String.class;
        if (!(value instanceof String)) throw new IllegalArgumentException("String required");
        converted = value;
      }
      case "bool" -> {
        arg = boolean.class;
        if (!(value instanceof Boolean)) throw new IllegalArgumentException("Bool required");
        converted = value;
      }
      case "double" -> {
        arg = double.class;
        converted = number(value).doubleValue();
      }
      case "uint64" -> {
        arg = long.class;
        converted = integer(value, 0, 9007199254740991L);
      }
      case "int32" -> {
        arg = int.class;
        converted = (int) integer(value, Integer.MIN_VALUE, Integer.MAX_VALUE);
      }
      case "uint32" -> {
        arg = int.class;
        converted = (int) integer(value, 0, Integer.MAX_VALUE);
      }
      case "Locomotion" -> {
        arg = int.class;
        name += "Value";
        converted = (int) integer(value, 0, 3);
      }
      case "ActionKind" -> {
        arg = int.class;
        name += "Value";
        converted = (int) integer(value, 0, 18);
      }
      default -> {
        if (!(value instanceof Map<?, ?> map))
          throw new IllegalArgumentException("Message required");
        converted = encode(f.type, map);
        arg = converted.getClass();
      }
    }
    builder.getClass().getMethod(name, arg).invoke(builder, converted);
  }

  static Number number(Object v) {
    if (!(v instanceof Number n) || !Double.isFinite(n.doubleValue()))
      throw new IllegalArgumentException("Finite number required");
    return n;
  }

  static long integer(Object v, long lo, long hi) {
    double d = number(v).doubleValue();
    if (d != Math.rint(d) || d < lo || d > hi) throw new IllegalArgumentException("Integer range");
    return (long) d;
  }

  static Map<Object, Object> decode(String type, Object value) throws Exception {
    var result = new LinkedHashMap<Object, Object>();
    for (Field f : SCHEMA.get(type)) {
      String suffix = (f.type.equals("ActionKind") || f.type.equals("Locomotion")) ? "Value" : "";
      if (f.repeated) {
        var list =
            (List<?>) value.getClass().getMethod("get" + camel(f.name) + "List").invoke(value);
        int cap =
            switch (f.name) {
              case "plans", "residents" -> 128;
              case "actions" -> 24;
              case "threats" -> 32;
              case "route" -> 256;
              default -> 4096;
            };
        if (list.size() > cap) throw new IllegalArgumentException("Decoded array too large");
        var arr = new LinkedHashMap<Object, Object>();
        int i = 0;
        for (Object item : list)
          arr.put((double) ++i, SCHEMA.containsKey(f.type) ? decode(f.type, item) : item);
        result.put(f.name, arr);
      } else {
        if (SCHEMA.containsKey(f.type)
            && !(boolean) value.getClass().getMethod("has" + camel(f.name)).invoke(value)) continue;
        Object v = value.getClass().getMethod("get" + camel(f.name) + suffix).invoke(value);
        if (SCHEMA.containsKey(f.type)) v = decode(f.type, v);
        else if (v instanceof Number n) v = n.doubleValue();
        result.put(f.name, v);
      }
    }
    return result;
  }
}
