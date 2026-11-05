# LootGameSolver - 2.1.0 --> 3.0.0

**Full Changelog**: https://github.com/KyroXova/LootGameSolver/compare/2.1.0...3.0.0

## Sudoku

- Keep correct-number holograms visible when a player enters an incorrect or conflicting digit.
- Solve from the original clues while tracking player entries separately, allowing incorrect entries to be corrected.
- Correct digit cycling through zero when restarting from an incorrect number.
- Wait for updates to the selected tile before sending another click, with retries for unconfirmed interactions.
- Discard stale actions when the puzzle changes between rounds.
- Remove redundant completion submissions that produced repeated acknowledgement and incomplete-solution messages.
- Cache solved puzzles while their fixed clues remain unchanged.

## HUD and controls

- Keep the solver HUD visible when holograms are toggled inside a puzzle.
- Hide the exploration display with the hologram toggle outside a puzzle.
- Show exploration information in the original bottom-right panel, with a compact Explore control beside Auto and Holograms.
- Prevent duplicate shortcut toggles and ignore solver shortcuts while menus are open.

## Experimental automatic finder

The finder and navigation remain beta features under a code-level switch. Public builds keep the switch disabled;
local testing builds can enable it. Navigation is experimental and does not guarantee a safe route through every terrain.

- Find active boards and unopened LootGames structures in loaded terrain.
- Search existing single-player save files in the background for structures beyond loaded chunks, without generating terrain.
- Explore progressively using bounded routes and expanding search goals when no reachable structure is known.
- Use one configurable start/stop action for walking and guarded staircase digging.
- Route around obstacles and handle one-block terrain changes using bounded ground pathfinding.
- Use normal player movement with smoother turns and sprinting on checked straight sections.
- Support surface-swimming routes while rejecting lava and stopping when air is low.
- Preserve forward swimming when jump is held, and avoid reversing toward a waypoint before landing.
- Smooth mining turns and refresh structure targets while exploration is active.
- Preserve staircase heading while centering with sideways movement, and seek a dry supported entrance before digging from water.
- Tolerate surface bobbing when completing swimming waypoints.
- Cache terrain checks within each route search and bound route-search time to reduce work on the client thread.
- Attempt alternative staircase directions, select suitable hotbar tools, and restore the previous hotbar slot when stopped.
- Dig without tools in creative mode; select modded survival tools using Forge harvesting and digging-speed hooks.
- Find suitable tools throughout the main inventory and move them to the hotbar with item-preserving swaps.
- Show a formatted reminder to carry a usable pickaxe and shovel when survival auto-digging lacks a suitable tool.
- Check headroom, support, nearby hazards, falling blocks, tool durability, and mining progress before continuing excavation.
- Stop for manual movement, menus, damage, unsafe terrain, or stalled progress.
- Add configurable exploration enablement, staircase digging, route range, and progressive search limits.

## Build and releases

- Package this version's changelog inside the mod JAR and use the same file for release notes.
- Run build checks before publishing and report CurseForge upload failures instead of silently ignoring them.
