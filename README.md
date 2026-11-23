# FireBADnoFire's Storage Tweaks

An independently implemented inventory and finder mod. The current implementation
targets **Minecraft 1.12.2 / Forge 14.23.5.2864**. It is a development build with
runtime-tested scenarios, not a claim of complete modpack or multiversion support.
See [compatibility](docs/compatibility.md) and [testing](docs/testing.md).

## Features

- Take, Store, and Sort above supported vanilla chest and shulker screens. Take
  and Store include the hotbar; Store does not exclude tools by default.
- Sort in the normal survival inventory consolidates and orders the main 27 slots
  by displayed name. Hotbar, armor, offhand, and crafting slots stay untouched.
- Legal, paced inventory clicks with confirmation, state checks, and a timeout.
  An interrupted operation preserves cursor contents; put them away normally.
- Client-side `/cfind`, `/cfindblock`, and `/cfinditem`. Item search interacts with
  reachable storage normally, hides intermediate screens, and shows the original
  matching container screen. [Exact syntax and limitations](docs/finder.md).
- Negotiated server-authoritative punch deposit into vanilla chests and ender
  chests, without opening their GUI. Tools and damageable equipment are protected.

Client inventory controls and finder commands work without a server installation.
A server installation accepts clients without the mod. **Only punch deposit needs
both sides.** No punch workaround runs on an unsupported server.

## Installation and controls

Install the `*-mc1.12.2-forge.jar` in the Forge instance's `mods` directory. Do not
install the sources or runtime-test JAR. Dedicated servers use the same mod JAR.
Java 8 is the tested runtime. Back up worlds before testing development software.

Use Mods → Storage Tweaks → Config, or the rebindable P settings shortcut. If P is
already assigned, the shortcut starts unbound; assign it in Controls. A cancel
shortcut is also available, initially unbound. Feature toggles, pacing, search
budgets, exclusions, and capability status are available in settings. Configuration
is persisted by Forge.

When loader mod ID `clientcommands` is present, all three finder commands and their
services are skipped. Other command-name collisions are skipped with a log message.
The upstream mod is a read-only behavioral reference, not a runtime dependency.

## Important limits

Only explicitly recognized vanilla handlers are admitted. Arbitrary modded
containers are unsupported. The label requires a recent positive server access
and capacity probe; the actual punch validates access again.
There is currently no third-party protection integration: any additional unreviewed
server mod disables punch conservatively. See [the protocol](docs/protocol.md).

Entity and block searches can reveal terrain-obscured data already sent to the
client. Multiplayer rules may restrict their use. Searches never load distant
chunks or query privileged NBT.

## Build and validation

Pinned tools: JDK 8, Gradle 4.9, ForgeGradle 3.0.197, stable mappings 39-1.12,
Forge 14.23.5.2864. From PowerShell, set `JAVA_HOME` to a JDK 8 installation and
`GRADLE_USER_HOME` to a cache on a drive with sufficient space, then run:

```powershell
.\gradlew.bat --no-daemon :common:test :forge-1.12.2:build
```

`scripts/Validate.ps1 -JavaHome <jdk8> -GradleHome <cache>` also writes SHA-256
sidecars. Add `-RuntimeTest` with the documented WSL/Xvfb prerequisites to run
integrated-server assertions on a private virtual display, with 0% master volume
and software rendering. This creates fresh test worlds in the isolated validation
directory; it stops if private-display tools are unavailable.
The development `runClient` task currently has an upstream class-merging problem;
the private test harness uses official Forge binaries for runtime validation.

AGPL-3.0-or-later, preserving the existing project license. Reference provenance
and actual upstream licenses are recorded in [licensing](docs/licensing.md) and
[NOTICE](NOTICE). No releases are published automatically.

