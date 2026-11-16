# LootGameSolver - 3.0.0 → 3.1.0

**Full Changelog**: https://github.com/KyroXova/LootGameSolver/compare/3.0.0...3.1.0

## Baritone transit & exploration

- Progressive exploration now uses 128m step legs with continuous horizontal pathing across multi-segment legs.
- Player sprints continuously ("swifts") while navigating when food and hunger permit.
- Added live remaining distance badge `[ NNm ]` to HUD line 2 during exploration.
- Pre-routing switches automatically to surface pathing above underground dungeons before commencing excavation.
- Mid-path structure discovery scans and interrupts open search paths to immediately route to newly spotted dungeons.
- Prevented premature route abandonment and eliminated false solved markings on pathing interruptions.
- Cleaned up line overlay artifacts and red/green path lines when stopping or toggling exploration.

## Room entrance & puzzle opening

- Fixed room center approach stalling where player stopped 3-4 blocks away from the center block without right-clicking.
- Player now approaches within direct reach of the master block and reliably activates it with fallback right-clicks.
- Automatically cleans room foliage, mushrooms, torches, and surface plants in the 21x3x21 construction volume to prevent construction failure messages.
- Cleaned up solver tile navigation so pathlines are only rendered when walking between distant tiles during puzzle solving.

## Mining & Camera Aiming

- Restored direct aiming at mined blocks during digging to prevent crosshair drift and block break damage resets.
- Camera heading smoothly holds a 2-second (40-tick) cooldown after mining finishes before restoring path heading.

## Inventory & Backpacks

- Kept backpacks in hotbar slots while operating inside dungeon structures rather than cycling them to upper inventory.
- Trash cleanup now recognizes standard 27-slot chest containers from `/trash_can` and automatically clears dungeon debris from previous runs.
