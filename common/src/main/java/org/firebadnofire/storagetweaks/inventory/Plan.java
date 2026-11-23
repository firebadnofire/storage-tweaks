package org.firebadnofire.storagetweaks.inventory;

import java.util.*;

public final class Plan {
    public static final class Click {
        public final int slot;
        public final int hotbarButton; // -1: PICKUP; 0..8: native hotbar SWAP
        public final Snapshot before, after;
        Click(int slot, Snapshot before, Snapshot after) { this(slot, -1, before, after); }
        Click(int slot, int hotbarButton, Snapshot before, Snapshot after) {
            this.slot = slot; this.hotbarButton = hotbarButton; this.before = before; this.after = after;
        }
    }
    public final Snapshot initial, result;
    public final List<Click> clicks;
    Plan(Snapshot initial, Snapshot result, List<Click> clicks) {
        this.initial = initial;
        this.result = result;
        this.clicks = Collections.unmodifiableList(new ArrayList<>(clicks));
    }
}
