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
clientcommands coexistence requires a compatible distribution. The Forgejo workflow
builds and inspects the exact release binary, signs it only after a passing
dedicated-server smoke test, and publishes only pushed `v*`/`V*` tags. A workflow
run is build/startup evidence, not a replacement for the required gameplay gates.
Ports must follow the required 1.12.2 validation gates; none is advertised as
implemented or verified yet.

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

## Creative inventory and command-chat fixes, 2026-11-22

`validation-user-fixes-final-build.log` records Validate.ps1 exiting successfully.
The focused production client run used
`:forge-1.12.2:productionClient -PruntimeMode=creative-regression
-PruntimeInstance=regression-creative-focused-v2` with Java 8 and master volume 0.
`validation-creative-focused-v2.log` records server-accepted creative main-27
sorting with the diamond hotbar unchanged, command entry through GuiChat accepted,
and cancellation releasing item-search suppression. These are automated event-path
checks, not physical clicks or remote multiplayer validation of the new creative
path. The ordinary inventory mouse path was validated separately above.

Earlier new-fixture attempts are retained: stage 51 read a future already consumed
by the shared harness, then the first focused run hid the screen without normally
closing its container. Both fixture errors were corrected before the final pass.
The rejected-transaction messages in these disposable worlds are deliberate
injected protocol tests; an ordinary gameplay rejection must still be investigated.
Held-stack changes now invalidate pending punch probes and request new authorization
without waiting for the previous response. This refresh change builds; rapid
physical hotbar switching under network latency has not been separately verified.

## Creative button placement and commands, 2026-11-22

Validate.ps1 passed in validation-punch-command-build.log. The focused production
run in validation-commands-confirm-runtime.log passed all assertions: creative Sort
is right of the panel, initially hidden on the catalogue tab and visible on the
player tab, server accepts the sort with hotbar preserved; item search accepts chat
and cancels cleanly; clook aims at a block center without movement; the integrated
server owner can toggle punchdeposit false then true and the config property is
persisted. This uses automated GUI/button events, not physical remote input.
The fixture invokes server commands through ICommand so ForgeGradle remaps the
interface methods correctly. Earlier attempts retain their native JNA crash,
concurrent-build mapping failure, and fixture method-mapping failure logs.
The aiming assertion normalizes Minecraft's lookup-table look vector before
comparing direction, rather than incorrectly requiring a perfect unit magnitude.
Live LuckPerms and dedicated-server permission delegation remain unverified.

## Best-match item search, version 0.0.1

Validate.ps1 succeeded (validation-best-item-build.log). The first new client
exited with native access-violation code -1073741819 during startup, before the
fixture. The fresh retry validation-best-item-v2-runtime.log exited 0 and passed
BEST_ITEM_SEARCH_PASSED. Chat reported counts 12, 7, and 3 with [Look at], then
reported three inspected inventories. Server interactions show the best chest
reopened after the final candidate. The fixture verified 12 apples in the revealed
container, matching player look direction, and no retained search suppression.
This is an integrated-server automated scenario, not a remote physical-click test.
Mod metadata declares Fire's Storage Tweaks, creator FireBADnoFire, version 0.0.1.
Project scope is Forge 1.12.2 only; the speculative target matrix and generator
were removed.

## Stationary scan and chat action regression

Validate.ps1 passed in validation-look-links-build.log. The production item-best
fixture passed in validation-look-links-runtime.log (exit 0): every scan tick
retained the starting yaw/pitch until the final reveal; all three matches were
reported and the 12-apple chest remained selected. The fixture extracted actual
chat-history components, wrapped them to 70 pixels using Minecraft's own splitter,
passed a retained Look at component to GuiChat.handleComponentClick, and verified
the resulting aim matched that link's coordinates. This exercises the real chat
component handler and client-command dispatch, rather than invoking clook directly.
It does not claim a physical mouse hit-test. Links require open chat, as ordinary
Minecraft chat links do.

## Punch failure with Traveler's Backpack, 2026-11-22

Both read-only inspected Prism instances contained Storage Tweaks plus Traveler's
Backpack 1.0.35, with client and server punch toggles enabled. The blanket unknown-
mod policy blocked the Backpack distribution. Its exact SHA-256 and event-handler
review are documented in protocol.md; a digest-bound compatibility rule now admits
this content mod without bypassing any target/access checks. Unknown builds and
other unknown mods remain blocked. Personal instances and worlds were not changed.

Validate.ps1 passed (validation-travelers-punch-build.log). Isolated copies of the
actual Backpack JAR/config passed PUNCH_RECOVERY_PASSED and
PUNCH_SWITCH_RECOVERY_PASSED in both survival and creative. The fixtures waited for
real availability negotiation, toggled false/true via the server command, invoked
the client mouse handler, and verified server-held/chest counts, then switched
held items and deposited again. Logs: validation-travelers-punch-runtime.log and
validation-travelers-creative-runtime.log; both exited 0. These are mouse-handler
fixtures, not physical mouse clicks or remote multiplayer tests.

