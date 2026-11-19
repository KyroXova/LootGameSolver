package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.*;

import org.junit.Test;

import com.cleanroommc.bogosorter.common.network.SDropOffMessage;
import com.cleanroommc.bogosorter.common.network.SDropOffThrottled;

import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;

public class BogoDropOffBridgeTest {

    @Test
    public void observesCompletionWithoutConsumingThePacket() {
        ChannelInboundHandlerAdapter codec = new ChannelInboundHandlerAdapter();
        EmbeddedChannel channel = new EmbeddedChannel(codec);
        BogoDropOffBridge.Session session = new BogoDropOffBridge.Session();
        assertTrue(session.observe(channel.pipeline(), codec));
        SDropOffMessage message = new SDropOffMessage();
        channel.writeInbound(message);
        assertSame(message, channel.readInbound());
        assertEquals(1, session.response());
        session.close();
        channel.finish();
    }

    @Test
    public void recognizesThrottleAsFailure() {
        ChannelInboundHandlerAdapter codec = new ChannelInboundHandlerAdapter();
        EmbeddedChannel channel = new EmbeddedChannel(codec);
        BogoDropOffBridge.Session session = new BogoDropOffBridge.Session();
        assertTrue(session.observe(channel.pipeline(), codec));
        channel.writeInbound(new SDropOffThrottled());
        assertEquals(-1, session.response());
        channel.readInbound();
        session.close();
        channel.finish();
    }
}
