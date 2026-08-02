package dev.campaigncore.command;

import com.mojang.brigadier.Command;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.campaigncore.api.CampaignApi;
import dev.campaigncore.data.CampaignSavedData;
import dev.campaigncore.washedashore.act.RegionalQuestStage;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.function.Predicate;

public final class CampaignCommands {
    private CampaignCommands(){}
    public static void register(){
        CommandRegistrationEvent.EVENT.register((dispatcher,registry,selection)->dispatcher.register(
                Commands.literal("campaign")
                        .then(Commands.literal("quests")
                                .then(Commands.literal("available").executes(context->
                                        listQuests(context.getSource(),"Available quests",
                                                stage->stage!=RegionalQuestStage.LOCKED&&stage!=RegionalQuestStage.COMPLETE)))
                                .then(Commands.literal("complete").executes(context->
                                        listQuests(context.getSource(),"Completed quests",
                                                stage->stage==RegionalQuestStage.COMPLETE)))
                                .then(Commands.literal("locked").executes(context->
                                        listQuests(context.getSource(),"Locked quests",
                                                stage->stage==RegionalQuestStage.LOCKED))))
                        .then(Commands.literal("list").requires(source->source.hasPermission(2)).executes(context->{
                            String ids=CampaignApi.registry().definitions().stream().map(d->d.id().toString()).sorted().reduce("",(a,b)->a+(a.isEmpty()?"":", ")+b);
                            context.getSource().sendSuccess(()->Component.literal(ids.isEmpty()?"No campaigns registered.":ids),false);
                            return Command.SINGLE_SUCCESS;
                        }))
                        .then(Commands.literal("inspect").requires(source->source.hasPermission(2))
                                .then(Commands.argument("campaign",ResourceLocationArgument.id()).executes(context->{
                                    var id=ResourceLocationArgument.getId(context,"campaign");
                                    var definition=CampaignApi.registry().get(id).orElse(null);
                                    if(definition==null){context.getSource().sendFailure(Component.literal("Unknown campaign: "+id));return 0;}
                                    var instance=CampaignSavedData.get(context.getSource().getLevel()).campaign(id);
                                    context.getSource().sendSuccess(()->Component.literal("Campaign "+id+"; definition v"+definition.definitionVersion()
                                            +"; initial_stage="+definition.initialStage()+"; stages="+definition.stages().size()
                                            +"; objectives="+definition.objectives().size()+"; locations="+definition.locations().size()
                                            +"; encounters="+definition.encounters().size()+"; hooks="+definition.hooks().size()),false);
                                    if(instance==null)context.getSource().sendSuccess(()->Component.literal("Runtime instance: not active"),false);
                                    else context.getSource().sendSuccess(()->Component.literal("Runtime instance="+instance.instanceId()
                                            +"; version="+instance.definitionVersion()+"; generation="+instance.generationStatus()
                                            +"; resolved_locations="+instance.locations().size()+"; encounter_states="+instance.encounters().size()),false);
                                    return Command.SINGLE_SUCCESS;
                                })))
        ));
    }

    private static int listQuests(CommandSourceStack source,String heading,Predicate<RegionalQuestStage> filter){
        ServerPlayer player=source.getPlayer();
        if(player==null){source.sendFailure(Component.literal("This command can only be used by a player."));return 0;}
        var progress=WashedAshoreSavedData.get(player.serverLevel()).player(player.getUUID());
        List<QuestView> quests=List.of(
                new QuestView(Component.translatable("quest.campaign_core.washed_ashore.consuming_dread"),progress.dreadQuest()),
                new QuestView(Component.translatable("quest.campaign_core.washed_ashore.devils_crossing"),progress.crossingQuest())
        );
        List<QuestView> matching=quests.stream().filter(quest->filter.test(quest.stage())).toList();
        player.sendSystemMessage(Component.literal(heading+":"));
        if(matching.isEmpty())player.sendSystemMessage(Component.literal("  None"));
        else matching.forEach(quest->player.sendSystemMessage(Component.literal("  • ").append(quest.name())));
        return Command.SINGLE_SUCCESS;
    }

    private record QuestView(Component name,RegionalQuestStage stage){}
}
