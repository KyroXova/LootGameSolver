package com.kyroxova.lootgamesolver.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MovingObjectPosition;

import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.explorer.Explorer;

public final class StorageCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "lootgamesolver";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("lgs");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/lgs setHome | setStorage <warp> | setChest | clearStorage | storageStatus";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null || args.length == 0) {
            reply(sender, getCommandUsage(sender));
            return;
        }
        String world = Explorer.worldIdentity(mc);
        if ("setHome".equalsIgnoreCase(args[0]) && args.length == 1) {
            LootGameSolverConfig.setStorageDestination(world, "/home");
            reply(
                sender,
                "Home selected. Mark your storage with /lgs setChest; automatic recovery is not enabled yet.");
        } else if ("setStorage".equalsIgnoreCase(args[0]) && args.length == 2) {
            if (!validWarpName(args[1])) {
                reply(sender, "Warp names may contain only letters, numbers, underscores and hyphens.");
                return;
            }
            LootGameSolverConfig.setStorageDestination(world, "/warp " + args[1]);
            reply(
                sender,
                "Storage warp selected. Mark storage with /lgs setChest; automatic recovery is not enabled yet.");
        } else
            if ("setChest".equalsIgnoreCase(args[0]) || ("setStorage".equalsIgnoreCase(args[0]) && args.length == 1)) {
                if (!world.equals(LootGameSolverConfig.storageWorld)
                    || LootGameSolverConfig.storageDestination.isEmpty()) {
                    reply(sender, "First select a home or storage warp in this world.");
                    return;
                }
                MovingObjectPosition hit = mc.objectMouseOver;
                String target = null;
                if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
                    TileEntity tile = mc.theWorld.getTileEntity(hit.blockX, hit.blockY, hit.blockZ);
                    if (tile instanceof IInventory) target = "block:" + mc.theWorld.provider.dimensionId
                        + ":"
                        + hit.blockX
                        + ":"
                        + hit.blockY
                        + ":"
                        + hit.blockZ;
                } else if (hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                    && hit.entityHit instanceof IInventory) {
                        target = "entity:" + mc.theWorld.provider.dimensionId + ":" + hit.entityHit.getUniqueID();
                    }
                if (target == null) {
                    reply(sender, "Look directly at a loaded inventory block or inventory entity.");
                    return;
                }
                List<String> targets = new ArrayList<String>(Arrays.asList(LootGameSolverConfig.storageTargets));
                if (!targets.contains(target)) targets.add(target);
                LootGameSolverConfig.setStorageTargets(targets.toArray(new String[0]));
                reply(sender, "Storage target marked: " + target + ". Automatic recovery is not enabled yet.");
            } else if ("clearStorage".equalsIgnoreCase(args[0])) {
                LootGameSolverConfig.setStorageDestination("", "");
                reply(sender, "Storage configuration cleared.");
            } else if ("storageStatus".equalsIgnoreCase(args[0])) {
                reply(
                    sender,
                    "Storage: "
                        + (world.equals(LootGameSolverConfig.storageWorld) ? LootGameSolverConfig.storageDestination
                            : "not configured for this world")
                        + "; targets: "
                        + LootGameSolverConfig.storageTargets.length
                        + ". Automatic recovery is not enabled yet.");
            } else reply(sender, getCommandUsage(sender));
    }

    static boolean validWarpName(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,32}");
    }

    private static void reply(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText("\u00a7e[LootGameSolver] " + message));
    }
}
