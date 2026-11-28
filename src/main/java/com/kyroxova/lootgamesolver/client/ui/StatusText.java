package com.kyroxova.lootgamesolver.client.ui;

import java.util.Locale;

public final class StatusText {

    private static final int MAX_LENGTH = 24;

    private StatusText() {}

    public static String explorer(String detail) {
        if (detail == null || detail.isEmpty()) return "Ready";
        String text = detail.toLowerCase(Locale.ROOT);
        String result = explorerLootStatus(text);
        if (result == null) result = explorerStageStatus(text);
        if (result == null) result = explorerPuzzleStatus(text);
        if (result == null) result = explorerBlockedOrWaitStatus(text);
        if (result == null) result = explorerNavigationStatus(text);
        return result != null ? result : shorten(detail);
    }

    private static String explorerLootStatus(String text) {
        if (containsAny(text, "swim", "shore")) return "Swimming to Shore";
        if (text.contains("full") && text.contains("rewards remain")) return "Bags Full or Unavailable";
        if (containsAny(text, "backpack", "packing")) return "Packing Backpack Loot";
        if (containsAny(text, "chest", "reward")) {
            if (containsAny(text, "jump", "clear of")) return "Leaving Chests";
            if (containsAny(text, "waiting", "finding")) return "Finding Chests";
            if (containsAny(text, "moving", "position")) return "Moving to Chests";
            if (text.contains("opening")) return "Opening Chest";
            if (text.contains("collect")) return "Collecting Loot";
            if (text.contains("stopped")) return "Looting Stopped";
            return "Checking Chests";
        }
        if (text.contains("trash")) return "Clearing Inventory";
        if (containsAny(text, "foliage", "obstruction")) return "Clearing Foliage";
        if (text.contains("checkpoint") && text.contains("cleared")) return "Checkpoint Done";
        return null;
    }

    private static String explorerStageStatus(String text) {
        if (text.contains("stage reveal failed")) return "Stage Failed";
        if (text.contains("board reveal failed")) return "Board Failed";
        if (containsAny(text, "activator failed", "could not click floor activator")) return "Activator Failed";
        if (text.contains("activator did not open")) return "Activator Stalled";
        if (text.contains("opening floor activator")) return "Opening Activator";
        if (text.contains("opening lootgames master")) return "Opening Master";
        if (text.contains("revealing")) return "Revealing Board";
        if (text.contains("stage")) return "Opening Stage";
        if (text.contains("reopening")) return "Reopening Board";
        return null;
    }

    private static String explorerPuzzleStatus(String text) {
        if (containsAny(text, "searching for the next structure", "next structure", "exploring for structures"))
            return "Searching Structures";
        if (containsAny(text, "puzzle solved", "stage complete")) return "Stage Complete";
        if (containsAny(text, "puzzle could not be located", "opened puzzle could not")) return "Holding for Puzzle";
        if (text.contains("puzzle did not open")) return "Puzzle Stalled";
        if (containsAny(text, "reported a loss", "lost")) return "Puzzle Lost";
        if (containsAny(text, "could not reach the board center", "center; explorer held")) return "Holding at Center";
        if (containsAny(text, "pre-routing", "prerouting")) return "Pre-routing to Puzzle";
        if (text.contains("found nearby lootgame")) return "Puzzle Found";
        if (containsAny(text, "arrived at lootgame", "arrived beside lootgame")) return "Puzzle Ready";
        if (containsAny(text, "opening puzzle", "waiting for lootgames to create")) return "Opening Puzzle";
        if (text.contains("resuming")) return "Resuming Puzzle";
        if (text.contains("solving")) return "Solving Puzzle";
        if (containsAny(text, "checking room board", "checking board")) return "Checking Board";
        if (text.contains("target no longer loaded")) return "Target Unloaded";
        if (text.contains("returned to access entrance")) return "Returned Outside";
        if (containsAny(text, "no usable tool", "needs cobblestone", "needs dirt", "keep a usable pickaxe"))
            return "Mining Tool Needed";
        return null;
    }

