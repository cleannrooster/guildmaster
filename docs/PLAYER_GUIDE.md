# Player Guide

This guide explains how to play Campaign Core without revealing exact encounter
solutions or every story beat.

## 1. Beginning Washed Ashore

In a normal new world, Campaign Core prepares Act I automatically. You awaken near a
beach route and receive directions through Immersive Messages. Follow signs, roads and
the point-of-interest markers rather than expecting a traditional quest-book screen.

World preparation may take a little time because the campaign searches for suitable
terrain and places settlements incrementally. If the game announces that settlement
construction is in progress, remain in the world until the completion message appears.
First-time players who arrive before the primary beach is ready remain under Darkness
and receive a short void narrative until generation completes. The effect is removed
before they wash ashore. If generation fails, Darkness is removed and the normal
preparation error is shown instead.

Your campaign stage, discovered landmarks and completed objectives persist in the
world save.

## 2. Controls and quest information

Campaign Core adds two controls, both rebindable under the **Campaign Core** controls
category:

- **Show Available Quests** — grave accent/backtick by default. Reviews current
  regional quests and refreshes their markers.
- **Toggle Quest Markers** — `J` by default. Opens the point-of-interest marker screen,
  where individual revealed markers can be shown or hidden.

Newly discovered markers are visible by default. Marker visibility and the option to
show available quests after joining a server are stored in
`config/campaign_core-client.properties`.

Players may also use:

```text
/campaign quests available
/campaign quests complete
/campaign quests locked
```

These commands currently list the Consuming Dread and Devil's Crossing regional
quests. The marker screen covers the broader set of discovered story locations.

## 3. How progression works

Washed Ashore has one shared questline per player, even when a world contains several
physical copies of its locations. A matching graveyard, forest, crossing or settlement
from any eligible layout can advance the same story objective.

The location itself still owns physical event state such as its boss, raid, horde or
cleanup. Completing an objective at one copy records that completion for your player,
prevents duplicate campaign rewards at another copy, and leaves other eligible copies
available for players who still need them.

In multiplayer, remain reasonably close to an encounter when it ends. Campaign
completion and rewards are awarded to participating players within that encounter's
completion area.

## 4. The Act I journey

The main progression is:

1. Follow the route inland from the beach.
2. Investigate the graveyard threat.
3. Reach the first settlement, which becomes a place of safety and a respawn anchor.
4. Resolve the Dark Forest and Devil's Crossing.
5. Follow the route revealed at Devil's Crossing and defend the distant settlement.
6. Follow the revealed route toward the sculk-covered Act I finale.

Regional work includes the Dark Forest, Devil's Crossing and a distant settlement in
danger. The distant settlement is revealed after Devil's Crossing and all three objectives
must be completed before the final route opens.

### Dark Forest

The forest quest builds through atmosphere and events around livestock. Stay within
an eligible forest and pay attention to changes in sound, darkness and nearby animals.
The rare **Dread Fragment** can provide access to this activity before its normal story
unlock.

### Devil's Crossing

Discover the ruined crossing, inspect the disturbed ground and follow the progress
messages. Digging up blocks around the crossing advances the investigation much faster
than watching alone. Depending on the selected encounter data, the culmination may be a
single creature or the current temporary wave-horde implementation. The rare **Writhing
Fragment** can provide early access.

### Distant settlement

After completing Devil's Crossing, reach the warned settlement and help repel its attackers. The raid is attached to that
physical settlement, while successful quest completion is recorded on participating
players.

### Sculk Surface

Beyond the Dark Forest lies the sculk-covered Raven arena. Ten mob deaths on its active
ground feed the arena before the Sculken Raven and its supporting waves awaken.

## 5. Encounters and rewards

Encounter creatures may vary with the installed mod set. Campaign Core loads weighted
candidate pools and can prefer compatible Sculk & Scavenge creatures. Missing optional
candidate mods are ignored; the encounter falls back to another valid candidate or its
native definition.

Campaign encounters can grant dedicated loot-table rewards. A particular player only
receives a campaign objective's progression and reward once, even if the world contains
multiple matching POIs.

If an active boss disappears without dying, the encounter enters a retry delay. When
the delay ends, its physical site becomes available again and players who were actively
running that objective return to an available quest state.

### Prestige: the Fragment of Blight

Defeating the Sculken Raven rewards a **Fragment of Blight**. Hold it in your hand at
the Sculk Surface after completing the act and feed the arena its ten deaths again: the
Raven rises at **full power** (no story-fight mercy), stronger still for every prestige
level you already carry.

Win while you are present at the arena — anyone may land the killing blow — and you gain
a prestige level for the act, at a price: your character is wiped on the spot. Inventory,
experience, effects, respawn point, and all campaign progression are taken, and you wash
ashore again to begin anew. Logging out does not dodge the wipe; it is applied when you
return. Only your prestige levels persist.

Each prestige level for an act makes that act's encounters harder for you from then on
and grants a **25% surpassing chance** per level to multiply the act's encounter rewards:
at 100% the reward is always doubled, at 125% it is doubled with a 25% chance to triple,
and so on. Prestige is per act — leveling one act never changes another.

## 6. Settlements and settlers

Campaign settlements are persistent communities rather than renamed vanilla villages.
They track:

- residents and datapack-defined roles;
- homes, workplaces, sleep positions and activity anchors;
- daily routines and social behavior;
- farming, herding, fishing and stored production;
- food capacity, consumption and economic condition;
- morale, threats, sheltering and recovery;
- visitors and scripted settlement situations.

Settlers react to their schedule and surroundings. Guards and authority figures respond
differently from civilians during danger, while civilians can seek shelter or panic.
Some systems are primarily visible through behavior and administrator inspection rather
than a finished player management interface.

Ordinary villages do not automatically become Settlers settlements unless the server
owner enables the legacy conversion option.

## 7. Hub incidents

Once a campaign hub is active and a player is nearby, it can periodically select an
incident appropriate to its tier. Incidents may ask players to kill a group or leader,
defend a location, protect NPCs, or survive a battle already in progress.

Each incident belongs to a specific physical hub. Its announcement and marker point to
that hub rather than to every equivalent settlement. Incidents have durations and can
succeed or fail independently of the main Act I questline.

Successfully resolving an incident rewards every living player still within that
hub's response area. Each responder receives a personal, generous loot roll appropriate
to the hub tier. Items go into the inventory, with overflow dropped at the player's
feet. Failed or abandoned incidents do not award loot.

## 8. Recovery and respawning

The opening awakening and some respawns use a short prone-recovery sequence. During
recovery, movement and combat may be restricted and hostile mobs can temporarily
ignore the recovering player. These details are configurable by the server or pack.

Reaching the first settlement updates the campaign's safe respawn behavior.

## 9. Troubleshooting

### The world says it is preparing

Wait for terrain search and placement to finish. During settlement construction, do
not leave or shut down the world until the completion message appears.

### I cannot see a marker

Press `J`, check that the individual marker is enabled, and use the Show Available
Quests key to refresh objectives. A marker may intentionally be absent if its physical
encounter was already consumed and another eligible copy has not been generated.

### A quest will not complete

Remain near the encounter through its final death or success condition. On multiplayer
servers, completion is granted to nearby participating players rather than globally to
everyone online.

### Generation reports a failure

Ask an operator to inspect the campaign before resetting it. Reset is an administrative
saved-state operation and does not remove old structures or automatically clean up all
live encounter entities. See the [Server & Pack Guide](SERVER_GUIDE.md#7-recovery-and-reset-safety).
