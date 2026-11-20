# LootGameSolver 3.1.0

This update expands the experimental Explorer with Baritone navigation, more flexible storage recovery, and improvements to puzzle, reward, and terrain handling.

**Full comparison:** [3.0.0...3.1.0](https://github.com/KyroXova/LootGameSolver/compare/3.0.0...3.1.0). The comparison becomes available when the 3.1.0 release tag is created.

## Added

### Navigation and structure discovery

- Bundled the Minecraft 1.7.10 Baritone fork in the release JAR. Explorer uses it for structure travel, search routes, room approaches, and selected interactions; a separate Baritone download is not required.
- Added progressive exploration of newly loaded terrain when no structure is known, including outward search legs and avoidance of recently visited areas.
- Added periodic background rescanning of saved region files in single-player worlds to discover structures in newly generated regions.
- Added cave-mode controls through `/lgs caveMode`. Cave mode is off by default, preferring surface travel while still allowing excavation to reach structures.
- Added room obstruction handling for foliage and other removable blocks that can prevent a puzzle from opening.
- Added retries and separate blocked-structure tracking for rooms that cannot be opened.

### Inventory and storage recovery

- Added optional home or warp storage destinations, marked inventory blocks or entities, and commands to enable, disable, inspect, and clear storage settings.
- Added temporary return-warp positioning and saved reward checkpoints so an interrupted storage trip can return to unfinished loot collection.
- Added packing of eligible rewards into supported backpacks and unloading into marked storage. Binnie's multi-page Compartments and compatible nearby drop-off integration are supported.
- Added transaction and inventory-change checks for container transfers, and safeguards for protected equipment, backpacks, tools, and reserved building blocks.
- Added persistent tracking and cleanup of eligible mined debris, including ores and related drops, when compatible cleanup is available.

### Controls and feedback

- Added `/lootgamesolver` and `/lgs` storage and exploration commands, command-specific help, and tab completion.
- Added storage-target outlines, elapsed-time and solved-puzzle counters, and clearer Explorer and inventory status messages.
- Added Explorer pause and resume through `G`; holding the key clears active automation and its last checkpoint.

## Improved

### Puzzle solving and room transitions

- Improved the handoff from structure navigation to puzzle activation, between puzzle stages, and from completed puzzles to reward collection.
- Improved Minesweeper stage handling and action confirmation, Sudoku tile selection and number entry, and Game of Light sequence acknowledgement.
- Improved movement toward puzzle tiles and allowed interactions during movement when the target is visible and in reach.
- Improved room-center activation and obstruction checks, including recovery for blocked or inaccessible rooms.
- Prevented unrelated structure-search routes from taking control while a puzzle or reward collection is active.

### Rewards and storage

- Improved reward-chest approaches and visibility checks, including situations where the player is standing above or close to a chest.
- Improved backpack selection, packing, hotbar preservation, and recovery when a bag or container is full or unavailable.
- Excluded several non-storage items, such as jetpacks and specialized equipment, from backpack handling.
- Improved storage-target approaches, transfer verification, interrupted-trip recovery, and resumption of unfinished rewards.
- Improved checkpoint persistence on Windows and restored inventory tracking after world or client reloads.

### Terrain and movement

- Improved route planning around water, shorelines, lily pads, ice, snow layers, foliage, and known hazards.
- Improved underwater structure approaches, post-puzzle exits, and excavation around falling sand and gravel.
- Improved tool selection and mining input, including maintaining aim while breaking blocks and using the Healing Axe during travel when available.
- Improved movement ownership and recovery after route interruptions, paused automation, and room transitions.
- Refined exploration camera steering, path overlays, and interaction aiming to reduce unnecessary movement and visual clutter.

## Notes

Explorer and automatic storage remain experimental. Supervise automation, especially around hazards and valuable inventory: it can move or discard items, change blocks, and may need help in unfamiliar terrain or with other mods. Probability-based Minesweeper moves are not guaranteed safe.

Storage recovery requires a configured destination, marked containers, the relevant server commands and permissions, and `/lgs enable`. Use `/lgs help` for the complete command list.
