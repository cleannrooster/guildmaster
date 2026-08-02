package dev.campaigncore.washedashore.encounter;
/**
 * Lifecycle of an encounter. {@code FAILED}/{@code ABANDONED} are transient cooldown states:
 * the encounter's boss is unloaded and, after {@link EncounterAnchor#retryAt()} elapses, it is
 * automatically returned to {@code DORMANT} (its available state) so the quest can be retried.
 */
public enum EncounterStatus { DORMANT, ACTIVE, COMPLETED, RESETTING, FAILED, ABANDONED }
