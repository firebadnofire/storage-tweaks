# Punch protocol v1 (Forge 1.12.2)

Storage Tweaks is optional in both directions (`acceptableRemoteVersions="*"`).
Custom messages use channel `fbstorage`. Ordinary inventory controls and finders
never depend on this channel.

The server initiates an offer only for a Forge connection declaring the mod ID,
or a local integrated connection that still must complete the handshake. The
client answers the offer and activates only after server acknowledgment. A server
session has a random identity and short-lived random token refreshed every 100
server ticks. The immediately previous token remains valid within the same 200-tick
age bound so rotation does not invalidate an in-flight request. Unconfirmed
negotiation is limited to three offers. Disconnect
discards session and replay state. Respawn and dimension changes issue a new
session and require negotiation again; old requests cannot apply in the new world.

Each fixed 46-byte packet contains type (byte), protocol version (int), enabled
(boolean), session/token/request ID (three longs), packed block position (long),
selected hotbar slot (int), and result count (int). Types: offer 0, answer 1,
capability acknowledgment 2, deposit request 3, result 4, target-availability
probe 5, probe result 6, and client punch preference 7. The handshake answer
also carries the client enabled preference. Requests never carry
authoritative item data or arbitrary mutable slot numbers. Invalid lengths fail
decoding; incompatible versions never activate.

The server uses its actual held stack, current slot, block, inventory, reach,
ray trace, locks, spawn protection, loaded-target state, Forge interaction events,
and registered access policies. Token age is bounded to 200 server ticks; IDs must
increase. A pre-scheduling budget allows at most 40 messages per one-second
window per player for deposits/handshake, and a separate 40 for label probes.
It permits bursts caused by tick stalls; accepted deposits have no minimum tick
spacing and execute serially against current server state. Mutations run on the game
thread and plan insertion before changing held/destination contents. Partial
capacity leaves the remainder held. Duplicate/stale requests cannot apply again.
Availability probes run the same access and capacity checks without mutation,
with a separate replay gate and request budget. The client refreshes probes every
six ticks for an unchanged target/stack, and immediately after a target, slot, or
stack change, with one outstanding probe for the current selection and a finite
response timeout. A positive result authorizes the label for
at most ten client ticks and the same target/held stack. The actual deposit always
validates again. Changing targets, held contents, or connection invalidates it. Held-stack changes
clear the old pending probe and immediately schedule a new availability check.

## Protection boundary

A generic Forge event is not proof that every protection mod approves. By default,
any additional loaded server mod without a registered `PunchAccess.Policy` blocks
the server's punch capability. Integrations register an authoritative decision for
each covered mod ID with `PunchAccess.register(modId, policy)`. No third-party
protection integration is currently supplied. One exact Traveler's Backpack 1.0.35
content-mod build has a reviewed compatibility rule; vanilla storage checks remain. This conservative restriction also
affects harmless but unreviewed mods; it is visible in capability status. Inventory
buttons and finder commands remain available independently.

The initial server implementation admits exact vanilla chest and ender-chest
blocks. Shulker punch deposit is not implemented. Unknown custom inventories are
denied. Tools, weapons, hoes, shears, damageable items, Forge tool classifications,
and configured registry exclusions require explicit override opt-in. There is no vanilla-server
open/shift-click/close fallback.

## Server administrator toggle

`/punchdeposit <true|false>` writes server.enabled to the Forge configuration and
refreshes confirmed client capabilities immediately. Every server deposit still
checks the live setting. The permission node is
`firebadnofire_storage_tweaks.command.punchdeposit`, default OP through Forge
PermissionAPI. The integrated server's owner is allowed without OP; remote LAN
players use normal permission checks. Console uses command level 2. A compatible
LuckPerms/permission bridge may supply the Forge permission decision, but no real
LuckPerms distribution has been tested. Enabling the global setting does not bypass
container protections or authorize otherwise unreviewed mods.

Reviewed content-mod exception: travelersbackpack 1.0.35, JAR SHA-256
39a7914b5a55b7d5ad5d1192c38a66b926895317e0426a359a529de2f8028581.
The inspected manifest declares no coremod/tweaker. Its event subscribers handle
item/block registration, models/config, backpack capabilities/player lifecycle,
loot/explosions, and a client warning screen; the distribution has no chest access
or protection handlers. The compatibility rule checks the entire JAR digest once
at initialization. Unknown builds remain blocked, and normal vanilla/protection
checks remain mandatory. This does not add support for depositing into backpacks.
/punchdeposit true now reports an exact unreviewed-mod blocker instead of only
reporting that the saved toggle is enabled.

Hotbar changes no longer wait for the next tick or an availability reply before
accepting a deposit click. Before a probe or deposit, the client invokes vanilla's
controller update to synchronize the selected slot; its held-slot packet precedes
the deposit on the same connection. The server still requires the request slot to
match the actual selection and never accepts client-supplied item contents.

Once compatible enabled server capability is negotiated, an eligible click on a
reachable supported chest is consumed as a deposit attempt even if the label
probe is pending, stale, full, or denied. A denied attempt never falls back to
mining. Mouse interception, the client LeftClickBlock event, and clearing held
attack input stop local destruction and continued mining after the held stack
empties. Forge 1.12 creative sends START_DESTROY before its client click event,
so the server also cancels ordinary chest break events for confirmed clients
that opted into punch while server capability is enabled. This guard does not
deposit anything; only a validated deposit request can mutate inventories.
The preference packet lets disabling the client feature restore normal mining,
without changing the server setting for other players. Unmodded/unconfirmed
players never acquire this guard. To mine normally, hold a protected tool without the override, or an empty hand, or
disable client punch. Unsupported/disabled sessions retain ordinary punching.
Duplicate attack paths in one client tick are coalesced; there is no four-tick
client/server deposit delay. Server/network processing time still applies.
The label remains based on a positive, current-stack server probe and may appear
after the click is already usable. All access and protection checks still run for
each deposit. Valid-session rejected probes receive a negative response; capability
token rotation invalidates prior probe ownership.

## Protected-item override

For deposit/probe types 3/5, `enabled` carries explicit protected-item override
opt-in. Availability probes may preview override eligibility without mutation.
For handshake answer/preference types 1/7, `enabled` remains client punch enabled
and the slot field is 0/1 for override released/held. Actual deposit/probe slot
fields remain the selected hotbar index. The server checks its actual held item
and requires override for protected classifications, including configured exclusions.
It does not verify a physical keyboard key or require crouching: the default Left
Shift binding can be rebound. Access, freshness, replay and capacity checks remain.
