# LootGameSolver - 3.0.0 → 3.1.0

**Full Changelog**: https://github.com/KyroXova/LootGameSolver/compare/5475a8d29f05fd4991e370c3e795dc8e38fb1617...3.1.0

> **Note:** Baritone is bundled via Jar-in-Jar with this project. Baritone has been backported to Minecraft 1.7.10 under the `GNU Lesser General Public License` as-is and modified only to accommodate LootGameSolver functionality. It is hosted under the fork [KyroXova/baritone](https://github.com/KyroXova/baritone) for maintenance and build compatibility, contributing backport enhancements without unlawful redistribution. All modifications remain open source and compliant with the LGPL.

## New Features

- **Embedded Baritone navigation**: Bundled lightweight 1.7.10 Baritone via Jar-in-Jar. Structure search legs, terrain transit, reward viewing, and container interactions are now powered through Baritone pathfinding with a dedicated `BaritoneTransitCoordinator`.
- **21×3×21 room obstruction clearing**: Automatically scans the 21×3×21 room construction area above the floor and removes naturally spawning mushrooms, torches, tallgrass, snow layers, and foliage before activation, preventing LootGames space check failures.
- **Surface pre-routing**: Explorer routes across surface terrain directly above underground dungeons before starting excavation and descent.
- **Mid-path structure discovery**: Periodically scans loaded chunks during active routes to immediately intercept and path to newly spotted structures.
- **Continuous sprinting**: Player automatically sprints continuously ("swifts") while navigating when hunger levels permit.

## Fixes & Improvements

- **Room center approach & master activation**: Fixed player stopping 3–4 blocks away from the middle block without right-clicking. The player now steps directly into reach and uses a fallback right-click mechanism to reliably trigger `PuzzleMasterBlock`.
- **Mining crosshair drift & cooldown**: Fixed camera aiming during block breaking so the crosshair stays locked on the targeted block without resetting break progress, and smoothly maintains heading through a 2-second (40-tick) cooldown after mining finishes.
- **Trash cleanup & previous run debris**: Fixed `/trash_can` triggering for ServerUtilities by supporting 27-slot chest inventories, and extended cleanup to discard dungeon wall bricks, lights, and excavated rock accumulated from previous runs.
- **Hotbar backpack retention**: Prevented backpacks in hotbar slots from being repeatedly cycled into main inventory while collecting reward chests inside structures.
- **Visual overlay artifacts**: Fixed lingering red/green pathlines when stopping or toggling exploration, and isolated solver navigation to only render lines between distant board tiles.
- **Structure state protection**: Eliminated false solved markings on navigation replans or pathing interruptions, ensuring unvisited dungeons remain queued for discovery.
