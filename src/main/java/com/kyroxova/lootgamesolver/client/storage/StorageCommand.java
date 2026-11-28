package com.kyroxova.lootgamesolver.client.storage;

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

import com.kyroxova.lootgamesolver.client.ClientHooks;
import com.kyroxova.lootgamesolver.config.LootGameSolverConfig;
import com.kyroxova.lootgamesolver.explorer.Explorer;

public final class StorageCommand extends CommandBase {

    private static final String[] SUBCOMMANDS = { "help", "setHome", "setWarp", "setChest", "setStorage", "clearChest",
        "clearChests", "clearWarp", "clearStorage", "enable", "disable", "storageStatus", "resumeLoot",
        "clearCheckpoint", "caveMode" };

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
        return "/lgs help [command] | caveMode [on|off|status] | setHome | setWarp <name> | setChest | setStorage [name] | clearChest | clearChests | clearWarp | clearStorage | enable | disable | storageStatus | resumeLoot | clearCheckpoint";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args == null || args.length == 0) return new ArrayList<String>();
        if (args.length == 1 || (args.length == 2 && "help".equalsIgnoreCase(args[0])))
            return getListOfStringsMatchingLastWord(args, SUBCOMMANDS);
        if (args.length == 2 && "caveMode".equalsIgnoreCase(args[0]))
            return getListOfStringsMatchingLastWord(args, "on", "off", "status");
        return new ArrayList<String>();
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            reply(sender, getCommandUsage(sender));
            return;
        }
        String world = Explorer.worldIdentity(mc);
        String sub = args.length == 0 ? "" : args[0];
        if (handleSimpleCommand(sender, args, sub, world)) return;
        if ("setChest".equalsIgnoreCase(sub) || "clearChest".equalsIgnoreCase(sub)
            || ("setStorage".equalsIgnoreCase(sub) && args.length == 1)) {
            handleTargetCommand(sender, mc, sub, world);
            return;
        }
        if ("storageStatus".equalsIgnoreCase(sub)) {
            reply(
                sender,
                "Storage: "
                    + (world.equals(LootGameSolverConfig.storageWorld) ? LootGameSolverConfig.storageDestination
                        : "not configured for this world")
                    + "; targets: "
                    + LootGameSolverConfig.storageTargets.length
                    + "; preference: "
                    + (LootGameSolverConfig.storageEnabled ? "enabled" : "disabled")
                    + ".");
            return;
        }
        reply(sender, getCommandUsage(sender));
    }

    private boolean handleSimpleCommand(ICommandSender sender, String[] args, String sub, String world) {
        if ("resumeLoot".equalsIgnoreCase(sub)) {
            ClientHooks.requestResumeLoot();
            return true;
        }
        if ("clearCheckpoint".equalsIgnoreCase(sub) || "resetCheckpoint".equalsIgnoreCase(sub)) {
            ClientHooks.requestClearCheckpoint();
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(sub)) {
            handleHelpCommand(sender, args);
            return true;
        }
        if ("caveMode".equalsIgnoreCase(sub)) {
            handleCaveModeCommand(sender, args);
            return true;
        }
        if ("enable".equalsIgnoreCase(sub) || "disable".equalsIgnoreCase(sub)) {
            boolean enable = "enable".equalsIgnoreCase(sub);
            LootGameSolverConfig.setStorageEnabled(enable);
            reply(
                sender,
                "Storage recovery preference " + (enable ? "enabled" : "disabled")
                    + ". Destinations and targets remain saved.");
            return true;
        }
        return handleDestinationCommand(sender, args, sub, world);
    }

    private void handleHelpCommand(ICommandSender sender, String[] args) {
        if (args.length > 1) {
            String detail = commandHelp(args[1]);
            reply(sender, detail == null ? "Unknown command. Use /lgs help to see all commands." : detail);
            return;
        }
        reply(sender, "Commands (use /lgs help <command> for details):");
        for (String command : SUBCOMMANDS) reply(sender, commandHelp(command));
    }

    private void handleCaveModeCommand(ICommandSender sender, String[] args) {
        if (args.length == 2 && ("on".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1]))) {
            LootGameSolverConfig.setCaveMode("on".equalsIgnoreCase(args[1]));
            ClientHooks.requestTravelModeRefresh();
        } else if (args.length != 1 && !(args.length == 2 && "status".equalsIgnoreCase(args[1]))) {
            reply(sender, commandHelp("caveMode"));
            return;
        }
        reply(
            sender,
            "Cave mode " + (LootGameSolverConfig.caveMode ? "enabled: cave travel allowed."
                : "disabled: Explorer travels near the surface."));
    }

    private boolean handleDestinationCommand(ICommandSender sender, String[] args, String sub, String world) {
        if ("clearWarp".equalsIgnoreCase(sub) || "clearChests".equalsIgnoreCase(sub)) {
            if (!world.equals(LootGameSolverConfig.storageWorld)) {
                reply(sender, "No storage configuration for this world.");
                return true;
            }
            if ("clearWarp".equalsIgnoreCase(sub)) {
                LootGameSolverConfig.setStorageDestination(world, "");
                reply(sender, "Storage destination cleared; marked targets remain saved.");
            } else {
                LootGameSolverConfig.setStorageTargets(new String[0]);
                reply(sender, "All marked storage targets cleared.");
            }
            return true;
        }
        if ("setHome".equalsIgnoreCase(sub) && args.length == 1) {
            LootGameSolverConfig.setStorageDestination(world, "/home");
            reply(sender, "Home selected. Mark storage with /lgs setChest and use /lgs enable to allow recovery.");
            return true;
        }
        if (("setWarp".equalsIgnoreCase(sub) || "setStorage".equalsIgnoreCase(sub)) && args.length == 2) {
            if (!validWarpName(args[1])) {
                reply(
                    sender,
                    "Warp names may contain letters, numbers, underscores and hyphens; returnLooting is reserved for the return trip.");
                return true;
            }
            LootGameSolverConfig.setStorageDestination(world, "/warp " + args[1]);
            reply(sender, "Storage warp selected. Mark storage with /lgs setChest and use /lgs enable to allow recovery.");
            return true;
        }
        if ("clearStorage".equalsIgnoreCase(sub)) {
            LootGameSolverConfig.setStorageDestination("", "");
            LootGameSolverConfig.setStorageEnabled(false);
            reply(sender, "Storage configuration cleared.");
            return true;
        }
        return false;
    }

    private void handleTargetCommand(ICommandSender sender, Minecraft mc, String sub, String world) {
        if (LootGameSolverConfig.storageWorld.isEmpty()) LootGameSolverConfig.setStorageDestination(world, "");
        if (!world.equals(LootGameSolverConfig.storageWorld)) {
            reply(sender, "Storage is configured for another world. Clear it before marking targets here.");
            return;
        }
        String target = resolveLookedAtTarget(mc);
        if (target == null) {
            reply(sender, "Look directly at a loaded inventory block or inventory entity.");
            return;
        }
        List<String> targets = new ArrayList<String>(Arrays.asList(LootGameSolverConfig.storageTargets));
        if ("clearChest".equalsIgnoreCase(sub)) {
            boolean removed = targets.remove(target);
            LootGameSolverConfig.setStorageTargets(targets.toArray(new String[0]));
            reply(sender, removed ? "Storage target unmarked." : "That target was not marked.");
            return;
        }
        if (!targets.contains(target)) targets.add(target);
        LootGameSolverConfig.setStorageTargets(targets.toArray(new String[0]));
        reply(sender, "Storage target marked: " + target + ". Use /lgs enable to allow recovery.");
    }

    private static String resolveLookedAtTarget(Minecraft mc) {
        MovingObjectPosition hit = mc.objectMouseOver;
        if (hit == null) return null;
        if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            TileEntity tile = mc.theWorld.getTileEntity(hit.blockX, hit.blockY, hit.blockZ);
            if (tile instanceof IInventory) return "block:" + mc.theWorld.provider.dimensionId
                + ":"
                + hit.blockX
                + ":"
                + hit.blockY
                + ":"
                + hit.blockZ;
        } else if (hit.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
            && hit.entityHit instanceof IInventory) {
                return "entity:" + mc.theWorld.provider.dimensionId + ":" + hit.entityHit.getUniqueID();
            }
        return null;
    }

    public static boolean validWarpName(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,32}")
            && !StorageWarpFeedback.RETURN_WARP.equalsIgnoreCase(value);
    }

    public static String commandHelp(String command) {
        if (command == null) return null;
        switch (command.toLowerCase(java.util.Locale.ROOT)) {
            case "cavemode":
                return "/lgs caveMode [on|off|status] - Allow cave travel or prefer surface routes; structure access remains allowed.";
            case "help":
                return "/lgs help [command] - List commands or describe one command.";
            case "sethome":
                return "/lgs setHome - Use your existing /home command as the storage destination.";
            case "setwarp":
                return "/lgs setWarp <name> - Use /warp <name> as the storage destination; returnLooting is reserved.";
            case "setchest":
                return "/lgs setChest - Mark the inventory block or entity you are looking at for storage.";
            case "setstorage":
                return "/lgs setStorage [name] - With no name, mark the looked-at inventory; with a name, select that storage warp.";
            case "clearchest":
                return "/lgs clearChest - Unmark the storage block or entity you are looking at.";
            case "clearchests":
                return "/lgs clearChests - Remove all marked storage targets for this world.";
            case "clearwarp":
                return "/lgs clearWarp - Clear the home or warp destination, keeping marked targets.";
            case "clearstorage":
                return "/lgs clearStorage - Clear the destination and all targets, and disable recovery.";
            case "enable":
                return "/lgs enable - Allow recovery when a destination and storage targets are configured.";
            case "disable":
                return "/lgs disable - Disable recovery while keeping the destination and targets.";
            case "storagestatus":
                return "/lgs storageStatus - Show the destination, number of targets, and enabled setting.";
            case "resumeloot":
                return "/lgs resumeLoot - Resume saved reward collection when you are back near its location.";
            case "clearcheckpoint":
            case "resetcheckpoint":
                return "/lgs clearCheckpoint - Clear saved loot and room navigation checkpoints.";
            default:
                return null;
        }
    }

    private static void reply(ICommandSender sender, String message) {
        sender.addChatMessage(new ChatComponentText("\u00a7e[LootGameSolver] " + message));
    }
}
