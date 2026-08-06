package dev.campaigncore.prestige;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.data.CampaignSavedData;
import dev.campaigncore.washedashore.act.WashedAshoreProgress;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.message.CampaignMessages;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.UUID;

/**
 * The act-agnostic prestige rules: awarding a level for a witnessed prestige-challenge victory,
 * and the whole-character wipe that pays for it. The wipe is queued on the player's persistent
 * {@link PrestigeLedger} before it is applied, so a timely logout only defers it to the next join
 * ({@code checkPendingWipe}). Only the ledger survives the wipe; progression in every act resets
 * and the existing intro machinery washes the fresh character ashore again.
 */
public final class PrestigeManager {
    /** Added boss health/damage per prestige level of the triggering player (act-scoped). */
    public static final double HEALTH_PER_LEVEL=0.5;
    public static final double DAMAGE_PER_LEVEL=0.25;
    /** Chance per prestige level to double an act reward, overflowing past 100% into the next tier. */
    public static final double SURPASSING_CHANCE_PER_LEVEL=0.25;

    private PrestigeManager(){}

    public static int level(WashedAshoreSavedData data,UUID player,ResourceLocation actId){
        return data.player(player).prestige().level(actId);
    }
    public static double healthMultiplier(int prestige){return 1+HEALTH_PER_LEVEL*Math.max(0,prestige);}
    public static double damageMultiplier(int prestige){return 1+DAMAGE_PER_LEVEL*Math.max(0,prestige);}

    /**
     * How many times an act reward is granted for a given prestige level: the surpassing chance's
     * whole part is guaranteed, its remainder is one extra roll at that probability. Prestige 0 →
     * always 1; prestige 4 (100%) → always 2; prestige 5 (125%) → 2, with a 25% chance of 3.
     */
    public static int surpassingRolls(int prestige,net.minecraft.util.RandomSource random){
        double chance=SURPASSING_CHANCE_PER_LEVEL*Math.max(0,prestige);
        int rolls=1+(int)chance;
        return random.nextDouble()<chance-(int)chance?rolls+1:rolls;
    }

    /** Piles the triggering player's act prestige onto a boss's base stats (no-op at level 0). */
    public static void applyDifficulty(net.minecraft.world.entity.LivingEntity living,int prestige){
        if(prestige<=0)return;
        var health=living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if(health!=null){health.setBaseValue(Math.max(1,health.getBaseValue()*healthMultiplier(prestige)));living.setHealth(living.getMaxHealth());}
        var damage=living.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        if(damage!=null)damage.setBaseValue(Math.max(0,damage.getBaseValue()*damageMultiplier(prestige)));
    }

    /** Credits a witnessed prestige victory: level up the act's ledger entry, then take everything. */
    public static void award(ServerLevel level,WashedAshoreSavedData data,ServerPlayer invoker,ResourceLocation actId){
        PrestigeLedger ledger=data.player(invoker.getUUID()).prestige();
        int newLevel=ledger.increment(actId);
        ledger.queueWipe(actId);
        data.dirty();
        CampaignCore.LOGGER.info("prestige_awarded player={} act={} level={}",invoker.getUUID(),actId,newLevel);
        CampaignMessages.send(invoker,"prestige_earned",newLevel);
        applyWipe(invoker,data);
    }

    /** Applies a wipe deferred by logout; call before any other per-player join handling. */
    public static void checkPendingWipe(ServerPlayer player,WashedAshoreSavedData data){
        if(data.player(player.getUUID()).prestige().pendingWipeAct()!=null)applyWipe(player,data);
    }

    public static void applyWipe(ServerPlayer player,WashedAshoreSavedData data){
        UUID id=player.getUUID();
        ResourceLocation actId=data.player(id).prestige().pendingWipeAct();
        WashedAshoreProgress fresh=data.player(id).resetForPrestige();
        fresh.prestige().clearPendingWipe();
        data.replacePlayer(id,fresh);
        CampaignSavedData.get(player.serverLevel()).clearPlayer(id);
        player.getInventory().clearContent();
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5f);
        player.setRespawnPosition(Level.OVERWORLD,null,0f,false,false);
        // Land the character in the overworld (the world spawn is pinned to the beach); the intro
        // machinery then re-runs the washed-ashore arrival because introPlayed is fresh.
        ServerLevel overworld=player.server.overworld();
        BlockPos spawn=overworld.getSharedSpawnPos();
        player.teleportTo(overworld,spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,player.getYRot(),0);
        CampaignMessages.send(player,"prestige_wipe");
        CampaignCore.LOGGER.info("prestige_wipe_applied player={} act={}",id,actId);
    }
}
