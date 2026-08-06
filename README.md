# Campaign Core

Campaign Core adds a persistent adventure campaign and living frontier settlements to
Minecraft 1.21.1. It supports Fabric and NeoForge and ships as one mod jar.

The included **Washed Ashore** campaign begins on a remote beach, leads through
graveyards, settlements and regional threats, and culminates in a sculk-infested
finale. Its companion **Settlers** system gives campaign settlements persistent
residents, jobs, routines, food production, morale and reactions to danger.

## Start here

- [Player Guide](docs/PLAYER_GUIDE.md) — controls, progression, settlements and
  spoiler-light troubleshooting.
- [Server & Pack Guide](docs/SERVER_GUIDE.md) — installation expectations,
  configuration, administration, multiple layouts and reset behavior.
- [Command Reference](docs/COMMANDS.md) — player and operator commands, grouped by
  purpose.
- [Developer API](docs/API.md) — Java and datapack integration details.

## Highlights

- A persistent Act I campaign whose progress survives restarts.
- In-world directions, quest messages and toggleable point-of-interest markers.
- Several simultaneous regional objectives that may be completed in different orders.
- Interchangeable copies of campaign POIs: any eligible copy can advance the shared
  player questline.
- Data-driven encounter candidates that can use creatures from compatible installed
  mods.
- Frontier settlements with named residents, work and home assignments, daily
  routines, production, food pressure, morale, travelers and threat responses.
- Timed settlement incidents ranging from animal attacks to high-tier monster events.
- Multiplayer-aware progression: physical encounters belong to their location while
  quest completion belongs to each participating player.

## Requirements

Campaign Core requires Java 21 and the loader-specific versions of Architectury,
GeckoLib, Cloth Config, Immersive Messages, TxniLib and Sculk & Scavenge. Fabric also
uses Fabric API, Forge Config API Port and the Fabric loader. See the distributed mod
metadata for exact version constraints.

## A note on world generation

Campaign settlements are created by the campaign by default. Random frontier-hub
world generation and automatic conversion of ordinary villages are legacy opt-ins,
not normal behavior. Existing worlds remain supported, but back up a world before
using destructive administrative recovery commands.

## For contributors

This repository contains the unified `campaign_core` mod. Campaign content uses the
`campaign_core:` namespace; the bundled Settlers subsystem intentionally retains the
`settlers:` resource namespace. Contributor rules, including the designer-owned
settlement structure restriction, are in [AGENTS.md](AGENTS.md).

Build with `gradlew build` and run the common JUnit suite with `gradlew test`.
