# Campaign Core

Campaign Core is an Architectury campaign framework for Minecraft 1.21.1 with Fabric
and NeoForge support. Its bundled reference campaign is
`campaign_core:washed_ashore`.

## Unified package

Campaign Core is a **single jar with two subsystems**:

- the campaign staging/sequencing framework and the bundled Washed Ashore campaign
  (`dev.campaigncore.*`, `campaign_core:` namespace); and
- the merged **Settlers** subsystem that powers persistent, role-driven frontier
  settlements created by campaign acts (`dev.campaigncore.settlers.*`, `settlers:`
  namespace).

Both ship and evolve together under mod id `campaign_core`. The `settlers:` resource
namespace is preserved for all Settlers assets/data (a registry namespace is
independent of the mod id), so no settler `ResourceLocation`s change. Sculk &
Scavenge remains an external dependency. Two saved-data stores coexist unchanged:
`campaign_core_campaigns` and `settlers_settlements`.

Washed Ashore drives the beach awakening, persistent regional progression, frontier
settlement/hub placement (via the in-process Settlers `FrontierHubRuntimePlacer`), the
Undertaker, Consuming Dread, Investigate Devil's Crossing, Warn the Distant Settlement,
and the Sculken Raven finale. Devil's Crossing currently raises a temporary husk and
skeleton wave horde (sunlight cannot burn them) in place of the unfinished Thrasher. A
Sculk Surface arena lies beyond the Dark Forest — once two regional objectives are done,
mob deaths on its sculk-converted ground wake a half-scaled Sculken Raven backed by draugr
waves.

Campaign-facing identifiers live below `campaign_core:washed_ashore/`. Encounter
definitions are in `data/campaign_core/campaign_encounters`, translations and item
assets are under `assets/campaign_core`, and common implementation code uses the
`dev.campaigncore` package root. Merged Settlers code lives under
`dev.campaigncore.settlers` with assets/data under the `settlers` namespace.

Reloadable campaign skeletons are authored at
`data/<namespace>/campaigns/*.json`. See the API guide for the current schema.
Data-driven Immersive Messages are authored as bundles under
`data/<namespace>/campaign_messages/`.

## Settlement creation

By default, settlements are created only as part of campaign acts. The authored
frontier hub remains available to the Washed Ashore runtime placer, but it is no
longer registered for random natural world generation. Vanilla villages also do
not convert into settlements automatically.

Both former behaviors remain available as opt-in legacy compatibility features.
The ready-to-copy natural-worldgen datapack and its installation notes live in
`legacy/natural_settlement_worldgen/`. Legacy village conversion can be enabled in
the Settlers configuration; its conversion chance is configurable separately.

## Commands

General campaign commands:

- `/campaign list`
- `/campaign inspect <campaign>`

Washed Ashore development and recovery commands are below:

- `/campaign washed_ashore inspect`
- `/campaign washed_ashore generate`
- `/campaign washed_ashore reset`
- `/campaign washed_ashore setstage <player> <stage>`
- `/campaign washed_ashore teleport <location>`
- `/campaign washed_ashore encounter ...`
- `/campaign washed_ashore quest ...`
- `/campaign washed_ashore recovery ...`

## Compatibility

Campaign Core reads the previous saved-data key, resource namespace, and encounter
metadata when loading an existing world. Loaded identifiers are normalized to
`campaign_core:washed_ashore/...`; all newly written runtime identifiers use the
Campaign Core namespace.

## Build

Run `gradlew build` to build both loader artifacts.

Run `gradlew test` for the JUnit 5 suite. It runs in the `common` project and covers
both subsystems: Campaign Core's own tests plus the merged Settlers suite under
`common/src/test/java/dev/campaigncore/settlers/**`.

Contributor guidance, including the designer-owned settlement-asset constraint, is in
[`AGENTS.md`](AGENTS.md). The current Java API and its limitations are documented in
[`docs/API.md`](docs/API.md).
