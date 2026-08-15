# LootGameSolver

**LootGameSolver** is a client-side Minecraft **1.7.10** mod designed for **GregTech: New Horizons (GTNH)** to assist with and automate **LootGames** mini-games.

It provides two distinct ways to play:

* **Auto-Solver** — The fast, fully automated option. The mod solves the game and physically performs the required clicks and movements for you.
* **Hologram Mode** — The more survival-friendly option. The mod solves the game in the background and simply shows you what to do through in-world holograms, leaving all interactions to the player.

Whether you want the convenience of complete automation or prefer to remain in control, LootGameSolver lets you choose.

---

## Two Ways to Solve

### Auto-Solver — Full Automation

**Press `R` to toggle the Auto-Solver.**

Auto-Solver is designed for maximum speed and convenience.

Once enabled, LootGameSolver will:

* Detect the active LootGames board.
* Solve the puzzle automatically.
* Move your crosshair/player as required.
* Perform the necessary clicks and interactions.
* Continue solving until the game is completed.

Essentially, **you let the solver play the mini-game for you.**

This is the fastest and most hands-off way to complete LootGames.

---

### Hologram Mode — Player-Assisted

**Press `H` to toggle Holograms.**

Hologram Mode is intended for players who want assistance **without handing control of their character over to the mod**.

Instead of interacting with the game, LootGameSolver displays the correct information directly in the world:

* Safe cells
* Mines / suspected mines
* Correct Sudoku numbers
* Relevant targets and moves

You remain completely in control and perform the actual interactions yourself.

**The solver tells you what to do — you do it.**

This makes Hologram Mode the more **survival-friendly and low-interference** option, while still providing the same solving intelligence behind the scenes.

---

## Minesweeper

LootGameSolver uses constraint-based deduction to identify guaranteed-safe cells and mines.

### Auto-Solver

Automatically moves and clicks through the board, revealing safe cells and flagging mines as necessary.

When deterministic deduction is no longer possible, the solver can use **probabilistic reasoning** to select the most favorable move.

### Hologram Mode

Instead of interacting with the board, holograms show you:

* Which cells are safe to reveal.
* Which cells are identified as mines.
* Where the solver recommends making a move.

You simply follow the displayed recommendations yourself.

> **Note: Probabilistic guesses are not guaranteed.** If the board reaches a state with no guaranteed-safe move, even the solver may have to take a calculated risk.

---

## Sudoku

LootGameSolver can automatically parse and solve Sudoku boards.

### Auto-Solver

The mod handles the entire interaction process:

1. Detects the puzzle.
2. Solves the board.
3. Moves the crosshair to the required positions.
4. Cycles the appropriate digits.
5. Verifies the completed board.
6. Submits the solution.

### Hologram Mode

The mod does **not** control your character.

Instead, it displays the correct numbers and target positions, allowing you to manually enter the solution while following the holograms.

---

## Game of Light — Work in Progress

**Game of Light support is currently incomplete and under active development.**

The planned implementation includes:

* Automatic game initialization.
* Sequence detection and tracking.
* Symbol recognition.
* Automated symbol activation.
* Support for all four difficulty rounds.
* Full Auto-Solver support.
* Hologram/assisted solving.

Game of Light will be expanded in a future update once development is complete.

---

## HUD & Holograms

LootGameSolver includes an in-world visualization system designed to make its solving decisions easy to follow.

* 3D holograms displayed directly over relevant blocks.
* Safe-cell and mine indicators for Minesweeper.
* Correct-number indicators for Sudoku.
* Target and action indicators for supported games.
* 360° structure detection so the solver can maintain board state even when you're looking away.
* Color-coded HUD information.

The hologram system is intentionally **visual-only**: it provides the information while leaving all physical interactions to you.

---

## Controls

| Key | Function |
| :---: | ------------------------------------------------------------------ |
| **`R`** | Toggle **Auto-Solver** — fully automated movement & interaction |
| **`H`** | Toggle **Holograms** — shows the correct moves without interacting |

### Which mode should I use?

**Want it done as quickly as possible?**  
-> **Auto-Solver**

**Want assistance while keeping control of your character?**  
-> **Hologram Mode**

---

## Showcases

Video demonstrations will be added below.

### Minesweeper

*Video showcasing the automated Minesweeper solver coming soon.*

### Sudoku

*Video showcasing the automated Sudoku solver coming soon.*

### Game of Light

*Currently under development — showcase coming once the solver is complete.*

---

## Development Status

| Feature | Status |
| :--- | :---: |
| Minesweeper — Auto-Solver | Complete |
| Minesweeper — Holograms | Complete |
| Sudoku — Auto-Solver | Complete |
| Sudoku — Holograms | Complete |
| Game of Light | In Development |
| HUD & Hologram System | Complete |

---

## Installation

1. Download the latest **LootGameSolver** `.jar`.
2. Place it inside your Minecraft `.minecraft/mods/` folder.
3. Launch **GTNH / Minecraft 1.7.10 with Forge**.
4. Enter a LootGames mini-game.
5. Choose your preferred mode:

   * Press **`R`** for full automation.
   * Press **`H`** for visual assistance.

---

> **LootGameSolver:**  
> *You decide how much help you want. Let it play for you, or let it tell you exactly what to do.*
