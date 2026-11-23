# Validation procedures and evidence

## Actual mouse regression, 2026-11-22

Earlier automated button fixtures posted ActionPerformedEvent directly. They
validated plans and networking but bypassed GuiContainer's mouse path and missed
a real UI defect. A physical Take click on a separate LAN client reproduced an
outside-slot THROW packet (slot -999), followed by one pickup and an abort.
The fix consumes both edges of clicks on our buttons before vanilla slot handling.

On the rebuilt remote client, joined through Minecraft's LAN discovery list,
ordinary mouse clicks completed Take then Store. Chest stone 20 + apples 28 and
player hotbar stone 10 became player stone 30 + apples 28, then chest stone 30 +
apples 28 with player inventory empty. The screen remained open and cursor empty.
The host trace recorded eight planned PICKUP packets, actions 1–8, with no -999
packet. These are main-monitor GUI checks with master volume 0%, separate from
the event-only fixtures below. Raw logs: validation-manual-client-fixed.log and
validation-manual-lan-host-retry.log (the latter also retains the failed old-client
reproduction). Fixture modes lan-host and lan-creative advertise real Minecraft
LAN worlds; manual installs only the distribution mod for remote mouse tests.

Repeat with two isolated instances: launch productionClient with
`-PruntimeMode=lan-host -PruntimeInstance=<fresh-host> -PruntimeName=StorageHost`,
then `-PruntimeMode=manual -PruntimeInstance=<fresh-client>`. On the second client,
choose Multiplayer, wait for the LAN world, and double-click that row. Use real
mouse presses on Take/Store and inspect both final counts and server click trace.
Use lan-creative instead of lan-host to assign creative mode to the joining player.

The creative LAN run used the distribution-only remote client. Two separate real
left clicks, each after the availability label appeared, deposited held stacks of
10 and 30 stone respectively. The host logged deposited=10 at 21:15:15 and
deposited=30 at 21:16:39; subsequent ordinary chest opens confirmed the counts.
Neither punch opened a GUI or broke the chest. A real chest Sort click placed
apples before stone, and Take plus ordinary pickup/place clicks also succeeded in
creative. Log: validation-manual-lan-creative.log. The user's perceived creative
delay was not reproduced by these two paced clicks; rapid target changes and
network-jitter responsiveness remain unverified. This is not a latency benchmark.
Structured results and the exact mouse-tested distribution checksum are recorded
in [manual-mouse-validation.json](evidence/manual-mouse-validation.json).

Evidence date: 2026-11-22. Local JDK: Eclipse Temurin 8u472. Game caches, disposable
server instances, downloads, and test worlds were kept on F:. No existing game
installation or reference repository was modified. No commit or publication ran.

## Build and automated tests

```powershell
$env:JAVA_HOME = '<JDK 8>'
$env:GRADLE_USER_HOME = '<isolated cache>'
.\gradlew.bat --no-daemon :common:test :forge-1.12.2:build
.\gradlew.bat --no-daemon :forge-1.12.2:productionClient
```

Build and unit tests pass. Thirteen JUnit tests include 1,500 seeded sort cases and 500
transfer cases, every-click conservation and forbidden-slot checks, deterministic
ordering/idempotence, metadata identities, unusual limits, cursor rejection,
request replay/freshness/throttling, and packet length/round-trip checks. These
algorithm tests do not substitute for Minecraft adapter gameplay.

The separate runtime-test mod is excluded from distribution and source JARs. It
creates a fresh flat world, builds controlled fixtures on the integrated server,
and exercises the real game connection. Success requires the explicit
`ALL INTEGRATED RUNTIME TESTS PASSED` marker in
`forge-1.12.2/build/production-client/logs/latest.log`; process exit alone is not
enough. Failures have `STORAGE_TWEAKS_RUNTIME_TEST_FAILED` and a stack trace.
All spawned game clients have master volume set to 0% before launch. Physical
input is suppressed only inside the disposable automated-test window. Test punch
requests send an ordinary view-rotation packet toward the fixture; the server's
normal ray trace is still required. Late container replies after cancellation
remain visible and are closed normally before the separate punch test phase.

