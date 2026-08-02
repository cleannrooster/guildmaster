package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.Optional;

/// Brief paired conversation: approach a nearby resident, stop, and face one another.
public final class ConversationBehavior implements SettlerBehavior {
    private static final double SEARCH_RADIUS = 10.0;
    private static final double TALK_DISTANCE_SQ = 6.25;
    private boolean attemptedPairing;

    @Override
    public boolean tick(SettlerEntity settler, Settlement settlement, ServerLevel level) {
        long now = level.getGameTime();
        if (!settler.hasActiveConversation(now)) {
            if (this.attemptedPairing) {
                return false;
            }
            this.attemptedPairing = true;
            Optional<SettlerEntity> partner = level.getEntitiesOfClass(
                            SettlerEntity.class,
                            settler.getBoundingBox().inflate(SEARCH_RADIUS),
                            other -> other != settler
                                    && other.isAlive()
                                    && other.getTarget() == null
                                    && !other.hasDeliveryRequest()
                                    && other.settlementId().equals(settler.settlementId())
                                    && !other.hasActiveConversation(now))
                    .stream()
                    .min(Comparator.comparingDouble(settler::distanceToSqr));
            if (partner.isEmpty()) {
                return false;
            }
            long until = now + 160L + settler.getRandom().nextInt(161);
            settler.beginConversation(partner.get().getUUID(), until);
            partner.get().beginConversation(settler.getUUID(), until);
        }

        SettlerEntity partner = settler.conversationPartner()
                .map(level::getEntity)
                .filter(SettlerEntity.class::isInstance)
                .map(SettlerEntity.class::cast)
                .filter(other -> other.isAlive()
                        && settler.settlementId().equals(other.settlementId())
                        && other.conversationPartner().filter(settler.getUUID()::equals).isPresent())
                .orElse(null);
        if (partner == null) {
            settler.clearConversation();
            return false;
        }
        if (settler.distanceToSqr(partner) > TALK_DISTANCE_SQ) {
            if (settler.getNavigation().isDone()) {
                settler.getNavigation().moveTo(partner, 0.75);
            }
        } else {
            settler.getNavigation().stop();
            settler.getLookControl().setLookAt(partner, 30.0F, 30.0F);
        }
        return settler.hasActiveConversation(now);
    }

    @Override
    public void stop(SettlerEntity settler) {
        settler.getNavigation().stop();
        settler.clearConversation();
    }
}
