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
- Automatically deposit collected loot into marked home or warp storage targets (including Binnie's multi-page Compartments) and return to complete unfinished rooms.
- Use food and the Extra Utilities Healing Axe while exploring.
- Clean unwanted inventory items when the trash-can command is available, while protecting backpacks, tools, food, and a stack of building material.

Reward collection uses known local loot-table weights when available; on servers, item weights may be unknown. Inventory cleanup can remove ores and excess blocks.

**Early beta:** Explorer automation is experimental and may fail to find a structure, navigate a route, solve a board, or collect every reward. Supervise it and be ready to take control; it can change blocks and inventory contents while active.

Structure discovery is progressive. The explorer checks loaded LootGames structures first and can also inspect saved region data in single-player worlds. When it has no known target, it explores outward in expanding search legs and checks newly loaded terrain. It navigates and digs in the world rather than teleporting.

## Controls

| Default key | Action |
| --- | --- |
| **R** | Toggle the Auto-Solver |
| **H** | Toggle holograms and navigation lines |
| **G** | Start or stop the explorer |
| **Hold G** | Clear the last checkpoint and replan |

Keys can be changed in Minecraft's Controls menu. The HUD shows solver and explorer status, the selected structure, and hold-G progress. Navigation pauses for open menus, manual movement, damage, or flight.

## Commands

Manage storage destinations, marked targets, checkpoints, and navigation preferences with `/lootgamesolver` (alias `/lgs`):

| Command | Description |
| --- | --- |
| `/lgs help [command]` | Show command list or detailed help for a specific command. |
| `/lgs setHome` | Select your existing `/home` command as the storage destination. |
| `/lgs setWarp <name>` | Select `/warp <name>` as the storage destination (`returnLooting` is reserved). |
| `/lgs setChest` / `/lgs setStorage` | Mark the looked-at inventory block, entity, or Binnie Compartment for storage deposits. |
| `/lgs clearChest` | Unmark the looked-at storage target. |
| `/lgs clearChests` | Remove all marked storage targets for the current world. |
| `/lgs clearWarp` | Clear the home or warp destination while keeping marked targets saved. |
| `/lgs clearStorage` | Clear destination and all targets, and disable recovery. |
| `/lgs enable` / `/lgs disable` | Enable or disable automated storage recovery trips. |
| `/lgs storageStatus` | Display configured destination, target count, and recovery preference. |
| `/lgs resumeLoot` | Resume saved reward collection when you are back near the dungeon room. |
| `/lgs clearCheckpoint` | Clear saved loot collection and navigation checkpoints. |
| `/lgs caveMode [on\|off\|status]` | Allow subterranean pathing or prefer surface travel for long-distance routes. |

### Multi-Page Storage Containers

When depositing into marked storage, LootGameSolver supports multi-page storage containers such as Binnie's Compartments (Wood, Copper, Bronze, Iron, Gold, and Diamond). The depositor cycles through all available tabs, verifies slot acceptance on each page, and deposits eligible loot while strictly protecting carried gear, weapons, tools, and backpacks.

## Installation

1. Install **GTNH for Minecraft 1.7.10** with Forge and LootGames.
2. Download the latest LootGameSolver JAR and place it in the instance's `mods` folder.
3. Launch the instance and approach a LootGames board, or use the experimental explorer to search for one.

## Configuration

The configuration includes solver interaction delays, Minesweeper probability settings, display options, and experimental exploration settings such as route range and staircase digging.

See the [3.1.0 changelog](changelog/changelog_3.0.0_to_3.1.0.md) for the latest changes.

## Baritone Integration & Licensing

> **Note:** Baritone is bundled via Jar-in-Jar with this project. Baritone has been backported to Minecraft 1.7.10 under the `GNU Lesser General Public License` as-is and modified only to accommodate LootGameSolver functionality. It is hosted under the fork [KyroXova/baritone](https://github.com/KyroXova/baritone) for maintenance and build compatibility, contributing backport enhancements without unlawful redistribution. All modifications remain open source and compliant with the LGPL.

