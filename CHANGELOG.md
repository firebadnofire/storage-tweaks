# Changelog

## 0.0.1 (unreleased)

- Extend numbered hotbar swaps to small and double chests, with unbound row 1-9 bindings and bottom-up numbering.
- Allow protected punch items with a rebindable held override (Left Shift by default); show the required key below the deposit label.

* Accept punch deposits immediately after hotbar changes without waiting for the
  hover probe. Synchronize the held slot before sending; prevent attack fall-through
  and held-input chest destruction, including creative mode. Replace fixed click
  delays with a bounded burst budget so tick stalls do not discard normal clicks.

* Add 3/2/1 row-to-hotbar swap buttons in survival and the creative player inventory,
  with unbound optional keys and an independent toggle. Preserve exact stack counts
  and column order using acknowledged vanilla hotbar exchanges.

* Fix held-slot probe ordering: switching hotbar slots while aiming at storage
  no longer leaves punch availability waiting until the player looks away.

* Restore vanilla-storage punch alongside the reviewed Traveler's Backpack 1.0.35
  distribution; verify its exact JAR before accepting that compatibility rule.
* Explain the exact blocking mod when enabling punch remains denied by policy.

* Keep the camera stationary during item-search checks; face only the final match.
* Make Look at chat links distinct, underlined actions beside coordinates.

* Set title to Fire's Storage Tweaks and creator to FireBADnoFire.
* Scan every reachable storage candidate for cfinditem, report matching counts with
  [Look at], then face and open the container with the highest observed count.

* Add persisted server /punchdeposit true/false, a Forge permission node, and a
  singleplayer-owner permission exception.

* Add client-only /clook x y z and clickable Look actions on finder results.

* Move creative Sort to the right and check player-tab visibility on drawing and
  clicking, so catalogue tabs cannot display or activate it.

* Allow entering /cfinditem through chat while still rejecting open containers.
* Use selector, block and item placeholders in command help; document all commands
  and cancellation forms in README.
* Refresh punch authorization immediately after changing the held stack.
* Add main-27 Sort on the creative player-inventory tab using real window-0 slots;
  changing creative tabs interrupts the operation. Integrated-server fixture tested.

* Fixed real mouse clicks on inventory controls falling through to vanilla's
  outside-slot handling, which interrupted multiplayer Take/Store operations.
* Added private WSL/Xvfb GUI validation with muted clients and software rendering.
* Corrected entity finder coordinates and distances, including self searches.
* Preserve and reveal a finder-owned container when a cursor item interrupts
  search, avoiding an automatic close that could drop it from full inventories.
* Added Java 8 inventory planning with randomized conservation and forbidden-slot tests.
* Implemented Forge 1.12.2 chest Take/Store/Sort, main-inventory Sort, settings,
  bounded transaction handling, and independent client finder commands.
* Implemented optional server punch negotiation, freshness/replay validation, tool
  protection, and a conservative access-policy boundary.
* Added a development-only integrated gameplay harness; see testing documentation
  for actual results and remaining validation. Target: Forge 1.12.2 only.
