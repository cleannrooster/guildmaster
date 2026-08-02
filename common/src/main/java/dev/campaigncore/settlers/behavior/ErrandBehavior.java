package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.entity.data.SettlerDataManager;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;

/// Short profession-flavored daytime trip between a workplace and useful settlement infrastructure.
public final class ErrandBehavior extends AnchorSeekingBehavior {
    public ErrandBehavior() {
        super(AnchorType.STORAGE);
    }

    @Override
    protected Optional<BlockPos> pickTarget(SettlerEntity settler, Settlement settlement) {
        String profession = settler.profileId()
                .flatMap(SettlerDataManager::profile)
                .map(profile -> profile.profession())
                .orElse("civilian");
        List<AnchorType> preferences = switch (profession) {
            case "farmer" -> List.of(AnchorType.STORAGE, AnchorType.WATER, AnchorType.CARRY_SOURCE);
            case "herder" -> List.of(AnchorType.WATER, AnchorType.STORAGE, AnchorType.CARRY_SOURCE);
            case "fisher" -> List.of(AnchorType.STORAGE, AnchorType.WATER, AnchorType.CARRY_DESTINATION);
            case "smith" -> List.of(AnchorType.STORAGE, AnchorType.CARRY_SOURCE, AnchorType.CARRY_DESTINATION);
            case "reeve" -> List.of(AnchorType.MAP_TABLE, AnchorType.AUTHORITY, AnchorType.PLAZA);
            case "guard" -> List.of(AnchorType.GUARD_POST, AnchorType.PLAZA, AnchorType.STORAGE);
            default -> List.of(AnchorType.WATER, AnchorType.STORAGE, AnchorType.PLAZA);
        };
        for (AnchorType type : preferences) {
            Optional<BlockPos> target = settlement.anchors().random(type, settler.getRandom());
            if (target.isPresent()) {
                return target;
            }
        }
        return Optional.of(settlement.center());
    }

    @Override
    protected double speed() {
        return 0.9;
    }
}
