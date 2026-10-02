package net.akr.scenario;

import java.nio.file.Files;
import java.nio.file.Path;
import se.krka.kahlua.luaj.compiler.LuaCompiler;
import se.krka.kahlua.vm.Prototype;

/** Compile upstream Lua without executing it, exposing the engine's closure line semantics. */
public final class ClosureProbe {
  private ClosureProbe() {}

  private static void visit(Prototype prototype) {
    if (prototype.lines != null && prototype.lines.length > 0)
      System.out.println(prototype.name + "\t" + prototype.lines[0]);
    if (prototype.prototypes != null) for (Prototype child : prototype.prototypes) visit(child);
  }

  /** Inspect the supplied private upstream file without initializing a world or Lua globals. */
  public static void main(String[] args) throws Exception {
    try (var input = Files.newInputStream(Path.of(args[0]))) {
      visit(LuaCompiler.loadis(input, "BanditUpdate.lua", null).prototype);
    }
  }
}
