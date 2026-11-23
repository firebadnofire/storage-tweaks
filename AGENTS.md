# AGENTS.md

## Project and mission

**Project:** FireBADnoFire's Storage Tweaks  
**Repository identifier:** `firebadnofire-storage-tweaks`  
**Preferred mod ID:** `firebadnofire_storage_tweaks`  
**Minecraft:** Java Edition, stable releases from 1.8.x through the latest stable 26.x release (initial upper target: 26.3, as of November 22, 2026)

This file contains standing instructions for coding agents working in this repository. Implement the actual mod, keep it maintainable across Minecraft generations, and substantiate support claims with builds and runtime tests. The detailed feature specification in the project brief remains authoritative for product behavior; this file establishes engineering guardrails and workflow.

If a requirement is impractical for a particular version or loader, document the specific blocker. Never paper over an unsupported target with a stub, misleading release label, or untested compatibility claim.

## Inspect before changing anything

1. Read this file and any more-specific `AGENTS.md` files governing the directories you edit.
2. Inspect the repository, build files, mappings, loader metadata, source sets, and tests before choosing an implementation.
3. Inspect the provided original *Punch To Deposit* README and screenshot if available. Its original Forge 1.8.9 behavior is inspiration, not a reason to preserve its client-only punch workaround.
4. Inspect `../clientcommands` for upstream command syntax, identifiers, implementation details, and license notices. **Treat it as read-only.** If absent, record that fact and avoid claiming upstream parity.
5. Verify the *actual* license texts in source distributions before copying code. The source README describes Punch To Deposit as MIT and clientcommands as LGPL-3.0-only; inspect real notices before reuse.
6. Determine which Minecraft versions/loaders/toolchains can genuinely build in the current environment. Do not assume APIs, mappings, or loaders exist across the entire requested range.

Do not blindly replace working code, downgrade dependencies, or restructure modules without showing the concrete benefit.

## Non-negotiable user-facing behavior

### Chest GUI: Take / Store / Sort

Add compact vanilla-compatible **Take**, **Store**, and **Sort** buttons to supported chest screens. Avoid blocking slots, conflicting with GUI scaling, or modifying unrelated container screens.

- **Take:** Move as much as safely possible from the open chest into the player's main inventory **and hotbar**, merging compatible stacks first. Leave anything that cannot fit. Keep the GUI open.
- **Store:** Move as much as safely possible from the player's main inventory **and hotbar** into the open chest, merging compatible stacks first. Exclude armor, offhand, crafting slots, and special equipment slots. Do **not** globally exempt tools from this button. Keep the GUI open.
- **Chest Sort:** Consolidate truly stack-compatible items, then sort the **chest slots only** by displayed item name (case-insensitive A–Z). Use stable deterministic tie-breakers such as registry ID and distinct stack metadata. Leave empty slots at the end. Do not rearrange player inventory.

Start with normal, double, trapped, and ender chests. Support barrels and shulker boxes only where they exist and their handlers are understood. Do not mistake hoppers, furnaces, crafting grids, or arbitrary modded screens for unrestricted chest storage.

### Player inventory: Sort

Add **Sort** to the player's normal E inventory. Sort and consolidate only the **27 main inventory slots**. The **nine hotbar slots must remain untouched, including during intermediate operations**. Never sort armor, offhand, crafting inputs/results, or modded equipment slots. Do not use the hotbar as scratch space.

### Sorting contract

- Sort using actual displayed/localized names; custom names participate.
- Merge only if Minecraft's version-appropriate stackability rules allow it. Respect count limits, durability, enchantments, NBT, data components, and mod-defined properties.
- Preserve every item, count, and property. Never drop a cursor stack outside the inventory to make room.
- Identical input state must produce deterministic output; a second sort must not change an already sorted inventory.
- Do not implement sorting by changing client-local slot contents and pretending the server accepted it. All mutations must follow legal, server-authoritative inventory interactions.

### Punch-to-deposit: *server support required*

