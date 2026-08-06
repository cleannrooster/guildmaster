# Server & Pack Guide

This guide covers normal installation, configuration and recovery. Development and
datapack APIs remain in [API.md](API.md); the complete command syntax is in
[COMMANDS.md](COMMANDS.md).

## 1. Installation model

Campaign Core is one mod jar containing both the campaign runtime and Settlers. Do not
install a separate Settlers jar. Campaign content uses `campaign_core:` identifiers;
Settlers data retains `settlers:` identifiers for compatibility.

Install the correct Fabric or NeoForge artifact and all dependencies declared by its
mod metadata. Servers and clients should use the same mod and content set. Java 21 is
required for Minecraft 1.21.1.

Back up an existing world before first migration or before using reset, conversion or
manual placement commands.

## 2. Default world behavior

With default settings:

- the primary Washed Ashore layout is generated automatically;
- campaign acts create their required frontier settlements;
- ordinary villages are not automatically converted;
- frontier hubs do not generate randomly as normal structures;
- settlers, travelers and threat responses are enabled inside created settlements;
- compatible Sculk & Scavenge encounter candidates are preferred.

Natural storyline anchors can appear rarely in newly generated beach chunks. When a
player approaches an unused anchor, it attempts to create an additional physical layout
for the same shared Washed Ashore questline, subject to separation and terrain checks.

## 3. Campaign server settings

`config/campaign_core-server.properties` contains:

```properties
generateInitialActLayout=true
preferSculkAndScavengeEncounters=true
preferRpgMinibossesSettlementRaid=true
```

- `generateInitialActLayout` controls automatic creation of the primary Act I layout
  in an uninitialized world. Turning it off does not remove existing layouts.
- `preferSculkAndScavengeEncounters` gives valid Sculk & Scavenge candidates priority
  where the encounter supports them.
- `preferRpgMinibossesSettlementRaid` makes the second-settlement raid prefer the
  RPG Minibosses roster when that mod and its configured entities are available.

Restart the server after changing these properties.

## 4. Washed Ashore layout settings

The reloadable campaign layout file is
`data/campaign_core/campaign_config/washed_ashore.json`. It controls search distances,
regional spacing, additional-instance separation, placement attempts and prone-recovery
behavior.

Important settings include:

- `beach_search_radius` — maximum primary beach search area.
- settlement, graveyard, Raven, forest, second-settlement and sculk distance ranges.
- `settlement_separation` — clearance from existing Settlers settlements.
- `instance_minimum_separation` — minimum spacing between full Washed Ashore layouts;
  currently `12288` blocks in the bundled data.
- movement, protection and restriction settings for prone recovery.

Use `/reload` after changing datapack-backed definitions. Change placement distances
only before generating new layouts; existing saved coordinates are not moved.

## 5. Settlers settings

Settlers uses `config/settlers.json`. On Fabric, Mod Menu exposes its screen when
installed; NeoForge exposes it through the mod-list Config button.

### Population

- `generateSettlers` enables settlement population.
- `enableTravelers` enables temporary visiting NPCs.
- `enableThreatResponses` enables alert, defense, shelter and recovery behavior.

### Legacy village conversion

`enableSettlementConversion` is `false` by default and is not consulted for
campaign-created settlements. If enabled, eligible generated villages are evaluated
against structure-name rules, infrastructure requirements and `conversionChance`.

Conversion can optionally remove vanilla villagers within converted settlement bounds.
Review all include/exclude and infrastructure settings before enabling this on an
existing server.

The optional legacy frontier-hub worldgen datapack and installation notes are under
`legacy/natural_settlement_worldgen/`.

## 6. Multiple physical layouts

Operators can create another layout with:

```text
/campaign washed_ashore reinstance <x> <y> <z> <bypass>
```

With `bypass=false`, the requested position is the center of a beach search. With
`bypass=true`, the requested surface column is accepted without requiring a beach
biome. Normal spacing and placement validation still apply.

All layouts serve one player questline. Players may use an eligible POI from any
layout. Encounters, raids, horde waves, boss UUIDs and incidents remain local to their
physical site; player completion and campaign rewards remain global to that player's
Act I progress.

## 7. Recovery and reset safety

### Forced POI placement

```text
/campaign washed_ashore place <poi> <x> <y> <z>
```

This is a destructive recovery/design command. It places the named primary-layout POI
at the requested location and deliberately bypasses protections for inhabited chunks,
existing structures, automatic relocation and overlapping settlements. Terrain fitting
and structure placement can permanently replace player blocks. Only basic bounded-work
and loaded-chunk safety checks remain. Make a backup first.

Guide and graveyard placement require the primary settlement to have a saved location
so their route can be oriented. Settlement, distant-settlement and Devil's Crossing
placement build their physical hub or ruin; natural-site POIs update their saved
location and corresponding idle encounter anchor.

### Campaign reset

Inspect first:

```text
/campaign washed_ashore inspect
```

The full reset command:

```text
/campaign washed_ashore reset
```

currently clears the primary layout's saved coordinates and encounter state, creates a
new primary UUID, removes additional layouts from campaign saved data, clears hub
incident records and erases every player's Washed Ashore progress. It does **not**
remove generated structures or terrain edits, despawn every tracked encounter/incident
entity, close every orphaned boss bar, or clear processed natural-anchor history.

Treat it as a last-resort saved-state reset. Stop or finish active events first, back up
the world, and expect old physical locations to remain in the terrain.

`/campaign washed_ashore encounter reset <id>` is narrower but also debug-oriented: it
only resets the named primary-instance encounter record. It does not clear player
completion or fully clean an active encounter. Prefer `fail`, `abandon` or
`combat_encounter abort` when their behavior matches the problem.

## 8. Construction safety

Frontier hubs are placed incrementally. When construction starts, keep the relevant
dimension loaded and do not stop the server until completion is announced. Operators
can inspect manual placement with `/settlers placement status`.

Manual cancellation only succeeds before placement has changed the world. It is not a
rollback mechanism.

## 9. Persistence and compatibility

Campaign state and settlement state use separate saved-data stores:

- `campaign_core_campaigns`
- `settlers_settlements`

Washed Ashore also reads its legacy-compatible saved-data key and normalizes older
campaign identifiers when loading. Do not delete or edit these data files by hand while
the server is running.

## 10. Known player-facing limits

- The generic campaign quest commands currently enumerate only the two regional quest
  state machines, not every story stage or incident.
- Several settlement economy and roster systems are primarily observable through NPC
  behavior and operator diagnostics; a complete player settlement-management UI is not
  yet present.
- Administrative reset commands are not comprehensive world-cleanup tools.
- Natural anchors affect only newly generated chunks and remain recorded after the full
  campaign reset.
