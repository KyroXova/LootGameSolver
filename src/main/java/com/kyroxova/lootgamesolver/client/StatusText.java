package com.kyroxova.lootgamesolver.client;

import java.util.Locale;

public final class StatusText {

    private static final int MAX_LENGTH = 24;

    private StatusText() {}

    public static String explorer(String detail) {
        if (detail == null || detail.isEmpty()) return "Ready";
        String text = detail.toLowerCase(Locale.ROOT);

        if (text.contains("swim") || text.contains("shore")) return "Swimming to Shore";
        if (text.contains("full") && text.contains("rewards remain")) return "Inventory Full";
        if (text.contains("backpack") || text.contains("packing")) return "Packing Backpack Loot";
        if (text.contains("chest") || text.contains("reward")) {
            if (text.contains("jump") || text.contains("clear of")) return "Leaving Chests";
            if (text.contains("waiting") || text.contains("finding")) return "Finding Chests";
            if (text.contains("moving") || text.contains("position")) return "Moving to Chests";
            if (text.contains("opening")) return "Opening Chest";
            if (text.contains("collect")) return "Collecting Loot";
            if (text.contains("stopped")) return "Looting Stopped";
            return "Checking Chests";
        }
        if (text.contains("trash")) return "Clearing Inventory";
        if (text.contains("checkpoint") && text.contains("cleared")) return "Checkpoint Done";

        if (text.contains("stage reveal failed")) return "Stage Failed";
        if (text.contains("board reveal failed")) return "Board Failed";
        if (text.contains("activator failed") || text.contains("could not click floor activator"))
            return "Activator Failed";
        if (text.contains("activator did not open")) return "Activator Stalled";
        if (text.contains("opening floor activator")) return "Opening Activator";
        if (text.contains("opening lootgames master")) return "Opening Master";
        if (text.contains("revealing")) return "Revealing Board";
        if (text.contains("stage")) return "Opening Stage";
        if (text.contains("reopening")) return "Reopening Board";

        if (text.contains("searching for the next structure") || text.contains("next structure")
            || text.contains("exploring for structures")) return "Searching Structures";
        if (text.contains("puzzle solved") || text.contains("stage complete")) return "Stage Complete";
        if (text.contains("puzzle could not be located") || text.contains("opened puzzle could not"))
            return "Holding for Puzzle";
        if (text.contains("puzzle did not open")) return "Puzzle Stalled";
        if (text.contains("reported a loss") || text.contains("lost")) return "Puzzle Lost";
        if (text.contains("could not reach the board center") || text.contains("center; explorer held"))
            return "Holding at Center";
        if (text.contains("pre-routing") || text.contains("prerouting")) return "Pre-routing to Puzzle";
        if (text.contains("found nearby lootgame")) return "Puzzle Found";
        if (text.contains("arrived at lootgame") || text.contains("arrived beside lootgame")) return "Puzzle Ready";
        if (text.contains("opening puzzle") || text.contains("waiting for lootgames to create"))
            return "Opening Puzzle";
        if (text.contains("resuming")) return "Resuming Puzzle";
        if (text.contains("solving")) return "Solving Puzzle";
        if (text.contains("checking room board") || text.contains("checking board")) return "Checking Board";
        if (text.contains("target no longer loaded")) return "Target Unloaded";
        if (text.contains("returned to access entrance")) return "Returned Outside";
        if (text.contains("no usable tool") || text.contains("needs cobblestone")
            || text.contains("needs dirt")
            || text.contains("keep a usable pickaxe")) return "Mining Tool Needed";
        if (text.contains("paused")) {
            if (text.contains("movement keys")) return "Paused by Keys";
            if (text.contains("menu")) return "Paused in Menu";
            if (text.contains("damage")) return "Paused from Damage";
            if (text.contains("flight")) return "Paused in Flight";
            return "Paused";
        }
        if (text.contains("save") && (text.contains("could not") || text.contains("failed"))) return "Save Error";
        if (text.contains("low air")) return "Low Air Underwater";
        if (text.contains("baritone") && text.contains("busy")) return "Baritone Busy";
        if (text.contains("baritone") && text.contains("stopped")) return "Search Stalled";
        if (text.contains("no structures")) return "No Structures Found";
        if (text.contains("entrance")
            && (text.contains("no safe") || text.contains("no verified") || text.contains("blocked")))
            return "Entrance Blocked";
        if (text.contains("blocked") || text.contains("obstructed")
            || text.contains("no safe")
            || text.contains("no verified route")
            || text.contains("no open room exit")
            || text.contains("no walkable")
            || text.contains("no longer safe")
            || text.contains("still inside room")
            || text.contains("stopped")) {
            if (text.contains("exit") || text.contains("return") || text.contains("room")) return "Exit Blocked";
            if (text.contains("dig") || text.contains("tunnel") || text.contains("mining")) return "Tunnel Blocked";
            if (text.contains("center")) return "Center Blocked";
            return "Route Blocked";
        }
        if (text.contains("waiting") && text.contains("tool")) return "Waiting for Tool";
        if (text.contains("waiting") && text.contains("land")) return "Waiting for Land";
        if (text.contains("waiting") && text.contains("placement")) return "Waiting for Placement";
        if (text.contains("waiting") && text.contains("terrain")) return "Waiting for Chunks";
        if (text.contains("waiting") && text.contains("safe route")) return "Planning Route";
        if (text.contains("waiting")) return "Waiting";
        if (text.contains("tool") && (text.contains("inventory") || text.contains("prepare"))) return "Preparing Tool";
        if (text.contains("return step") && (text.contains("place") || text.contains("block"))) return "Placing Return Block";
        if (text.contains("exit") || text.contains("tunnel")
            || text.contains("return")
            || text.contains("saved access route")
            || text.contains("saved exit")) {
            if (text.contains("dig") || text.contains("mining")) return "Digging to Surface";
            if (text.startsWith("finding") || text.startsWith("checking")
                || text.startsWith("planning")
                || text.startsWith("continuing")
                || text.startsWith("rejoining")) return "Planning Surface Exit";
            return "Ascending to Surface";
        }
        if (text.contains("board center") || text.contains("room center") || text.contains("lootgame; opening"))
            return "Moving to Board";
        if (text.contains("entrance")
            && (text.startsWith("walking") || text.startsWith("moving") || text.startsWith("following")))
            return "Moving to Entrance";
        if (text.contains("aiming")) return "Aiming Mining Tool";
        if (text.contains("dig") || text.contains("stair") || text.contains("mining")) return "Digging Tunnel";
        if (text.startsWith("finding") || text.startsWith("checking")
            || text.startsWith("planning")
            || text.startsWith("continuation")
            || text.startsWith("next route")
            || text.startsWith("replanning")
            || text.startsWith("rerouted")) return "Planning Route";
        if (text.contains("search") || text.contains("scan") || text.contains("terrain")) return "Searching Structures";
        if (text.startsWith("walk") || text.startsWith("following")
            || text.startsWith("moving")
            || text.startsWith("aligning")
            || text.startsWith("centering")
            || text.startsWith("exploring")) return "Following Route";
        return shorten(detail);
    }

