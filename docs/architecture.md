# Architecture

The implemented target is Forge 1.12.2. Other targets have no adapter yet.

`common` contains immutable stack/snapshot values, transfer and sort planning, and
the server request replay/freshness gate. It has no Minecraft or loader dependency
and targets Java 8. A plan records every legal left-button PICKUP click and its
expected before/after snapshot. Full stacks can be rearranged using the cursor;
neither player slots nor the hotbar are scratch space for chest sorts.

`forge-1.12.2` embeds common classes in its distribution JAR. Sided proxies keep
client GUI, command, and rendering classes out of dedicated-server initialization.
Both installations declare the remote mod optional. Only punch uses a custom
channel; inventory buttons and commands use ordinary vanilla interactions.

The client adapter admits exact vanilla chest, shulker, and survival-player screen
and container classes. It identifies player slots by inventory identity and logical
index rather than GUI offsets. It checks every planned placement against Slot
restrictions before starting. Stack identities use canonical, type-preserving NBT
including serialized capabilities with Count excluded. Forge stack compatibility
is checked for colliding merge identities.

The client executor sends one click at a time and checks snapshots before dispatch,
after prediction, and after confirmation. A connection-specific Netty observer is
necessary because Forge 1.12.2 has no general inventory packet observation event.
It observes packets without replacing their handlers; state callbacks run on the
client thread after the ordinary packet handler. A 100-tick configurable timeout
stops missing confirmations. No outside click, rollback, or automatic cursor cleanup
is attempted. The protocol does not provide atomic transactions.
Observation is restricted to click confirmation, full-window contents, and slot
updates. Unexpected authoritative inventory changes stop the job. Actual 1.12.2
Forge and vanilla-server tests verified positive confirmations; injected missing
and negative confirmations verified finite stopping and recoverable cursor state.

Inventory controls consume both mouse press and release in Forge's pre-input
event. GuiContainer continues slot handling after GuiScreen's button callback;
using only ActionPerformedEvent for controls above the frame generated a stray
outside-slot packet. The pre-input handler dispatches the button action while
preventing that vanilla fall-through, without consuming ordinary slot clicks.

Finder jobs are independent bounded client tasks. Only item search owns container
interaction; it excludes inventory automation and manual container activity. It
retains the actual suppressed GuiContainer and installs its actual container before
the initial contents packet, because cancelling GuiOpenEvent skips normal initGui.
On a match it releases search ownership and displays that same screen. No substitute
container or close/reopen sequence is used.

Development runtime tests use a separate source set and test mod, excluded from
distribution and source JARs.