On left-clicking a supported chest while holding an eligible item, deposit as much of the held stack as the target can accept **without opening the GUI**, but **only after a compatible Storage Tweaks server capability handshake has succeeded**. Singleplayer uses its integrated server implementation.

- A vanilla or unsupported server must retain ordinary left-click behavior. **Do not fall back** to the original mod's open/shift-click/close technique.
- If the server or protocol does not support deposit, hide/disable the action quietly. Never intercept chest punches in an unsupported session.
- Preserve tools/weapons from punch deposits by default, using verified per-version classifications and a configurable exclusion policy. This restriction does **not** apply to the Store button.
- Preserve the optional `PUNCH TO DEPOSIT` hover text, but show it only when the feature is actually available for the target and held item.
- The server verifies reach, loaded target, permissions/protection hooks, actual held item and count, destination capacity, and request freshness. Never trust the client to specify authoritative item data or arbitrary mutable slots.
- Handle partial deposits, fast repeated clicks, replays, concurrent users, stale state, disconnects, and incompatible protocol versions without duplication.
- Run game-state mutations on the server game thread. Do not load distant chunks to satisfy requests.
- Keep the dedicated server entrypoint free from client-only GUI/rendering classes.

### Finding commands: client side

Implement `/cfind`, `/cfindblock`, and `/cfinditem`, using `../clientcommands` as the *behavioral and API reference*, not a required runtime dependency. Examine the upstream implementation before asserting exact argument or flag compatibility.

- **`/cfind`:** Find matching entities *known to the client* in loaded/client-visible world state.
- **`/cfindblock`:** Find matching blocks within available loaded chunks and a bounded client search radius; never claim to search unloaded world data.
- **`/cfinditem`:** Find matching items in legitimately available nearby inventory information. Closed, remote chests are **not automatically readable** client-side. Use only data the client has legitimately received or normal permitted interactions; distinguish cached from verified-live results. Do not circumvent server access rules.
- Use bounded work per tick or another safe incremental scheduler. Support cancellation, unload/disconnect cleanup, limits, and informative empty/incomplete results.
- Report positions/distances and provide clickable coordinates when supported. Respect version-specific selectors, item predicates, names, and formatting.
- Do not add a mandatory server component for the finder commands. Do not claim upstream's optional server-assisted item-search features work client-only.

### Coexistence with Earthcomputer's clientcommands

Detect clientcommands by its verified **loader mod identifier** using that loader's supported mod-list API, early enough to avoid registration conflicts. Never use a filename heuristic or hard dependency.

**If clientcommands is installed:** do not register **any** of our three finder commands, do not initialize their unnecessary services, and leave all other Storage Tweaks features enabled. The upstream commands take precedence.

**If clientcommands is absent:** register our commands on supported targets. Do not disable them just because upstream could theoretically run on that Minecraft release. Do not overwrite an unrelated mod's command registrations.

Verify both scenarios in-game on versions where clientcommands is actually available. Loader differences matter; do not infer presence from absence of a particular class.

## Architecture and compatibility strategy

Prefer **pure, shared algorithms with thin platform adapters**, not a giant shared abstraction that hides incompatible inventory or networking semantics. Separate these concerns:

| Concern | Shared responsibilities | Version/loader responsibilities |
| --- | --- | --- |
| Inventory planning | Snapshots, stack comparison, transfers, deterministic sorting, invariants | Slot mapping, click dispatch, synchronization, handlers |
| Finder | Predicates/results/search budgets/cancellation concepts | Commands, registries, entity/chunk access, chat components |
| Punch protocol | Capability model, request/result semantics, validation contract | Network registration, serialization, game-thread dispatch, server hooks |
| GUI/config | Behavior and settings schema | Screen events, controls, key bindings, config persistence |

Keep client-only and dedicated-server code physically or logically separated so a server never attempts to load client classes. Use official loader APIs/events before considering Mixins or coremods. Keep any required injections narrow and documented.

### Version targets and order

