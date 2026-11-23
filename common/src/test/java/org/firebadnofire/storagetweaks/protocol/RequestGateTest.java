package org.firebadnofire.storagetweaks.protocol;

import org.junit.Test;
import static org.junit.Assert.*;

public class RequestGateTest {
    @Test public void serializedDepositsCanShareServerTickWithoutReplay() {
        RequestGate gate=new RequestGate(0);
        assertTrue(gate.consume(1,100,100,0,0));
        assertTrue(gate.consume(2,100,100,1,1));
        assertFalse(gate.consume(2,100,100,1,1));
        assertFalse(gate.consume(3,100,100,2,1));
    }
    @Test public void rapidDepositsAllowEachTickButRejectReplayAndSlotRaces() {
        RequestGate gate=new RequestGate(1);
        assertTrue(gate.consume(1,100,100,0,0));
        assertFalse(gate.consume(2,100,100,1,1));
        assertFalse(gate.consume(2,101,100,1,1));
        assertTrue(gate.consume(3,101,100,1,1));
        assertFalse(gate.consume(4,102,100,2,1));
        assertTrue(gate.consume(5,102,100,2,2));
    }
    @Test public void replayFreshnessSlotAndThrottle() {
        RequestGate gate=new RequestGate();
        assertTrue(gate.consume(1,100,100,0,0));
        assertFalse(gate.consume(1,104,100,0,0));
        assertFalse(gate.consume(2,101,100,0,0));
        assertFalse(gate.consume(2,105,100,0,0));
        assertFalse(gate.consume(3,105,100,1,0));
        assertTrue(gate.consume(4,108,100,0,0));
        assertFalse(gate.consume(5,301,100,0,0));
        assertFalse(gate.consume(6,99,100,0,0));
    }
}
