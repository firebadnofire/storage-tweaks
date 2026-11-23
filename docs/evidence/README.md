# Runtime screenshots

Captured by the separate integrated-server harness on 2026-11-22 using the actual
Forge 1.12.2 distribution JAR and fresh disposable worlds, at 854×480 pixels.

- `forge-1.12.2-double-chest.png`: actual double-chest GUI after Take/Store/Sort.
  The left-side fallback keeps all controls visible when no room remains above.
- `forge-1.12.2-item-match.png`: item finder reveals its original matching screen;
  the test checks screen identity, container identity, and released suppression.

These are vanilla-fixture screenshots, not evidence of every GUI scale or modpack.
Transient vanilla tutorial/advancement toasts are present. Intermediate hotbar
immutability and item conservation are checked by the harness and algorithm tests.

`forge-1.12.2-private-display.png` shows the double-chest fixture rendered on the
private Xvfb display, using Mesa llvmpipe. The full private integrated regression,
two-client capacity contention, and optional-installation connection evidence are
summarized in [private-display-validation.json](private-display-validation.json).
