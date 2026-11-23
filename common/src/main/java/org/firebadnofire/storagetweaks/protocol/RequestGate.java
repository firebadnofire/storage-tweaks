package org.firebadnofire.storagetweaks.protocol;

/** Server-thread session replay/freshness gate; rejected IDs cannot later mutate state. */
public final class RequestGate {
    private long lastId, lastTick = Long.MIN_VALUE / 2;
    public boolean consume(long id, long now, long issued, int selected, int actual) {
        if (id <= lastId || id <= 0) return false;
        lastId = id;
        if (now < issued || now-issued > 200 || now-lastTick < 4 || selected < 0 || selected > 8 || selected != actual) return false;
        lastTick = now;
        return true;
    }
}
