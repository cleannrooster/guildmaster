# Campaign Core API guide

This guide describes Campaign Core **as implemented in version 0.1.0 for Minecraft
1.21.1**. It does not describe planned systems that are not yet present.

Campaign Core currently provides:

- an in-memory campaign definition registry;
- persistent generic campaign, player, location, encounter, and variable containers;
- the bundled `campaign_core:washed_ashore` campaign;
- Immersive Messages presentation helpers;
- a client request for showing available Washed Ashore quests;
- operator and player commands;
- migration from the pre-Campaign Core world format.

It does **not** yet provide datapack campaign loading, reload listeners, generic
trigger/action registries, a generic campaign manager, or public lifecycle events.

## Depending on Campaign Core

Campaign Core is an Architectury project with common, Fabric, and NeoForge outputs.
There is not yet a separately published API-only artifact. During local development,
depend on the appropriate loader JAR or on the `common` project:

```groovy
dependencies {
    modImplementation files("../campaign-core/fabric/build/libs/campaign-core-fabric-0.1.0+1.21.1.jar")
}
```

For another module in the same Gradle build:

```groovy
dependencies {
    modImplementation project(path: ":common", configuration: "namedElements")
}
```

Use `compileOnly` instead when Campaign Core should be an optional integration.
Runtime integrations should test whether `campaign_core` is loaded before directly
calling its classes.

## Identifiers

The mod ID is `campaign_core`.

```java
ResourceLocation coreId = CampaignCore.id("my_path");
// campaign_core:my_path

ResourceLocation washedAshoreId = CampaignCore.washedAshoreId("location/beach");
// campaign_core:washed_ashore/location/beach
```

The bundled campaign ID is:

```java
CampaignCore.WASHED_ASHORE
// campaign_core:washed_ashore
```

Other mods should use their own namespace for their own campaigns:

```java
ResourceLocation MY_CAMPAIGN =
        ResourceLocation.fromNamespaceAndPath("example_mod", "my_campaign");
```

## Registering a campaign

The current definition contains only an ID and a positive definition version:

```java
CampaignDefinition definition =
        CampaignApi.registry().register(MY_CAMPAIGN, 1);
```

Registry operations:

```java
Optional<CampaignDefinition> found =
        CampaignApi.registry().get(MY_CAMPAIGN);

Collection<CampaignDefinition> all =
        CampaignApi.registry().definitions();
```

Register during mod initialization. Duplicate IDs throw `IllegalStateException`, and
versions below 1 throw `IllegalArgumentException`. The registry has no reload,
replacement, removal, or synchronization API.

Registration currently makes a campaign visible to `/campaign list`; it does not
start the campaign, create saved state, or run progression logic. Reloadable campaign
skeletons can replace the minimal registered definition as described below.

## Generic persistent state

Generic state is stored in the overworld data storage under
`campaign_core_campaigns`. Calling `CampaignSavedData.get` from any dimension returns
that server's overworld-backed record:

```java
CampaignSavedData data = CampaignSavedData.get(serverLevel);
```

Available lookups:

```java
Map<ResourceLocation, CampaignInstance> campaigns = data.campaigns();
CampaignInstance instance = data.campaign(MY_CAMPAIGN);

PlayerCampaignProgress progress =
        data.player(player.getUUID(), MY_CAMPAIGN);
```

`campaigns()` returns an unmodifiable outer map. `player(...)` creates missing player
progress. Mutating returned progress or instance collections does not automatically
mark the saved record dirty; call:

```java
data.setDirty();
```

after a logical mutation.

There is currently no public method that creates and inserts a new
`CampaignInstance` into the saved campaign map. The bundled migration creates the
Washed Ashore instance internally. External campaigns can register definitions and
use player progress, but full generic instance creation needs a future manager/API
addition.

## `CampaignInstance`

A campaign instance contains:

```java
UUID instanceId();
ResourceLocation campaignId();
int definitionVersion();
CampaignGenerationStatus generationStatus();

Map<ResourceLocation, ResolvedLocation> locations();
Map<ResourceLocation, EncounterState> encounters();
Set<ResourceLocation> completedWorldObjectives();
Map<ResourceLocation, CampaignValue> variables();
```

Constructing an instance creates a random instance UUID and starts it in
`UNINITIALIZED`:

```java
CampaignInstance instance = new CampaignInstance(MY_CAMPAIGN, 1);
```

`restoreIdentity(...)` is public because it is used by persistence and migration. It
should not normally be used as a gameplay transition API:

