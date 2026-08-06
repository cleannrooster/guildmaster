package dev.campaigncore.washedashore.config;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

public final class WashedAshoreConfig {
    public static final WashedAshoreConfig INSTANCE = new WashedAshoreConfig();
    public int beachSearchRadius=10240, settlementMinimumDistance=384, settlementMaximumDistance=768;
    public double graveyardMinimumProgress=.45, graveyardMaximumProgress=.70;
    public int graveyardMaximumLateralOffset=32, guideMinimumDistance=20, guideMaximumDistance=120;
    public int ravenMinimumDistanceFromSettlement=1400,ravenMaximumDistanceFromSettlement=2200;
    public int forestMinimumDistanceFromSettlement=1200,forestMaximumDistanceFromSettlement=2200;
    public int otherSettlementMinimumDistance=3200,otherSettlementMaximumDistance=4800;
    public int sculkMinimumDistanceBeyondForest=900,sculkMaximumDistanceBeyondForest=1500;
    public int generationAttemptLimit=96;
    public int frontierHubPlacementAttempts=8;
    public int settlementSeparation=512;
    public int instanceMinimumSeparation=12288;
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
        c.ravenMaximumDistanceFromSettlement=GsonHelper.getAsInt(json,"raven_maximum_distance_from_settlement",c.ravenMaximumDistanceFromSettlement);
        c.forestMinimumDistanceFromSettlement=GsonHelper.getAsInt(json,"forest_minimum_distance_from_settlement",c.forestMinimumDistanceFromSettlement);
        c.forestMaximumDistanceFromSettlement=GsonHelper.getAsInt(json,"forest_maximum_distance_from_settlement",c.forestMaximumDistanceFromSettlement);
        c.otherSettlementMinimumDistance=GsonHelper.getAsInt(json,"other_settlement_minimum_distance",c.otherSettlementMinimumDistance);
        c.otherSettlementMaximumDistance=GsonHelper.getAsInt(json,"other_settlement_maximum_distance",c.otherSettlementMaximumDistance);
        c.sculkMinimumDistanceBeyondForest=GsonHelper.getAsInt(json,"sculk_minimum_distance_beyond_forest",c.sculkMinimumDistanceBeyondForest);
        c.sculkMaximumDistanceBeyondForest=GsonHelper.getAsInt(json,"sculk_maximum_distance_beyond_forest",c.sculkMaximumDistanceBeyondForest);
        c.generationAttemptLimit=GsonHelper.getAsInt(json,"generation_attempt_limit",c.generationAttemptLimit);
        c.frontierHubPlacementAttempts=Math.max(1,GsonHelper.getAsInt(json,"frontier_hub_placement_attempts",c.frontierHubPlacementAttempts));
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
        c.settlementMinimumDistance=Math.max(1,c.settlementMinimumDistance);
        c.settlementMaximumDistance=Math.max(c.settlementMinimumDistance,c.settlementMaximumDistance);
        c.ravenMinimumDistanceFromSettlement=Math.max(1,c.ravenMinimumDistanceFromSettlement);
        c.ravenMaximumDistanceFromSettlement=Math.max(c.ravenMinimumDistanceFromSettlement,c.ravenMaximumDistanceFromSettlement);
        c.forestMinimumDistanceFromSettlement=Math.max(1,c.forestMinimumDistanceFromSettlement);
        c.forestMaximumDistanceFromSettlement=Math.max(c.forestMinimumDistanceFromSettlement,c.forestMaximumDistanceFromSettlement);
        c.otherSettlementMinimumDistance=Math.max(1,c.otherSettlementMinimumDistance);
        c.otherSettlementMaximumDistance=Math.max(c.otherSettlementMinimumDistance,c.otherSettlementMaximumDistance);
        c.sculkMinimumDistanceBeyondForest=Math.max(1,c.sculkMinimumDistanceBeyondForest);
        c.sculkMaximumDistanceBeyondForest=Math.max(c.sculkMinimumDistanceBeyondForest,c.sculkMaximumDistanceBeyondForest);
    }
}
