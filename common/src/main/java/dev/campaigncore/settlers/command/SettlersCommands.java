package dev.campaigncore.settlers.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.config.SettlersConfig;
import dev.campaigncore.settlers.detection.FarmSizeClassifier;
import dev.campaigncore.settlers.detection.SettlementConverter;
import dev.campaigncore.settlers.detection.VillageDetector;
import dev.campaigncore.settlers.economy.SettlementEconomyModel;
import dev.campaigncore.settlers.economy.SettlementEconomyReport;
import dev.campaigncore.settlers.economy.SettlementEconomyState;
import dev.campaigncore.settlers.economy.SettlementFoodStorageReserve;
import dev.campaigncore.settlers.item.LedgerItem;
import dev.campaigncore.settlers.production.ProductionState;
import dev.campaigncore.settlers.production.SettlementProductionTicker;
import dev.campaigncore.settlers.production.SettlementResources;
import dev.campaigncore.settlers.settlement.SettlementLaborModel.LaborEntry;
import dev.campaigncore.settlers.settlement.SettlementLaborModel.LaborReport;
import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.entity.data.SettlerDataManager;
import dev.campaigncore.settlers.expansion.SettlementExpander;
import dev.campaigncore.settlers.expansion.SettlementExpansionResult;
import dev.campaigncore.settlers.expansion.FrontierHubRuntimePlacer;
import dev.campaigncore.settlers.registry.SettlersEntities;
import dev.campaigncore.settlers.settlement.*;
import dev.campaigncore.settlers.situation.SituationManager;
import dev.campaigncore.settlers.situation.SituationRunner;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/// `/settlers` — the dev/inspection command surface. This is how every milestone is iterated on and
/// evaluated in-game. All player-facing feedback is emitted through translation keys (`settlers.command.*`
/// / `settlers.delivery.*`) so it can be localized.
public final class SettlersCommands {
    private SettlersCommands() {
    }

    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, selection) -> {
                dispatcher.register(Commands.literal("settlers")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("profiles").executes(SettlersCommands::listProfiles))
                        .then(Commands.literal("spawn")
                                .then(Commands.argument("profile", ResourceLocationArgument.id())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggestResource(SettlerDataManager.profileIds(), builder))
                                        .executes(context -> spawn(context.getSource(),
                                                ResourceLocationArgument.getId(context, "profile")))))
                        .then(Commands.literal("recruit")
                                .executes(context -> recruit(context.getSource(), SettlementRecruitmentService.CIVILIAN))
                                .then(Commands.argument("profile", ResourceLocationArgument.id())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggestResource(SettlerDataManager.profileIds(), builder))
                                        .executes(context -> recruit(context.getSource(),
                                                ResourceLocationArgument.getId(context, "profile")))))
                        .then(Commands.literal("expand")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument(
                                                "role",
                                                StringArgumentType.word()
                                        )
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(
                                                        List.of(
                                                                "residence",
                                                                "farm",
                                                                "workshop",
                                                                "storage",
                                                                "infirmary",
                                                                "guard_post",
                                                                "shrine"
                                                        ),
                                                        builder
                                                )
                                        )
                                        .executes(SettlersCommands::expand)))
                        .then(Commands.literal("place_frontier_hub")
                                .requires(source -> source.hasPermission(2))
                                .executes(SettlersCommands::placeFrontierHub)
                                .then(Commands.literal("intact")
                                        .executes(SettlersCommands::placeFrontierHub))
                                .then(Commands.literal("ruined")
                                        .executes(SettlersCommands::placeRuinedFrontierHub)))
                        .then(Commands.literal("placement")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("status")
                                        .executes(SettlersCommands::placementStatus))
                                .then(Commands.literal("cancel")
                                        .executes(SettlersCommands::cancelPlacement)))
                        .then(Commands.literal("convert").executes(SettlersCommands::convert))
                        .then(Commands.literal("list").executes(SettlersCommands::list))
                        .then(Commands.literal("info").executes(SettlersCommands::info))
                        .then(Commands.literal("threat")
                                .then(Commands.argument("state", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("normal", "alert", "under_attack", "panic", "recovery", "abandoned"), builder))
                                        .executes(SettlersCommands::setThreat)))
                        .then(Commands.literal("anchors")
                                .executes(SettlersCommands::showAnchors)
                                .then(Commands.literal("rebuild").executes(SettlersCommands::rebuildAnchors)))
                        .then(Commands.literal("situation")
                                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(SettlersCommands::setSituation)))
                        .then(Commands.literal("repopulate")
                                .executes(SettlersCommands::repopulate))
                        .then(Commands.literal("economy").executes(SettlersCommands::economy))
                        .then(Commands.literal("food")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.0))
                                                .executes(SettlersCommands::foodAdd)))));
                // A separate, player-usable root (NOT op-gated) so the Ledger's clickable [Request
                // delivery] line works for any player. All safety checks live in the handler.
        });
    }

    /// How long a dispatched reeve has to reach the requesting player before the errand is abandoned.
    private static final long DELIVERY_TIMEOUT_TICKS = 1200L;
    private static final long LEDGER_REDRAW_COOLDOWN_TICKS = 1200L;
    private static final Map<UUID, Long> LEDGER_REDRAW_COOLDOWNS = new HashMap<>();

    private static int placeFrontierHub(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos position = BlockPos.containing(source.getPosition());
        FrontierHubRuntimePlacer.StartResult result =
                FrontierHubRuntimePlacer.enqueue(source, position, false);
        if (!result.success()) {
            source.sendFailure(Component.translatable(
                    "settlers.command.place_frontier_hub.failed", result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "settlers.command.place_frontier_hub.started", "intact"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int placeRuinedFrontierHub(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos position = BlockPos.containing(source.getPosition());
        FrontierHubRuntimePlacer.StartResult result =
                FrontierHubRuntimePlacer.enqueue(source, position, true);
        if (!result.success()) {
            source.sendFailure(Component.translatable(
                    "settlers.command.place_frontier_hub.ruined.failed", result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "settlers.command.place_frontier_hub.started", "ruined"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int placementStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<FrontierHubRuntimePlacer.JobStatus> status =
                FrontierHubRuntimePlacer.status(source.getLevel());
        if (status.isEmpty()) {
            source.sendFailure(Component.translatable("settlers.command.placement.none"));
            return 0;
        }
        FrontierHubRuntimePlacer.JobStatus job = status.get();
        source.sendSuccess(() -> Component.translatable(
                "settlers.command.placement.status",
                job.ruined() ? "ruined" : "intact",
                job.phase(),
                job.percent()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int cancelPlacement(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        FrontierHubRuntimePlacer.CancelResult result =
                FrontierHubRuntimePlacer.cancel(source.getLevel());
        if (!result.success()) {
            source.sendFailure(Component.translatable(
                    "settlers.command.placement.cancel_failed", result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "settlers.command.placement.cancelled"), true);
        return Command.SINGLE_SUCCESS;
    }

    /// Player-usable: dispatch the named settlement's reeve to bring the caller their provisions. Fired
    /// by the Ledger's clickable list; enforces distance, peace, availability, and a live reeve.
    private static int deliver(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("settlers.delivery.player_only"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        java.util.UUID id = net.minecraft.commands.arguments.UuidArgument.getUuid(context, "settlement");
        Optional<Settlement> found = SettlementManager.get(level).byId(id);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("settlers.delivery.missing_settlement"));
            return 0;
        }
        Settlement settlement = found.get();
        if (settlement.center().distToCenterSqr(player.position())
                > LedgerItem.SCAN_RADIUS * LedgerItem.SCAN_RADIUS) {
            source.sendFailure(Component.translatable("settlers.delivery.too_far", settlement.name()));
            return 0;
        }
        if (settlement.threatState() != ThreatState.NORMAL) {
            source.sendFailure(Component.translatable("settlers.delivery.unrest", settlement.name()));
            return 0;
        }
        if (settlement.economy().claimableProvisionBundles() <= 0) {
            source.sendFailure(Component.translatable("settlers.delivery.none_available"));
            return 0;
        }
        Optional<SettlerEntity> reeve = findLiveReeve(level, settlement);
        if (reeve.isEmpty()) {
            source.sendFailure(Component.translatable("settlers.delivery.reeve_unavailable", settlement.name()));
            return 0;
        }
        if (reeve.get().hasDeliveryRequest()) {
            source.sendFailure(Component.translatable("settlers.delivery.reeve_busy", settlement.name()));
            return 0;
        }
        reeve.get().setDeliveryRequest(player.getUUID(), level.getGameTime() + DELIVERY_TIMEOUT_TICKS);
        source.sendSuccess(() -> Component.translatable("settlers.delivery.dispatched", settlement.name()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int ledgerRecruit(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        ServerLevel level = source.getLevel();
        java.util.UUID id = net.minecraft.commands.arguments.UuidArgument.getUuid(context, "settlement");
        Optional<Settlement> found = SettlementManager.get(level).byId(id);
        if (found.isEmpty() || found.get().center().distToCenterSqr(player.position())
                > LedgerItem.SCAN_RADIUS * LedgerItem.SCAN_RADIUS) {
            source.sendFailure(Component.translatable("settlers.recruitment.unavailable"));
            return 0;
        }
        Settlement settlement = found.get();
        if (settlement.economy().claimableProvisionBundles() < 1) {
            source.sendFailure(Component.translatable("settlers.recruitment.no_provisions"));
            return 0;
        }
        RecruitmentResult result = SettlementRecruitmentService.recruit(
                level, settlement, SettlementRecruitmentService.CIVILIAN);
        if (!result.success()) {
            source.sendFailure(Component.translatable("settlers.command.recruit.failed", result.reason()));
            return 0;
        }
        settlement.economy().claimBundles(1);
        SettlementManager.get(level).markDirty();
        source.sendSuccess(() -> Component.translatable("settlers.recruitment.success", settlement.name()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int ledgerPopulation(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Settlement> found = ledgerSettlement(context);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("settlers.ledger.settlement_unavailable"));
            return 0;
        }
        Settlement settlement = found.get();
        Map<String, Integer> roles = new TreeMap<>();
        settlement.roster().forEach(entry -> roles.merge(SettlementLaborModel.professionOf(entry), 1, Integer::sum));
        String summary = roles.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));

        source.sendSuccess(() -> Component.translatable("settlers.ledger.population_header",
                settlement.name(), settlement.roster().size()), false);
        source.sendSuccess(() -> Component.translatable("settlers.ledger.morale_detail",
                SettlementMoraleModel.evaluate(source.getLevel(), settlement).getSerializedName()), false);
        source.sendSuccess(() -> Component.translatable("settlers.ledger.population_summary", summary), false);
        for (int i = 0; i < settlement.roster().size(); i++) {
            ResidentEntry resident = settlement.roster().get(i);
            int number = i + 1;
            String profession = SettlementLaborModel.professionOf(resident);
            source.sendSuccess(() -> Component.translatable("settlers.ledger.population_entry",
                    number, resident.profile().getPath(), profession), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int ledgerRedrawAnchors(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        Optional<Settlement> found = ledgerSettlement(context);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("settlers.ledger.settlement_unavailable"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        long now = level.getGameTime();
        long readyAt = LEDGER_REDRAW_COOLDOWNS.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now < readyAt) {
            long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
            source.sendFailure(Component.translatable("settlers.ledger.redraw_cooldown", seconds));
            return 0;
        }
        Settlement settlement = found.get();
        if (!SettlementAnchorUpdater.rebuild(level, settlement)) {
            source.sendFailure(Component.translatable("settlers.command.anchors.rebuild_unloaded"));
            return 0;
        }
        settlement.completeAnchorRebuild(now + SettlementAnchorUpdater.AUDIT_INTERVAL_TICKS);
        SettlementManager.get(level).markDirty();
        LEDGER_REDRAW_COOLDOWNS.put(player.getUUID(), now + LEDGER_REDRAW_COOLDOWN_TICKS);
        source.sendSuccess(() -> Component.translatable("settlers.ledger.redraw_complete", settlement.name()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int ledgerChronicle(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Optional<Settlement> found = ledgerSettlement(context);
        if (found.isEmpty()) {
            source.sendFailure(Component.translatable("settlers.ledger.settlement_unavailable"));
            return 0;
        }
        Settlement settlement = found.get();
        source.sendSuccess(() -> Component.translatable("settlers.ledger.chronicle_header", settlement.name()), false);
        if (settlement.chronicle().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("settlers.ledger.chronicle_empty"), false);
            return Command.SINGLE_SUCCESS;
        }
        int first = Math.max(0, settlement.chronicle().size() - 20);
        for (int i = settlement.chronicle().size() - 1; i >= first; i--) {
            ChronicleEntry entry = settlement.chronicle().get(i);
            long day = entry.gameTime() / SettlementProductionTicker.DAY_TICKS + 1L;
            Component event = chronicleEvent(entry);
            source.sendSuccess(() -> Component.translatable("settlers.ledger.chronicle_entry", day, event), false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Component chronicleEvent(ChronicleEntry entry) {
        return switch (entry.event()) {
            case "resident_joined" -> Component.translatable("settlers.chronicle.resident_joined", entry.subject());
            case "traveler_stayed" -> Component.translatable("settlers.chronicle.traveler_stayed");
            case "resident_died" -> Component.translatable("settlers.chronicle.resident_died", entry.subject());
            case "expanded" -> Component.translatable("settlers.chronicle.expanded", entry.subject());
            case "attack_began" -> Component.translatable("settlers.chronicle.attack_began");
            case "panic" -> Component.translatable("settlers.chronicle.panic");
            case "recovery_began" -> Component.translatable("settlers.chronicle.recovery_began");
            case "peace_restored" -> Component.translatable("settlers.chronicle.peace_restored");
            case "founded" -> Component.translatable("settlers.chronicle.founded");
            default -> Component.translatable("settlers.chronicle.unknown", entry.event());
        };
    }

    private static Optional<Settlement> ledgerSettlement(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            return Optional.empty();
        }
        UUID id = net.minecraft.commands.arguments.UuidArgument.getUuid(context, "settlement");
        return SettlementManager.get(context.getSource().getLevel()).byId(id)
                .filter(settlement -> settlement.center().distToCenterSqr(player.position())
                        <= LedgerItem.SCAN_RADIUS * LedgerItem.SCAN_RADIUS);
    }

    /// The settlement's first live, alive reeve entity, resolved by roster entity id (no entity scan).
    private static Optional<SettlerEntity> findLiveReeve(ServerLevel level, Settlement settlement) {
        return settlement.roster().stream()
                .filter(entry -> SettlementLaborModel.professionOf(entry).equals("reeve"))
                .map(entry -> entry.entityId().map(level::getEntity).orElse(null))
                .filter(entity -> entity instanceof SettlerEntity settler && settler.isAlive())
                .map(entity -> (SettlerEntity) entity)
                .findFirst();
    }
    private static Optional<StructureRole> parseExpansionRole(
            String value
    ) {
        String normalized =
                value.toLowerCase(Locale.ROOT)
                        .replace('-', '_');

        return switch (normalized) {
            case "residence", "house", "housing" ->
                    Optional.of(StructureRole.RESIDENCE);

            case "farm", "farming" ->
                    Optional.of(StructureRole.FARM);

            case "workshop", "work" ->
                    Optional.of(StructureRole.WORKSHOP);

            case "storage", "warehouse" ->
                    Optional.of(StructureRole.STORAGE);

            case "infirmary", "clinic" ->
                    Optional.of(StructureRole.INFIRMARY);

            case "guard_post", "guardpost", "guard" ->
                    Optional.of(StructureRole.GUARD_POST);

            case "shrine", "temple" ->
                    Optional.of(StructureRole.SHRINE);

            default -> Optional.empty();
        };
    }
    private static int expand(
            CommandContext<CommandSourceStack> context
    ) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.translatable("settlers.command.player_only"));
            return 0;
        }

        ServerLevel level = source.getLevel();

        Optional<Settlement> optionalSettlement =
                SettlementManager.get(level)
                        .nearest(player.blockPosition());

        if (optionalSettlement.isEmpty()) {
            source.sendFailure(Component.translatable("settlers.command.no_settlements_in_level"));
            return 0;
        }

        Settlement settlement = optionalSettlement.get();

        if (settlement.center()
                .distSqr(player.blockPosition()) > 128.0 * 128.0) {
            source.sendFailure(Component.translatable("settlers.command.expand.too_far"));
            return 0;
        }

        String roleName =
                StringArgumentType.getString(context, "role");

        Optional<StructureRole> optionalRole =
                parseExpansionRole(roleName);

        if (optionalRole.isEmpty()) {
            source.sendFailure(Component.translatable("settlers.command.expand.unknown_role", roleName));
            return 0;
        }

        StructureRole role = optionalRole.get();

        SettlementExpansionResult result =
                SettlementExpander.expand(
                        level,
                        player,
                        settlement,
                        role
                );

        if (!result.success()) {
            // Passthrough of the expansion subsystem's own computed message (not a fixed UI string).
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }

        source.sendSuccess(
                () -> Component.translatable("settlers.command.expand.success",
                        settlement.name(),
                        result.assignments().size(),
                        role.getSerializedName(),
                        result.templateId().toString()),
                true
        );

        return Command.SINGLE_SUCCESS;
    }
    private static int listProfiles(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        var ids = SettlerDataManager.profileIds();
        context.getSource().sendSuccess(() -> Component.translatable("settlers.command.profiles",
                ids.size(),
                String.join(", ", ids.stream().map(ResourceLocation::toString).sorted().toList())), false);
        return ids.size();
    }

    private static int spawn(CommandSourceStack source, ResourceLocation profileId) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        SettlerEntity settler = SettlersEntities.SETTLER.get().create(level);
        if (settler == null) {
            source.sendFailure(Component.translatable("settlers.command.spawn.failed"));
            return 0;
        }
        settler.moveTo(pos.x, pos.y, pos.z, source.getRotation().y, 0.0f);
        settler.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.COMMAND, null);
        settler.applyProfile(profileId);
        level.addFreshEntity(settler);
        SettlersMod.LOGGER.info("Spawned settler with profile {} at {}", profileId, settler.blockPosition());
        source.sendSuccess(() -> Component.translatable("settlers.command.spawn.success", profileId.toString()), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int recruit(CommandSourceStack source, ResourceLocation profileId) {
        ServerLevel level = source.getLevel();
        Optional<Settlement> nearest = SettlementManager.get(level).nearest(BlockPos.containing(source.getPosition()));
        if (nearest.isEmpty() || nearest.get().center().distSqr(BlockPos.containing(source.getPosition())) > 128 * 128) {
            source.sendFailure(Component.translatable("settlers.command.recruit.no_settlement"));
            return 0;
        }
        Settlement settlement = nearest.get();
        RecruitmentResult result = SettlementRecruitmentService.recruit(level, settlement, profileId);
        if (!result.success()) {
            source.sendFailure(Component.translatable("settlers.command.recruit.failed", result.reason()));
            return 0;
        }
        String work = result.work() == null ? "unemployed" : result.work().toShortString();
        source.sendSuccess(() -> Component.translatable("settlers.command.recruit.success",
                profileId.toString(), settlement.name(), result.home().toShortString(), work), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int convert(
            com.mojang.brigadier.context.CommandContext<CommandSourceStack> context
    ) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();

        if (player == null) {
            source.sendFailure(Component.translatable("settlers.command.player_only"));
            return 0;
        }

        ServerLevel level = source.getLevel();

        /*
         * Force conversion only requires that the player be inside a recognized
         * settlement candidate. It deliberately bypasses normal infrastructure
         * qualification and the passive conversion-chance rejection.
         */
        Optional<VillageDetector.CandidateDiagnostic> diagnostic =
                VillageDetector.diagnoseAt(level, player);

        if (diagnostic.isEmpty()) {
            source.sendFailure(Component.translatable("settlers.command.convert.no_candidate"));
            return 0;
        }

        VillageDetector.CandidateDiagnostic candidate =
                diagnostic.get();

        SettlementManager manager =
                SettlementManager.get(level);

        if (manager.isConverted(
                candidate.start().getChunkPos()
        )) {
            source.sendFailure(Component.translatable("settlers.command.convert.already"));
            return 0;
        }

        /*
         * Force-convert bypasses any prior passive conversion rejection.
         */
        manager.unrejectStart(
                candidate.start().getChunkPos()
        );

        Settlement settlement =
                SettlementConverter.convert(
                        level,
                        candidate.start(),
                        candidate.villageType()
                );

        Component qualificationNote =
                candidate.qualification().accepted()
                        ? Component.empty()
                        : Component.translatable("settlers.command.convert.bypassed",
                                candidate.qualification().reason());

        source.sendSuccess(
                () -> Component.translatable("settlers.command.convert.success",
                        candidate.structureId().toString(),
                        settlement.name(),
                        settlement.id().toString(),
                        qualificationNote),
                true
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        List<Settlement> settlements = SettlementManager.get(context.getSource().getLevel()).all();
        if (settlements.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("settlers.command.no_settlements"), false);
            return 0;
        }
        for (Settlement s : settlements) {
            context.getSource().sendSuccess(() -> Component.translatable("settlers.command.list.entry",
                    s.name(), s.id().toString(), s.center().toShortString(), s.profile().primaryEconomy(),
                    s.profile().authority(), s.threatState().getSerializedName(), s.roster().size()), false);
        }
        return settlements.size();
    }

    private static int info(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return withNearestSettlement(context, settlement -> {
            CommandSourceStack source = context.getSource();
            source.sendSuccess(() -> Component.translatable("settlers.command.info.header",
                    settlement.name(), settlement.id().toString()), false);
            SettlementProfile profile = settlement.profile();
            String secondary = profile.secondaryEconomy().isEmpty() ? "" : " / " + profile.secondaryEconomy();
            source.sendSuccess(() -> Component.translatable("settlers.command.info.source",
                    profile.sourceVillageType(), profile.primaryEconomy(), secondary), false);
            source.sendSuccess(() -> Component.translatable("settlers.command.info.authority",
                    profile.authority(), profile.faction(), profile.culture()), false);
            source.sendSuccess(() -> Component.translatable("settlers.command.info.threat",
                    profile.localThreat(), settlement.threatState().getSerializedName(),
                    settlement.currentSituation()), false);
            source.sendSuccess(() -> Component.translatable("settlers.command.info.morale",
                    SettlementMoraleModel.evaluate(source.getLevel(), settlement).getSerializedName()), false);
            source.sendSuccess(() -> Component.translatable("settlers.command.info.structures_header",
                    settlement.structures().size()), false);
            Map<String, Long> roleCounts = settlement.structures().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            s -> s.role().getSerializedName(), java.util.stream.Collectors.counting()));
            for (StructureAssignment s : settlement.structures()) {
                source.sendSuccess(() -> Component.translatable("settlers.command.info.structure_line",
                        s.role().getSerializedName(), s.center().toShortString(), s.template().toString()), false);
            }
            source.sendSuccess(() -> Component.translatable("settlers.command.info.role_summary",
                    String.valueOf(roleCounts)), false);
            source.sendSuccess(() -> Component.translatable("settlers.command.info.roster",
                    settlement.roster().size(),
                    settlement.roster().stream().map(r -> r.profile().getPath()).toList().toString()), false);
            ReeveTheme.ReeveIdentity reeve = ReeveTheme.identify(settlement);
            source.sendSuccess(() -> Component.translatable("settlers.command.info.reeve",
                    reeve.title(), Component.translatable(reeve.theme().translationKey())), false);
            appendProductionInfo(source, settlement);
            appendEconomyInfo(source, settlement);
            return 1;
        });
    }

    /// The compact economy readout, shared by `/settlers info` and `/settlers economy`. Read-only.
    private static void appendEconomyInfo(CommandSourceStack source, Settlement settlement) {
        SettlementEconomyReport report = SettlementEconomyModel.report(source.getLevel(), settlement);
        source.sendSuccess(() -> Component.translatable("settlers.command.economy.header_line"), false);
        source.sendSuccess(() -> Component.translatable("settlers.command.economy.condition",
                report.condition().getSerializedName(), report.population()), false);
        source.sendSuccess(() -> Component.translatable("settlers.command.economy.production",
                fmt(report.dailyFoodProduction()), fmt(report.dailyFoodConsumption())), false);
        source.sendSuccess(() -> Component.translatable("settlers.command.economy.surplus",
                signed(report.projectedDailySurplus()), fmt(report.foodStorageReserve()),
                fmt(report.storedFood()), fmt(report.foodDebt())), false);
    }

    private static int economy(CommandContext<CommandSourceStack> context) {
        return withNearestSettlement(context, settlement -> {
            context.getSource().sendSuccess(() -> Component.translatable("settlers.command.economy.header",
                    settlement.name()), false);
            appendEconomyInfo(context.getSource(), settlement);
            return 1;
        });
    }

    private static int provisionsAdd(CommandContext<CommandSourceStack> context) {
        int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "count");
        return withNearestSettlement(context, settlement -> {
            int added = settlement.economy().addBundles(count);
            SettlementManager.get(context.getSource().getLevel()).markDirty();
            context.getSource().sendSuccess(() -> Component.translatable("settlers.command.provisions.added",
                    added, settlement.name(), settlement.economy().claimableProvisionBundles(),
                    SettlementEconomyState.MAX_PROVISION_BUNDLES_STORED), true);
            return 1;
        });
    }

    private static int provisionsClear(CommandContext<CommandSourceStack> context) {
        return withNearestSettlement(context, settlement -> {
            settlement.economy().setBundles(0);
            SettlementManager.get(context.getSource().getLevel()).markDirty();
            context.getSource().sendSuccess(() -> Component.translatable("settlers.command.provisions.cleared",
                    settlement.name()), true);
            return 1;
        });
    }

    private static int foodAdd(CommandContext<CommandSourceStack> context) {
        double amount = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(context, "amount");
        return withNearestSettlement(context, settlement -> {
            settlement.production().addStored(SettlementResources.FOOD, amount);
            SettlementManager.get(context.getSource().getLevel()).markDirty();
            context.getSource().sendSuccess(() -> Component.translatable("settlers.command.food.added",
                    fmt(amount), settlement.name(),
                    fmt(settlement.production().stored(SettlementResources.FOOD))), true);
            return 1;
        });
    }

    private static String signed(double value) {
        return (value >= 0.0 ? "+" : "") + fmt(value);
    }

    /// Diagnostic-only production/labor/food readout appended to `/settlers info`. Reads live state and
    /// uses the same projected production and consumption formulas as the economy report.
    private static void appendProductionInfo(CommandSourceStack source, Settlement settlement) {
        ServerLevel level = source.getLevel();
        ProductionState production = settlement.production();
        LaborReport report = SettlementLaborModel.report(level, settlement);

        source.sendSuccess(() -> Component.translatable("settlers.command.production.header"), false);
        source.sendSuccess(() -> Component.translatable("settlers.command.production.stored",
                fmt(production.stored(SettlementResources.FOOD)),
                fmt(production.stored(SettlementResources.ANIMAL_GOODS)),
                fmt(production.stored(SettlementResources.FISH))), false);

        source.sendSuccess(() -> Component.translatable("settlers.command.labor.header"), false);
        sendLaborLine(source, Component.translatable("settlers.command.labor.farmers"), report.entry("farmer"));
        sendLaborLine(source, Component.translatable("settlers.command.labor.herders"), report.entry("herder"));
        sendLaborLine(source, Component.translatable("settlers.command.labor.fishers"), report.entry("fisher"));

        double foodCapacity = FarmSizeClassifier.foodCapacity(
                FarmSizeClassifier.totalFoodTiers(level, settlement.structures(), settlement.anchors()),
                FarmSizeClassifier.infirmaryModifier(settlement.structures()));
        double projected = SettlementProductionTicker.projectedFoodPerDay(level, settlement);
        double consumption = SettlementEconomyModel.dailyConsumption(settlement);
        double storageReserve = SettlementFoodStorageReserve.count(level, settlement)
                * SettlementFoodStorageReserve.FOOD_PER_STORAGE_BLOCK;
        double surplus = projected + storageReserve - consumption;
        source.sendSuccess(() -> Component.translatable("settlers.command.food.header"), false);
        source.sendSuccess(() -> Component.translatable("settlers.command.food.capacity",
                fmt(foodCapacity), fmt(projected)), false);
        source.sendSuccess(() -> Component.translatable("settlers.command.food.consumption",
                fmt(consumption), fmt(surplus), fmt(storageReserve)), false);
    }

    private static void sendLaborLine(CommandSourceStack source, Component label, LaborEntry entry) {
        source.sendSuccess(() -> Component.translatable("settlers.command.labor.line",
                label, entry.active(), entry.assigned(), entry.required()), false);
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int setThreat(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "state");
        ThreatState state;
        try {
            state = ThreatState.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.translatable("settlers.command.threat.unknown", raw));
            return 0;
        }
        ThreatState finalState = state;
        return withNearestSettlement(context, settlement -> {
            settlement.setThreatState(finalState);
            SettlementManager.get(context.getSource().getLevel()).markDirty();
            context.getSource().sendSuccess(() -> Component.translatable("settlers.command.threat.set",
                    settlement.name(), finalState.getSerializedName()), true);
            return 1;
        });
    }

    private static int showAnchors(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return withNearestSettlement(context, settlement -> {
            ServerLevel level = context.getSource().getLevel();
            int total = 0;
            for (Map.Entry<AnchorType, List<BlockPos>> entry : settlement.anchors().view().entrySet()) {
                for (BlockPos pos : entry.getValue()) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.01);
                    total++;
                }
            }
            int finalTotal = total;
            context.getSource().sendSuccess(() -> Component.translatable("settlers.command.anchors.visualized",
                    finalTotal, settlement.name()), false);
            return finalTotal;
        });
    }

    private static int rebuildAnchors(CommandContext<CommandSourceStack> context) {
        return withNearestSettlement(context, settlement -> {
            ServerLevel level = context.getSource().getLevel();
            if (!SettlementAnchorUpdater.rebuild(level, settlement)) {
                context.getSource().sendFailure(Component.translatable("settlers.command.anchors.rebuild_unloaded"));
                return 0;
            }
            settlement.completeAnchorRebuild(level.getGameTime() + SettlementAnchorUpdater.AUDIT_INTERVAL_TICKS);
            SettlementManager.get(level).markDirty();
            context.getSource().sendSuccess(() -> Component.translatable(
                    "settlers.command.anchors.rebuilt", settlement.name()), true);
            return Command.SINGLE_SUCCESS;
        });
    }

    /// Sets the settlement's current situation and immediately re-stages it (props/travelers/bark).
    /// Lets a designer iterate on a situations/*.json file and re-trigger it with /reload + this
    /// command, without re-converting the whole village.
    private static int setSituation(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        String id = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "id");
        if (SituationManager.byName(id).isEmpty()) {
            context.getSource().sendFailure(Component.translatable("settlers.command.situation.unknown", id));
            return 0;
        }
        return withNearestSettlement(context, settlement -> {
            settlement.setCurrentSituation(id);
            SettlementManager.get(context.getSource().getLevel()).markDirty();
            SituationRunner.trigger(context.getSource().getLevel(), settlement);
            context.getSource().sendSuccess(() -> Component.translatable("settlers.command.situation.staged",
                    id, settlement.name()), true);
            return 1;
        });
    }

    /// Clears every roster entry's home/work/sleep binding and re-round-robins them, then pushes the
    /// fresh assignments onto already-spawned residents. Use this to fix a settlement that was
    /// populated by an older, buggier build (e.g. one where residents all ended up sharing a single
    /// bed or workstation) without needing to convert a fresh village. SettlementTicker also does this
    /// automatically for any newly-empty bindings on its normal cadence, but this forces an immediate,
    /// full re-assignment on demand.
    private static int repopulate(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
        return withNearestSettlement(context, settlement -> {
            settlement.roster().forEach(entry -> {
                entry.setHome(null);
                entry.clearWork();
                entry.setSleep(null);
            });
            int spawned = SettlerPopulator.populate(context.getSource().getLevel(), settlement);
            SettlementManager.get(context.getSource().getLevel()).markDirty();
            context.getSource().sendSuccess(() -> Component.translatable("settlers.command.repopulate.done",
                    settlement.name(), settlement.roster().size(), spawned), true);
            return 1;
        });
    }

    private static int withNearestSettlement(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,
                                              java.util.function.ToIntFunction<Settlement> action) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        Optional<Settlement> settlement = SettlementManager.get(level).at(pos)
                .or(() -> SettlementManager.get(level).nearest(pos));
        if (settlement.isEmpty()) {
            source.sendFailure(Component.translatable("settlers.command.no_settlements"));
            return 0;
        }
        return action.applyAsInt(settlement.get());
    }
}