```java
instance.restoreIdentity(
        existingUuid,
        2,
        CampaignGenerationStatus.COMPLETE
);
```

Generation statuses are:

```text
UNINITIALIZED
SEARCHING
PLACING
COMPLETE
DEGRADED
FAILED
```

## Player progress

`PlayerCampaignProgress` uses resource IDs rather than campaign-specific enums:

```java
PlayerCampaignProgress progress =
        data.player(player.getUUID(), MY_CAMPAIGN);

progress.setCurrentStage(id("stage/arrival"));
progress.activeObjectives().add(id("objective/find_shelter"));
progress.completedObjectives().add(id("objective/reach_town"));
progress.discoveredLocations().add(id("location/old_tower"));
progress.defeatedEncounters().add(id("encounter/guardian"));
progress.variables().put(id("variable/reputation"), CampaignValue.of(4));

data.setDirty();
```

The exposed collections are mutable. Campaign Core does not currently enforce
objective exclusivity or automatically remove a completed objective from the active
set. Callers must maintain their own invariants.

## Typed variables

`CampaignValue` supports seven persisted types:

| Type | Factory |
|---|---|
| Boolean | `CampaignValue.of(true)` |
| Integer | `CampaignValue.of(12)` |
| Double | `CampaignValue.of(2.5D)` |
| String | `CampaignValue.of("value")` |
| Resource location | `CampaignValue.of(resourceLocation)` |
| Block position | `CampaignValue.of(blockPos)` |
| UUID | `CampaignValue.of(uuid)` |

Example:

```java
ResourceLocation reputation =
        ResourceLocation.fromNamespaceAndPath("example_mod", "variable/reputation");

progress.variables().put(reputation, CampaignValue.of(10));
data.setDirty();

CampaignValue value = progress.variables().get(reputation);
if (value != null && value.type() == CampaignValue.Type.INTEGER) {
    int amount = (Integer) value.value();
}
```

The record's public constructor accepts `Object`; prefer the `of(...)` factories.
There are no typed getter or arithmetic helpers yet.

## Locations

A resolved location stores its ID, dimension ID, and immutable block position:

```java
ResourceLocation locationId = id("location/meeting_place");

ResolvedLocation location = new ResolvedLocation(
        locationId,
        level.dimension().location(),
        meetingPosition
);

instance.locations().put(locationId, location);
data.setDirty();
```

Campaign Core does not currently resolve, validate, generate, or teleport to generic
locations. Those behaviors still exist only in the bundled Washed Ashore runtime.

## Encounters

Generic `EncounterState` currently stores:

```java
String status();
UUID activeBossUuid();
BlockPos anchor();
BlockPos spawn();
boolean oneShot();
```

State can be restored as a unit:

```java
EncounterState encounter = new EncounterState();
encounter.restore(
        "ACTIVE",
        boss.getUUID(),
        arenaCenter,
        boss.blockPosition(),
        true
);

instance.encounters().put(id("encounter/guardian"), encounter);
data.setDirty();
```

The status is currently a free string in the generic container. Generic activation,
spawning, scaling, recovery, and callbacks are not implemented. The bundled
`washedashore.encounter.EncounterManager` supplies those behaviors specifically for
Washed Ashore.

## Messaging

Message bundles are loaded from:

```text
data/<namespace>/campaign_messages/**/*.json
```

The file path forms the ID prefix and each root property is one message. For example,
`data/example/campaign_messages/my_campaign/messages.json` containing:

```json
{
  "introduction": {
    "channel": "story",
    "translation": "message.example.introduction"
  },
  "find_shelter": {
    "channel": "objective",
    "translation": "objective.example.find_shelter"
  }
}
```

defines:

```text
example:my_campaign/messages/introduction
example:my_campaign/messages/find_shelter
```

Supported channels are:

```text
story
objective
quest
ambient
warning
completion
announcement
```

Unknown fields, unknown channels, blank translations, invalid IDs, and duplicate
message IDs reject the message reload. Send a loaded definition from server code:

```java
CampaignMessageManager.send(
        player,
        ResourceLocation.fromNamespaceAndPath(
                "example",
                "my_campaign/messages/find_shelter"
        )
);
```

Runtime translation arguments are supplied after the ID:

```java
CampaignMessageManager.send(player, messageId, settlementName);
```

For multiple `quest` messages, pass a vertical index:

```java
CampaignMessageManager.send(player, messageId, 1, argument);
```

The channel controls Immersive Messages styling. A rendering or integration failure
falls back to localized chat. Missing message IDs log a warning and show the ID in
chat so authoring errors remain visible.

