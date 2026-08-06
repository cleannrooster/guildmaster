package dev.campaigncore.washedashore.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import dev.campaigncore.washedashore.act.*;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.*;
import dev.campaigncore.washedashore.incident.*;
import dev.campaigncore.washedashore.recovery.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;

public final class WashedAshoreCommands {
    private WashedAshoreCommands(){}
    public static void register(){
        CommandRegistrationEvent.EVENT.register((dispatcher,registry,selection)->dispatcher.register(
                Commands.literal("campaign").requires(s->s.hasPermission(2)).then(Commands.literal("washed_ashore")
                        .then(Commands.literal("inspect").executes(WashedAshoreCommands::inspect))
                        .then(Commands.literal("generate").executes(WashedAshoreCommands::generate))
                        .then(Commands.literal("reset").executes(WashedAshoreCommands::resetAct))
                        .then(Commands.literal("place")
                                .then(Commands.argument("poi",StringArgumentType.word())
                                        .suggests((c,b)->SharedSuggestionProvider.suggest(WashedAshoreLayoutGenerator.placeablePois(),b))
                                        .then(Commands.argument("pos",BlockPosArgument.blockPos())
                                                .executes(WashedAshoreCommands::placePoi))))
                        .then(Commands.literal("reinstance")
                                .then(Commands.argument("pos",BlockPosArgument.blockPos())
                                        .then(Commands.argument("bypass",BoolArgumentType.bool())
                                                .executes(WashedAshoreCommands::reinstance))))
                        .then(Commands.literal("setstage").then(Commands.argument("player",EntityArgument.player())
                                .then(Commands.argument("stage",StringArgumentType.word())
                                        .suggests((c,b)->SharedSuggestionProvider.suggest(java.util.Arrays.stream(WashedAshoreStage.values()).map(v->v.name().toLowerCase(Locale.ROOT)),b))
                                        .executes(WashedAshoreCommands::setStage))))
                        .then(Commands.literal("teleport")
                                .then(Commands.literal("beach").executes(c->teleport(c,"beach")))
                                .then(Commands.literal("guide").executes(c->teleport(c,"guide")))
                                .then(Commands.literal("undertaker").executes(c->teleport(c,"undertaker")))
                                .then(Commands.literal("settlement").executes(c->teleport(c,"settlement")))
                                .then(Commands.literal("dread").executes(c->teleport(c,"dread")))
                                .then(Commands.literal("crossing").executes(c->teleport(c,"crossing")))
                                .then(Commands.literal("other_settlement").executes(c->teleport(c,"other_settlement")))
                                .then(Commands.literal("sculk").executes(c->teleport(c,"sculk")))
                                .then(Commands.literal("raven").executes(c->teleport(c,"raven"))))
                        .then(Commands.literal("encounter")
                                .then(Commands.literal("activate").then(Commands.argument("id",ResourceLocationArgument.id()).executes(WashedAshoreCommands::activate)))
                                .then(Commands.literal("reset").then(Commands.argument("id",ResourceLocationArgument.id()).executes(WashedAshoreCommands::resetEncounter)))
                                .then(Commands.literal("fail").then(Commands.argument("id",ResourceLocationArgument.id()).executes(c->endEncounter(c,false))))
                                .then(Commands.literal("abandon").then(Commands.argument("id",ResourceLocationArgument.id()).executes(c->endEncounter(c,true))))
                                .then(Commands.literal("complete").then(Commands.argument("id",ResourceLocationArgument.id()).executes(WashedAshoreCommands::complete))))
                        .then(Commands.literal("incident")
                                .then(Commands.literal("list").executes(WashedAshoreCommands::incidentList))
                                .then(Commands.literal("status").executes(WashedAshoreCommands::incidentStatus))
                                .then(Commands.literal("trigger")
                                        .then(Commands.argument("hub",ResourceLocationArgument.id()).suggests(WashedAshoreCommands::suggestHubs)
                                                .then(Commands.argument("incident",ResourceLocationArgument.id()).suggests(WashedAshoreCommands::suggestIncidents)
                                                        .executes(WashedAshoreCommands::incidentTrigger))))
                                .then(Commands.literal("stop")
                                        .then(Commands.argument("hub",ResourceLocationArgument.id()).suggests(WashedAshoreCommands::suggestHubs)
                                                .executes(WashedAshoreCommands::incidentStop))))
                        .then(Commands.literal("combat_encounter")
                                .then(Commands.literal("list").then(slotArg().executes(WashedAshoreCommands::combatList)))
                                .then(Commands.literal("select").then(slotArg()
                                        .then(Commands.argument("candidate",ResourceLocationArgument.id())
                                                .suggests(WashedAshoreCommands::suggestCandidates)
                                                .executes(WashedAshoreCommands::combatSelect))))
                                .then(Commands.literal("start").then(slotArg().executes(WashedAshoreCommands::combatStart)))
                                .then(Commands.literal("status").executes(WashedAshoreCommands::combatStatus))
                                .then(Commands.literal("abort").then(slotArg().executes(WashedAshoreCommands::combatAbort))))
                        .then(Commands.literal("quest")
                                .then(Commands.literal("inspect")
                                        .then(Commands.argument("player",EntityArgument.player()).executes(WashedAshoreCommands::inspectQuest)))
                                .then(Commands.literal("set")
                                        .then(Commands.argument("player",EntityArgument.player())
                                                .then(Commands.argument("quest",StringArgumentType.word())
                                                        .suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"dread","crossing"},b))
                                                        .then(Commands.argument("state",StringArgumentType.word())
                                                                .suggests((c,b)->SharedSuggestionProvider.suggest(java.util.Arrays.stream(RegionalQuestStage.values()).map(v->v.name().toLowerCase(Locale.ROOT)),b))
                                                                .executes(WashedAshoreCommands::setQuestState)))))
                                .then(Commands.literal("dread")
                                        .then(Commands.argument("player",EntityArgument.player())
                                                .then(Commands.argument("level",IntegerArgumentType.integer(0,100)).executes(WashedAshoreCommands::setDreadLevel))))
                                .then(Commands.literal("crossing")
                                        .then(Commands.argument("player",EntityArgument.player())
                                                .then(Commands.argument("investigation",IntegerArgumentType.integer(0,100)).executes(WashedAshoreCommands::setCrossingInvestigation))))
                                .then(Commands.literal("event")
                                        .then(Commands.argument("player",EntityArgument.player())
                                                .then(Commands.argument("event",StringArgumentType.word())
                                                        .suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"howl","darkness","silence","missing","manifest","spawn_dread","discover_crossing","spawn_thrasher","spawn_crossing_horde","form_sculk","start_sculk","reset_sculk"},b))
                                                        .executes(WashedAshoreCommands::triggerQuestEvent))))))
                        .then(Commands.literal("prestige")
                                .then(Commands.literal("get").then(Commands.argument("player",EntityArgument.player()).executes(WashedAshoreCommands::prestigeGet)))
                                .then(Commands.literal("set").then(Commands.argument("player",EntityArgument.player())
                                        .then(Commands.argument("act",ResourceLocationArgument.id())
                                                .suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{dev.campaigncore.CampaignCore.WASHED_ASHORE.toString()},b))
                                                .then(Commands.argument("level",IntegerArgumentType.integer(0,100)).executes(WashedAshoreCommands::prestigeSet)))))
                                .then(Commands.literal("queue-wipe").then(Commands.argument("player",EntityArgument.player())
                                        .then(Commands.argument("act",ResourceLocationArgument.id())
                                                .suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{dev.campaigncore.CampaignCore.WASHED_ASHORE.toString()},b))
                                                .executes(WashedAshoreCommands::prestigeQueueWipe)))))
                        .then(Commands.literal("recovery")
                                .then(Commands.literal("inspect").then(Commands.argument("player",EntityArgument.player()).executes(WashedAshoreCommands::inspectRecovery)))
                                .then(Commands.literal("start").then(Commands.argument("player",EntityArgument.player())
                                        .then(Commands.literal("first").executes(c->startRecovery(c,ProneRecoveryReason.FIRST_AWAKENING,0)))
                                        .then(Commands.literal("death").executes(c->startRecovery(c,ProneRecoveryReason.DEATH_RESPAWN,0)))
                                        .then(Commands.literal("scripted").then(Commands.argument("ticks",IntegerArgumentType.integer(1,72000))
                                                .executes(c->startRecovery(c,ProneRecoveryReason.SCRIPTED,IntegerArgumentType.getInteger(c,"ticks")))))))
                                .then(Commands.literal("complete").then(Commands.argument("player",EntityArgument.player()).executes(WashedAshoreCommands::completeRecovery)))
                                .then(Commands.literal("cancel").then(Commands.argument("player",EntityArgument.player()).executes(WashedAshoreCommands::cancelRecovery)))
                                .then(Commands.literal("reset-first-awakening").then(Commands.argument("player",EntityArgument.player()).executes(WashedAshoreCommands::resetFirstAwakening))))
                ));
    }
    private static int inspect(CommandContext<CommandSourceStack> c){
        var source=c.getSource();var data=WashedAshoreSavedData.get(source.getLevel());
        var nearest=nearestLayout(data,BlockPos.containing(source.getPosition()),"settlement");
        var act=nearest==null?data.act():nearest;
        source.sendSuccess(()->Component.literal("Nearest Washed Ashore layout: "+act.generationStatus()+" layout v"+act.layoutVersion()+" instance "+act.actInstanceId()),false);
        source.sendSuccess(()->Component.literal("beach="+fmt(act.beachSpawn())+" guide="+fmt(act.guideLandmark())+" undertaker="+fmt(act.undertakerGraveyard())),false);
        source.sendSuccess(()->Component.literal("settlement="+fmt(act.settlement())+" raven="+fmt(act.ravenArena())),false);
        source.sendSuccess(()->Component.literal("dark_forest="+fmt(act.darkForest())+" devils_crossing="+fmt(act.devilsCrossing())+" other_settlement="+fmt(act.otherSettlement())),false);
        source.sendSuccess(()->Component.literal("sculk_surface="+fmt(act.sculkSurface())+" formed="+SculkSurfaceManager.isFormed(act)
                +" mob_deaths="+act.sculkMobDeaths()+" start_tick="+act.sculkEncounterStartTick()+" waves="+act.sculkWavesSpawned()),false);
        long gameTime=source.getLevel().getGameTime();
        for(EncounterAnchor e:act.encounters().values())source.sendSuccess(()->Component.literal(e.encounterId()+" "+e.status()+" boss="+e.activeBossUuid()
                +(e.awaitingRetry()?" retry_in="+Math.max(0,(e.retryAt()-gameTime)/20)+"s":"")),false);
        int layoutIndex=0;
        for(WashedAshoreInstance instance:data.instances()){
            int index=layoutIndex++;
            if(instance==act)continue;
            source.sendSuccess(()->Component.literal("additional["+index+"] instance="+instance.actInstanceId()
                    +" status="+instance.generationStatus()+" beach="+fmt(instance.beachSpawn())
                    +" settlement="+fmt(instance.settlement())),false);
            source.sendSuccess(()->Component.literal("additional["+index+"] dark_forest="+fmt(instance.darkForest())
                    +" devils_crossing="+fmt(instance.devilsCrossing())+" other_settlement="+fmt(instance.otherSettlement())
                    +" sculk_surface="+fmt(instance.sculkSurface())+" encounters="+instance.encounters().size()),false);
        }
        ServerPlayer player=source.getPlayer();if(player!=null){var progress=data.player(player.getUUID());
            source.sendSuccess(()->Component.literal("player stage="+progress.stage()+" dread="+progress.dreadQuest()+"("+progress.dreadLevel()+"%) crossing="+progress.crossingQuest()+"("+progress.crossingInvestigation()+"%)"),false);}
        return Command.SINGLE_SUCCESS;
    }
    private static int generate(CommandContext<CommandSourceStack> c){
        var data=WashedAshoreSavedData.get(c.getSource().getLevel());if(data.act().generationStatus()!=WashedAshoreGenerationStatus.UNINITIALIZED&&data.act().generationStatus()!=WashedAshoreGenerationStatus.FAILED){
            c.getSource().sendFailure(Component.literal("Campaign layout already exists; use /campaign washed_ashore reset first."));return 0;}
        WashedAshoreLayoutGenerator.begin(c.getSource().getLevel(),data);c.getSource().sendSuccess(()->Component.literal("Act generation queued."),true);return 1;
    }
    private static int resetAct(CommandContext<CommandSourceStack> c){
        var data=WashedAshoreSavedData.get(c.getSource().getLevel());data.act().reset();data.clearAdditionalInstances();data.clearHubIncidents();data.clearPlayers();
        c.getSource().sendSuccess(()->Component.literal("Act state reset. Generation will start when a player is present."),true);return 1;
    }
    private static int placePoi(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException{
        String poi=StringArgumentType.getString(c,"poi").toLowerCase(Locale.ROOT);
        BlockPos requested=BlockPosArgument.getLoadedBlockPos(c,"pos");
        WashedAshoreSavedData data=WashedAshoreSavedData.get(c.getSource().getLevel());
        WashedAshoreInstance instance=nearestLayout(data,requested,poi);
        if(instance==null)instance=data.act();
        var result=WashedAshoreLayoutGenerator.forcePlacePoi(c.getSource().getLevel(),data,instance,poi,requested);
        if(!result.success()){
            c.getSource().sendFailure(Component.literal("Could not force-place "+poi+": "+result.message()));
            return 0;
        }
        c.getSource().sendSuccess(()->Component.literal("Force-placed "+poi+" at "+result.position().toShortString()
                +". Inhabited-location and overlap safeguards were bypassed; overwritten blocks are not recoverable."),true);
        return Command.SINGLE_SUCCESS;
    }
    private static int reinstance(CommandContext<CommandSourceStack> c){
        BlockPos requested=BlockPosArgument.getBlockPos(c,"pos");
        boolean bypass=BoolArgumentType.getBool(c,"bypass");var level=c.getSource().getLevel();var data=WashedAshoreSavedData.get(level);
        String failure=WashedAshoreLayoutGenerator.createReinstance(level,data,requested,bypass);
        if(failure!=null){c.getSource().sendFailure(Component.literal(failure));return 0;}
        WashedAshoreInstance created=data.instances().getLast();
        c.getSource().sendSuccess(()->Component.literal("Created Washed Ashore layout instance "+created.actInstanceId()+" at "+created.beachSpawn().toShortString()+" (beach bypass="+bypass+")."),true);return 1;
    }
    private static int setStage(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");WashedAshoreStage stage=WashedAshoreStage.valueOf(StringArgumentType.getString(c,"stage").toUpperCase(Locale.ROOT));
            var data=WashedAshoreSavedData.get(c.getSource().getLevel());data.player(player.getUUID()).setStage(stage);data.dirty();
            c.getSource().sendSuccess(()->Component.literal("Set "+player.getName().getString()+" to "+stage),true);return 1;
        }catch(Exception ex){c.getSource().sendFailure(Component.literal("Unknown Act 1 stage or player."));return 0;}
    }
    private static int teleport(CommandContext<CommandSourceStack> c,String target){
        ServerPlayer player=c.getSource().getPlayer();if(player==null)return 0;
        WashedAshoreInstance a=nearestLayout(WashedAshoreSavedData.get(c.getSource().getLevel()),sourcePos(c),target);
        BlockPos p=a==null?null:landmarkPos(a,target);
        if(p==null){c.getSource().sendFailure(Component.literal("That landmark has not been generated."));return 0;}
        player.teleportTo(c.getSource().getLevel(),p.getX()+.5,p.getY()+2,p.getZ()+.5,player.getYRot(),player.getXRot());return 1;
    }
    private static int activate(CommandContext<CommandSourceStack> c){
        ResourceLocation id=ResourceLocationArgument.getId(c,"id");var data=WashedAshoreSavedData.get(c.getSource().getLevel());
        WashedAshoreInstance instance=nearestEncounterInstance(data,sourcePos(c),id);EncounterAnchor e=instance==null?null:instance.encounters().get(id);
        if(e==null){c.getSource().sendFailure(Component.literal("Unknown encounter "+id));return 0;}
        return EncounterManager.activate(c.getSource().getLevel(),data,e,c.getSource().getPlayer())?1:0;
    }
    private static int resetEncounter(CommandContext<CommandSourceStack> c){
        ResourceLocation id=ResourceLocationArgument.getId(c,"id");var data=WashedAshoreSavedData.get(c.getSource().getLevel());
        WashedAshoreInstance instance=nearestEncounterInstance(data,sourcePos(c),id);
        return instance!=null&&EncounterManager.reset(data,instance,id)?1:0;
    }
    private static int endEncounter(CommandContext<CommandSourceStack> c,boolean abandoned){
        ResourceLocation id=ResourceLocationArgument.getId(c,"id");var level=c.getSource().getLevel();var data=WashedAshoreSavedData.get(level);
        WashedAshoreInstance instance=nearestEncounterInstance(data,sourcePos(c),id);
        boolean ok=instance!=null&&EncounterManager.endEncounter(level,data,instance,id,abandoned);
        if(!ok){c.getSource().sendFailure(Component.literal("Unknown or already-completed encounter "+id));return 0;}
        EncounterAnchor e=instance.encounters().get(id);long ticks=e==null?0:Math.max(0,e.retryAt()-level.getGameTime());
        c.getSource().sendSuccess(()->Component.literal((abandoned?"Abandoned ":"Failed ")+id+"; retry in ~"+(ticks/20)+"s"),true);return 1;
    }
    private static int complete(CommandContext<CommandSourceStack> c){
        ResourceLocation id=ResourceLocationArgument.getId(c,"id");var data=WashedAshoreSavedData.get(c.getSource().getLevel());
        WashedAshoreInstance instance=nearestEncounterInstance(data,sourcePos(c),id);
        return instance!=null&&EncounterManager.complete(c.getSource().getLevel(),data,instance,id)?1:0;
    }
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestHubs(
            CommandContext<CommandSourceStack> c,com.mojang.brigadier.suggestion.SuggestionsBuilder b){
        return SharedSuggestionProvider.suggestResource(HubIncidentRegistry.hubs().keySet(),b);
    }
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestIncidents(
            CommandContext<CommandSourceStack> c,com.mojang.brigadier.suggestion.SuggestionsBuilder b){
        try{
            HubDefinition hub=HubIncidentRegistry.hubs().get(ResourceLocationArgument.getId(c,"hub"));
            return SharedSuggestionProvider.suggestResource(HubIncidentRegistry.incidents().entrySet().stream()
                    .filter(e->hub==null||e.getValue().tier()==hub.tier()).map(java.util.Map.Entry::getKey),b);
        }catch(Exception ex){return SharedSuggestionProvider.suggestResource(HubIncidentRegistry.incidents().keySet(),b);}
    }
    private static int incidentList(CommandContext<CommandSourceStack> c){
        c.getSource().sendSuccess(()->Component.literal("Hub incidents ("+HubIncidentRegistry.incidents().size()+"):"),false);
        for(var hubEntry:HubIncidentRegistry.hubs().entrySet()){
            var hub=hubEntry.getValue();c.getSource().sendSuccess(()->Component.literal("  "+hubEntry.getKey()+" tier="+hub.tier()+" slot="+hub.slot()),false);
            HubIncidentRegistry.incidents().entrySet().stream().filter(e->e.getValue().tier()==hub.tier()).forEach(e->
                    c.getSource().sendSuccess(()->Component.literal("    "+e.getKey()+" ["+e.getValue().objective().getSerializedName()+"]"),false));
        }
        return 1;
    }
    private static int incidentStatus(CommandContext<CommandSourceStack> c){
        WashedAshoreSavedData data=WashedAshoreSavedData.get(c.getSource().getLevel());long now=c.getSource().getLevel().getGameTime();
        for(WashedAshoreInstance act:data.instances())for(var entry:HubIncidentRegistry.hubs().entrySet()){
            if(act.slot(entry.getValue().slot())==null)continue;
            HubIncidentState state=data.hubIncident(act.actInstanceId(),entry.getKey());String status=state.active()
                    ?"active="+state.activeIncident()+" wave="+state.wave()+" members="+state.members().size()+" protected="+state.protectedEntities().size()+" opponents="+state.opponents().size()+" expires_in="+Math.max(0,(state.expiresAt()-now)/20)+"s"
                    :"waiting next_in="+Math.max(0,(state.nextSelectionAt()-now)/20)+"s";
            c.getSource().sendSuccess(()->Component.literal(entry.getKey()+" instance="+act.actInstanceId()+" center="+act.slot(entry.getValue().slot()).toShortString()+": "+status),false);
        }
        return 1;
    }
    private static int incidentTrigger(CommandContext<CommandSourceStack> c){
        ResourceLocation hub=ResourceLocationArgument.getId(c,"hub"),incident=ResourceLocationArgument.getId(c,"incident");
        var level=c.getSource().getLevel();var data=WashedAshoreSavedData.get(level);
        BlockPos origin=BlockPos.containing(c.getSource().getPosition());
        if(!HubIncidentManager.debugStart(level,data,hub,incident,origin)){c.getSource().sendFailure(Component.literal("Could not start "+incident+" at "+hub+". Check tier compatibility, layout positions, and required entity mods."));return 0;}
        c.getSource().sendSuccess(()->Component.literal("Started hub incident "+incident+" at "+hub),true);return 1;
    }
    private static int incidentStop(CommandContext<CommandSourceStack> c){
        ResourceLocation hub=ResourceLocationArgument.getId(c,"hub");var level=c.getSource().getLevel();
        BlockPos origin=BlockPos.containing(c.getSource().getPosition());
        if(!HubIncidentManager.debugStop(level,WashedAshoreSavedData.get(level),hub,origin)){c.getSource().sendFailure(Component.literal("No active incident at the nearest "+hub));return 0;}
        c.getSource().sendSuccess(()->Component.literal("Stopped active incident at "+hub),true);return 1;
    }
    private static int inspectQuest(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");WashedAshoreProgress p=WashedAshoreSavedData.get(c.getSource().getLevel()).player(player.getUUID());
            c.getSource().sendSuccess(()->Component.literal(player.getName().getString()+": dread="+p.dreadQuest()+" level="+p.dreadLevel()+" manifest="+p.dreadManifestTicks()
                    +" crossing="+p.crossingQuest()+" investigation="+p.crossingInvestigation()),false);return 1;
        }catch(Exception ex){c.getSource().sendFailure(Component.literal("Player not found."));return 0;}
    }
    private static int setQuestState(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");String quest=StringArgumentType.getString(c,"quest");
            RegionalQuestStage state=RegionalQuestStage.valueOf(StringArgumentType.getString(c,"state").toUpperCase(Locale.ROOT));
            WashedAshoreSavedData data=WashedAshoreSavedData.get(c.getSource().getLevel());WashedAshoreProgress p=data.player(player.getUUID());
            if(quest.equals("dread"))p.setDreadQuest(state);else if(quest.equals("crossing"))p.setCrossingQuest(state);else throw new IllegalArgumentException();
            data.dirty();c.getSource().sendSuccess(()->Component.literal("Set "+quest+" quest for "+player.getName().getString()+" to "+state),true);return 1;
        }catch(Exception ex){c.getSource().sendFailure(Component.literal("Unknown player, quest, or state."));return 0;}
    }
    private static int setDreadLevel(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");int value=IntegerArgumentType.getInteger(c,"level");
            WashedAshoreSavedData data=WashedAshoreSavedData.get(c.getSource().getLevel());data.player(player.getUUID()).setDreadLevel(value);data.dirty();
            c.getSource().sendSuccess(()->Component.literal("Set hidden dread for "+player.getName().getString()+" to "+value),true);return 1;
        }catch(Exception ex){return 0;}
    }
    private static int setCrossingInvestigation(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");int value=IntegerArgumentType.getInteger(c,"investigation");
            WashedAshoreSavedData data=WashedAshoreSavedData.get(c.getSource().getLevel());data.player(player.getUUID()).setCrossingInvestigation(value);data.dirty();
            c.getSource().sendSuccess(()->Component.literal("Set Devil's Crossing investigation for "+player.getName().getString()+" to "+value),true);return 1;
        }catch(Exception ex){return 0;}
    }
    private static int triggerQuestEvent(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");String event=StringArgumentType.getString(c,"event");
            WashedAshoreSavedData data=WashedAshoreSavedData.get(c.getSource().getLevel());
            if(!RegionalQuestManager.debugEvent(c.getSource().getLevel(),data,player,event)){c.getSource().sendFailure(Component.literal("Event could not run; check nearby prey or encounter state."));return 0;}
            c.getSource().sendSuccess(()->Component.literal("Triggered quest event "+event+" for "+player.getName().getString()),true);return 1;
        }catch(Exception ex){c.getSource().sendFailure(Component.literal("Player or event unavailable."));return 0;}
    }
    private static int inspectRecovery(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");ProneRecoveryData d=WashedAshoreSavedData.get(c.getSource().getLevel()).player(player.getUUID()).proneRecovery();
            c.getSource().sendSuccess(()->Component.literal(player.getName().getString()+": state="+d.state()+" reason="+d.reason()
                    +" movement="+d.accumulatedMovementTicks()+"/"+d.requiredMovementTicks()+" firstComplete="+d.firstAwakeningComplete()
                    +" grace="+d.obstructionGraceTicks()+" protection="+d.protectionTicks()+" prone="+ProneCondition.isApplied(player)),false);return 1;
        }catch(Exception ex){c.getSource().sendFailure(Component.literal("Player not found."));return 0;}
    }
    private static int startRecovery(CommandContext<CommandSourceStack> c,ProneRecoveryReason reason,int scriptedTicks){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");int ticks=reason==ProneRecoveryReason.SCRIPTED?scriptedTicks:
                    ProneRecoveryManager.requiredRecoveryTicks(player,reason);
            if(!ProneRecoveryManager.beginRecovery(player,reason,ticks,true)){c.getSource().sendFailure(Component.literal("Recovery could not start."));return 0;}
            c.getSource().sendSuccess(()->Component.literal("Started "+reason+" recovery for "+player.getName().getString()+" ("+ticks+" movement ticks)."),true);return 1;
        }catch(Exception ex){c.getSource().sendFailure(Component.literal("Player not found."));return 0;}
    }
    private static int completeRecovery(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");ProneRecoveryManager.completeRecovery(player);
            c.getSource().sendSuccess(()->Component.literal("Completed recovery for "+player.getName().getString()),true);return 1;
        }catch(Exception ex){return 0;}
    }
    private static int cancelRecovery(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");ProneRecoveryManager.cancelRecovery(player);
            c.getSource().sendSuccess(()->Component.literal("Cancelled recovery for "+player.getName().getString()),true);return 1;
        }catch(Exception ex){return 0;}
    }
    private static int resetFirstAwakening(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");WashedAshoreSavedData data=WashedAshoreSavedData.get(c.getSource().getLevel());
            data.player(player.getUUID()).proneRecovery().resetFirstAwakening();data.dirty();
            c.getSource().sendSuccess(()->Component.literal("Reset first awakening for "+player.getName().getString()),true);return 1;
        }catch(Exception ex){return 0;}
    }
    // --- prestige ---------------------------------------------------------------------------------
    private static int prestigeGet(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");
            var ledger=WashedAshoreSavedData.get(c.getSource().getLevel()).player(player.getUUID()).prestige();
            String levels=ledger.levelsView().isEmpty()?"none"
                    :ledger.levelsView().entrySet().stream().map(e->e.getKey()+"="+e.getValue())
                            .sorted().reduce("",(a,b)->a+(a.isEmpty()?"":", ")+b);
            c.getSource().sendSuccess(()->Component.literal("Prestige for "+player.getName().getString()
                    +": "+levels+(ledger.pendingWipeAct()==null?"":" (wipe pending: "+ledger.pendingWipeAct()+")")),false);
            return 1;
        }catch(Exception ex){return 0;}
    }
    private static int prestigeSet(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");
            ResourceLocation act=ResourceLocationArgument.getId(c,"act");
            int level=IntegerArgumentType.getInteger(c,"level");
            WashedAshoreSavedData data=WashedAshoreSavedData.get(c.getSource().getLevel());
            data.player(player.getUUID()).prestige().setLevel(act,level);data.dirty();
            c.getSource().sendSuccess(()->Component.literal("Set "+player.getName().getString()+" prestige "+act+"="+level),true);return 1;
        }catch(Exception ex){return 0;}
    }
    private static int prestigeQueueWipe(CommandContext<CommandSourceStack> c){
        try{ServerPlayer player=EntityArgument.getPlayer(c,"player");
            ResourceLocation act=ResourceLocationArgument.getId(c,"act");
            WashedAshoreSavedData data=WashedAshoreSavedData.get(c.getSource().getLevel());
            data.player(player.getUUID()).prestige().queueWipe(act);data.dirty();
            dev.campaigncore.prestige.PrestigeManager.checkPendingWipe(player,data);
            c.getSource().sendSuccess(()->Component.literal("Prestige wipe applied to "+player.getName().getString()+" for "+act),true);return 1;
        }catch(Exception ex){return 0;}
    }
    // --- combat_encounter: data-driven encounter pool ---------------------------------------------
    /** A fresh {@code slot} argument (Brigadier builders are single-use) suggesting the shipped encounter ids. */
    private static RequiredArgumentBuilder<CommandSourceStack,ResourceLocation> slotArg(){
        return Commands.argument("slot",ResourceLocationArgument.id())
                .suggests((c,b)->SharedSuggestionProvider.suggestResource(EncounterManager.slots().stream(),b));
    }
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestCandidates(
            CommandContext<CommandSourceStack> c,com.mojang.brigadier.suggestion.SuggestionsBuilder b){
        try{
            ResourceLocation slot=ResourceLocationArgument.getId(c,"slot");
            return SharedSuggestionProvider.suggestResource(
                    EncounterManager.candidates().forSlot(slot).stream().map(EncounterCandidate::id),b);
        }catch(Exception ex){return b.buildFuture();}
    }
    private static int combatList(CommandContext<CommandSourceStack> c){
        ResourceLocation slot=ResourceLocationArgument.getId(c,"slot");
        var candidates=EncounterManager.candidates().forSlot(slot);
        if(candidates.isEmpty()){c.getSource().sendFailure(Component.literal("No candidates registered for slot "+slot));return 0;}
        c.getSource().sendSuccess(()->Component.literal("Candidates for "+slot+" ("+candidates.size()+"):"),false);
        var selector=EncounterManager.selector();
        for(EncounterCandidate cand:candidates){
            String line="  "+cand.id()+" weight="+cand.weight()+(cand.nativeFallback()?" [native]":"")
                    +" "+selector.availability(cand)+cand.variant().map(v->" variant="+v).orElse("");
            c.getSource().sendSuccess(()->Component.literal(line),false);
        }
        return 1;
    }
    private static int combatSelect(CommandContext<CommandSourceStack> c){
        ResourceLocation slot=ResourceLocationArgument.getId(c,"slot");
        ResourceLocation candidateId=ResourceLocationArgument.getId(c,"candidate");
        var data=WashedAshoreSavedData.get(c.getSource().getLevel());
        WashedAshoreInstance instance=nearestEncounterInstance(data,sourcePos(c),slot);
        EncounterAnchor e=instance==null?null:instance.encounters().get(slot);
        if(e==null){c.getSource().sendFailure(Component.literal("Unknown encounter slot "+slot));return 0;}
        var candidate=EncounterManager.candidates().byId(candidateId);
        if(candidate.isEmpty()||!candidate.get().slot().equals(slot)){
            c.getSource().sendFailure(Component.literal("Unknown candidate "+candidateId+" for slot "+slot));return 0;}
        e.setSelectedCandidate(candidateId);data.dirty();
        c.getSource().sendSuccess(()->Component.literal("Selected "+candidateId+" for "+slot),true);return 1;
    }
    private static int combatStart(CommandContext<CommandSourceStack> c){
        ResourceLocation slot=ResourceLocationArgument.getId(c,"slot");
        var level=c.getSource().getLevel();var data=WashedAshoreSavedData.get(level);
        WashedAshoreInstance instance=nearestEncounterInstance(data,sourcePos(c),slot);
        if(instance==null||!EncounterManager.debugStart(level,data,instance,slot,c.getSource().getPlayer())){
            c.getSource().sendFailure(Component.literal("Could not start "+slot+"; check the layout is generated and the slot exists."));return 0;}
        c.getSource().sendSuccess(()->Component.literal("Started encounter "+slot),true);return 1;
    }
    private static int combatStatus(CommandContext<CommandSourceStack> c){
        var data=WashedAshoreSavedData.get(c.getSource().getLevel());
        for(ResourceLocation slot:EncounterManager.slots()){
            WashedAshoreInstance instance=nearestEncounterInstance(data,sourcePos(c),slot);
            EncounterAnchor e=instance==null?null:instance.encounters().get(slot);
            int pool=EncounterManager.candidates().forSlot(slot).size();
            String state=e==null?"unmaterialized":e.status().name()
                    +" selected="+(e.selectedCandidate()==null?"native":e.selectedCandidate())
                    +" boss="+e.activeBossUuid()+(EncounterBossBars.isOpen(e)?" bar":"");
            String owner=instance==null?"":" instance="+instance.actInstanceId();
            c.getSource().sendSuccess(()->Component.literal(slot+owner+" pool="+pool+" "+state),false);
        }
        return 1;
    }
    private static int combatAbort(CommandContext<CommandSourceStack> c){
        ResourceLocation slot=ResourceLocationArgument.getId(c,"slot");
        var level=c.getSource().getLevel();var data=WashedAshoreSavedData.get(level);
        WashedAshoreInstance instance=nearestEncounterInstance(data,sourcePos(c),slot);
        if(instance==null||!EncounterManager.abortAndClear(level,data,instance,slot)){
            c.getSource().sendFailure(Component.literal("Unknown encounter slot "+slot));return 0;}
        c.getSource().sendSuccess(()->Component.literal("Aborted and cleared "+slot),true);return 1;
    }

    private static BlockPos sourcePos(CommandContext<CommandSourceStack> c){return BlockPos.containing(c.getSource().getPosition());}
    private static WashedAshoreInstance nearestEncounterInstance(WashedAshoreSavedData data,BlockPos origin,ResourceLocation id){
        return data.instances().stream().filter(WashedAshoreInstance::contentReady)
                .filter(instance->instance.encounters().containsKey(id))
                .min(java.util.Comparator.comparingDouble(instance->instance.encounters().get(id).anchorPos().distSqr(origin))).orElse(null);
    }
    private static WashedAshoreInstance nearestLayout(WashedAshoreSavedData data,BlockPos origin,String target){
        return data.instances().stream().filter(WashedAshoreInstance::contentReady)
                .filter(instance->landmarkPos(instance,target)!=null)
                .min(java.util.Comparator.comparingDouble(instance->landmarkPos(instance,target).distSqr(origin))).orElse(null);
    }
    private static BlockPos landmarkPos(WashedAshoreInstance instance,String target){
        return switch(target){case"beach"->instance.beachSpawn();case"guide"->instance.guideLandmark();case"undertaker","graveyard"->instance.undertakerGraveyard();
            case"settlement"->instance.settlement();case"dread","dark_forest"->instance.darkForest();case"crossing","devils_crossing"->instance.devilsCrossing();
            case"other_settlement"->instance.otherSettlement();case"sculk","sculk_surface"->instance.sculkSurface();
            case"raven"->instance.ravenArena();default->null;};
    }

    private static String fmt(BlockPos p){return p==null?"missing":p.toShortString();}
}
