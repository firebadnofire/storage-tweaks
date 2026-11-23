# Finder commands (Forge 1.12.2)

The implementation is independent; it does not claim full modern clientcommands
syntax parity. All three registrations and services are omitted when Forge reports
mod ID `clientcommands`. Existing command names owned by another mod are skipped
with a log diagnostic. Disabled settings prevent execution, not command ownership.

* `/cfind <selector-or-exact-name-or-UUID> [--keep-searching]`
* `/cfindblock <block-id> [metadata-or-state-properties] [--keep-searching]`
* `/cfindblock <block-id>[property=value,...] [--keep-searching]`
* `/cfinditem <item-id> [metadata] [--keep-searching]`
* `/cfind cancel`, `/cfindblock cancel`, `/cfinditem cancel`

Entity selectors: @e, @a, @p, @r, @s; filters: type, name, tag, r, rm, c,
x/y/z, dx/dy/dz. Name, type and tag support `!` negation. Scores, NBT selectors,
modern distance ranges, tags as registry predicates, and modern item components
are not implemented. Unknown flags/filters fail explicitly. Coordinates suggest
a `/tp` command; clicking does not teleport the player automatically.

Entity/block searches use only known entities and loaded blocks. They require no
OP check, line-of-sight restriction, or server handshake. They can reveal data
obscured by terrain; multiplayer server rules may restrict their use. They never
load chunks or request privileged data. Block searches retain the nearest bounded
set of matches in the radius around the starting location and report unavailable
positions. Searches cancel on world changes, disconnect, death, or feature disable.

Item searches automatically use normal right-click interactions with loaded,
reachable vanilla chests, ender chests, shulker boxes, and chest minecarts.
Intermediate screens are suppressed. Only initial server-supplied inventory contents
are inspected; player slots are excluded. Each inspected container closes normally after its matching count is recorded.
Positive matches are reported with coordinates and clickable `[Look at]` text
running `/clook`. After every reachable candidate has been inspected, the container
with the largest observed matching count is opened again and checked using fresh
server contents. The player faces its center using the same aiming implementation
as `/clook`, and its actual screen/container is revealed without an extra reopen.
If that final match has disappeared or become unreachable, the search stops with
an explicit message. Counts are observations rather than an atomic world snapshot.
Ties retain the nearer candidate. Chat results obey the configured display limit;
all candidates are still scanned and omitted positive results are summarized. Double chests and ender inventory are
deduplicated. Shulker item contents are inspected to depth 4 with a 4096-stack limit.
No cached remote inventories or privileged NBT queries are used.

Use Escape during a hidden item search, a cancel command, or the configurable
cancel binding to stop. Other mouse interactions cancel the search. Screen
suppression is scoped to the outstanding search and released on every exit.
Only one item search may run; close other screens and empty the cursor first.
The chat screen used to enter the command is allowed.
If an authoritative update puts an item on the cursor during a suppressed
container interaction, the search stops and reveals that same container for
manual recovery. It avoids automatically closing that window, because vanilla
closure can drop a cursor stack when the player's inventory is full.
Server-denied containers and timeouts can make results incomplete. A continuing
search repeats available inventories after a paced cycle and can discover changed
contents and newly reachable inventories. It selects the best observed match after a complete scan, stopping when a cycle has matches. Candidate
collection is incremental and bounded; nested or candidate-budget exhaustion is
reported as incomplete, and truncated positive counts are reported as a lower bound.

Defaults: 64-block block-search radius, further bounded by render distance; 2 ms
and 4096 positions per tick; 20 displayed results; four ticks between interactions;
100-tick response timeout. The settings screen exposes these limits.

## Look command

`/clook <x> <y> <z>` turns the local player's view toward the center of the
specified block. Legacy `~` relative block coordinates are accepted. It does not
move, open a container, interact, or require OP/server support. Finder results
include a clickable `[Look at]` action using the result coordinates, including item
search container locations. Coordinate clicks still suggest `/tp`. Existing clook
registrations are preserved with a diagnostic. This command registers independently
of the three finder commands; clientcommands presence still suppresses those three.

Item-search checks do not rotate the camera. Only the completed best-match reveal
automatically faces its target. [Look at] is aqua and underlined with a hover hint;
open chat with T to click it. Coordinates and the look link are separate styled
components so wrapped lines retain their independent click actions.
