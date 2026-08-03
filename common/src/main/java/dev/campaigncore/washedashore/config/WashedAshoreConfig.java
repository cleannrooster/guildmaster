package dev.campaigncore.washedashore.config;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

public final class WashedAshoreConfig {
    public static final WashedAshoreConfig INSTANCE = new WashedAshoreConfig();
    public int beachSearchRadius=10240, settlementMinimumDistance=300, settlementMaximumDistance=800;
    public double graveyardMinimumProgress=.45, graveyardMaximumProgress=.70;
    public int graveyardMaximumLateralOffset=32, guideMinimumDistance=20, guideMaximumDistance=120;
    public int ravenMinimumDistanceFromSettlement=600, generationAttemptLimit=96;
    public int settlementSeparation=320;
    public int instanceMinimumSeparation=4096;
    public int firstAwakeningMovementTicks=80,deathRespawnMovementTicks=40,obstructionGraceTicks=10;
    public int respawnProtectionTicks=60,maximumRespawnProtectionTicks=100;
    public double minimumMovementDistance=.02;
    public boolean disableAttacksWhileRecovering=true,disableJumpingWhileRecovering=true,hostileMobIgnoreWhileRecovering=true;
    private WashedAshoreConfig(){}

    /** Overlays the singleton with values from a datapack config object; absent keys keep code defaults. */
    public static void load(JsonObject json){
        WashedAshoreConfig c=INSTANCE;
        c.beachSearchRadius=GsonHelper.getAsInt(json,"beach_search_radius",c.beachSearchRadius);
        c.settlementMinimumDistance=GsonHelper.getAsInt(json,"settlement_minimum_distance",c.settlementMinimumDistance);
        c.settlementMaximumDistance=GsonHelper.getAsInt(json,"settlement_maximum_distance",c.settlementMaximumDistance);
        c.graveyardMinimumProgress=GsonHelper.getAsDouble(json,"graveyard_minimum_progress",c.graveyardMinimumProgress);
        c.graveyardMaximumProgress=GsonHelper.getAsDouble(json,"graveyard_maximum_progress",c.graveyardMaximumProgress);
        c.graveyardMaximumLateralOffset=GsonHelper.getAsInt(json,"graveyard_maximum_lateral_offset",c.graveyardMaximumLateralOffset);
        c.guideMinimumDistance=GsonHelper.getAsInt(json,"guide_minimum_distance",c.guideMinimumDistance);
        c.guideMaximumDistance=GsonHelper.getAsInt(json,"guide_maximum_distance",c.guideMaximumDistance);
        c.ravenMinimumDistanceFromSettlement=GsonHelper.getAsInt(json,"raven_minimum_distance_from_settlement",c.ravenMinimumDistanceFromSettlement);
        c.generationAttemptLimit=GsonHelper.getAsInt(json,"generation_attempt_limit",c.generationAttemptLimit);
        c.settlementSeparation=Math.max(256,GsonHelper.getAsInt(json,"settlement_separation",c.settlementSeparation));
        c.instanceMinimumSeparation=Math.max(1,GsonHelper.getAsInt(json,"instance_minimum_separation",c.instanceMinimumSeparation));
        c.firstAwakeningMovementTicks=GsonHelper.getAsInt(json,"first_awakening_movement_ticks",c.firstAwakeningMovementTicks);
        c.deathRespawnMovementTicks=GsonHelper.getAsInt(json,"death_respawn_movement_ticks",c.deathRespawnMovementTicks);
        c.obstructionGraceTicks=GsonHelper.getAsInt(json,"obstruction_grace_ticks",c.obstructionGraceTicks);
        c.respawnProtectionTicks=GsonHelper.getAsInt(json,"respawn_protection_ticks",c.respawnProtectionTicks);
        c.maximumRespawnProtectionTicks=GsonHelper.getAsInt(json,"maximum_respawn_protection_ticks",c.maximumRespawnProtectionTicks);
        c.minimumMovementDistance=GsonHelper.getAsDouble(json,"minimum_movement_distance",c.minimumMovementDistance);
        c.disableAttacksWhileRecovering=GsonHelper.getAsBoolean(json,"disable_attacks_while_recovering",c.disableAttacksWhileRecovering);
        c.disableJumpingWhileRecovering=GsonHelper.getAsBoolean(json,"disable_jumping_while_recovering",c.disableJumpingWhileRecovering);
        c.hostileMobIgnoreWhileRecovering=GsonHelper.getAsBoolean(json,"hostile_mob_ignore_while_recovering",c.hostileMobIgnoreWhileRecovering);
    }
}
