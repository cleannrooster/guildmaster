package dev.campaigncore.washedashore.message;

import dev.campaigncore.washedashore.act.RegionalQuestStage;
import dev.campaigncore.washedashore.act.WashedAshoreInstance;
import dev.campaigncore.washedashore.act.WashedAshoreProgress;
import dev.campaigncore.washedashore.act.WashedAshoreStage;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import net.minecraft.server.level.ServerPlayer;

public final class CampaignQuestMessages {
    private CampaignQuestMessages(){}
    public static void playAvailable(ServerPlayer player,boolean keybindPressed){
        WashedAshoreSavedData data=WashedAshoreSavedData.get(player.serverLevel());
        WashedAshoreProgress progress=data.player(player.getUUID());
        int index=0;
        if(!progress.questKeybindUsed()){
            if(keybindPressed){
                progress.markQuestKeybindUsed();
                data.dirty();
                CampaignMessages.sendIndexed(player,"quest_show_available_complete",index++);
            }else CampaignMessages.sendIndexed(player,"quest_show_available",index++);
        }
        if(available(progress.dreadQuest()))CampaignMessages.sendIndexed(player,"quest_dark_forest",index++);
        if(available(progress.crossingQuest()))CampaignMessages.sendIndexed(player,"quest_devils_crossing",index++);
        if(distantSettlementAvailable(progress,data.act()))CampaignMessages.sendIndexed(player,"quest_distant_settlement",index++,
                SettlementDialogueNames.distant(player.serverLevel(),data.act()));
        if(index==0)CampaignMessages.sendIndexed(player,"no_available_quests",0);
    }
    private static boolean available(RegionalQuestStage stage){
        return stage!=RegionalQuestStage.LOCKED&&stage!=RegionalQuestStage.COMPLETE;
    }
    /** Regional Encounter C: open through the regional phase until the distant settlement's raid is repelled. */
    private static boolean distantSettlementAvailable(WashedAshoreProgress progress,WashedAshoreInstance act){
        return progress.stage().atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES)
                &&!progress.stage().atLeast(WashedAshoreStage.RAVEN_ROUTE_REVEALED)
                &&!act.completedWorldObjectives().contains(EncounterManager.REGIONAL_C);
    }
}