Washed Ashore's bundle is
`data/campaign_core/campaign_messages/washed_ashore/messages.json`. Its Java logic
now supplies local IDs rather than translation keys or presentation channels.
`CampaignQuestMessages.playAvailable(player)` displays its three currently available
regional objectives: the Consuming Dread (Dark Forest), Investigate Devil's Crossing,
and Warn the Distant Settlement (Regional Encounter C). Each carries its own objective
marker while incomplete.

### Devil's Crossing horde (temporary)

The Devil's Crossing encounter (`campaign_core:washed_ashore/thrasher`) is authored
around a Thrasher boss that is not yet finished. Until it is, the encounter's
`activation.on_full.action` is `raise_crossing_horde`, and an optional `horde` block
on the encounter defines the stand-in: successive waves of mobs that erupt from the
ground once the investigation meter fills. Each wave rises only after the previous is
cleared; all members are capped with a helmet so daylight cannot burn them.

```json
"horde": {
  "spawn_radius": 9,
  "scan_radius": 64,
  "waves": [
    { "members": [ { "entity": "minecraft:husk", "count": 4 }, { "entity": "minecraft:skeleton", "count": 2 } ] }
  ]
}
```

`CrossingHordeManager` drives the waves and completion from world-level state (a start
flag plus a persisted wave counter on the act). Restoring the real boss is a pure-data
change: drop the `horde` block and point `on_full.action` back at a boss-spawn action.

### Sculk Surface arena

`campaign_core:washed_ashore/sculk_surface` is a death-triggered arena at its own layout
slot (`sculk_surface`), resolved on the settlement→forest bearing but further out, with a
large-radius fallback near the settlement. It becomes eligible once a player has cleared
two regional objectives (`EncounterManager.hasCompletedRequiredRegionalObjectives`). When
such a player first approaches, a swath of ground converts to sculk and is dressed with
sensors and shriekers; after `mob_deaths_to_trigger` mobs die within `scan_radius`, a
Sculken Raven scaled to `health_scale` / `damage_scale` rises, reinforced by `wave_count`
waves of `wave_size` `wave_entity` spawned `wave_delay_ticks` apart. It is won only when a
player is credited with the Raven's kill. Tuning lives in the encounter's `sculk` block:

```json
"sculk": {
  "health_scale": 0.5,
  "damage_scale": 0.5,
  "mob_deaths_to_trigger": 10,
  "formation_radius": 20,
  "scan_radius": 40,
  "wave_entity": "mebahelcreaturesdraugr:skeleton_warrior",
  "wave_size": 5,
  "wave_count": 3,
  "wave_delay_ticks": 400
}
```

`SculkSurfaceManager` owns the formation, the death trigger, wave pacing, boss scaling and
player-credit completion, all from persisted world-level act state. Damage scaling applies
to the `ATTACK_DAMAGE` attribute; damage a boss deals through custom (non-attribute) code
is outside its reach.

## Client quest request

The client can ask the server to display its available Washed Ashore quests:

```java
CampaignClientNetwork.requestAvailableQuests();
```

Only call this from the physical client. The server remains authoritative and derives
quest availability from `WashedAshoreSavedData`.

The default grave-accent/backtick key uses this request. It is exposed in Minecraft's
Controls screen as **Campaign Core → Show Available Quests**.

Automatic playback happens once per client connection, including integrated and
dedicated servers, but not on dimension changes. It is controlled by:

```text
config/campaign_core-client.properties
showAvailableQuestsOnServerJoin=true
```

## Bundled Washed Ashore state

The bundled campaign still has a richer campaign-specific runtime alongside the
generic mirror:

```java
WashedAshoreSavedData saved = WashedAshoreSavedData.get(level);
WashedAshoreInstance world = saved.act();
WashedAshoreProgress playerProgress = saved.player(player.getUUID());
```

Important campaign-specific types include:

- `WashedAshoreStage`
- `RegionalQuestStage`
- `WashedAshoreGenerationStatus`
- `EncounterAnchor`
- `EncounterStatus`
- `WashedAshoreLayoutGenerator`
- `EncounterManager`
- `ProneRecoveryManager`

After directly changing Washed Ashore state, call:

```java
saved.dirty();
```

These classes expose implementation methods publicly because the refactor is in
progress. They are not yet a stable cross-campaign API.

## Bundled Settlers subsystem

