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
- Aim visibly at each excavation block and require a matching crosshair target, reach check, and stable aim before mining its selected face.
- Wait for landing before advancing staircase steps instead of stopping during the descent.
- Plan connected side approaches around blocked roofs using level detours and supported stairs, with bounded search work across ticks.
- Clear obstructing leaves and ordinary breakable wood or masonry while protecting tile entities and unbreakable blocks.
- Turn toward movement and mining targets faster while retaining visible aim checks before breaking blocks.
- Keep walking headings tied to route segments, consume overshot endpoints, and center on stairs without turning back toward the player's feet.
- Update camera turns per rendered frame through vanilla mouse-look handling, hold the view while planning or waiting to land, and prefer fewer bends between equally scored routes.
- Walk diagonally across clear level ground while checking both side passages to prevent cutting through corners.
- Extend partial walking routes before reaching their endpoints, scan for structures during outward exploration, and retry alternate dry approaches when excavation cannot reach the target.
- Prepare underground access routes while approaching a dry checkpoint, then revalidate planned steps against the live world before moving or mining.
- Distinguish walking beside water from opening a hole beside water, allowing supported shoreline detours without permitting flooding excavation.
- Show the current navigation decision in the HUD and a blue vertical waypoint at the selected structure with a live distance label.
- Choose dry underground access while walking toward a structure, rank candidate entrances by travel distance, and inspect upcoming checkpoints for obstructions before reaching them.
- Pause with a specific reason for movement input, menus, flight, or damage, then replan from the player's current position when resuming.
- Retain walking-route search progress across time slices instead of interpreting an expired frame budget as an unreachable path.
- Start checked staircase sections without waiting for a complete underground route, then replan as excavation progresses.
- Clear soft plants intercepting the mining crosshair in the occupied or planned digging column before targeting the block behind them.
- Show the number of checked nodes while planning underground access.
- Preserve earlier stair floors across excavation plans so alternating steps cannot collapse the access route into a two-column shaft.
- Permit breakable LootGames dungeon masonry with suitable tools, prefer softer cracked sections, and continue protecting shielded blocks and puzzle tile entities.
- Restrict plant clearing to obstructions on the ray toward the planned mining block, instead of whichever plant the camera happens to face.
- Reject dry-entrance routes that stop short of the selected point or take excessive detours; increase the cost of tunneling through wood.
- Hold vanilla attack input while mining so normal client processing preserves block-breaking progress; count work after aim checks using tool-speed-based limits.
- Open the player inventory visibly, perform the standard GUI hotbar swap, verify the tool, and close the screen before resuming excavation.
- Target visible solid faces around thin plants and prefer their support block when it is already part of the safe planned excavation.
- Stop pathfinding once the puzzle is visible and within interaction reach, including when standing below its master block.
- Anchor waypoint labels to the structure's surface terrain instead of following the player's height.
- Select explicit straight tunnels, upward/downward stairs, or compact clockwise spiral access layouts before falling back to terrain search.
- Support a configurable 3x3 spiral footprint, or 4x4 for three steps between right turns, while preserving floors and checking clearance and hazards.
- Clear the extra overhead block needed for upward stairs before jumping to the next step.
- Retain reached access waypoints and offer Return on the same exploration key at the puzzle; stop if the saved path becomes obstructed.
- Increase render-time camera turn speed while retaining easing, short-angle turns, and frame-rate-independent timing.
- Show cached route decisions as green checked walking segments, yellow excavation or water/height transitions, and red rejected steps; the hologram toggle also hides these lines.
- Require a three-block liquid buffer around staircase entrances.
- Tolerate surface bobbing when completing swimming waypoints.
- Keep yaw continuous across the camera angle boundary to prevent full-turn render interpolation.
- Advance reached waypoints without forced airborne braking or stale forward input toward the previous segment.
- Check player-width body clearance, prefer dry detours to water crossings, and search more shoreline entrance directions and elevations.
- Separate dry entrance selection from the underground target, retain the chosen entrance, and check alternative entrances across ticks.
- Continue a verified staircase as a level tunnel once it reaches the structure's height, without mining directly under the player.
- Plan swimming at surface height and tolerate the player's lower floating foot position when approaching a bank.
- Look ahead along straight route segments, ease camera turns, and consume passed waypoints without turning back.
- Cache terrain checks within each route search and bound route-search time to reduce work on the client thread.
- Attempt alternative staircase directions, select suitable hotbar tools, and restore the previous hotbar slot when stopped.
- Dig without tools in creative mode; select modded survival tools using Forge harvesting and digging-speed hooks.
- Find suitable tools throughout the main inventory and move them to the hotbar with item-preserving swaps.
- Show a formatted reminder to carry a usable pickaxe and shovel when survival auto-digging lacks a suitable tool.
- Check headroom, support, nearby hazards, falling blocks, tool durability, and mining progress before continuing excavation.
- Stop for manual movement, menus, damage, unsafe terrain, or stalled progress.
- Add configurable exploration enablement, staircase digging, route range, and progressive search limits.
- Search for a safe, already-open route into an underground structure before choosing a dig route.
- Click the LootGames master block on arrival, start the solver automatically, and hold exploration until the puzzle finishes.
- Resume progressive exploration after a confirmed LootGames win and persist solved master locations per world and dimension.
- Keep the explorer stopped after a loss or an unopenable puzzle, without marking it solved.
- Follow a cleared excavation route into the room using normal running movement instead of stepping through the entrance one block at a time.
- After activating the puzzle master, walk to and click the board's center floor cell to generate and reveal it before auto-solving.
- Use fewer Sudoku clicks by sneaking for backward number cycling, keeping sneak active through the server click and restoring the prior state on the following tick.
- Record safe approach waypoints on existing routes and excavated access, reconnect to the nearest saved waypoint after solving, and dig a checked breakable link if the saved tunnel is blocked.
- Render a full-height blue waypoint as rotating circular rings instead of a dotted laser-like spine.
- Cycle backward from Sudoku zero with a single sneaking click when that is the shorter route.
- Retry aimed master and board-center interactions when the board state has not arrived yet, and continue from the authoritative server win message.
- Rejoin the saved approach trail when resuming from inside a structure, even when the Return action was not armed.
- Persist the checked dig entrance route per world, dimension, and structure so it survives client restarts.
- Return through the excavation entrance instead of retracing the surface approach to the player's old starting point.
- Clear the temporary entrance route after exiting, then resume searching from the surface entrance.
- Avoid non-water liquid hazards, including oil, when checking movement routes.
- Hold the camera at a steady up or down angle through stair runs, and keep it level while swimming.

## Build and releases

- Package this version's changelog inside the mod JAR and use the same file for release notes.
- Run build checks before publishing and report CurseForge upload failures instead of silently ignoring them.
- Start excavation near the closest safe structure wall, keep the entry about 10–15 blocks beyond the footprint, and move closer to the board center before interaction.
- Right-click the board floor to reveal it, then handle Minesweeper opening flags again at each subsequent stage before resuming the solver.
- Reuse nearby checked paths and place a safe solid block from the hotbar or inventory when an upward return step is missing.
- Render Minesweeper safe and red flags with the bundled flag textures, and allow supported slab, snow, and stair surfaces in walking routes.
- Trigger automatic puzzle opening only after the explorer reaches verified interaction range of the master block.
- Wait for the master interaction to settle before navigating to the game center, then confirm the floor click before starting the solver.
- Retry opening when the master click succeeds but the game object is not yet available to the client.
- Hold exploration when no verified return trail exists after solving instead of choosing an arbitrary route inside the structure.
