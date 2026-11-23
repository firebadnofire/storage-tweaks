package org.firebadnofire.storagetweaks.protocol;

/** Server-thread session replay/freshness gate; rejected IDs cannot later mutate state. */
public final class RequestGate {
    private final int minimumTicks;
    public RequestGate() { this(4); }
    public RequestGate(int minimumTicks) {
        if(minimumTicks<0) throw new IllegalArgumentException("Minimum tick interval must not be negative");
        this.minimumTicks=minimumTicks;
    }
    private long lastId, lastTick = Long.MIN_VALUE / 2;
    public boolean consume(long id, long now, long issued, int selected, int actual) {
        if (id <= lastId || id <= 0) return false;
        lastId = id;
        if (now < issued || now-issued > 200 || now-lastTick < minimumTicks || selected < 0 || selected > 8 || selected != actual) return false;
        lastTick = now;
        return true;
    }
}
