package org.firebadnofire.storagetweaks.protocol;

/** Bounds queued work while allowing normal clicks batched by client/server stalls. */
public final class RequestBudget {
    private long windowStart;
    private boolean started;
    private int used;
    public synchronized boolean allow(long nowNanos) {
        if(!started || nowNanos-windowStart>=1_000_000_000L) {
            started=true;windowStart=nowNanos;used=0;
        }
        if(used>=40) return false;
        used++;return true;
    }
}
