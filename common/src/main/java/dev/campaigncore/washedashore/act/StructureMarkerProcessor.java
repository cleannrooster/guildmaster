package dev.campaigncore.washedashore.act;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.encounter.EncounterAnchor;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import net.minecraft.core.BlockPos;

/**
 * Entry point for authored structure-template data markers. Placement code calls this after replacing
 * a structure block with air; bosses are never created here.
 */
public final class StructureMarkerProcessor {
    private StructureMarkerProcessor(){}
    public static boolean process(String marker,BlockPos pos,WashedAshoreInstance act){
        return switch(marker){
            case "campaign_core:washed_ashore/guide_anchor" -> true;
            case "campaign_core:washed_ashore/encounter_anchor","campaign_core:washed_ashore/boss_spawn" -> relocate(act,EncounterManager.UNDERTAKER,pos);
            case "campaign_core:washed_ashore/arena_center" -> relocate(act,EncounterManager.RAVEN,pos);
            case "campaign_core:washed_ashore/settlement_entrance","campaign_core:washed_ashore/objective_marker" -> true;
            default -> {CampaignCore.LOGGER.debug("unknown_structure_marker marker={} pos={}",marker,pos);yield false;}
        };
    }
    private static boolean relocate(WashedAshoreInstance act,net.minecraft.resources.ResourceLocation id,BlockPos pos){
        EncounterAnchor anchor=act.encounters().get(id);
        if(anchor==null)return false;
        anchor.relocate(pos,pos.above());
        return true;
    }
}
