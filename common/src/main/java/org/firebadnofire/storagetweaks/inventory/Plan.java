package org.firebadnofire.storagetweaks.inventory;

import java.util.*;

public final class Plan {
    public static final class Click {
        public final int slot;
        public final Snapshot before, after;
        Click(int slot, Snapshot before, Snapshot after) { this.slot = slot; this.before = before; this.after = after; }
    }
    public final Snapshot initial, result;
    public final List<Click> clicks;
    Plan(Snapshot initial, Snapshot result, List<Click> clicks) {
        this.initial = initial;
        this.result = result;
        this.clicks = Collections.unmodifiableList(new ArrayList<>(clicks));
    }
}
