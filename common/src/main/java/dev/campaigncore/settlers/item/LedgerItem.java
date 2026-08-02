package dev.campaigncore.settlers.item;

import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

/// The Settlement Ledger: a survey tool. Using it scans nearby settlements and lists, as clickable chat
/// rows, any reeve who has claimable provision bundles ready. Clicking a row fires the player-usable
/// {@code /settlers_deliver <id>} command, which dispatches that settlement's reeve to walk over and
/// hand the bundles across. The item itself only reads state and prints — no state is mutated here.
@Deprecated(forRemoval = true)
public final class LedgerItem extends Item {
    /// Only settlements whose center is within this many blocks of the player are listed.
    public static final double SCAN_RADIUS = 128.0;
    private static final double SCAN_RADIUS_SQR = SCAN_RADIUS * SCAN_RADIUS;

    public LedgerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel)) {
            // All settlement state lives server-side; the client just reports a successful use so the
            // arm swings.
            return InteractionResultHolder.success(held);
        }

        List<Settlement> nearby = SettlementManager.get(serverLevel).all().stream()
                .filter(settlement -> settlement.center().distToCenterSqr(player.position()) <= SCAN_RADIUS_SQR)
                .sorted(Comparator.comparingDouble(settlement -> settlement.center().distToCenterSqr(player.position())))
                .toList();

        if (nearby.isEmpty()) {
            player.sendSystemMessage(Component.translatable("settlers.ledger.none"));
            return InteractionResultHolder.success(held);
        }

        player.sendSystemMessage(Component.translatable("settlers.ledger.header")
                .withStyle(ChatFormatting.GOLD));
        for (Settlement settlement : nearby) {
            int bundles = settlement.economy().claimableProvisionBundles();
            Component line = Component.translatable("settlers.ledger.entry", settlement.name(), bundles);
            line = line.copy().append(Component.literal(" ")).append(Component.translatable(
                    "settlers.ledger.morale", dev.campaigncore.settlers.settlement.SettlementMoraleModel
                            .evaluate(serverLevel, settlement).getSerializedName()).withStyle(ChatFormatting.GRAY));
            if (bundles > 0) {
                Component request = Component.translatable("settlers.ledger.request")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/settlers_deliver " + settlement.id()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable("settlers.ledger.request_hover",
                                                settlement.name(), bundles))));
                line = line.copy().append(Component.literal(" ")).append(request);
            }
            if (bundles > 0 && dev.campaigncore.settlers.settlement.SettlementRecruitmentService.hasAvailableHousing(settlement)
                    && settlement.threatState() == dev.campaigncore.settlers.settlement.ThreatState.NORMAL) {
                Component recruit = Component.translatable("settlers.ledger.recruit")
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                        "/settlers_recruit " + settlement.id()))
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable("settlers.ledger.recruit_hover", settlement.name()))));
                line = line.copy().append(Component.literal(" ")).append(recruit);
            }
            Component population = Component.translatable("settlers.ledger.population")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.YELLOW)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/settlers_ledger_population " + settlement.id()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("settlers.ledger.population_hover", settlement.name()))));
            Component redraw = Component.translatable("settlers.ledger.redraw_anchors")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.GRAY)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/settlers_ledger_redraw " + settlement.id()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("settlers.ledger.redraw_anchors_hover", settlement.name()))));
            Component chronicle = Component.translatable("settlers.ledger.chronicle")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.LIGHT_PURPLE)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/settlers_ledger_chronicle " + settlement.id()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.translatable("settlers.ledger.chronicle_hover", settlement.name()))));
            line = line.copy().append(Component.literal(" ")).append(population)
                    .append(Component.literal(" ")).append(chronicle)
                    .append(Component.literal(" ")).append(redraw);
            player.sendSystemMessage(line);
        }
        return InteractionResultHolder.success(held);
    }
}
