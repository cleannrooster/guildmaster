package dev.campaigncore.compat;

/** Read-only identifiers used solely to upgrade worlds from the previous release. */
public final class LegacyCampaignCompatibility {
    public static final String NAMESPACE = "guildmaster";
    public static final String SAVED_DATA_KEY = "guildmaster_act_one";
    public static final String ENCOUNTER_TAG_PREFIX = "guildmaster_encounter=";
    public static final String TUTORIAL_BOSS_TAG = "guildmaster_tutorial_boss";
    private LegacyCampaignCompatibility(){}
}
