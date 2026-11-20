# LootGameSolver

Solve LootGames puzzles in GregTech: New Horizons with automatic interactions or in-world hints. LootGameSolver is a client-side mod for Minecraft 1.7.10 and Forge, supporting Minesweeper, Sudoku, and Game of Light.

## Choose how to play

- **Auto-Solver (`R`)** detects the board, chooses moves, and interacts with the puzzle.
- **Holograms (`H`)** show suggested moves and navigation lines while you play.
- **Explorer (`G`)** is an experimental mode for finding structures, reaching their puzzles, and collecting rewards. Press `G` to pause or resume active automation; hold it to clear the current automation and checkpoint.

Minesweeper can use probabilities when no guaranteed safe move is available. Such guesses are not guaranteed to be safe.

## Explore and collect

Explorer searches loaded terrain and can inspect saved region data in single-player worlds. It can navigate toward structures, excavate entrances, solve puzzles, open reward chests, and pack eligible loot into supported backpacks. It also supports configured home or warp storage, marked containers, and Binnie's multi-page Compartments.

Explorer is experimental. It can change blocks and inventory contents, including discarding mined debris and ores when cleanup is enabled and available. Supervise automation and be ready to take control. Structures outside loaded terrain may require exploration before they can be discovered.

Use `/lgs help` to see all commands and `/lgs help <command>` for details. For storage, use `/lgs setHome` or `/lgs setWarp <name>`, look at a container and use `/lgs setChest`, then `/lgs enable`. Storage trips require the corresponding server commands and permissions; `/setwarp returnLooting` is used for the return point. Use `/lgs disable` to keep your settings without running trips. `/lgs caveMode on` enables underground travel; cave mode is off by default.

The default keys can be changed in Minecraft's Controls menu. The HUD shows automation status, elapsed time, and the puzzle count.

## Installation

Install the JAR into the `mods` folder of a Minecraft 1.7.10 Forge instance with LootGames, such as GTNH. The release JAR includes the [Baritone 1.7.10 fork](https://github.com/KyroXova/baritone); a separate Baritone installation is not required.

[Source and full command reference](https://github.com/KyroXova/LootGameSolver) · [GitHub releases](https://github.com/KyroXova/LootGameSolver/releases) · [Report an issue](https://github.com/KyroXova/LootGameSolver/issues) · [3.1.0 changelog](https://github.com/KyroXova/LootGameSolver/blob/main/changelog/changelog_3.0.0_to_3.1.0.md)
