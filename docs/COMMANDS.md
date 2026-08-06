# Command Reference

Arguments in angle brackets are required. Most administration commands require
permission level 2.

## Player commands

```text
/campaign quests available
/campaign quests complete
/campaign quests locked
```

These show the current state of the Consuming Dread and Devil's Crossing regional
quests. The normal player experience uses messages, controls and the marker screen.

## Campaign inspection

```text
/campaign list
/campaign inspect <campaign>
/campaign washed_ashore inspect
```

The first two inspect registered generic campaign definitions. The Washed Ashore
inspection includes the primary layout, additional layouts, locations, encounter
states and the executing player's regional progress.

## Layout generation

```text
/campaign washed_ashore generate
/campaign washed_ashore reinstance <x> <y> <z> <bypass>
/campaign washed_ashore place <poi> <x> <y> <z>
/campaign washed_ashore reset
```

- `generate` queues the uninitialized primary layout.
- `reinstance` creates another physical layout for the shared storyline.
- `place` force-places or relocates one POI on the primary layout at the requested
  coordinates. Supported names are `beach`, `guide`, `graveyard`, `settlement`,
  `raven`, `dark_forest`, `devils_crossing`, `other_settlement` and `sculk_surface`.
  It intentionally bypasses inhabited-location, existing-structure, settlement-overlap
  and automatic-relocation safeguards. It may irreversibly overwrite player builds.
- `reset` clears campaign saved state but does not remove old structures or fully clean
  active entities. Read [reset safety](SERVER_GUIDE.md#7-recovery-and-reset-safety)
  before using it.

## Navigation and progression debugging

```text
/campaign washed_ashore setstage <player> <stage>
/campaign washed_ashore teleport beach
/campaign washed_ashore teleport guide
/campaign washed_ashore teleport undertaker
/campaign washed_ashore teleport settlement
/campaign washed_ashore teleport dread
/campaign washed_ashore teleport crossing
/campaign washed_ashore teleport other_settlement
/campaign washed_ashore teleport sculk
/campaign washed_ashore teleport raven
```

Teleport commands currently target the primary instance.

## Encounter administration

```text
/campaign washed_ashore encounter activate <id>
/campaign washed_ashore encounter reset <id>
/campaign washed_ashore encounter fail <id>
/campaign washed_ashore encounter abandon <id>
/campaign washed_ashore encounter complete <id>
```

These ID-based commands currently address encounters on the primary instance.

Candidate and live-combat tools:

```text
/campaign washed_ashore combat_encounter list <slot>
/campaign washed_ashore combat_encounter select <slot> <candidate>
/campaign washed_ashore combat_encounter start <slot>
/campaign washed_ashore combat_encounter status
/campaign washed_ashore combat_encounter abort <slot>
```

Use command suggestions for datapack-backed slot and candidate identifiers.

## Quest debugging

```text
/campaign washed_ashore quest inspect <player>
/campaign washed_ashore quest set <player> <dread|crossing> <state>
/campaign washed_ashore quest dread <player> <0-100>
/campaign washed_ashore quest crossing <player> <0-100>
/campaign washed_ashore quest event <player> <event>
```

Quest events include atmospheric tests, manifestation/spawn tests and sculk arena
controls. Use tab completion for the current event list.

## Incident administration

```text
/campaign washed_ashore incident list
/campaign washed_ashore incident status
/campaign washed_ashore incident trigger <hub> <incident>
/campaign washed_ashore incident stop <hub>
```

Trigger and stop select the nearest matching physical hub to the command source. An
incident must match the selected hub's tier.

## Recovery administration

```text
/campaign washed_ashore recovery inspect <player>
/campaign washed_ashore recovery start <player> first
/campaign washed_ashore recovery start <player> death
/campaign washed_ashore recovery start <player> scripted <ticks>
/campaign washed_ashore recovery complete <player>
/campaign washed_ashore recovery cancel <player>
/campaign washed_ashore recovery reset-first-awakening <player>
```

## Prestige administration

```text
/campaign washed_ashore prestige get <player>
/campaign washed_ashore prestige set <player> <act> <level>
/campaign washed_ashore prestige queue-wipe <player> <act>
```

`get` prints the player's per-act prestige levels and any pending wipe. `set` writes a
level directly (0 removes the entry). `queue-wipe` queues and immediately applies the
full prestige wipe for the given act — the same reset a won Fragment of Blight challenge
performs, so use it deliberately.

## Settlement administration

The `/settlers` root is operator-only:

```text
/settlers profiles
/settlers spawn <profile>
/settlers recruit [profile]
/settlers expand <role>
/settlers place_frontier_hub [intact|ruined]
/settlers placement status
/settlers placement cancel
/settlers convert
/settlers list
/settlers info
/settlers threat <state>
/settlers anchors
/settlers anchors rebuild
/settlers situation <id>
/settlers repopulate
/settlers economy
/settlers food add <amount>
```

Most settlement commands operate on the settlement at or nearest the command source.
They are diagnostic, recovery and content-authoring tools rather than required parts of
normal play.
