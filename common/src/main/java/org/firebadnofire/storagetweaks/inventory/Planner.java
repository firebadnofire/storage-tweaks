package org.firebadnofire.storagetweaks.inventory;

import java.util.*;

/** Plans left-button PICKUP clicks for unrestricted vanilla storage slots only. */
public final class Planner {
    private final Snapshot initial;
    private Snapshot state;
    private final List<Plan.Click> clicks = new ArrayList<>();
    private final Set<Integer> allowed = new HashSet<>();

    private Planner(Snapshot initial, int[]... groups) {
        if (!initial.cursor.empty()) throw new IllegalArgumentException("Put the cursor item away before starting");
        this.initial = this.state = initial;
        for (int[] group : groups) for (int slot : group) {
            if (slot < 0 || slot >= initial.slots.size() || !allowed.add(slot))
                throw new IllegalArgumentException("Invalid or overlapping slot groups");
            if (initial.limits()[slot] < 1) throw new IllegalArgumentException("Restricted slot");
        }
    }

    public static Plan transfer(Snapshot snapshot, int[] sources, int[] destinations) {
        Planner planner = new Planner(snapshot, sources, destinations);
        for (int source : sources) {
            Stack item = planner.at(source);
            if (item.empty()) continue;
            boolean picked = false;
            destinations:
            for (int pass = 0; pass < 2; pass++) for (int destination : destinations) {
                Stack target = planner.at(destination);
                if ((pass == 0 && !target.compatible(item)) || (pass == 1 && !target.empty())) continue;
                if (target.count >= planner.state.limit(destination, item)) continue;
                if (!picked) { planner.click(source); picked = true; }
                planner.click(destination);
                if (planner.state.cursor.empty()) break destinations;
            }
            if (!planner.state.cursor.empty()) planner.click(source);
        }
        return planner.finish();
    }

    public static Plan sort(Snapshot snapshot, int[] slots, boolean caseSensitive) {
        Planner planner = new Planner(snapshot, slots);
        // Merge into earlier slots so full stacks precede partial stacks of the same identity.
        for (int i = 0; i < slots.length; i++) {
            int target = slots[i];
            if (planner.at(target).empty()) continue;
            for (int j = i + 1; j < slots.length; j++) {
                int source = slots[j];
                if (planner.at(source).compatible(planner.at(target)) &&
                        planner.at(target).count < planner.state.limit(target, planner.at(target))) {
                    planner.click(source); planner.click(target);
                    if (!planner.state.cursor.empty()) planner.click(source);
                }
            }
        }
        Comparator<Stack> order = order(caseSensitive);
        for (int i = 0; i < slots.length; i++) {
            int best = i;
            for (int j = i + 1; j < slots.length; j++)
                if (order.compare(planner.at(slots[j]), planner.at(slots[best])) < 0) best = j;
            if (best == i) continue;
            int source = slots[best], target = slots[i];
            planner.click(source); planner.click(target);
            if (!planner.state.cursor.empty()) planner.click(source);
        }
        return planner.finish();
    }

    /** Both arrays are ordered left to right; hotbar entries correspond to number keys 1..9. */
    public static Plan swapRows(Snapshot snapshot, int[] row, int[] hotbar) {
        if (row.length != 9 || hotbar.length != 9) throw new IllegalArgumentException("Expected two nine-slot rows");
        Planner planner = new Planner(snapshot, row, hotbar);
        for (int column = 0; column < 9; column++) {
            int source = row[column], destination = hotbar[column];
            Stack a = planner.at(source), b = planner.at(destination);
            if (a.count > planner.state.limit(destination, a) || b.count > planner.state.limit(source, b))
                throw new IllegalArgumentException("Stack does not fit in swapped slot");
            if (a.equals(b)) continue;
            List<Stack> next = new ArrayList<>(planner.state.slots);
            next.set(source, b); next.set(destination, a);
            Snapshot after = new Snapshot(next, Stack.EMPTY, planner.state.limits());
            planner.clicks.add(new Plan.Click(source, column, planner.state, after));
            planner.state = after;
        }
        return planner.finish();
    }

    public static Comparator<Stack> order(boolean caseSensitive) {
        return (left, right) -> {
            if (left.empty() || right.empty()) return Boolean.compare(left.empty(), right.empty());
            int compare = caseSensitive ? left.name.compareTo(right.name) : left.name.compareToIgnoreCase(right.name);
            if (compare == 0) compare = left.registryId.compareTo(right.registryId);
            if (compare == 0) compare = left.key.compareTo(right.key);
            if (compare == 0) compare = Integer.compare(right.count, left.count);
            return compare;
        };
    }

    private Stack at(int slot) { return state.slots.get(slot); }
    private void click(int slot) {
        if (!allowed.contains(slot)) throw new IllegalStateException("Forbidden slot write");
        Stack target = at(slot), cursor = state.cursor;
        List<Stack> next = new ArrayList<>(state.slots);
        if (cursor.empty()) { cursor = target; target = Stack.EMPTY; }
        else if (target.empty() || target.compatible(cursor)) {
            int moved = Math.min(cursor.count, state.limit(slot, cursor) - target.count);
            target = cursor.count(target.count + moved);
            cursor = cursor.count(cursor.count - moved);
        } else {
            if (cursor.count > state.limit(slot, cursor)) throw new IllegalArgumentException("Insufficient safe swap space");
            Stack swap = target; target = cursor; cursor = swap;
        }
        next.set(slot, target);
        Snapshot after = new Snapshot(next, cursor, state.limits());
        if (!state.equals(after)) clicks.add(new Plan.Click(slot, state, after));
        state = after;
    }
    private Plan finish() {
        if (!state.cursor.empty()) throw new IllegalStateException("Planner left a cursor item");
        if (!counts(initial).equals(counts(state))) throw new IllegalStateException("Planner violated conservation");
        return new Plan(initial, state, clicks);
    }
    public static Map<String, Long> counts(Snapshot snapshot) {
        Map<String, Long> counts = new TreeMap<>();
        List<Stack> all = new ArrayList<>(snapshot.slots); all.add(snapshot.cursor);
        for (Stack stack : all) if (!stack.empty()) counts.merge(stack.key, (long) stack.count, Long::sum);
        return counts;
    }
}