Required modpack regression used F:/mods.tzs, SHA-256
9e7af621d9296b1217a1ab7851bea7b856220bf1411890ad843001a4a03ce66d;
all 39 extracted JAR hashes matched evidence/requested-pack.json. The fresh pack
run validation-required-pack-punch-fix.log reached ALL PACK RUNTIME TESTS PASSED,
including client inventory/finder and the expected blocked punch capability.
After assertions completed and worlds saved, the Windows process exited with
-1073741819 during shutdown. No hs_err report was produced; the shutdown failure
is unresolved and this is not a clean process-exit pass. Other pack mods remain
unreviewed, so the full pack's punch capability is still conservatively disabled.

## Hotbar selection ordering regression

The old same-slot item replacement fixture did not cover real hotbar selection.
A new high-priority client END-tick fixture switches selected slots immediately
before availability processing, reproducing the input/synchronization ordering.
Initial attempts accidentally selected an empty slot and were discarded. The
corrected fixture against the previous dist binary fails with seven apples held
and the crosshair still on the chest: validation-hotbar-original-corrected-retry.log.

The fixed production binary passes in survival (validation-hotbar-corrected-fixed.log)
and creative (validation-hotbar-creative-fixed.log), both exiting 0. Availability
recovers within the 20-tick fixture bound without looking away; invoking the mouse
hook deposits seven apples from slot 1, leaves slot 0's ten stone untouched, and
server-side assertions verify those counts. This tests real selected-slot changes
and network ordering, but uses a mouse-hook fixture rather than physical input.
`productionClient -PruntimeMod=<jar>` supports comparison with an existing binary
in isolated test instances without changing distribution files.

Windows full-pack retry (validation-pack-hotbar-fix.log) failed during startup,
before runtime assertions. Its hs_err_pid10784.log locates the access violation
in JNA Native.registerMethod called by NarratorWindows initialization. This is
separate from the passing Windows hotbar fixtures. A Linux/Xvfb pack run uses the
same extracted mod set and production binary to avoid that native Windows path.
The Linux runner now accepts --mode pack and allocates 6 GiB for that pack.

The first Linux pack run stopped at Traveler's Backpack's one-time GuiWarning
screen (validation-linux-pack-hotbar-fix.log), before gameplay. That mod saved its
warning flag to false in the isolated instance. The retry passed every pack
assertion and exited 0 (validation-linux-pack-hotbar-fix-retry.log). All 39 loaded
pack JAR hashes were verified against the original mods.tzs manifest. Punch
remains disabled in the full pack for other unreviewed mods, as reported above.
The tested focused Windows binary is copied to dist with its SHA-256 sidecar.


## Row/hotbar exchange validation (2026-11-23)

Shared engine: `:common:test` passes 11 Planner tests and one RequestGate test,
including 500 seeded exact-stack row swaps, reversal, conservation after every
click, unchanged other slots, occupied-cursor rejection, incompatible destination
limits, and matching partial stacks that must exchange without merging.
`:common:test :forge-1.12.2:build :forge-1.12.2:exportProductionRuntime` exited 0
(`validation-row-swap-final-build.log`).

Focused runtime mode `row-swap` verifies each numbered button on the actual
integrated server in survival and creative. It asserts exact names/counts/order
across all 36 slots, an empty cursor, exactly three buttons, and hidden buttons in
creative catalogue tabs. The first Windows six-button run exited 0
(`validation-row-swap-runtime.log`). The expanded Windows run crashed at native
startup with exit -1073741819 before gameplay (`validation-row-swap-final-runtime.log`);
this is not counted as a gameplay pass. Linux/Xvfb runs additionally enqueue a
temporarily bound key for each row with no screen open in both game modes and
verify all keys initially unbound. These are automated button-event/key-queue
fixtures, not physical mouse/keyboard tests.

Reproduction uses the existing private-display runner:
`linux-virtual-display.sh python3 test-harness/linux-client.py --manifest
forge-1.12.2/build/production-runtime.json --instance <isolated-instance>
--native-cache <linux-natives> --mode row-swap`.
Pack mode also runs all twelve row/button/key scenarios after the existing pack
checks. All clients use master volume 0. Original mods.tzs and personal instances
remain untouched; 39 reference pack JAR hashes were checked against
`docs/evidence/requested-pack.json` before the run.


Final outcomes: `validation-linux-row-swap-final.log` passed all twelve focused
server-verified cases and exited 0. Survival and creative screenshots
`row-swap-0.png` through `row-swap-5.png` were captured; survival and creative
images were visually inspected for row alignment and absence of a hotbar button.

