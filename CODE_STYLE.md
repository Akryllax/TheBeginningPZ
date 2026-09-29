# Source layout and formatting

The game agent is one JAR. Its stable entry point is `net.akr.scenario.ScenarioAgent`.
`compat` owns pinned build and native-library checks; `bridge` owns bounded Lua/protobuf
conversion; `npc.core` contains engine-independent civilian rules. Engine hooks and the
scenario orchestrator remain in the root package while their smaller interfaces are
extracted. Observer's separate agent uses `net.akr.observer`. Avoid making internal
fields public merely to move a file into another package.

Run `./dayone format` after editing and `./dayone lint` before committing. The former
formats authored Java, Python, Lua, C++, protobuf, TypeScript, CSS, HTML, JSON, YAML,
shell, CMake and TOML files. `./dayone format --check` only reports differences.
Generated protobuf, map data, downloaded mods, saves, dependency locks and build output
are excluded. The checked-in format configuration is `.editorconfig`, `.clang-format`,
`.stylua.toml`, `.ruff.toml` and `.prettierrc.json`.

Java uses Google Java Format 1.36.1 and two-space indentation. Document exported
classes and module-facing methods with Javadoc, including meaningful `@param`, `@return`
and `@throws` information. Document nontrivial internal routines when their contracts
or failure modes are not obvious; do not add comments that only repeat a getter name.
`./dayone docs java` builds the agent and renders Javadoc with syntax/reference
doclint errors treated as failures into `artifacts/`.
It requires the pinned game files but does not start a server.

Game Lua is parsed as Lua 5.1; the separate worker rules are Lua 5.4. StyLua runs
with `--verify` and does not reorder `require` calls. C++ uses LLVM-derived
clang-format 22.1.8 with four-space indentation; protobuf uses a two-space override.
Python uses project-local Ruff 0.13.0, a 100-column target and Google-style
docstrings for nontrivial public functions. Web code uses Prettier 3.6.2 with two
spaces. Shell uses shfmt 3.13.1; CMake uses gersemi 0.23.0; TOML uses Taplo 0.10.0.

The Java formatter JAR, Taplo archive and shfmt binary are cached under `.tooling/format` and
checked against hashes by `scripts/bootstrap_format.py`; other tools are in the
project's `.tooling/venv` or `observer/web/node_modules`. Formatting commits
belong in `.git-blame-ignore-revs`.

New source must use AKR identity (`AKRStoryteller`, `AKRScenario`, `net.akr`,
`akr-npc-service`). `lofers.net` DNS and the existing Git remote stay as they are.
Old development saves remain attached to their previous build. Start a fresh disposable
world before running the AKR build; do not migrate it by renaming persisted keys.
