# Compatibility evidence

The project targets **Minecraft 1.12.2 with Forge 14.23.5.2864 only**.

| Minecraft / loader | Toolchain | Build | Launch | Gameplay evidence |
| --- | --- | --- | --- | --- |
| 1.12.2 / Forge 14.23.5.2864 | Java 8u472, Gradle 4.9, FG 3.0.197, stable 39-1.12 | passes | actual client, integrated and dedicated server | automated integrated inventory/finder/punch scenarios; client-only inventory/finder on vanilla server |

Forge 1.12.2 optional installation was tested in both directions: the modded client
joined a vanilla server and used inventory/finder features; an unmodded protocol
client joined the modded dedicated server without a Storage Tweaks handshake.
An actual Forge client containing only the independent baseline fixture mod also
joined this server and remained connected for 200 client ticks, with Storage
Tweaks explicitly absent. This is a private-display game-client connection test;
the unmodded vanilla client result remains a protocol test, not vanilla gameplay.

Normal/double/trapped/ender/shulker inventory operations, survival inventory sorting,
item finder handoff, and negotiated normal-chest punch were exercised in game.
Confirmation timeout and explicit rejection were injected in the actual connection;
both stopped subsequent clicks and preserved items. Additional punch fixtures
passed for the second half of double chests, ender chests, equipment protection,
locks, blocked/full storage, unloaded targets, second-half policy denial and
unsupported shulker targets.
Subsequent ordinary mouse tests on a separate LAN client exposed and fixed button
input falling through to vanilla outside-slot handling. Take, Store and chest Sort
then completed through real clicks with no stray outside-slot packets. Two creative
punches succeeded after the availability label appeared; reported creative lag was
not reproduced. Earlier event-only button fixtures did not cover this mouse path.
Two clients on private Xvfb displays competed for one free diamond position in
a chest otherwise filled to capacity: the chest reached 64, and saved player
inventories retained six and seven diamonds (77 total before and after).
Latency, GUI scale/resource-pack combinations, actual clientcommands coexistence,
and wider modpack gameplay are outstanding.
The first copied-pack run disconnected in DamageIndicatorsMod at join; a baseline
without Storage Tweaks joined. A repeat passed its fixture harness. The user's
39-JAR mods.tzs pack also passed all pack-harness assertions with default configs;
punch remained disabled for unreviewed integrations. See testing.md and the
hashed pack manifest for exact scope.
No compatible upstream clientcommands distribution has been established for this
Forge 1.12.2 target; no simulated test is presented as real coexistence evidence.

The ordinary ForgeGradle development launcher fails because its merged Side enum
includes BUKKIT, which 1.12 FML does not handle. A fresh isolated cache reproduces
the failure. The distributable JAR builds, and the runtime harness instead uses
the unmodified official Forge universal and vanilla client JARs. This limitation
must be resolved or retained explicitly in developer instructions.


Row/hotbar swaps: all three buttons and all three optional bindings passed actual
integrated-server inventory assertions in survival and creative, both in an
isolated minimal instance and the 39-JAR mods.tzs pack (2026-11-23). Tests preserve
exact stack counts, names and column order. GUI screenshots verify the numbered
buttons beside the main rows only. See testing.md for the corrected fixture key
conflict, native Windows startup failure, and exact automated-input scope.


Rapid punch switching (2026-11-23): current production JAR passes immediate
same-frame slot-switch/click tests in survival and creative with the reviewed
Backpack build. Delaying real hover replies by two seconds does not block deposits;
all nine stacks/54 items are conserved and both client/server chests survive.
Client opt-out restores normal server break behavior. The 39-mod pack regression
passes with its existing conservative punch restriction unchanged. These are
integrated-server/controller tests; new dedicated-server network latency and
physical keyboard tests are not claimed. See testing.md for exact logs and scope.

Chest row swaps (2026-11-23): all six small-chest and nine double-chest row choices
and their reversals pass server-state checks in minimal survival and the exact
39-mod archive baseline in creative. Nine optional row bindings start unbound.
Protected punch items support a held, rebindable override; positive survival/creative
tests use vanilla storage and the reviewed Backpack build, not the full pack.
See testing.md for input scope and logs.
