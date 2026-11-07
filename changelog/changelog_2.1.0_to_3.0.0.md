# LootGameSolver - 2.1.0 → 3.0.0

**Full Changelog**: https://github.com/KyroXova/LootGameSolver/compare/2.1.0...3.0.0

## Puzzle solving

### Sudoku

- Solve puzzles from their original clues while tracking player entries separately.
- Display hints for empty or incorrect cells and use the shorter direction when entering numbers.
- Confirm tile updates before continuing and avoid repeated completion submissions.

### Minesweeper

- Detect boards from farther away and continue through multiple stages.
- Show safe moves, flags, and probabilities using the full board mine count.
- Avoid guesses when available information or search limits do not support a reliable result.

### Game of Light

- Track and replay the detected sequence, including repeated tiles.
- Group repeated hologram numbers and wait for accepted clicks before continuing.

### General

- Open and reveal game boards automatically when the explorer reaches a structure.
- Reset cached actions when a puzzle or stage changes and handle delayed board updates.

## Experimental explorer

The explorer is a beta feature and may still require player intervention.

### Finding and reaching structures

- Find loaded structures and search saved regions in single-player worlds.
- Plan walking routes, tunnels, and stairs, with dry approaches around water.
- Avoid known hazardous plants, traps, and unsafe liquids; select suitable tools when digging.
- Extend routes while walking and reuse existing entrances and tunnels where possible.
- Use smoother camera movement, diagonal shortcuts, and sprinting on suitable terrain.

### Puzzles, rewards, and return

- Start or resume an unfinished game inside a structure and return through the saved entrance after solving.
- Collect reward chests after completing a game and store items in supported backpacks.
- Protect backpacks, tools, food, and useful building blocks during inventory cleanup.
- Use food and the Extra Utilities Healing Axe automatically while exploring.
- Keep solved-structure history and hold G to clear the last checkpoint.

## HUD and controls

- Show compact solver and exploration statuses, structure distance, and navigation routes.
- Highlight active exploration and show progress while holding G.
- Pause navigation for open menus, manual movement, damage, or flight.
