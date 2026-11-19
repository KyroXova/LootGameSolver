package com.kyroxova.lootgamesolver.client.storage;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.client.Minecraft;

import cpw.mods.fml.relauncher.Side;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

/** Optional integration with the same server request used by BogoSorter's drop-off button. */
public final class BogoDropOffBridge {

    private BogoDropOffBridge() {}

    public static boolean available() {
        try {
            Class.forName("com.cleanroommc.bogosorter.common.network.CDropOff");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    public static boolean safeToRequest(Minecraft mc) {
        if (mc == null || mc.thePlayer == null) return false;
        int first = 0;
        if (mc.isSingleplayer()) {
            try {
                Class<?> config = Class.forName("com.cleanroommc.bogosorter.common.config.BogoSorterConfig");
                Object dropOff = config.getField("dropOff")
                    .get(null);
                if (!dropOff.getClass()
                    .getField("enableHotbarDropOff")
                    .getBoolean(dropOff)) first = 9;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
                return false;
            }
        }
        for (int slot = first; slot < 36; slot++)
            if (InventoryProtection.essential(mc.thePlayer.inventory.getStackInSlot(slot))) return false;
        int reserve = TrashCleanup.findBuildingReserveSlot(mc);
        if (reserve >= first && reserve < 36) return false;
        return true;
    }

    public static final class Session {

        private final AtomicInteger response = new AtomicInteger();
        private ChannelPipeline pipeline;
        private final String handlerName = "lootgamesolver:dropoff-observer";

        boolean start() {
            try {
                Class<?> handler = Class.forName("com.cleanroommc.bogosorter.common.network.NetworkHandler");
                Object wrapper = handler.getField("network")
                    .get(null);
                Field channelsField = wrapper.getClass()
                    .getDeclaredField("channels");
                channelsField.setAccessible(true);
                Object channel = ((Map<?, ?>) channelsField.get(wrapper)).get(Side.CLIENT);
                Field codecField = wrapper.getClass()
                    .getDeclaredField("packetCodec");
                codecField.setAccessible(true);
                Object codec = codecField.get(wrapper);
                if (!(channel instanceof io.netty.channel.Channel)
                    || !(codec instanceof io.netty.channel.ChannelHandler)) return false;
                if (!observe(((io.netty.channel.Channel) channel).pipeline(), (io.netty.channel.ChannelHandler) codec))
                    return false;
                if (request()) return true;
                close();
                return false;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                close();
                return false;
            }
        }

        boolean observe(ChannelPipeline observed, io.netty.channel.ChannelHandler codec) {
            ChannelHandlerContext context = observed.context(codec);
            if (context == null || observed.get(handlerName) != null) return false;
            pipeline = observed;
            pipeline.addAfter(context.name(), handlerName, new ChannelInboundHandlerAdapter() {

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
                    String type = message.getClass()
                        .getName();
                    if (type.equals("com.cleanroommc.bogosorter.common.network.SDropOffMessage")) response.set(1);
                    else if (type.equals("com.cleanroommc.bogosorter.common.network.SDropOffThrottled"))
                        response.set(-1);
                    ctx.fireChannelRead(message);
                }
            });
            return true;
        }

        int response() {
            return response.get();
        }

        void close() {
            if (pipeline != null && pipeline.get(handlerName) != null) pipeline.remove(handlerName);
            pipeline = null;
        }
    }

    public static boolean request() {
        try {
            Class<?> packetType = Class.forName("com.cleanroommc.bogosorter.common.network.IPacket");
            Class<?> dropOff = Class.forName("com.cleanroommc.bogosorter.common.network.CDropOff");
            Class<?> network = Class.forName("com.cleanroommc.bogosorter.common.network.NetworkHandler");
            Object packet = dropOff.getMethod("fromClientPreference")
                .invoke(null);
            if (packet == null || !packetType.isInstance(packet)) return false;
            network.getMethod("sendToServer", packetType)
                .invoke(null, packet);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return false;
        }
    }
}
