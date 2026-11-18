package com.kyroxova.lootgamesolver.client;

import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;

/** Observes, without consuming, the server response for one container click. */
final class ContainerClickConfirmation extends ChannelDuplexHandler {

    private static final String NAME = "lootgamesolver:container-confirmation";
    private static final int UNSET = Integer.MIN_VALUE;
    private final int window, slot, mode;
    private final AtomicInteger action = new AtomicInteger(UNSET);
    private final AtomicInteger response = new AtomicInteger();
    private ChannelPipeline pipeline;

    private ContainerClickConfirmation(int window, int slot, int mode) {
        this.window = window;
        this.slot = slot;
        this.mode = mode;
    }

    static ContainerClickConfirmation attach(Minecraft mc, int window, int slot, int mode) {
        if (mc == null || mc.getNetHandler() == null
            || mc.getNetHandler()
                .getNetworkManager() == null
            || mc.getNetHandler()
                .getNetworkManager()
                .channel() == null)
            return null;
        return attach(
            mc.getNetHandler()
                .getNetworkManager()
                .channel()
                .pipeline(),
            window,
            slot,
            mode);
    }

    static ContainerClickConfirmation attach(ChannelPipeline pipeline, int window, int slot, int mode) {
        if (pipeline == null || pipeline.get("packet_handler") == null || pipeline.get(NAME) != null) return null;
        ContainerClickConfirmation observer = new ContainerClickConfirmation(window, slot, mode);
        try {
            pipeline.addBefore("packet_handler", NAME, observer);
            observer.pipeline = pipeline;
            return observer;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
        if (message instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow click = (C0EPacketClickWindow) message;
            if (click.func_149548_c() == window && click.func_149544_d() == slot && click.func_149542_h() == mode)
                action.compareAndSet(UNSET, click.func_149547_f());
        }
        context.write(message, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
        if (message instanceof S32PacketConfirmTransaction) {
            S32PacketConfirmTransaction reply = (S32PacketConfirmTransaction) message;
            int expected = action.get();
            if (expected != UNSET && reply.func_148889_c() == window && reply.func_148890_d() == expected)
                response.compareAndSet(0, reply.func_148888_e() ? 1 : -1);
        }
        context.fireChannelRead(message);
    }

    int response() {
        return response.get();
    }

    void close() {
        try {
            if (pipeline != null && pipeline.context(this) != null) pipeline.remove(this);
        } catch (RuntimeException ignored) {}
        pipeline = null;
    }
}
