# LootGameSolver

[![CurseForge](https://img.shields.io/badge/CurseForge-1654587-orange?style=for-the-badge&logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/lootgamesolver)
[![Minecraft 1.7.10](https://img.shields.io/badge/Minecraft-1.7.10-blue?style=for-the-badge&logo=minecraft)](https://www.curseforge.com/minecraft/mc-mods/lootgamesolver/files/all?page=1&pageSize=20&version=1.7.10)
[![Forge](https://img.shields.io/badge/ModLoader-Forge-dfa86a?style=for-the-badge)](https://www.curseforge.com/minecraft/mc-mods/lootgamesolver)

**LootGameSolver** is a feature-rich, client-side Minecraft 1.7.10 mod designed for **GregTech: New Horizons (GTNH)**. It automates and provides 3D visual hints for all **LootGames** minigames—**Game of Light (G O L)**, **Minesweeper**, and **Sudoku**.

---

## Key Highlights in v2.0.0

### Game of Light (G O L) - Fully Supported!
- **Continuous Multi-Step Playback**: Automatically tracks flashing light patterns and inputs full multi-step sequence rounds in a single smooth run without stalling.
- **3D Flag Holograms**: Replaced old text indicators with floating green 3D flag holograms rendered directly over active sequence target tiles.

### Improved Sudoku Solver
- **Fastest Click Strategy**: Automatically calculates the fastest input path for any target digit (using forward right-clicks for digits `1..5` and Shift + right-clicks for `6..9`).
- **Board Submission Guard**: Checks that all 81 slots are filled and verified before submitting solution packets, preventing premature game-over failures.
- **Desync & Network Auto-Retry**: Automatically verifies cell values after clicks and retries if packet loss or client/server desync occurs.

### Improved Minesweeper
- **Probabilistic Deduction**: Solves deterministic safe cells and flags mines automatically, utilizing probabilistic reasoning when guessing is required.
- **Ungenerated Stage Handling**: Seamlessly handles ungenerated stage openings and multi-stage expansion transitions.

### Smart Empty-Hand Tool Protection
- **Automatic Hotbar Switching**: Before right-clicking or Shift-right clicking puzzle blocks, the solver automatically switches your active slot to an empty hand.
- **GT Tool Safety**: Prevents GT tools (Hammers, Wrenches, Pickaxes) or usable items from intercepting or canceling Shift-right click block interactions.

---

## 🎮 Modes & Controls

| Keybind | Mode | Description |
| :---: | :--- | :--- |
| **`R`** | **Auto-Solver** | Fully automated mode. Solves the puzzle, aims your crosshair, switches to an empty hand, and executes clicks automatically. |
| **`H`** | **Holograms** | Player-assisted mode. Renders 3D holograms over safe cells, mines, Sudoku numbers, and G O L sequences while you keep total movement control. |

---

## 📋 Minigames Supported

| Minigame | Auto-Solver | 3D Holograms | Key Features |
| :--- | :---: | :---: | :--- |
| **Game of Light (G O L)** | ✅ | ✅ | Single-run continuous sequence input & green 3D flag target overlays |
| **Sudoku** | ✅ | ✅ | Forward/backward click path calculation, 81-slot verification guard & cell retry |
| **Minesweeper** | ✅ | ✅ | Fast safe cell opening, mine flagging, probabilistic solver & stage expansion delay |

---

## 🛠️ Installation & Links

1. Download **`lootgamesolver-2.0.0.jar`**.
2. Place the jar file into your `.minecraft/mods/` directory.
3. Launch Minecraft 1.7.10 (Forge / GTNH), approach any LootGames puzzle room, and press **`R`** or **`H`** to begin!

- **CurseForge Page**: [https://www.curseforge.com/minecraft/mc-mods/lootgamesolver](https://www.curseforge.com/minecraft/mc-mods/lootgamesolver)
- **Project ID**: `1654587`
- **License**: MIT
