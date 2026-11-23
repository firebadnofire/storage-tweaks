package org.firebadnofire.storagetweaks.inventory;

import java.util.Objects;

/** Immutable value. key must encode every stackability property, including platform capabilities. */
public final class Stack {
    public static final Stack EMPTY = new Stack("", "", "", 0, 64);
    public final String key, name, registryId;
    public final int count, max;

    public Stack(String key, String name, String registryId, int count, int max) {
        if (count < 0 || max < 1 || count > max) throw new IllegalArgumentException("Invalid stack count/limit");
        this.key = Objects.requireNonNull(key);
        this.name = Objects.requireNonNull(name);
        this.registryId = Objects.requireNonNull(registryId);
        this.count = count;
        this.max = max;
    }

    public boolean empty() { return count == 0; }
    public boolean compatible(Stack other) { return !empty() && !other.empty() && key.equals(other.key); }
    public Stack count(int value) { return value == 0 ? EMPTY : new Stack(key, name, registryId, value, max); }

    @Override public boolean equals(Object other) {
        if (!(other instanceof Stack)) return false;
        Stack stack = (Stack) other;
        return empty() && stack.empty() || count == stack.count && max == stack.max && key.equals(stack.key);
    }
    @Override public int hashCode() { return empty() ? 0 : Objects.hash(key, count, max); }
    @Override public String toString() { return empty() ? "empty" : registryId + " x" + count; }
}