The first full-pack run (`validation-linux-pack-row-swap.log`) passed all six
buttons but failed the first synthetic key case. The fixture had hard-coded F10,
which the pack already binds to `key.debug.desc`. The fixture now chooses an
unused key rather than assuming a free binding. The retry logs that existing
F10 binding and its selected free key (102). All twelve row scenarios and all
preceding pack checks passed, with process exit 0
(`validation-linux-pack-row-swap-retry.log`). This run uses the final distribution
binary. Pack screenshots were also inspected. Unknown server integrations still
disable punch conservatively; these results do not extend punch compatibility.

Final `scripts/Validate.ps1` exited 0 (`validation-row-swap-distribution.log`):
17 tests total (12 common, 5 Forge adapter/protocol), production/source JAR build,
and exclusion checks for runtime-test and bundled Forge classes. The exact
pack-tested production JAR is staged in dist with a SHA-256 sidecar:
`64c6b0aa5e824c2b8cc2f193e29c7f2c2dcedf9184fb10c6c07569592558c9e9`.
No new dedicated multiplayer row-swap or physical key-input test was performed;
the new scenarios use integrated-server packet transactions and queued keys.


## Immediate punch after rapid hotbar changes (2026-11-23)

Target remains Forge 1.12.2 / 14.23.5.2864, Java 8u472. The client now consumes
eligible deposit attempts without requiring item-specific hover authorization,
synchronizes the held slot before sending, intercepts keyboard attack input before
vanilla processes it, and clears held attack input. The server guards vanilla
break packets only for confirmed clients opting into punch while global capability
is enabled. Inventory mutations still require independently validated requests.

Build/test command: `gradlew.bat --no-daemon :common:test :forge-1.12.2:build
:forge-1.12.2:exportProductionRuntime`, with the established isolated JDK/cache.
`validation-immediate-punch-input-build.log` exited 0. All 21 tests passed:
11 Planner, 3 RequestGate, 2 RequestBudget, 3 InventoryIdentity, 2 Message tests.
The burst budget tests include concurrent calls and window reset; replay, stale
IDs, and mismatched selections remain rejected even when requests share a tick.

Runtime commands use `linux-virtual-display.sh python3 test-harness/linux-client.py`
with the exported production manifest, an isolated instance and native cache;
`--mode rapid-punch` and `--mode rapid-punch-creative` select the new fixtures.
All clients use 0% master volume and private Xvfb/software rendering. The creative
instance also contains the exact reviewed Traveler's Backpack 1.0.35 JAR from the
supplied pack. The original archive and personal Prism instances are untouched.

The previous dist JAR reproduces attack fall-through without a current label
(`validation-rapid-punch-original-retry.log`, stage 92). The first original run
stopped before negotiation was ready, and initial fixed runs lacked a valid target
wait; those are failures, not claimed passes (`validation-rapid-punch-original.log`,
`validation-rapid-punch-fixed.log`, `validation-rapid-punch-creative-backpack.log`).

The corrected survival run exposed the old pre-scheduling 20 ms filter dropping
one legitimate request during tick catch-up: 51 apples deposited, three remained
held (`validation-rapid-punch-fixed-retry.log`). No items were lost. Replacing
spacing with a bounded 40-message/second window allowed the complete burst
(`validation-rapid-punch-burst-fixed.log`). The initial creative/controller test
exposed START_DESTROY preceding Forge's client event, then a stale selected-slot
packet in the direct controller fixture (`validation-rapid-punch-creative-burst-fixed.log`,
`validation-rapid-punch-guard-creative.log`). Production mouse/key input now sends
its held-slot update before either attack path; the direct controller fixture
explicitly applies that ordering too. A negotiated server break guard protects
the server mutation; a client preference message prevents it blocking mining
when client punch is disabled.

Final focused runs exited 0:
- `validation-rapid-punch-survival-input-fixed.log`: all nine selected stacks,
  totaling 54 apples, deposited exactly once. Eight real availability replies
  delayed by two seconds. Chest and cursor invariants held.
- `validation-rapid-punch-creative-input-fixed.log`: same exact counts and chest
  survival with Traveler's Backpack loaded. One real availability reply delayed
  by two seconds. Both runs verified that disabling client punch leaves a server
  LeftClickBlock event uncancelled, restoring normal mining.

The attack fixtures alternate the mouse hook and actual controller/block-event
path, explicitly switch slots and click in the same frame, and clear label
identity first. They also exercise held-attack clearing. These are automated
input/controller tests, not physical keyboard tests or a new dedicated-server
latency test. Probe replies are delayed on the actual local-channel pipeline;
deposit packets themselves are not artificially delayed. Early latency hooks
placed after Forge's custom-packet handler saw no replies; the final hook runs
before that handler and asserts that real replies were intercepted.


