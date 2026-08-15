# LootGameSolver

**LootGameSolver** is a client-side Minecraft 1.7.10 mod designed for **GregTech: New Horizons (GTNH)** to automatically solve LootGames mini-games: **Minesweeper**, **Sudoku**, and **Game of Light**.

---

## Features

- **Minesweeper Solver**:
  - Deterministic deduction engine for 100% safe reveals & mine flagging.
  - Automated probabilistic guessing when no guaranteed safe moves exist.
  - Non-stop board completion across large untractable constraint frontiers.
- **Sudoku Solver**:
  - Automatic grid parsing from game givens.
  - Physical crosshair locking & right-click digit cycling.
  - Direct board completion verification and auto-submission.
- **Game of Light Solver**:
  - Automatically handles center block activation to start games.
  - Sequence playback tracking.
  - Automated physical symbol activation across all 4 difficulty rounds.
- **HUD & Hologram Overlay**:
  - In-world 3D holograms for safe cells, mine flags, and target numbers.
  - Full 360° structure radius detection (never drops board state when looking away).
  - Modern, color-coded client HUD.

---

## Controls & Keybindings

| Key | Function |
| :---: | :--- |
| **`R`** | **Toggle Auto-Solver** (Toggles automated interaction & solver loop) |
| **`H`** | **Toggle Holograms** (Toggles in-world 3D visual overlays) |

---

## Installation

1. Place `LootGameSolver-1.0.0.jar` into your Minecraft `.minecraft/mods/` folder.
2. Launch GTNH (Minecraft 1.7.10 with Forge).
3. Walk into any LootGames minigame structure and press **`R`** to auto-solve!

---

> [!NOTE]
> *Note: There is a small chance you might blow your cover up in Minesweeper when taking probabilistic guesses, so keep an eye out to escape! :P*