Observed passing integrated scenarios: Take (hotbar merge), Store (tools and
hotbar included), chest sorting, player sorting with hotbar checked throughout,
finder registration without clientcommands, entity/block command execution,
item search handoff to the same real container, cancellation cleanup, negotiated
GUI-free punch, partial capacity, replay rejection, and access-policy denial.
The harness also injects a dropped-confirmation fault to verify finite waiting,
one outstanding click, and cursor preservation. Keep its explicit final marker
as the authority for the complete run. Explicit rejection injection also passed:
one click, negative server confirmation, resynchronization, no subsequent mutation,
and conserved server/cursor contents. Double, trapped, ender, and shulker container
Take/Store/Sort scenarios passed with authoritative count checks.
Operations are invoked through the real GUI buttons' Forge action events, with
screen-bounds assertions. A left-side column is used when the scaled container
leaves no room above. Nested shulker search and unsupported-selector rejection
passed. An actual Forge dimension transfer using a test teleporter renewed the
punch session and released active finder suppression. The first direct transfer
attempt lacked vanilla portal state and failed in Teleporter; the deterministic
test fixture now uses Forge's ITeleporter API instead of creating portals.

## Optional installation

Two isolated dedicated servers were launched with Java 8: official vanilla
1.12.2 on loopback port 25590, and official Forge 14.23.5.2864 with only Storage
Tweaks on loopback port 25589. Both launched successfully. Test instances use
offline mode only on loopback for deterministic local identities; do not reuse
that configuration for a public server. No RCON or query endpoint was enabled.

The private-display integrated run also passed twelve additional punch fixtures:
pickaxe, hoe and damageable elytra protection; locked and blocked normal chests;
full capacity; a million-block-away unloaded target (still unloaded afterward);
insertion into the second half of a double chest; denial on that second half;
allowed and blocked ender chests; and rejection of unsupported shulker punch.
Each checks actual held contents and destination counts on the server thread.
The complete run finished with `ALL INTEGRATED RUNTIME TESTS PASSED`; raw log
`validation-private-punch-passed.log` retains the individual fixture markers.

The finder cursor-recovery fixture deliberately withholds initial container
contents, fills all chest/player slots, and sends an authoritative two-diamond
cursor update while the screen is suppressed. Cleanup reveals the same screen
and keeps the server window open, preserving the cursor. After the fixture frees
one storage slot, an ordinary pickup/place click deposits both diamonds; server
counts and the full player inventory are checked. This passed with the full
integrated final marker in `validation-isolated-gui.log`. The production fix
avoids vanilla automatic window closure when that closure could drop a cursor
stack. These fixtures remain outside distribution artifacts.

For the graphical modded-client → vanilla-server test:

```powershell
.\gradlew.bat --no-daemon :forge-1.12.2:productionClient -PruntimeMode=vanilla
```

The harness expects a reachable chest at (0,4,3), the test player at (0.5,4,0.5),
and chest contents stone, apples, and a custom-named sword. It waits ten seconds
after joining for console fixture setup. The observed run finished with
`ALL VANILLA CONNECTION TESTS PASSED`: join, punch unavailable, Take/Store/Sort
confirmed by vanilla, and client-only item-search handoff. This fixture setup
is presently manual. An initial run lacked the fixture and failed explicitly;
the corrected setup passed.

For unmodded-protocol-client → modded-server validation, install the test-only
`minecraft-protocol@1.68.0` in an isolated directory, set NODE_PATH to its
node_modules, and run:

```powershell
node test-harness/vanilla-client.cjs 25589
```

Result: exit 0, `PASS: unmodded client accepted; no Storage Tweaks handshake required or sent`.
This is not an authenticated graphical vanilla-client gameplay test.

## Toolchain diagnosis

The requested pinned build has passed. Ordinary FG `runClient` failed during FML
initialization: MCP's MergeTool generates a Side enum with BUKKIT, whereas the
official Forge universal has only CLIENT and SERVER. Reproduced with a fresh
isolated Gradle cache. Pinning annotation dependencies alone does not fix that
merged development class. No Forge class is patched or included in the mod.
`productionClient` launches the reobfuscated distributable against the official
universal and vanilla client, and has passed real integrated gameplay assertions.

