# LootGameSolver - 3.0.0 → 3.1.0

[Full comparison](https://github.com/KyroXova/LootGameSolver/compare/3.0.0...3.1.0)

## Added

- Integrated the Minecraft 1.7.10 Baritone fork into the release JAR for navigation and selected interactions; players do not need a separate Baritone JAR.
- Added progressive structure search, outward exploration of newly loaded areas, and periodic rescanning of saved region files in single-player worlds.
- Added configurable home or warp storage recovery, marked storage blocks and entities, temporary return warps, and saved reward-collection checkpoints. `/lgs help` describes the available commands.
- Added support for packing rewards into supported backpacks and depositing into marked containers, including Binnie's multi-page Compartments and supported nearby drop-off integration.
- Added a cave-mode preference, disabled by default, for long-distance exploration.
- Added elapsed-time and solved-puzzle counters, storage target outlines, command completion, and detailed command help.
- Added persistent tracking and cleanup of eligible mined debris, including ores and related drops, and handling for foliage obstructing room construction.

## Changed and fixed

- Improved transitions between exploration, puzzle stages, reward collection, storage trips, and returning to the surface. Structure search no longer takes ownership of an active room task.
- Improved Minesweeper stage handling, Sudoku and Minesweeper movement toward puzzle tiles, and Game of Light action acknowledgement.
- Improved chest approaches, visibility and reach checks, backpack packing, transaction confirmation, and recovery when storage or inventory space is unavailable.
- Improved navigation around water, shorelines, lily pads, ice, snow, falling blocks, hazardous terrain, and blocked room exits.
- Improved walking camera behavior, mining aim, path overlays, HUD statuses, and pause/resume controls.
- Added retries and blocked-structure tracking for rooms that cannot be opened, and improved checkpoint persistence on Windows.
- Improved tool selection, healing-axe handling, food use, and inventory protection during automated cleanup and storage.

Explorer and storage automation remain experimental. Keep an eye on the player, particularly around hazards and valuable inventory; behavior can vary with server rules and installed mods.
