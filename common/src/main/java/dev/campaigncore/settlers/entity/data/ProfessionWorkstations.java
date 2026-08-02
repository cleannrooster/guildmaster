package dev.campaigncore.settlers.entity.data;

import dev.campaigncore.settlers.settlement.AnchorType;

import java.util.List;
import java.util.Map;

/// Default profession -> preferred workstation anchor types, keyed by the free-form `profession`
/// string on {@link SettlerProfile}. Consulted only when a profile doesn't explicitly declare its own
/// {@link SettlerProfile#workstation()} list, so most profiles (civilian, militia, farmer, smith,
/// shrine_keeper, reeve, ...) need no JSON changes at all — they already carry the right profession
/// name. Each list is a fallback chain: {@link dev.campaigncore.settlers.settlement.SettlerPopulator} tries anchor
/// types in order and uses the first one the settlement actually has any of, ultimately falling back
/// to {@link AnchorType#WORK}.
public final class ProfessionWorkstations {
    private static final Map<String, List<AnchorType>> DEFAULTS = Map.ofEntries(
            Map.entry("farmer", List.of(AnchorType.CROP_TENDING, AnchorType.WORK)),
            Map.entry("herder", List.of(AnchorType.ANIMAL_TENDING, AnchorType.WORK)),
            Map.entry("shepherd", List.of(AnchorType.ANIMAL_TENDING, AnchorType.WORK)),
            Map.entry("stable_hand", List.of(AnchorType.ANIMAL_TENDING, AnchorType.WORK)),
            Map.entry("fisher", List.of(AnchorType.FISHING, AnchorType.WORK)),
            Map.entry("fisherman", List.of(AnchorType.FISHING, AnchorType.WORK)),
            Map.entry("smith", List.of(AnchorType.WORK)),
            Map.entry("carpenter", List.of(AnchorType.WORK)),
            Map.entry("cook", List.of(AnchorType.MEAL, AnchorType.WORK)),
            Map.entry("shrine_keeper", List.of(AnchorType.SHRINE)),
            Map.entry("reeve", List.of(AnchorType.AUTHORITY)),
            Map.entry("records_keeper", List.of(AnchorType.MAP_TABLE)),
            Map.entry("cartographer", List.of(AnchorType.MAP_TABLE)),
            Map.entry("trader", List.of(AnchorType.CARRY_DESTINATION, AnchorType.STORAGE)),
            Map.entry("storekeeper", List.of(AnchorType.CARRY_DESTINATION, AnchorType.STORAGE)),
            Map.entry("apothecary", List.of(AnchorType.INFIRMARY))
    );

    private static final List<AnchorType> FALLBACK = List.of(AnchorType.WORK);

    private ProfessionWorkstations() {
    }

    public static List<AnchorType> forProfession(String profession) {
        return DEFAULTS.getOrDefault(profession, FALLBACK);
    }
}