Local raw build/client logs use ignored `validation-*.log` files. Dedicated-server
logs are in the isolated instances. Summary evidence is maintained here; generated
assets, caches, identities, and test worlds must not be committed.

## Outstanding gates

Unexpected GUI and delayed contents, nested-content bounds, latency,
death/disconnect interruptions, broader contention scenarios, GUI scales,
and representative modpack gameplay need more
runtime coverage. No third-party protection integration exists yet. Real
clientcommands coexistence requires a compatible distribution. CI configuration
is not a hosted CI result. Ports must follow the required 1.12.2 validation gates;
none is advertised as implemented or verified yet.

## Pack diagnosis

A copied 30-JAR local 1.12.2 pack (including JEI, Mouse Tweaks, Iron Chests,
OpenComputers, Tinkers, OptiFine, Xaero, and ForgeEssentials client) was launched
with fresh test worlds. The first Storage Tweaks run reached world join but failed
in DamageIndicatorsMod's DIPermissions handler: it reads the client player on the
Netty thread and dereferences it before initialization. A separate baseline test
mod, containing no Storage Tweaks references or installed Storage Tweaks JAR,
subsequently reached `BASELINE_PACK_JOIN_PASSED`. This establishes a join-time
compatibility problem to investigate, not proof that Storage Tweaks is unrelated.
Further runs are required before claiming pack gameplay compatibility.
After narrowing inventory packet observation, a repeated run completed
`ALL PACK RUNTIME TESTS PASSED`. The earlier join failure remains recorded; this
is evidence for the specific fixture scenarios, not universal pack stability.

Baseline and pack tests use `productionClient -PruntimeMode=baseline` or `pack`,
with `-PruntimePack=<reference instance>`. They copy mods/configuration/Flan content
to separate build directories and create fresh worlds; the reference is read-only.
Test mod JARs are excluded from distribution. The baseline is only a join check.

### User-requested archive

The subsequently requested `mods.tzs` contains 39 regular JAR entries. Paths and
links were rejected before extraction. Its archive and individual mod SHA-256
values are recorded in [requested-pack.json](evidence/requested-pack.json).
The archive contains no configuration or worlds; tests use generated defaults and
fresh worlds in a separate instance. The original archive is unchanged.

```powershell
.\gradlew.bat --no-daemon :forge-1.12.2:productionClient `
  -PruntimeMode=pack -PruntimePack=<extracted instance> `
  -PruntimeInstance=production-user-pack
```

Result: exit 0, `ALL PACK RUNTIME TESTS PASSED` on 2026-11-22. This includes actual
GUI button events, single/double/trapped/ender/shulker operations, hotbar checks,
first-match screen identity, cancellation, nested item contents, and injected
missing/negative confirmations. Punch was correctly unavailable because the
additional server mods have no reviewed access integration. No pack punch support
is claimed. Screenshots from this instance remain in its ignored build directory.

The first launch exposed a harness classpath mismatch: FG3's ASM 6 was selected
instead of the official Forge runtime's `asm-debug-all:5.2`; MixinBooter failed
with missing `org.objectweb.asm.ConstantDynamic`. The launcher now uses exactly
the ASM version in Forge 2864's installer version.json and excludes build-tool ASM
JARs. The unchanged mod set then loaded successfully. Traveler's Backpack also
displayed its informational first-launch migration notice. No old backpack data
exists in this fresh instance. Mod update-check errors and upstream Mixin/refmap
warnings are present in raw logs and are not treated as feature-test passes.

### Private GUI display

Further GUI fixtures use WSL Ubuntu 24.04 with a private Xvfb display, not WSLg
or the Windows desktop. `scripts/Validate.ps1 -RuntimeTest` delegates to
`scripts/Validate-IsolatedGui.ps1`; it never falls back to a visible desktop.
The runner clears inherited display variables, uses X authentication, disables
X TCP listening, lowers process priority, selects Mesa software rendering with two worker threads, caps
the game at 30 FPS, and sets master volume to zero before launch. OpenAL's null
driver avoids using the personal audio session. Game files, native downloads,
caches, JDK libraries, logs and worlds remain in the isolated validation directory
on F:. A few executable files and private X authentication state use WSL `/tmp`, because this host's
DrvFS mount exposes downloaded files without executable permission.

