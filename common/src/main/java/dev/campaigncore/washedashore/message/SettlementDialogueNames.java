package dev.campaigncore.washedashore.message;

import dev.campaigncore.settlers.settlement.SettlementManager;
import dev.campaigncore.washedashore.act.WashedAshoreInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Resolves authored hub references to the randomized Settlers name used in this world. */
public final class SettlementDialogueNames {
    private static final double MAX_NAME_DISTANCE_SQR=256.0*256.0;
    private SettlementDialogueNames(){}

    public static Component primary(ServerLevel level,WashedAshoreInstance act){
        return at(level,act==null?null:act.settlement(),"settlement.campaign_core.washed_ashore.primary");
    }
    public static Component distant(ServerLevel level,WashedAshoreInstance act){
        return at(level,act==null?null:act.otherSettlement(),"settlement.campaign_core.washed_ashore.secondary");
    }
    public static Component at(ServerLevel level,BlockPos position,String fallbackKey){
        if(position!=null){
            var settlement=SettlementManager.get(level).nearest(position)
                    .filter(value->value.center().distSqr(position)<=MAX_NAME_DISTANCE_SQR).orElse(null);
            if(settlement!=null&&settlement.name()!=null&&!settlement.name().isBlank())
                return Component.literal(settlement.name());
        }
        return Component.translatable(fallbackKey);
    }
    /** For remote encounter sites associated with a town, where the incident may be beyond 256 blocks. */
    public static Component nearest(ServerLevel level,BlockPos position,String fallbackKey){
        if(position!=null){
            var settlement=SettlementManager.get(level).nearest(position).orElse(null);
            if(settlement!=null&&settlement.name()!=null&&!settlement.name().isBlank())
                return Component.literal(settlement.name());
        }
        return Component.translatable(fallbackKey);
    }
}
