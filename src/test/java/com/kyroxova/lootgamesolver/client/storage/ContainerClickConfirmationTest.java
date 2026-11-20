package com.kyroxova.lootgamesolver.client.storage;

import static org.junit.Assert.*;

import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import org.junit.Test;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.embedded.EmbeddedChannel;

public class ContainerClickConfirmationTest {

    @Test
    public void matchesOnlyTheSentClickAndPassesPacketsThrough() {
        ChannelDuplexHandler anchor = new ChannelDuplexHandler();
        EmbeddedChannel channel = new EmbeddedChannel(anchor);
        channel.pipeline()
            .replace(anchor, "packet_handler", new ChannelDuplexHandler());
        ContainerClickConfirmation observer = ContainerClickConfirmation.attach(channel.pipeline(), 3, 12, 0);
        assertNotNull(observer);
        C0EPacketClickWindow click = new C0EPacketClickWindow(3, 12, 0, 0, null, (short) 42);
        channel.writeOutbound(click);
        assertSame(click, channel.readOutbound());
        S32PacketConfirmTransaction other = new S32PacketConfirmTransaction(3, (short) 41, true);
        channel.writeInbound(other);
        assertSame(other, channel.readInbound());
        assertEquals(0, observer.response());
        S32PacketConfirmTransaction accepted = new S32PacketConfirmTransaction(3, (short) 42, true);
        channel.writeInbound(accepted);
        assertSame(accepted, channel.readInbound());
        assertEquals(1, observer.response());
        observer.close();
        channel.finish();
    }

    @Test
    public void recordsARejectedTransaction() {
        ChannelDuplexHandler anchor = new ChannelDuplexHandler();
        EmbeddedChannel channel = new EmbeddedChannel(anchor);
        channel.pipeline()
            .replace(anchor, "packet_handler", new ChannelDuplexHandler());
        ContainerClickConfirmation observer = ContainerClickConfirmation.attach(channel.pipeline(), 7, 9, 1);
        assertNotNull(observer);
        channel.writeOutbound(new C0EPacketClickWindow(7, 9, 0, 1, null, (short) 5));
        channel.readOutbound();
        channel.writeInbound(new S32PacketConfirmTransaction(7, (short) 5, false));
        channel.readInbound();
        assertEquals(-1, observer.response());
        observer.close();
        channel.finish();
    }
}