    private static String explorerBlockedOrWaitStatus(String text) {
        if (text.contains("paused")) return explorerPausedStatus(text);
        if (text.contains("save") && containsAny(text, "could not", "failed")) return "Save Error";
        if (text.contains("low air")) return "Low Air Underwater";
        if (text.contains("baritone") && text.contains("busy")) return "Baritone Busy";
        if (text.contains("baritone") && text.contains("stopped")) return "Search Stalled";
        if (text.contains("no structures")) return "No Structures Found";
        if (text.contains("entrance") && containsAny(text, "no safe", "no verified", "blocked"))
            return "Entrance Blocked";
        if (containsAny(
            text,
            "blocked",
            "obstructed",
            "no safe",
            "no verified route",
            "no open room exit",
            "no walkable",
            "no longer safe",
            "still inside room",
            "stopped")) {
            if (containsAny(text, "exit", "return", "room")) return "Exit Blocked";
            if (containsAny(text, "dig", "tunnel", "mining")) return "Tunnel Blocked";
            if (text.contains("center")) return "Center Blocked";
            return "Route Blocked";
        }
        if (text.contains("waiting")) {
            if (text.contains("tool")) return "Waiting for Tool";
            if (text.contains("land")) return "Waiting for Land";
            if (text.contains("placement")) return "Waiting for Placement";
            if (text.contains("terrain")) return "Waiting for Chunks";
            if (text.contains("safe route")) return "Planning Route";
            return "Waiting";
        }
        return null;
    }

    private static String explorerPausedStatus(String text) {
        if (text.contains("movement keys")) return "Paused by Keys";
        if (text.contains("menu")) return "Paused in Menu";
        if (text.contains("damage")) return "Paused from Damage";
        if (text.contains("flight")) return "Paused in Flight";
        return "Paused";
    }

    private static String explorerNavigationStatus(String text) {
        if (text.contains("tool") && containsAny(text, "inventory", "prepare")) return "Preparing Tool";
        if (text.contains("return step") && containsAny(text, "place", "block")) return "Placing Return Block";
        if (containsAny(text, "exit", "tunnel", "return", "saved access route", "saved exit")) {
            if (containsAny(text, "dig", "mining")) return "Digging to Surface";
            if (startsWithAny(text, "finding", "checking", "planning", "continuing", "rejoining"))
                return "Planning Surface Exit";
            return "Ascending to Surface";
        }
        if (containsAny(text, "board center", "room center", "lootgame; opening")) return "Moving to Board";
        if (text.contains("entrance") && startsWithAny(text, "walking", "moving", "following"))
            return "Moving to Entrance";
        if (text.contains("aiming")) return "Aiming Mining Tool";
        if (containsAny(text, "dig", "stair", "mining")) return "Digging Tunnel";
        if (startsWithAny(text, "finding", "checking", "planning", "continuation", "next route", "replanning",
            "rerouted")) return "Planning Route";
        if (containsAny(text, "search", "scan", "terrain")) return "Searching Structures";
        if (startsWithAny(text, "walk", "following", "moving", "aligning", "centering", "exploring"))
            return "Following Route";
        return null;
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) return true;
        }
        return false;
    }

    private static boolean startsWithAny(String text, String... prefixes) {
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) return true;
        }
        return false;
    }

    public static String solver(String detail) {
        if (detail == null || detail.isEmpty()) return "Ready";
        String text = detail.toLowerCase(Locale.ROOT);
        if (text.contains("foliage")) return "Clearing Foliage";
        if (containsAny(text, "solved", "stage complete", "win")
            || text.contains("confirmed") && text.contains("next stage")) return "Stage Complete";
        if (containsAny(text, "watching", "sequence")) return "Watching Sequence";
        if (text.contains("waiting") && containsAny(text, "board", "update")) return "Waiting for Board";
        String actionStatus = solverActionStatus(text);
        if (actionStatus != null) return actionStatus;
        if (text.contains("disabled")) return "Solver Disabled";
        if (containsAny(text, "no supported", "no board")) return "No Board Found";
        if (containsAny(text, "guess", "probabilistic")) return "Waiting for Guess";
        if (containsAny(text, "timed out", "no acknowledgement")) return "Action Timed Out";
        if (text.contains("waiting")) return "Waiting";
        return shorten(detail);
    }

    private static String solverActionStatus(String text) {
        if (containsAny(text, "setting", "number")) return text.contains("navigat") ? "Moving to Number" : "Setting Number";
        if (text.contains("reveal")) return text.contains("navigat") ? "Moving to Reveal" : "Revealing Tile";
        if (text.contains("flag")) return text.contains("navigat") ? "Moving to Flag" : "Flagging Tile";
        if (text.contains("navigating")) return "Moving to Tile";
        return null;
    }

    private static String shorten(String detail) {
        String text = detail.trim()
            .replaceAll("\\s+", " ");
        if (text.length() <= MAX_LENGTH) return text;
        int end = text.lastIndexOf(' ', MAX_LENGTH - 3);
        return text.substring(0, end > 0 ? end : MAX_LENGTH - 3) + "...";
    }
}
