# LootGameSolver

**LootGameSolver** is a client-side Minecraft 1.7.10 mod for **GregTech: New Horizons (GTNH)** that automates and provides 3D visual hints for **LootGames** minigames (Minesweeper, Sudoku, and Game of Light).

---

## Features & Modes

- **Auto-Solver (`R`)**: Fully automated mode. Solves the minigame, aims your crosshair, automatically switches to an empty hand to prevent GT tools (hammers, wrenches) from canceling Shift-right clicks, and performs all required interactions.
- **Hologram Mode (`H`)**: Player-assisted mode. Renders 3D holograms directly in the world showing safe cells, mines, Sudoku numbers, and sequence steps while leaving total movement control to you.

---

## Supported Games

### 💣 Minesweeper
- **Auto-Solver**: Automatically opens safe cells, flags mines, and uses probabilistic deduction when necessary.
- **Holograms**: Displays green safe flags and red mine flags in 3D over the board.

### 🧩 Sudoku
- **Auto-Solver**: Solves the puzzle, calculates the fastest forward/backward click path for each digit (1..5 vs 6..9), and submits upon full board verification.
- **Holograms**: Renders floating 3D numbers above unsolved cells.

### 💡 Game of Light (Simon Says)
- **Auto-Solver**: Tracks flashing light patterns and automatically inputs multi-step sequence rounds in a single continuous run.
- **Holograms**: Highlights active sequence targets with green 3D flag holograms in real time.

---

## Controls

| Key | Function |
| :---: | :--- |
| **`R`** | Toggle **Auto-Solver** (full automation) |
| **`H`** | Toggle **Holograms** (in-world visual hints) |

---

## Development Status

| Feature | Status |
| :--- | :---: |
| Minesweeper (Auto-Solver & Holograms) | Complete |
| Sudoku (Auto-Solver & Holograms) | Complete |
| Game of Light (Auto-Solver & Holograms) | Complete |
| HUD & 3D Hologram Overlay | Complete |

---

## Installation

1. Download `lootgamesolver-2.0.0.jar`.
2. Place it into your `.minecraft/mods/` folder.
3. Launch GTNH (1.7.10 Forge), approach any LootGames puzzle, and press **`R`** or **`H`**.
