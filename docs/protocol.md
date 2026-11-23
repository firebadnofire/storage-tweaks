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
probe 5, and probe result 6. Requests never carry
authoritative item data or arbitrary mutable slot numbers. Invalid lengths fail
decoding; incompatible versions never activate.

The server uses its actual held stack, current slot, block, inventory, reach,
ray trace, locks, spawn protection, loaded-target state, Forge interaction events,
and registered access policies. Token age is bounded to 200 server ticks; IDs must
increase; deposits are separated by at least four server ticks. A pre-scheduling
arrival throttle bounds accepted packet scheduling. Mutations run on the game
thread and plan insertion before changing held/destination contents. Partial
capacity leaves the remainder held. Duplicate/stale requests cannot apply again.
Availability probes run the same access and capacity checks without mutation,
with a separate replay/throttle gate. The client polls at most once per six ticks
while aiming at an eligible target, with one outstanding probe and a finite response
timeout. A positive result authorizes the label for
at most ten client ticks and the same target/held stack. The actual deposit always
validates again. Changing targets, held contents, or connection invalidates it.

## Protection boundary

A generic Forge event is not proof that every protection mod approves. By default,
any additional loaded server mod without a registered `PunchAccess.Policy` blocks
the server's punch capability. Integrations register an authoritative decision for
each covered mod ID with `PunchAccess.register(modId, policy)`. No third-party
protection integration is currently supplied. This conservative restriction also
affects harmless but unreviewed mods; it is visible in capability status. Inventory
buttons and finder commands remain available independently.

The initial server implementation admits exact vanilla chest and ender-chest
blocks. Shulker punch deposit is not implemented. Unknown custom inventories are
denied. Tools, weapons, hoes, shears, damageable items, Forge tool classifications,
and configured registry exclusions are protected. There is no vanilla-server
open/shift-click/close fallback.
