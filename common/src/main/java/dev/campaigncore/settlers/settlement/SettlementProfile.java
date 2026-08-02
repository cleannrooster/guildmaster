package dev.campaigncore.settlers.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// The descriptive identity of a settlement, matching the design doc's settlement-profile schema.
/// Facets are open strings, not enums — vocabulary datapacks extend — except where a facet
/// explicitly drives a mechanic (that's {@link ThreatState}, tracked separately on {@link Settlement}).
public record SettlementProfile(
        String name,
        String sourceVillageType,
        String primaryEconomy,
        String secondaryEconomy,
        String authority,
        String faction,
        String localThreat,
        String currentSituation,
        String culture,
        String condition
) {
    public static final Codec<SettlementProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(SettlementProfile::name),
            Codec.STRING.fieldOf("source_village_type").forGetter(SettlementProfile::sourceVillageType),
            Codec.STRING.fieldOf("primary_economy").forGetter(SettlementProfile::primaryEconomy),
            Codec.STRING.optionalFieldOf("secondary_economy", "").forGetter(SettlementProfile::secondaryEconomy),
            Codec.STRING.fieldOf("authority").forGetter(SettlementProfile::authority),
            Codec.STRING.fieldOf("faction").forGetter(SettlementProfile::faction),
            Codec.STRING.fieldOf("local_threat").forGetter(SettlementProfile::localThreat),
            Codec.STRING.fieldOf("current_situation").forGetter(SettlementProfile::currentSituation),
            Codec.STRING.fieldOf("culture").forGetter(SettlementProfile::culture),
            Codec.STRING.fieldOf("condition").forGetter(SettlementProfile::condition)
    ).apply(instance, SettlementProfile::new));

    public SettlementProfile withCurrentSituation(String situation) {
        return new SettlementProfile(this.name, this.sourceVillageType, this.primaryEconomy, this.secondaryEconomy,
                this.authority, this.faction, this.localThreat, situation, this.culture, this.condition);
    }
}
