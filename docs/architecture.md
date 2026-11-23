# Architecture

The project targets Forge 1.12.2 only.

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
Item search records each candidate's count, closes it, and continues scanning.
After the scan it reopens the highest observed match through ordinary interaction,
rechecks fresh contents, faces the target, releases ownership, and displays that
same final screen. There is no substitute container or additional reopen during
the final reveal.

Development runtime tests use a separate source set and test mod, excluded from
distribution and source JARs.

Creative player-tab sorting plans against the actual ContainerPlayer, never the
creative catalogue or its wrapper slot numbers. Click dispatch temporarily selects
that real window-0 container for vanilla prediction, restoring the visible
container in a finally block. Tab changes stop the job. Only the main 27 slots
participate; the creative catalogue, trash slot, equipment and hotbar are excluded.


Row swaps use native 1.12.2 `ClickType.SWAP` (number-key) clicks rather than
pickup/merge sequences. Plans carry the hotbar button index and exact before/after
snapshots; each column exchanges complete stacks without a cursor intermediate.
The adapter maps logical InventoryPlayer indices to real container slots and
validates every changed destination, including the hotbar. Numbered controls are
3/2/1 beside player main rows, continuing upward through chest rows 4-6 or 4-9.
Only verified 27/54-slot vanilla chest screens gain storage row controls; the
hotbar has no button. Row coordinates come from actual container slots. Unbound optional keys use the same scheduler;
with no GUI open it tracks the real player container/window 0 and interrupts on
screen changes or manual interaction. Creative uses the real player container
while retaining the displayed creative wrapper. A whole row is paced, not atomic.
