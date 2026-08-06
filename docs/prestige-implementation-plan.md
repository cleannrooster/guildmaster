# Prestige System — Implementation Plan

> **Status (2026-08-06):** All phases implemented. Ledger + challenge registry + Fragment of
> Blight + full-power refight + queued wipe (1–3), surpassing reward rolls in `grantRewardLoot`
> (4), act-wide difficulty scaling at `EncounterManager.activate`, the sculk arena (invoker or
> replaying trigger), crossing horde and settlement raid spawns (5), and prestige commands + docs
> (6). See `docs/API.md` "Prestige challenges" for the future-act extension points.

Completing an act unlocks that act's **prestige challenge**: an item that re-summons the act's
final boss at full power. Winning while the invoker is present grants the invoker a prestige level
**for that act only** and queues a full character wipe (persisted so logout cannot dodge it). Each
act's prestige scales only that act's encounters and rewards for that player from then on.

For Act 1 (Washed Ashore) the challenge is the **Fragment of Blight** → full-power Sculken Raven.
Future acts register their own fragment + boss against the same framework.

## Locked design decisions

- **Prestige is per-act**, keyed by the registered campaign id (`CampaignCore.WASHED_ASHORE` for
  Act 1; future acts use their own id from `CampaignApi.registry()`). Prestiging Act 1 never
  touches Act 2's prestige, scaling, or rewards.
- **The wipe is whole-character**: inventory, XP, effects, respawn point, and progression across
  *all* acts reset (the character restarts from scratch). Only the prestige ledger survives.
- Ledger storage stays physically in `WashedAshoreProgress` (the primary per-player save entry),
  but as a self-contained `PrestigeLedger` accessed only through `PrestigeManager` — future acts
  read/write through the API and never care where the ledger lives; migrating storage later means
  changing one accessor.
- Shared encounters scale by the **triggering player's** prestige *for the act the encounter
  belongs to*.
