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
        return "/lgs help | setHome | setWarp <name> | setChest | clearChest | clearChests | clearWarp | clearStorage | enable | disable | storageStatus";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            reply(sender, getCommandUsage(sender));
            return;
        }
        String world = Explorer.worldIdentity(mc);
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            reply(
                sender,
                "setHome: select /home; setWarp <name>: select /warp <name>; setChest: mark the inventory you are looking at.");
            reply(
                sender,
                "clearChest: unmark the target; clearChests: remove all targets; clearWarp: remove the destination; clearStorage: clear everything.");
            reply(
                sender,
                "enable/disable: save the recovery preference; storageStatus: show settings. Automatic recovery is not implemented yet.");
        } else if ("enable".equalsIgnoreCase(args[0]) || "disable".equalsIgnoreCase(args[0])) {
            boolean enable = "enable".equalsIgnoreCase(args[0]);
            LootGameSolverConfig.setStorageEnabled(enable);
            reply(
                sender,
                "Storage recovery preference " + (enable ? "enabled" : "disabled")
                    + ". Automatic recovery is not implemented yet; destinations and targets remain saved.");
        } else if ("clearWarp".equalsIgnoreCase(args[0])) {
            if (!world.equals(LootGameSolverConfig.storageWorld)) {
                reply(sender, "No storage configuration for this world.");
                return;
            }
            LootGameSolverConfig.setStorageDestination(world, "");
            reply(sender, "Storage destination cleared; marked targets remain saved.");
        } else if ("clearChests".equalsIgnoreCase(args[0])) {
            if (!world.equals(LootGameSolverConfig.storageWorld)) {
                reply(sender, "No storage configuration for this world.");
                return;
            }
            LootGameSolverConfig.setStorageTargets(new String[0]);
            reply(sender, "All marked storage targets cleared.");
        } else if ("setHome".equalsIgnoreCase(args[0]) && args.length == 1) {
            LootGameSolverConfig.setStorageDestination(world, "/home");
            reply(
                sender,
                "Home selected. Mark your storage with /lgs setChest; automatic recovery is not enabled yet.");
        } else
            if (("setWarp".equalsIgnoreCase(args[0]) || "setStorage".equalsIgnoreCase(args[0])) && args.length == 2) {
                if (!validWarpName(args[1])) {
                    reply(sender, "Warp names may contain only letters, numbers, underscores and hyphens.");
                    return;
                }
                LootGameSolverConfig.setStorageDestination(world, "/warp " + args[1]);
                reply(
                    sender,
                    "Storage warp selected. Mark storage with /lgs setChest; automatic recovery is not enabled yet.");
            } else if ("setChest".equalsIgnoreCase(args[0]) || "clearChest".equalsIgnoreCase(args[0])
                || ("setStorage".equalsIgnoreCase(args[0]) && args.length == 1)) {
                    if (LootGameSolverConfig.storageWorld.isEmpty())
                        LootGameSolverConfig.setStorageDestination(world, "");
                    if (!world.equals(LootGameSolverConfig.storageWorld)) {
                        reply(sender, "Storage is configured for another world. Clear it before marking targets here.");
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
                    if ("clearChest".equalsIgnoreCase(args[0])) {
                        boolean removed = targets.remove(target);
                        LootGameSolverConfig.setStorageTargets(targets.toArray(new String[0]));
                        reply(sender, removed ? "Storage target unmarked." : "That target was not marked.");
                        return;
                    }
                    if (!targets.contains(target)) targets.add(target);
                    LootGameSolverConfig.setStorageTargets(targets.toArray(new String[0]));
                    reply(sender, "Storage target marked: " + target + ". Automatic recovery is not enabled yet.");
                } else if ("clearStorage".equalsIgnoreCase(args[0])) {
                    LootGameSolverConfig.setStorageDestination("", "");
                    LootGameSolverConfig.setStorageEnabled(false);
                    reply(sender, "Storage configuration cleared.");
                } else if ("storageStatus".equalsIgnoreCase(args[0])) {
                    reply(
                        sender,
                        "Storage: "
                            + (world.equals(LootGameSolverConfig.storageWorld) ? LootGameSolverConfig.storageDestination
                                : "not configured for this world")
                            + "; targets: "
                            + LootGameSolverConfig.storageTargets.length
                            + "; preference: "
                            + (LootGameSolverConfig.storageEnabled ? "enabled" : "disabled")
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