Broader regression outcomes: `validation-pack-immediate-punch.log` exited 0 with
ALL PACK RUNTIME TESTS PASSED, after all 39 archive mod hashes were rechecked.
This includes inventory/finder/rejection/timeout checks and all twelve row-swap
cases. Punch remains disabled in that full pack for other unreviewed mods; the
focused Backpack test is the positive modded punch test. The minimal
`validation-integrated-immediate-punch.log` also exited 0: inventory/finder checks,
punch restrictions 0�11, actual dimension/session renewal, and twelve row cases
passed. That harness currently terminates at the row-suite marker, so this run
is not evidence of rerunning its later creative GUI/command stages. Those stages
are outside this rapid-punch validation claim.

The final production/source JARs were built successfully, and the production JAR
was checked to include RequestBudget but exclude runtime fixtures and bundled
Forge implementation classes. SHA-256 matches between the build output, both
focused instances, the full pack instance, and the staged client/server JAR:
`2b62af10dec079cf2b5355476093bac85d88192cd389305f39111cd6ca151adc`.
The same tested binary replaces dist's testing JAR; no installation or release
was modified automatically. Both client and server must update for the burst
budget and creative server break guard. The older row-swap artifact digest above
is retained only as historical evidence.


## Chest row swaps and protected-item override (2026-11-23)

Build: JDK 8, Gradle 4.9; `:common:test :forge-1.12.2:build
:forge-1.12.2:exportProductionRuntime` exited 0 (`validation-chest-row-ready-build.log`).
All 22 unit tests passed, including every mapped player/chest row in 27/54-slot
storage, exact conservation, unaffected rows and reversal after every exchange.

Runtime runner: `wsl.exe -d Ubuntu -u root -- env
ST_GUI_TOOLS=/mnt/f/storage-tweaks-validation/linux-tools sh
/mnt/f/git/storage-tweaks/test-harness/linux-virtual-display.sh python3
/mnt/f/git/storage-tweaks/test-harness/linux-client.py --manifest
/mnt/f/git/storage-tweaks/forge-1.12.2/build/production-runtime.json --instance
<isolated-instance> --native-cache /mnt/f/storage-tweaks-validation/linux-natives
--mode <mode>`. These are private Linux/Xvfb clients with master volume zero.

Modes `chest-rows` (minimal survival) and `chest-rows-creative --heap-gb 6`
(the exact 39-JAR F:/mods.tzs baseline) both reached ALL CHEST ROW SWAP TESTS PASSED.
Logs: validation-chest-row-swaps-retry.log and validation-pack-chest-row-swaps.log.
Each exercises all six small-chest and nine double-chest row choices, then reverses
each in the same GUI: 30 complete row exchanges per run. Whole client and server
containers and empty cursors are checked, including named stacks, partial stacks,
empty slots and a nonstackable sword. Button and rebound-key dispatch alternate.
Nine bindings start unbound; row buttons remain available with Take/Store/Sort disabled.
Screenshots chest-rows-small.png and chest-rows-large.png in each instance were
captured; the large layouts were visually inspected, including the pack's JEI overlay.
These are synthetic Forge button/key-dispatch tests on an integrated server,
not physical input or a fresh dedicated-server test. An initial fixture run failed
because it tried opening before world/teleport readiness; the corrected fixture
waits for world readiness and the expected GUI before testing any swap.

Protected-item modes tool-override and tool-override-creative passed all eight cases
in validation-tool-override-survival.log and validation-tool-override-creative.log:
axe (named, damaged, enchanted), hoe, sword, bow, shears, elytra, configured diamond,
and ordinary apple. Without override the protected-item request is rejected and
normal tool attack remains available; with override exact items deposit and the
chest survives. Default Shift and rebound G are checked independently of crouching;
the SHIFT/G label screenshots were visually inspected. Key state is synthetic.
The positive creative test includes the reviewed Backpack build. The full 39-mod
pack still disables punch for other unreviewed mods; this row test does not change
that policy or claim positive punch compatibility for the entire pack.

The final production binary also passed tool-override-creative on the reviewed
Backpack instance (`validation-tool-override-final.log`, exit 0, all eight cases).
Minimal chest-row run exited 0; the pack run completed its success marker and
normal shutdown. All three instances contain the same production JAR, SHA-256
`94d7dad7519369de5d34a5dd1cbd606b9478a48112cc2c21d8221c3aed8ec59a`. This exact JAR is staged in dist with its checksum;
production contents were checked to exclude runtime fixtures and bundled Forge
implementation classes. Source artifact also built successfully. No personal
instance, original archive, release or Git commit was changed by this validation.
