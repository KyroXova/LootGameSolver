package com.kyroxova.lootgamesolver.client;

import java.util.Locale;

/** Compact, consistent HUD labels; detailed state remains available internally. */
final class StatusText {

    private static final int MAX_LENGTH = 24;

    private StatusText() {}

    static String explorer(String detail) {
        if (detail == null || detail.isEmpty()) return "Idle";
        String text = detail.toLowerCase(Locale.ROOT);

        if (text.contains("searching for the next structure") || text.contains("next structure"))
            return "Next Structure";
        if (text.contains("puzzle solved") || text.contains("stage complete")) return "Stage Complete";
        if (text.contains("puzzle could not be located")) return "Puzzle: Missing";
        if (text.contains("puzzle did not open")) return "Puzzle: Missing";
        if (text.contains("found nearby lootgame")) return "Puzzle: Found";
        if (text.contains("arrived at lootgame") || text.contains("arrived beside lootgame")) return "Puzzle: Ready";
        if (text.contains("opening puzzle") || text.contains("waiting for lootgames to create")) return "Puzzle: Open";
        if (text.contains("target no longer loaded")) return "Target: Unloaded";
        if (text.contains("returned to access entrance")) return "Walk: Exit";
        if (text.contains("no usable tool") || text.contains("needs cobblestone")
            || text.contains("needs dirt")
            || text.contains("keep a usable pickaxe")) return "Tool: Needed";
        if (text.contains("paused")) {
            if (text.contains("movement keys")) return "Paused: Keys";
            if (text.contains("menu")) return "Paused: Menu";
            if (text.contains("damage")) return "Paused: Damage";
            if (text.contains("flight")) return "Paused: Flight";
            return "Paused";
        }
        if (text.contains("save") && (text.contains("could not") || text.contains("failed"))) return "Save: Error";
        if (text.contains("low air")) return "Stop: Low Air";
        if (text.contains("entrance")
            && (text.contains("no safe") || text.contains("no verified") || text.contains("blocked")))
            return "Entrance: Blocked";
        if (text.contains("blocked") || text.contains("obstructed")
            || text.contains("no safe")
            || text.contains("no verified route")
            || text.contains("no open room exit")
            || text.contains("no walkable")
            || text.contains("no longer safe")
            || text.contains("still inside room")
            || text.contains("stopped")) return "Route: Blocked";
        if (text.contains("waiting") && text.contains("tool")) return "Waiting: Tool";
        if (text.contains("waiting") && text.contains("land")) return "Waiting: Landing";
        if (text.contains("waiting") && text.contains("placement")) return "Waiting: Place";
        if (text.contains("waiting") && text.contains("terrain")) return "Waiting: Chunks";
        if (text.contains("tool") && (text.contains("inventory") || text.contains("prepare"))) return "Tool: Prepare";
        if (text.contains("return step") && (text.contains("place") || text.contains("block"))) return "Place: Return";
        if (text.contains("exit") || text.contains("tunnel")
            || text.contains("return")
            || text.contains("saved access route")
            || text.contains("saved exit")) {
            if (text.contains("dig") || text.contains("mining")) return "Dig: Exit";
            if (text.startsWith("finding") || text.startsWith("checking")
                || text.startsWith("planning")
                || text.startsWith("continuing")
                || text.startsWith("rejoining")) return "Waiting: Reroute";
            return "Walk: Exit";
        }
        if (text.contains("board center") || text.contains("room center") || text.contains("lootgame; opening"))
            return "Walk: Center";
        if (text.contains("entrance")
            && (text.startsWith("walking") || text.startsWith("moving") || text.startsWith("following")))
            return "Walk: Entrance";
        if (text.contains("aiming")) return "Dig: Aim";
        if (text.contains("dig") || text.contains("stair") || text.contains("mining")) return "Dig: Access";
        if (text.startsWith("finding") || text.startsWith("checking")
            || text.startsWith("planning")
            || text.startsWith("continuing")
            || text.startsWith("replanning")
            || text.startsWith("rerouted")) return "Waiting: Reroute";
        if (text.contains("search") || text.contains("scan") || text.contains("terrain")) return "Next Structure";
        if (text.startsWith("walk") || text.startsWith("following")
            || text.startsWith("moving")
            || text.startsWith("aligning")
            || text.startsWith("centering")
            || text.startsWith("exploring")) return "Walk: Forward";
        return shorten(detail);
    }

    static String solver(String detail) {
        if (detail == null || detail.isEmpty()) return "Ready";
        String text = detail.toLowerCase(Locale.ROOT);
        if (text.contains("solved") || text.contains("stage complete")
            || text.contains("win")
            || text.contains("confirmed") && text.contains("next stage")) return "Stage Complete";
        if (text.contains("watching") || text.contains("sequence")) return "Stage: Watch";
        if (text.contains("waiting") && (text.contains("board") || text.contains("update"))) return "Waiting: Board";
        if (text.contains("navigating")) return "Walk: Tile";
        if (text.contains("reveal")) return "Solve: Reveal";
        if (text.contains("flag")) return "Solve: Flag";
        if (text.contains("setting")) return "Solve: Number";
        if (text.contains("disabled")) return "Disabled";
        if (text.contains("no supported") || text.contains("no board")) return "No Board";
        if (text.contains("guess") || text.contains("probabilistic")) return "Waiting: Guess";
        if (text.contains("timed out") || text.contains("no acknowledgement")) return "Stop: No Ack";
        if (text.contains("waiting")) return "Waiting";
        return shorten(detail);
    }

    private static String shorten(String detail) {
        if (detail.length() <= MAX_LENGTH) return detail;
        return detail.substring(0, MAX_LENGTH - 1) + "…";
    }
}
