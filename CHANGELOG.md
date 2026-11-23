# Changelog

## 0.1.0-dev (unreleased)

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
  for actual results and remaining validation. This is not a full-range release.