Tools are extracted, not installed system-wide. The isolated `linux-tools`
directory must contain `jdk8u472-b08` and an extracted Debian package tree `root`.
The packages used here are Ubuntu's `xvfb` 2:21.1.12-1ubuntu1.9,
`libxfont2` 1:2.0.6-1+deb13u1build0.24.04.2, `libfontenc1` 1:1.1.8-1build1,
and `x11-xkb-utils` 7.7+8build2. Existing Ubuntu `xauth`, `bwrap`, Python 3,
Mesa, XKB data and shared libraries are prerequisites. Downloads use validated
HTTPS and are checked against Ubuntu APT SHA-512 metadata. Temurin Linux x64
8u472-b08 JDK SHA-256:
`5becaa4ac660e844c5a39e2ebc39ff5ac824c37ff1b625af8c8b111dc13c3592`.
Linux game natives are checked against the official Minecraft 1.12.2 metadata.
The unused Linux narrator logs missing `flite` libraries on this host; narrator
support is not part of these fixture results. No system packages are installed
to suppress this warning.

The bootstrap starts through WSL root only to prepare temporary executable files
and a private mount namespace supplying Xvfb's hardcoded xkbcomp path. It changes
no host `/usr/bin` files and drops to uid 65534 before starting the X server or
game. This uid matches the owner that this host's DrvFS mount exposes; the runner
is intentionally specific to that environment and stops on a different uid.
The mount namespace makes the host filesystem read-only except for this
repository, its isolated validation directory and temporary files.
Separate X servers use separate authentication cookies and display numbers.
Multiplayer fixtures use a WSL-local dedicated server bound to 127.0.0.1;
no Windows firewall or port-forwarding changes are needed.

The first private integrated run failed `Chest did not open`: the fixture's fixed
delay did not establish that teleport and chunk data had reached the software-
rendered client. The harness now waits for the expected loaded block, nearby
player, and absence of a loading screen before interacting, with bounded response
waiting. This is a test-harness correction, not evidence that a failed operation
passed. Raw logs retain both runs.

A subsequent private run reached the nested-content fixture with the player
outside interaction range and correctly reported zero reachable inventories.
The harness now disables hostile spawning in its disposable world and explicitly
restores and verifies the nested fixture's player position. The cause of the
earlier displacement was not established; it is not counted as a feature pass.
Another startup run logged an 11-second integrated-server stall and failed the
fixture's chest-open deadline. The harness now allows up to 21 seconds for this
initial fixture screen and logs the real server interaction event. Inventory job
acknowledgment timeouts are unchanged. The final run exited 0 and completed
`ALL INTEGRATED RUNTIME TESTS PASSED`, including the corrected self coordinates,
nested contents and dimension transfer. This is recorded separately from the
earlier failures in `evidence/private-display-validation.json`.

Two independent private-display clients also joined a loopback-only dedicated
server and requested a deposit into one remaining stack position. The fixture
filled all 26 other slots with 64 stone and slot 0 with 63 diamonds, then gave
each player seven diamonds. Both requests completed without opening a screen;
client remainders were seven and six. The server console's `testforblock` verified
64 diamonds in the chest, and `test-harness/verify-contention.cjs` verified the
saved player counts. Total diamonds remained 77. An earlier fixture accidentally
left 26 empty slots and both complete deposits correctly fit; that attempt is
retained as a fixture failure, not a conservation failure or contention pass.

The `optional-server` Linux fixture installs only `storage_baseline_tests`, asserts
Storage Tweaks is absent, and connects to the private dedicated server. Its
2026-11-22 run exited 0 with `OPTIONAL_SERVER_JOIN_PASSED`; server logs record a
five-mod Forge client and the missing Storage Tweaks client mod, followed by a
successful login. It stayed connected for 200 client ticks. This validates that
the server does not require clients to install Storage Tweaks; it does not claim
a completely unmodified graphical vanilla-client test or punch availability.
