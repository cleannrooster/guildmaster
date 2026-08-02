# Campaign Core migration map

The former single-campaign implementation now lives under
`dev.campaigncore.washedashore`, while reusable state and APIs remain directly under
`dev.campaigncore`.

| Previous role | Current role |
|---|---|
| Act-specific bootstrap | `CampaignCore` plus the bundled campaign registration |
| Act instance | `WashedAshoreInstance`, mirrored to `CampaignInstance` |
| Act player progress | `WashedAshoreProgress`, mirrored to `PlayerCampaignProgress` |
| Enum stage | `campaign_core:washed_ashore/stage/*` |
| Encounter anchor | `EncounterState` plus the Washed Ashore runtime adapter |
| Layout generator | `WashedAshoreLayoutGenerator` |
| Messages | `CampaignMessages` |
| Items | `CampaignItems` |
| Development commands | `/campaign washed_ashore ...` |

## Saved-state mapping

The compatibility loader retains the historical saved-data key so existing worlds
remain discoverable. It normalizes historical resource IDs while loading and
preserves:

- campaign instance UUID and generation status;
- all generated location coordinates;
- settlement-placement completion flags;
- encounter status and active boss UUIDs;
- completed world objectives;
- player stages, discoveries, defeated encounters, quest states, and counters.

The generic record is stored as `campaign_core_campaigns`. Its bundled instance ID is
`campaign_core:washed_ashore`, with locations, encounters, objectives, stages, and
variables below that identifier.

## Intentionally unchanged mechanics

Settlers placement serialization, chunk tickets, asynchronous progress polling,
restart recovery, encounter spawning, prone recovery, regional mechanics, and
Immersive Messages behavior remain functionally unchanged. Their names and emitted
Campaign Core identifiers have been updated without replacing their gameplay logic.
