package org.firebadnofire.storagetweaks.server;

import io.netty.buffer.*;
import net.minecraft.util.math.BlockPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class MessageTest {
    @Test public void fixedSizeRoundTrip() {
        PunchNetwork.Message before=new PunchNetwork.Message();
        before.enabled=true;before.type=3;before.session=123;before.token=77;before.request=1;before.slot=8;before.pos=new BlockPos(-100,63,12);
        ByteBuf buf=Unpooled.buffer();
        try {
            before.toBytes(buf);assertEquals(46,buf.readableBytes());
            PunchNetwork.Message after=new PunchNetwork.Message();after.fromBytes(buf);
            assertTrue(after.enabled);assertEquals(before.type,after.type);assertEquals(before.session,after.session);
            assertEquals(before.token,after.token);assertEquals(before.request,after.request);
            assertEquals(before.pos,after.pos);assertEquals(before.slot,after.slot);
        } finally { buf.release(); }
    }
    @Test(expected=IllegalArgumentException.class) public void malformedLengthRejected() {
        ByteBuf buf=Unpooled.buffer();
        try { new PunchNetwork.Message().fromBytes(buf); } finally { buf.release(); }
    }
}