The **Settlers** subsystem is merged into this jar under `dev.campaigncore.settlers.*`.
It keeps its own `settlers:` resource namespace for all assets and data (a registry
namespace is independent of the mod id), so no settler `ResourceLocation`s change.
Its content-namespace constant `SettlersMod.MOD_ID` is therefore still `"settlers"`,
not `campaign_core`.

Because both subsystems now run in one process, campaign code calls Settlers directly
rather than across a mod boundary. The two managers most relevant to campaign code:

```java
// Persistent registry of all settlements in a level (SavedData "settlers_settlements").
SettlementManager settlements = SettlementManager.get(serverLevel);
List<Settlement> all = settlements.all();
Optional<Settlement> here = settlements.at(pos);      // settlement containing a position
Optional<Settlement> near = settlements.nearest(pos); // closest settlement

// Runtime placement of the authored frontier hub (used by Washed Ashore layout).
Optional<FrontierHubRuntimePlacer.JobStatus> status =
        FrontierHubRuntimePlacer.status(serverLevel);
FrontierHubRuntimePlacer.StartResult start =
        FrontierHubRuntimePlacer.enqueue(commandSource, requestedPos, /* ruined */ false);
```

Settlement creation is act-owned by default. The frontier hub's structure and
template-pool resources remain bundled because `FrontierHubRuntimePlacer` uses them,
but no live structure-set resource places hubs during ordinary world generation.
Likewise, passive conversion of vanilla villages is disabled by default via
`conversion.enableSettlementConversion = false` in the Settlers configuration.

For compatibility with older worlds or packs, `legacy/natural_settlement_worldgen/`
contains a ready-to-copy datapack restoring random frontier-hub placement. Enabling
the legacy conversion config restores probabilistic vanilla-village conversion;
neither legacy path is used by act-driven placement.

The two saved-data stores (`campaign_core_campaigns` and `settlers_settlements`)
coexist unchanged; there is no persistence unification. Settler entity attributes are
registered from the loader entrypoints (`FabricDefaultAttributeRegistry` on Fabric,
`EntityAttributeCreationEvent` on NeoForge). Settlers' JUnit 5 suite is merged under
`common/src/test/java/dev/campaigncore/settlers/**` and runs with `gradlew test`.

Like the Washed Ashore runtime, these Settlers classes expose implementation methods
publicly and are not yet a stable cross-mod API.

## Datapack campaign skeletons

Campaign skeletons are loaded from:

```text
data/<namespace>/campaigns/*.json
```

The file path determines the campaign ID. For example,
`data/example/campaigns/my_campaign.json` defines `example:my_campaign`.

```json
{
  "version": 1,
  "initial_stage": "stage/not_started",
  "stages": [
    "stage/not_started",
    "stage/arrival",
    "stage/complete"
  ],
  "objectives": [
    "objective/find_shelter"
  ],
  "locations": [
    "location/landing_site"
  ],
  "encounters": [
    "encounter/guardian"
  ],
  "hooks": [
    "hook/special_opening"
  ]
}
```

IDs without a namespace are scoped beneath the campaign ID. In the example,
`stage/arrival` becomes `example:my_campaign/stage/arrival`. Fully qualified resource
locations are also accepted.

`version`, `initial_stage`, `stages`, `objectives`, `locations`, and `encounters` are
required. `hooks` is optional. Arrays reject duplicate IDs, `stages` must not be
empty, the initial stage must occur in `stages`, unknown JSON fields are errors, and
the entire definition reload is atomic.

Use Minecraft's `/reload` command after changing a datapack. Successful reloads are
logged with the loaded campaign IDs. `/campaign inspect <campaign>` shows the loaded
skeleton and its runtime-instance state.

The repository also contains encounter JSON examples at:

```text
data/campaign_core/campaign_encounters/*.json
```

Those encounter files are still **not loaded**. Java defaults in the Washed Ashore
encounter manager remain authoritative. The following definition systems are not
implemented:

```text
campaign_acts/
campaign_objectives/
campaign_locations/
registered trigger types
registered action types
```

Do not ship a datapack expecting those definitions to execute yet.

## Hub incident definitions

Washed Ashore hub scheduling is defined under
`data/<namespace>/campaign_hubs/*.json`. A hub selects the existing campaign layout
slot named by `slot`, has a positive `tier`, and defaults to a 30-minute selection
interval and a 192-block player activation radius. An optional `incidents` list
restricts its incident pool; an empty list accepts every definition of the same tier.

