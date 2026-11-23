package org.firebadnofire.storagetweaks.protocol;

import org.junit.Test;
import static org.junit.Assert.*;

public class RequestGateTest {
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
