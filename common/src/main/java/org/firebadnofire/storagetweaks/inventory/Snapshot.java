package org.firebadnofire.storagetweaks.inventory;

import java.util.*;

public final class Snapshot {
    public final List<Stack> slots;
    public final Stack cursor;
    private final int[] limits;

    public Snapshot(List<Stack> slots, Stack cursor, int[] limits) {
        if (slots.size() != limits.length) throw new IllegalArgumentException("Slot/limit mismatch");
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
        this.cursor = Objects.requireNonNull(cursor);
        this.limits = limits.clone();
        for (int limit : limits) if (limit < 0) throw new IllegalArgumentException("Negative slot limit");
    }
    public int limit(int slot, Stack stack) { return Math.min(limits[slot], stack.max); }
    public int[] limits() { return limits.clone(); }
    @Override public boolean equals(Object other) {
        if (!(other instanceof Snapshot)) return false;
        Snapshot snapshot = (Snapshot) other;
        return slots.equals(snapshot.slots) && cursor.equals(snapshot.cursor) && Arrays.equals(limits, snapshot.limits);
    }
    @Override public int hashCode() { return Objects.hash(slots, cursor, Arrays.hashCode(limits)); }
}