Reusable incidents are loaded from
`data/<namespace>/campaign_hub_incidents/*.json`. Required fields are `tier`,
`objective`, `spawn`, and a non-empty `entities` list. Supported objectives are
`kill_group`, `kill_leader`, and `defend_location`. Supported spawn strategies are
`around_hub`, `approaching_hub`, and `moving_patrol`. Optional fields include
`weight`, `duration_ticks`, `count`, `minimum_distance`, `maximum_distance`, and
`defense_radius`.

Hub clocks, active definitions, locations, member UUIDs, patrol destinations, and
recent selections are persisted in the Washed Ashore overworld saved data. Selection
waits for a player inside the hub activation radius and avoids immediately repeating
the previous incident where another eligible definition exists.

Example incident:

```json
{
  "tier": 1,
  "weight": 10,
  "duration_ticks": 12000,
  "objective": "kill_group",
  "spawn": "approaching_hub",
  "entities": ["minecraft:slime"],
  "count": 8,
  "minimum_distance": 40,
  "maximum_distance": 80
}
```

## Commands

Available to all players:

```text
/campaign quests available
/campaign quests complete
/campaign quests locked
```

Operator-only generic commands:

```text
/campaign list
/campaign inspect <campaign>
```

Operator-only Washed Ashore development commands are rooted at:

```text
/campaign washed_ashore ...
```

They include inspection, generation/reset, stage changes, teleporting, encounter
controls, regional quest debugging, and prone recovery controls. These commands are
administrative tooling, not a Java API stability guarantee.

Hub incident debugging is available through:

```text
/campaign washed_ashore incident list
/campaign washed_ashore incident status
/campaign washed_ashore incident trigger <hub> <incident>
/campaign washed_ashore incident stop <hub>
```

Hub and incident arguments provide datapack-backed suggestions. Triggering requires
the incident tier to match the selected hub tier and replaces an already-active
incident at that hub.

Additional physical layouts for the shared Washed Ashore storyline can be generated
with:

```text
/campaign washed_ashore reinstance <x> <y> <z> <bypass>
```

When `bypass` is false, the requested position is the center of a normal beach
search. When true, its surface column is accepted as the starting point without the
beach-biome requirement. `instance_minimum_separation` in the Washed Ashore campaign
config controls the required distance from every existing layout (4096 by default).
Player progression remains shared. Revealed marker types and location triggers use
the closest corresponding POI across all persisted layouts.

Automatic creation of the primary layout is controlled by the normal server config
file `config/campaign_core-server.properties`:

```properties
generateInitialActLayout=true
```

It defaults to `true`. When false, new worlds leave the primary Act 1 layout
uninitialized without displaying preparation errors. Operators can still run the
`generate` or `reinstance` commands. Existing layouts are unaffected. Restart the
server after changing this setting.

## Natural storyline anchors

Campaign Core adds `campaign_core:storyline_anchor` to beach-biome local world
generation. The shipped placed feature attempts one anchor per 256 eligible beach
chunks. An anchor is a 3x3 smooth-sandstone pad, chiseled-sandstone pedestal, and
lodestone. When a player approaches a newly generated anchor, server-thread logic
records it and attempts a beach-bypassed reinstance at that position. Separation
and all normal reinstance validation still apply.

The feature itself never edits campaign saved data or loads neighboring chunks.
Servers can tune test frequency by overriding
`data/campaign_core/worldgen/placed_feature/storyline_anchor.json` and changing the
`minecraft:rarity_filter` `chance`; smaller values are more common. Only newly
generated chunks receive worldgen anchors.

## Threading and side rules

- Read and mutate saved state on the logical server thread.
- Use `CampaignSavedData.get` with a `ServerLevel`.
- Mark saved data dirty after mutation.
- Call `CampaignClientNetwork` and `CampaignCoreClient` only on the physical client.
- Do not call client classes from dedicated-server initialization.
- Register campaigns during normal mod initialization, before worlds load.

## Current API stability

Version 0.1.0 should be treated as an experimental API. In particular:

- packages under `dev.campaigncore.campaign` and `dev.campaigncore.api` are the
  intended reusable direction;
- packages under `dev.campaigncore.washedashore` are bundled-campaign
  implementation details;
- packages under `dev.campaigncore.settlers` are the merged Settlers subsystem
  (see "Bundled Settlers subsystem" above) and keep the `settlers:` namespace;
- NBT `save()` and `load()` methods are persistence internals;
- direct mutable collection access may be replaced with manager methods;
- data-driven definitions and lifecycle events will expand the registration model.

Integrations should isolate Campaign Core calls behind a small adapter so later API
changes remain inexpensive.