1. **Forge 1.12.2:** first fully working vertical slice, including client features, server punch, and actual modpack-oriented gameplay tests.
2. **Forge 1.8.9:** legacy reference baseline.
3. **Minecraft 26.3:** a verified available modern loader and modern registries/networking/data components.
4. **Intermediate stable releases and patch versions:** expand systematically, with per-version evidence.
5. **Additional loaders:** Forge, Fabric, NeoForge, and compatible Quilt targets **only where real support exists and passes tests**.

The long-term request is coverage from 1.8.x to 26.x. **Do not equate a handful of representative releases with full-range coverage.** Maintain an explicit per-Minecraft-version and per-loader matrix showing Java, mappings, Gradle/toolchain, build result, runtime result, feature status, and caveats.

Historical ForgeGradle and modern loader toolchains may need **separate Gradle builds and JDKs**. Do not force them into one incompatible runtime. Pin verified tools. Never invent mapping names or loader versions. Cross-version code must compile with the target Java language/runtime level.

### Suggested project structure, not a requirement

```text
common/                # inventory/finder/protocol/config algorithms
platform/              # loader and Minecraft-version integrations
versions/              # concrete source sets or builds
server/                # server-side punch support, if separately packaged
compat/                # optional integrations, if needed
tests/                 # unit/property/integration tests
test-harness/          # actual client and dedicated-server launch tests
docs/                  # architecture, compatibility, protocol, licensing
AGENTS.md
README.md
CHANGELOG.md
```

Honor an existing functional layout if one already exists. Prefer simple build orchestration to speculative scaffolding.

## Inventory safety: implementation rules

**Correctness takes precedence over throughput.** Implement inventory operations as planned sequences of legitimate interactions with explicit state tracking. A clean design distinguishes snapshot, proposed plan, execution state, and reconciliation.

- Never operate on stale slot maps or an outdated server/container state.
- Do not run conflicting Take, Store, or Sort jobs concurrently.
- Honor restricted output/input slots, stack limits, currently held cursor item, and modded handler semantics.
- Use version-correct click semantics and authoritative acknowledgments/state updates where available. Pace clicks rather than flooding a server.
- Stop on unexpected state changes, rejected transactions, GUI closure, death, dimension change, or disconnect. Reconcile before resuming.
- Do not promise perfect rollback or atomicity where vanilla protocols cannot provide either.
- Preserve the player's cursor stack. Never issue a click outside the GUI as a cleanup shortcut.
- Avoid using prohibited slots as temporary buffers. If a valid rearrangement is impossible using permitted moves/space, fail safely and explain the limitation.
- Treat storage UI compatibility with other mods as capability-based, not as permission to write arbitrary slots.

## Settings and interaction

Provide an accessible in-game settings screen and rebindable keybind (original reference uses **P**, but avoid overriding existing bindings). Configure independent toggles for chest buttons, inventory Sort, finder, punch, and hover label. Provide sensible transaction pacing and search budget controls. A Store exclusion list and punch-protection list are useful configurable additions. Persist settings using APIs available for each platform.

Prefer compact rectangular vanilla-style buttons; respect GUI scale, resource packs, and common inventory overlays where feasible. Keep chest GUI open after button operations. Do not spam chat with routine capability changes.

## Licensing and dependency rules

- The provided original README reports **MIT** for Punch To Deposit. Confirm the actual license and any source attribution before copying.
- `../clientcommands` is advertised as **LGPL-3.0-only**. Verify its actual notices. It is **read-only reference source**.
- Prefer independently written finder implementations based on observed behavior and APIs. If copying/adapting LGPL-covered source, keep it identifiable; preserve notices, source availability, modification records, and all applicable obligations.
- Do not declare copied LGPL-covered code MIT-only. Do not assume bundling or linking is obligation-free.
- Keep `docs/licensing.md` and `NOTICE` accurate to actual source reuse. Prefer a licensing review before distributing binaries.
- Avoid copying third-party assets or code without a verified license.

## Build, test, and validation expectations

Before editing, inspect existing build instructions and wrappers. Use the **actual per-module commands** rather than assuming the whole repository supports a universal `./gradlew build`. After modifications, run the narrowest relevant tests first, then targeted builds, then real client/server smoke tests. Record exact commands and exit results.

