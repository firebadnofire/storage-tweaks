# Compatibility evidence

As of 2026-11-22, only Forge 1.12.2 has an implementation. The complete machine
matrix in [targets.json](targets.json) enumerates 75 stable Minecraft releases
from 1.8 through 26.3, with one row for each of four loaders (300 rows). A loader
listing is availability evidence, not a working Storage Tweaks port. Forge and
Fabric metadata have been checked; NeoForge and Quilt availability is still
unreviewed. Missing implementations are explicitly marked not started.

| Minecraft / loader | Toolchain | Build | Launch | Gameplay evidence |
| --- | --- | --- | --- | --- |
| 1.12.2 / Forge 14.23.5.2864 | Java 8u472, Gradle 4.9, FG 3.0.197, stable 39-1.12 | passes | actual client, integrated and dedicated server | automated integrated inventory/finder/punch scenarios; client-only inventory/finder on vanilla server |
| 1.8.9 / Forge | not pinned yet | not started | not started | blocked behind completion of 1.12.2 gates |
| 26.3 / Fabric | requires isolated Java 25 and verified loader/toolchain | not started | not started | blocked behind legacy gates |
| Other exact versions/loaders | see matrix | not started | not started | no support claim |

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
