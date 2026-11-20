# LootGameSolver

Solve **Minesweeper, Sudoku, and Game of Light** in GregTech: New Horizons with automatic interactions or in-world hints. LootGameSolver is a client-side mod for **Minecraft 1.7.10 and Forge**.

## Choose how to play

- **Auto-Solver (`R`)** detects the active board, plans moves, and interacts with the puzzle.
- **Holograms (`H`)** show suggested moves and navigation lines while you stay in control.
- **Explorer (`G`)** brings structure discovery, navigation, puzzles, and reward collection together in an experimental mode. Press `G` to pause or resume active automation; hold it to clear active automation and the last checkpoint.

Rebind these controls in Minecraft's Controls menu. The HUD shows the current task, elapsed time, and solved-puzzle count.

### Supported puzzles

- **Minesweeper:** reveal safe cells, flag identified mines, and continue through stages. Optional probability-based moves are calculated guesses and are not guaranteed safe.
- **Sudoku:** follow suggested numbers or let the solver enter them and submit the board.
- **Game of Light:** follow sequence holograms or let the solver replay the sequence through its rounds.

## Explorer and rewards

Explorer searches loaded terrain and can inspect saved region data in single-player worlds. It can explore outward, travel toward structures, excavate entrances, clear some obstructing foliage, solve puzzles, and collect reward chests. Available terrain, server rules, and other mods affect whether a route or interaction succeeds.

The explorer can select tools, use food or a Healing Axe, pack eligible items into supported backpacks, and clean up unwanted mined debris when a compatible trash command is available. **Cleanup can discard ores and other items.**

**Explorer is experimental.** It can change blocks and inventory contents and may get stuck or fail to solve a puzzle or collect every reward. Supervise it and be ready to take control.

### Travel preference

Cave mode is off by default, preferring surface travel between structures while still allowing excavation to enter them. Use `/lgs caveMode on` to permit cave routes, `/lgs caveMode off` to prefer the surface, or `/lgs caveMode status` to check the setting.

### Storage recovery

When configured and enabled, storage recovery can save a temporary return warp, travel to a home or warp destination, deposit eligible loot in marked containers, and return to unfinished rewards. Supported targets include Binnie's multi-page Compartments; nearby drop-off integration is also supported when available. Backpacks and carried equipment are protected by the inventory rules.

Storage recovery is disabled by default and requires server commands and permissions for your chosen `/home` or `/warp` destination, `/setwarp returnLooting`, and `/warp returnLooting`. The name `returnLooting` is reserved for the temporary return point.

1. At your storage area, look at a storage block or supported inventory entity and use `/lgs setChest`. Repeat for additional targets.
2. Use `/lgs setHome` for `/home`, or `/lgs setWarp <name>` for a storage warp.
3. Use `/lgs enable` to allow storage trips. Use `/lgs disable` to pause them without deleting your configuration.

## Commands

All commands accept `/lootgamesolver` or the `/lgs` alias. Use `/lgs help <command>` for detailed in-game help.

| Command | What it does |
| --- | --- |
| `/lgs help [command]` | Show commands or help for one command. |
| `/lgs setHome` | Use `/home` as the storage destination. |
| `/lgs setWarp <name>` | Use `/warp <name>` as the storage destination. |
| `/lgs setChest` or `/lgs setStorage` | Mark the storage target you are looking at. |
| `/lgs setStorage <name>` | Another way to select a storage warp. |
| `/lgs clearChest` | Remove the marked target you are looking at. |
| `/lgs clearChests` | Remove all marked targets for the current world. |
| `/lgs clearWarp` | Clear the destination but keep the marked targets. |
| `/lgs clearStorage` | Clear storage settings and disable recovery. |
| `/lgs enable` / `/lgs disable` | Enable or disable storage recovery. |
| `/lgs storageStatus` | Show the storage configuration. |
| `/lgs resumeLoot` | Resume saved reward collection near the room. |
| `/lgs clearCheckpoint` | Clear saved loot and navigation checkpoints. |
| `/lgs caveMode [on\|off\|status]` | Set or check cave travel. |

## Installation

1. Install a Minecraft **1.7.10 Forge** instance with **LootGames**, such as GTNH.
2. Put the LootGameSolver release JAR in the instance's `mods` folder.
3. Launch the game and use the controls above near a LootGames puzzle, or try Explorer.

The release JAR includes the [Baritone 1.7.10 fork](https://github.com/KyroXova/baritone); a separate Baritone installation is not required. Solver timing, probability, display, and explorer settings are configurable.

[Source and documentation](https://github.com/KyroXova/LootGameSolver) · [GitHub releases](https://github.com/KyroXova/LootGameSolver/releases) · [Report an issue](https://github.com/KyroXova/LootGameSolver/issues) · [3.1.0 changelog](https://github.com/KyroXova/LootGameSolver/blob/main/changelog/changelog_3.0.0_to_3.1.0.md)
