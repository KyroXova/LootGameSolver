# LootGameSolver - 2.0.0 --> 2.0.1-hotfix

**Full Changelog**: https://github.com/KyroXova/LootGameSolver/compare/2.0.0...2.0.1-hotfix

## What's Changed

- Replace legacy ExampleMod packaging workflow with unified automated release pipeline by @KyroXova
- Publish reobfuscated mod JARs directly to GitHub Releases with proper version tags by @KyroXova
- Invalidate dormant cache on player interactions and key toggles in `ClientHooks` to ensure immediate responsiveness by
  @KyroXova
- Optimize dormant polling interval from 10 to 20 ticks (1s) to reduce idle client CPU load by @KyroXova
- Add reach assistance (auto-walk and flight nudge) in `SolverSession` for targets further than 4.2 blocks away by
  @KyroXova
- Cache `Pos2i` constructor reflection in `LootGamesBridge` to prevent redundant class lookups during board scanning by
  @KyroXova
- Clean up test cases and refactor click calculation logic into `calculateClicksNeeded` by @KyroXova
- Remove obsolete `CODEOWNERS` and update `.gitignore` by @KyroXova
