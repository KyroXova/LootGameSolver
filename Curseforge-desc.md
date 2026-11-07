# LootGameSolver

LootGameSolver is a client-side Minecraft 1.7.10 mod for **GregTech: New Horizons (GTNH)** that helps you play **LootGames**. It supports Minesweeper, Sudoku, and Game of Light with automatic solving and in-world hints.

Choose how you want to play: let the solver interact with a board, follow holograms yourself, or try the experimental explorer to find and complete structures.

## Ways to play

### Auto-Solver

Press **R** to toggle the solver. It reads the active board, plans moves, aims and interacts with puzzle tiles, and moves into reach when needed. The solver tracks stage changes and waits for board updates before continuing.

### Holograms

Press **H** to toggle in-world hints and navigation lines. Holograms show suggested puzzle actions while you control movement and interactions yourself. The status HUD remains available when holograms are hidden.

## Supported games

### Minesweeper

- Reveal safe cells and flag identified mines, with hints shown over the board.
- Continue through stages as boards are revealed.
- Use board-wide mine counts to calculate probabilities. Probability-based moves and their minimum safety threshold are configurable; a guess is never guaranteed safe.

### Sudoku

- Solve from the original clues while tracking player entries separately.
- Show numbers for empty or incorrect cells and choose the shorter direction when entering a value.
- Confirm changes before continuing and submit a completed board.

### Game of Light

- Track and replay the sequence through successive rounds.
- Display sequence positions as holograms, including multiple steps on the same tile.
- Wait for accepted interactions before advancing the sequence.

## Experimental explorer: powerful automation

Press **G** to start or stop the explorer. This experimental mode brings structure finding, navigation, puzzle solving, and reward collection together in one automated flow.

The explorer can:

- Find loaded LootGames structures and search saved regions in single-player worlds.
- Plan walking routes, tunnels, and stairs, including dry approaches around water.
- Select tools, clear obstructions, and avoid known hazardous plants, traps, and liquids.
- Open boards, solve games, and resume an unfinished game inside a structure.
- Collect reward chests and move eligible items into supported backpacks.
- Use food and the Extra Utilities Healing Axe while exploring.
- Clean unwanted inventory items when the trash-can command is available, while protecting backpacks, tools, food, and a stack of building material.

Reward collection uses known local loot-table weights when available; on servers, item weights may be unknown. Inventory cleanup can remove ores and excess blocks.

**Experimental:** Navigation, digging, placement, and looting may still require player intervention. The explorer can change blocks and inventory contents while active.

## Controls

| Default key | Action |
| --- | --- |
| **R** | Toggle the Auto-Solver |
| **H** | Toggle holograms and navigation lines |
| **G** | Start or stop the explorer |
| **Hold G** | Clear the last checkpoint and replan |

Keys can be changed in Minecraft's Controls menu. The HUD shows solver and explorer status, the selected structure, and hold-G progress. Navigation pauses for open menus, manual movement, damage, or flight.

## Configuration

The configuration includes solver interaction delays, Minesweeper probability settings, display options, and experimental exploration settings such as route range and staircase digging.

See the [3.0.0 changelog](https://github.com/KyroXova/LootGameSolver/blob/main/changelog/changelog_2.1.0_to_3.0.0.md) for the latest changes.
