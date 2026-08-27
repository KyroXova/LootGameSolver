# LootGameSolver - 1.0.0 --> 2.0.0

**Full Changelog**: https://github.com/KyroXova/LootGameSolver/compare/1.0.0...2.0.0

## What's Changed

- Fix Shift-clicking desync during Sudoku multi-click digit cycling by keeping sneak key state active across client ticks by @KyroXova
- Automatically switch player's active hotbar slot to an empty hand before right-clicking/shift-clicking to prevent GT tools (hammers, wrenches) from intercepting block interactions by @KyroXova
- Add fastest-path click strategy for Sudoku (using forward right-clicks for 1..5 and Shift + right-clicks for 6..9) by @KyroXova
- Prevent premature Sudoku game-over failures by checking that all 81 slots are filled and verified before submitting the solution by @KyroXova
- Add automatic retry and state verification for Sudoku cells to handle dropped clicks or network lag desyncs by @KyroXova
- Overhaul Game of Light solver to seamlessly play full sequence rounds in a single continuous run by @KyroXova
- Replace Game of Light "NEXT" text with green 3D flag holograms matching Minesweeper visual style by @KyroXova
- Fix Game of Light sequence lockup where the solver would stall on step 0 and repeatedly click the start button by @KyroXova
- Improve Minesweeper auto-opening for ungenerated stages and structure expansion transition delays by @KyroXova
- Streamline HUD overlay positioning and update control keybindings (`R` for Auto-Solve, `H` for Holograms) by @KyroXova