    public static String solver(String detail) {
        if (detail == null || detail.isEmpty()) return "Ready";
        String text = detail.toLowerCase(Locale.ROOT);
        if (text.contains("clearing foliage") || text.contains("foliage")) return "Clearing Foliage";
        if (text.contains("solved") || text.contains("stage complete")
            || text.contains("win")
            || text.contains("confirmed") && text.contains("next stage")) return "Stage Complete";
        if (text.contains("watching") || text.contains("sequence")) return "Watching Sequence";
        if (text.contains("waiting") && (text.contains("board") || text.contains("update"))) return "Waiting for Board";
        if (text.contains("setting") || text.contains("number")) {
            return text.contains("navigat") ? "Moving to Number" : "Setting Number";
        }
        if (text.contains("reveal")) {
            return text.contains("navigat") ? "Moving to Reveal" : "Revealing Tile";
        }
        if (text.contains("flag")) {
            return text.contains("navigat") ? "Moving to Flag" : "Flagging Tile";
        }
        if (text.contains("navigating")) return "Moving to Tile";
        if (text.contains("disabled")) return "Solver Disabled";
        if (text.contains("no supported") || text.contains("no board")) return "No Board Found";
        if (text.contains("guess") || text.contains("probabilistic")) return "Waiting for Guess";
        if (text.contains("timed out") || text.contains("no acknowledgement")) return "Action Timed Out";
        if (text.contains("waiting")) return "Waiting";
        return shorten(detail);
    }

    private static String shorten(String detail) {
        String text = detail.trim()
            .replaceAll("\\s+", " ");
        if (text.length() <= MAX_LENGTH) return text;
        int end = text.lastIndexOf(' ', MAX_LENGTH - 3);
        return text.substring(0, end > 0 ? end : MAX_LENGTH - 3) + "...";
    }
}