### Required unit/property tests

- Empty/full/partial transfers; stack merging and max sizes.
- Non-stackable, enchanted, damaged, custom-named, NBT/data-component, and modded items.
- Stable alphabetical ordering, deterministic tie-breakers, and idempotent sorting.
- Exact conservation of item type, count, and metadata; no forbidden-slot writes.
- Hotbar immutability **throughout** player inventory sorting, not merely at completion.
- Cursor-held items, insufficient scratch space, invalidated plans, rejected clicks, and disconnect cleanup.
- Finder filtering, chunk bounds, cancellation, limits, incomplete/cached results.
- Handshake incompatibility, invalid/unreachable/protected targets, partial deposits, replay, concurrent access.

### Required integration scenarios

1. Singleplayer with an integrated server: buttons, finder, and punch all behave as defined.
2. **Vanilla dedicated server:** buttons and finder function within client-only limits; punch does not activate and vanilla punching remains unchanged.
3. Modded dedicated server: valid punch works; invalid/protected/out-of-reach requests are rejected; dedicated server does not load client classes.
4. With **clientcommands absent:** our three finder commands register and function.
5. With **clientcommands present:** our three finder commands are not registered; upstream commands work; inventory features remain enabled.
6. GUI interruptions, latency, two players using the same chest, unusual items, and modded inventories are handled safely.

Prioritize real client and dedicated-server launches for Forge 1.12.2, Forge 1.8.9, and a modern 26.3 loader. Extend verification across the full matrix as targets are added. Compilation alone does **not** prove a mod launches, and a client launch alone does **not** prove gameplay correctness.

CI should use pinned toolchains and matrix jobs aligned with the actual supported targets. Name release artifacts with mod version, Minecraft version, and loader. Do not publish nonfunctional placeholder JARs or call a target verified without reproducible evidence.

## Working conventions

- Make small, reviewable changes. Preserve existing feature behavior unless the specification explicitly changes it.
- Document why an API-specific workaround or Mixin is necessary.
- Prefer readable code and targeted comments over clever abstractions.
- Never silently disable whole feature groups to make a build pass.
- Do not mutate `../clientcommands` or other upstream reference repositories.
- Never invent test output, user approval, performance figures, or support status.
- Keep dependencies as lean as possible, especially for legacy Minecraft versions.
- Update README and relevant docs when user-visible behavior or compatibility changes.
- Do not commit secrets, developer-local paths, downloaded game assets, caches, or generated files that should be ignored.

## Documentation to maintain

- `README.md`: features, installation, commands, server requirements, controls, and limitations.
- `CHANGELOG.md`: meaningful end-user changes.
- `docs/architecture.md`: modules, slot model, shared/adapter boundaries.
- `docs/compatibility.md`: **per-version, per-loader** status matrix with evidence.
- `docs/protocol.md`: handshake, packets, validation, compatibility.
- `docs/finder.md`: exact supported syntax and client visibility limitations.
- `docs/licensing.md` and `NOTICE`: sources, modifications, attribution, obligations.
- `docs/testing.md`: repeatable local and CI procedures, run logs, known gaps.

Do not write documentation for imaginary implemented features. Mark proposed interfaces as proposed until code exists.

## Definition of done and progress reports

A feature is done for a particular version/loader only when its code is implemented, builds, launches, works in an appropriate game scenario, preserves inventory invariants, and has accurate documentation. Track status separately as:

`not started` · `planned` · `implemented/untested` · `builds` · `launches` · `gameplay tested` · `verified for stated scope`

After each substantial work session, report:

1. What changed, including key files/modules.
2. Exact Minecraft/loader targets affected.
3. Build/test commands run and their real results.
4. What was validated in-game versus inferred or only compiled.
5. Failures, blockers, known limitations, and next priorities.

**Do the engineering work, not merely planning.** Start by auditing the repository and references, then deliver a complete Forge 1.12.2 implementation before expanding. Respect the core architectural rule throughout: **inventory buttons and finding commands remain client-side; punch-to-deposit requires a compatible server.**