- A non-invoker landing the killing blow still grants prestige to the invoker **if the invoker is
  present** (online and within the encounter's reset radius at kill time). Absent invoker =
  fizzle: no prestige, no wipe.
- Stash cheesing is accepted. No inventory snapshotting; ender chests are not wiped.
- **Surpassing chance**: each prestige level in an act grants +25% chance to double that act's
  rewards, overflowing past 100% into the next tier. `chance = 0.25 × prestige(act)`; reward
  multiplier `= 1 + floor(chance) + (roll < frac(chance) ? 1 : 0)`. E.g. prestige 5 → 125% →
  guaranteed double, 25% chance of triple.

## Phase 1 — Data model

**New `PrestigeLedger`** (`washedashore/act/` or a new `prestige/` package)
- Wraps `Map<ResourceLocation,Integer>` (campaign id → level) plus
  `ResourceLocation pendingWipeAct` (null = no wipe queued; storing *which* act triggered the wipe
  gives correct messaging and room for future per-act wipe policies).
- `level(actId)`, `increment(actId)`, `queueWipe(actId)`, `clearPendingWipe()`, `save()`/`load()`.

**`WashedAshoreProgress`** ([act/WashedAshoreProgress.java](../common/src/main/java/dev/campaigncore/washedashore/act/WashedAshoreProgress.java))
- Embed the ledger; persist it in `save()`/`load()`.
- Add `resetForPrestige()` returning fresh progress carrying only the ledger forward. Everything
  else — stage, defeated bosses, quests, dread/crossing meters, fragment flags, intro/void flags,
  prone recovery — resets to first-run state.

**`WashedAshoreInstance`** + `WashedAshoreSavedData` save/load
- Add `UUID sculkPrestigeInvoker` (null = normal fight) beside the existing `sculkRaven` fields.
  Non-null marks the current sculk fight as a prestige fight. (Future acts store the equivalent
  invoker on their own fight state.)

**Tests**: `PrestigeLedger` round-trip with multiple act ids; `resetForPrestige` asserts the
ledger survives and everything else clears; independence test — incrementing one act id leaves
others untouched.

## Phase 2 — Per-act challenge registry + Fragment of Blight

**`PrestigeChallenges`** (small static registry)
- `register(ResourceLocation actId, Supplier<Item> fragment)`; lookup by act and by held item.
- Act 1 registers `(CampaignCore.WASHED_ASHORE, BLIGHT_FRAGMENT)` at init. Future acts add one
  line. The invoker/present/fizzle rules live in shared `PrestigeManager` code, not per act.

**Item** ([registry/CampaignItems.java](../common/src/main/java/dev/campaigncore/washedashore/registry/CampaignItems.java))
- Register `BLIGHT_FRAGMENT` (`blight_fragment`, `Rarity.EPIC`, stack 16 to match siblings).
- Source: the Sculk Surface encounter's `rewardLootTable` (act-completion reward), not dungeon
  loot — finishing the act unlocks prestiging it.

**Invocation** ([act/SculkSurfaceManager.java](../common/src/main/java/dev/campaigncore/washedashore/act/SculkSurfaceManager.java))
- `eligiblePlayerNear`: a player holding the act's registered fragment is eligible even when
  `SCULK_SURFACE` is in their `defeatedBosses` (mirrors `RegionalQuestManager`'s `holds(...)`).
- The `EncounterManager` reopen path (`needsEncounter` / `reopenForPendingPlayer`) also treats a
  fragment holder as "needing" the encounter so a consumed arena resets for them.
- Buildup ritual unchanged: fragment grants access; mob deaths on the arena still trigger the rise.
- `startEncounter`: if fragment-driven, consume one fragment, set `sculkPrestigeInvoker`, log.
  Consuming at spawn means an untriggered arena costs nothing; a started-then-failed fight costs
  the fragment. One prestige fight at a time per arena; first invoker wins.
- `spawnRaven`: when `sculkPrestigeInvoker != null`, skip the `scale(...)` halving (native path)
  and apply the prestige difficulty multiplier (Phase 5) on top of either path — native stats or
  candidate `AttributeOverrides`. Scale draugr waves by the same multiplier.

## Phase 3 — Resolution: prestige grant + queued wipe

**`onRavenDeath`** (player-credited branch)
- If `sculkPrestigeInvoker != null`: resolve the invoker among online players. Present (within
  `encounter.resetRadius()` of the anchor) → `PrestigeManager.award(invoker,
  CampaignCore.WASHED_ASHORE)`: increment that act's ledger entry, queue the wipe for that act,
  message, and apply the wipe immediately since they're online. Absent → fizzle (log + no credit).
  Either way clear `sculkPrestigeInvoker` and let `EncounterManager.complete(...)` run normally
  (bystanders who still need the encounter get regular completion credit — intentional).

**`PrestigeManager`** (act-agnostic; same package as the ledger)
- `level(data, playerUuid, actId)` / `award(player, actId)` — the only prestige API surface other
  systems touch.
- `applyWipe(ServerPlayer, WashedAshoreSavedData)`:
  - Replace the player's `WashedAshoreProgress` via `resetForPrestige()` (ledger survives).
  - Clear the player's `PlayerCampaignProgress` for **every** campaign in `CampaignSavedData`
    (whole-character wipe — all acts restart).
  - `player.getInventory().clearContent()`, XP to 0, clear effects, restore health/food.
  - Clear bed/anchor respawn; teleport to the primary beach (`SafeSpawnResolver`) so the
    washed-ashore intro re-runs as a diegetic restart. (When future acts exist, the wipe always
    returns the character to the campaign's starting act.)
- `checkPendingWipe(ServerPlayer)`: called from `WashedAshoreManager.onJoin` **before** the
  void-waiting/intro logic; applies the wipe if `pendingWipeAct` is set. The flag lives in world
  SavedData, so disconnects, crashes, and death can't dodge it (`PLAYER_CLONE` is irrelevant —
  nothing is stored on the entity).

## Phase 4 — Surpassing rewards (per act)

**`EncounterManager.grantRewardLoot`** ([encounter/EncounterManager.java](../common/src/main/java/dev/campaigncore/washedashore/encounter/EncounterManager.java))
- Pure helper `static int surpassingRolls(int prestige, RandomSource random)` implementing
  `1 + floor(0.25p) + bernoulli(frac(0.25p))`. Unit-test it (0 → always 1, 4 → always 2,
  5 → 2 or 3 at ~25%).
- Roll the reward table that many times, using the **recipient's own** prestige for the act that
  owns the encounter (all current encounters → `CampaignCore.WASHED_ASHORE`; generic campaign
  encounters resolve their act id from their owning campaign). Difficulty follows the trigger;
  rewards follow the receiver.
- On a proc (rolls > 1), send a `CampaignMessages` line so the doubling is felt.
- Optional follow-up: same multiplier on candidate `campaign_loot` ground drops.

## Phase 5 — Prestige difficulty scaling (per act)

`PrestigeManager.difficultyMultiplier(player, actId)` — e.g. health ×(1 + 0.5p),
damage ×(1 + 0.25p), constants in `CampaignServerConfig`. A player's Act 2 prestige never touches
Act 1 spawns and vice versa.

Washed Ashore call sites (all already hold the triggering player; all pass
`CampaignCore.WASHED_ASHORE`):
- `EncounterManager.activate(...)` — after `configureCandidate`; covers Undertaker + Dread +
  Thrasher spawns.
- `SculkSurfaceManager.spawnRaven` / `spawnWave` (Phase 2 hook).
- `CrossingHordeManager.begin` wave spawns.
- `SettlementRaidManager` raid spawns.

Prestige-0 players are untouched (multiplier 1.0). A leveled player triggering a shared encounter
makes it harder for everyone nearby — accepted per design decision.

## Phase 6 — Commands, messages, docs

- `WashedAshoreCommands`: `debug prestige get|set <player> <act> [level]`,
  `debug prestige queue-wipe <player>`, and a `spawn_blight`-style quest debug event to shortcut
  the ritual.
- Lang/message entries: fragment flavor text, "the blight answers" challenge-start line, prestige
  earned + wipe narration, surpassing proc line.
- Update `docs/PLAYER_GUIDE.md` and `docs/COMMANDS.md`.
- `docs/API.md`: document `PrestigeChallenges.register` + `PrestigeManager` as the extension
  points for future acts.

## Adding a future act (checklist the framework must satisfy)

1. Register the act id in `CampaignApi.registry()` (existing pattern).
2. Register its fragment via `PrestigeChallenges.register(actId, fragment)`.
3. Store an invoker UUID on the act's final-fight state; call `PrestigeManager.award(invoker,
   actId)` on a present-invoker victory.
4. Pass `actId` into `difficultyMultiplier` / `surpassingRolls` at its spawn and reward sites.

Nothing in the ledger, wipe, presence rules, or surpassing math is Act-1-specific.

## Edge cases

| Case | Behavior |
|---|---|
| Prestige Act 1 while Act 2 exists | Act 1 ledger +1; whole-character wipe resets *progression* in both acts; Act 2's prestige level is untouched. |
| Invoker logs out mid-fight | Existing unattended-failure resets the arena; fragment is lost. |
| Invoker dead/respawning at kill | Present only if within reset radius; otherwise fizzle. |
| Raven killed by environment | Existing behavior: Raven respawns; prestige fight continues. |
| Two fragment holders | First trigger sets the invoker; the other keeps their fragment. |
| Admin `/campaign washed_ashore reset` | `clearPlayers()` drops the ledger — acceptable (full-world admin reset). |
| Third-party mod data (skills etc.) | Not wiped; out of scope. |

## Suggested order

Phases 1→3 are the contained core (ledger, challenge registry, fragment, full-power refight,
queued wipe) and are shippable on their own. Phases 4→5 make prestige *mean* something and can
land as a second pass. Phase 6 rides along with whichever phase touches it.
