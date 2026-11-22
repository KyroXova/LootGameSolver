# LootGameSolver 3.1.1

This hotfix makes Baritone's temporary block handling safe and limits automatic inventory movement to active Explorer navigation. A separate retrospective section records Baritone integration details that were missing or only briefly summarized in the 3.1.0 notes.

**Full comparison:** [3.1.0...3.1.1](https://github.com/KyroXova/LootGameSolver/compare/3.1.0...3.1.1).

## Fixed in 3.1.1

### Baritone inventory handling

- Disabled automatic Baritone inventory movement by default, at client startup, while Explorer navigation is paused, and after it stops. Explorer enables it only after successfully taking control of navigation.
- Removed the automatic behavior that kept moving acceptable building blocks into hotbar slot 9 during normal play.
- When Baritone needs a placeable block for a scaffold or construction placement, it now uses an empty hotbar slot first. If the hotbar is full, it may temporarily swap only a recognized throwaway building block; tools, equipment, backpacks, and other non-block items are left alone.
- Restores the displaced stack to its original slot after placement activity ends or Explorer is interrupted. Restoration waits for a safe inventory state and checks that the original slot contents have not changed. If no safe slot is available, Baritone leaves the inventory alone.
- Avoids repeating a swap when a suitable placeable block is already in the hotbar. Temporary blocks are not reserved there after their placement task ends.
- Restored placement on normal Explorer travel routes so Baritone can place a temporary step or scaffold when a route requires it; no-break puzzle approaches still cannot place blocks.

### Explorer room-entry cleanup

- When Explorer reaches a puzzle room with eligible mined-block trash and inventory pressure, it now pauses puzzle startup, runs the existing trash-can cleanup, then starts solving. Cleanup also works after the navigation route has stopped at the room.

## Earlier 3.1.0 details omitted from its release notes

These features shipped in 3.1.0. They are listed here to document details that the earlier changelog did not call out individually; they are not new in 3.1.1.

### Baritone navigation and placement

- Explorer uses the bundled Minecraft 1.7.10 Baritone fork for discovered-structure travel, outward search legs, room approaches, puzzle-center movement, reward-chest views, and selected return or storage routes. A separate Baritone installation is not required.
- When terrain blocks a route, Baritone's existing placement behavior can use a placeable block for a temporary step or scaffold to get unstuck. This 3.1.1 patch adds the Explorer-only activation and temporary, inventory-safe hotbar handling described above.
- Explorer controls Baritone's navigation goals, pauses or yields for player input and interruptions, and cancels its goals when navigation ends. The integration also disables embedded Baritone chat commands and coordinates route overlays and camera control.
- Surface travel is the default. Explorer's cave-mode setting controls whether Baritone may use cave routes between structures.

### Earlier route and interaction fixes

- Baritone can resume Explorer's structure-search routes after interruptions, return toward completed structures, and approach reward views without mining through the chest area.
- Puzzle and reward handoffs keep their active destination, avoid competing search routes, and allow a visible in-reach interaction while moving. Chest approaches also account for standing close to or above the target.
- Camera steering follows Baritone's path lookahead and yields for puzzle interactions or mining, preventing the travel camera from overriding mining aim.
- Explorer's terrain routing handles water and dry shore recovery, lily pads, ice, and snow layers; it steps up low snow, detours around tall snow, and avoids walking on leaves or snow-covered foliage.
- Progressive search continues into newly loaded chunks, avoids recently visited areas, and can rescan saved regions in single-player worlds for newly generated structures.

### Inventory and container integration

- LGS uses Baritone's inventory-control API for hotbar item use and guarded clicks in open containers. Container snapshots and stale-window checks help storage and reward interactions avoid acting on a changed screen; a standalone fallback remains available when Baritone is absent.
- Baritone's inventory handling also supports Explorer tool and block selection. The hotfix above documents and fixes the placeable-block case, including avoiding a permanent slot 9 reservation and restoring a safely displaced item.

## Notes

Explorer and automatic storage remain experimental. Supervise automation around hazards and valuable inventory. Storage recovery still requires a configured destination, marked containers, server commands, and permissions; use `/lgs help` for command details.
