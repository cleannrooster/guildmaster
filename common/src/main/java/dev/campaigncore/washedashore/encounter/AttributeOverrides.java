package dev.campaigncore.washedashore.encounter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.campaigncore.CampaignCore;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Optional;

/**
 * Data-configured attribute overrides applied to a selected candidate after it spawns, so an otherwise
 * suitable mob fits the campaign's expected difficulty. This is the primary tuning knob of the encounter
 * pool: it never touches an imported boss's mechanics, only its base attribute values.
 *
 * <p>{@code maxHealth}, {@code armor}, {@code armorToughness}, {@code knockbackResistance},
 * {@code followRange} and {@code scale} are absolute base values; {@code damageMultiplier} and
 * {@code speedMultiplier} multiply the entity's existing base (so ranged and melee mobs scale sanely
 * regardless of their native numbers). Every field is optional — an omitted field leaves that attribute
 * untouched — and an attribute absent from the entity is skipped with a warning rather than crashing.
 */
public record AttributeOverrides(
        Optional<Double> maxHealth,
        Optional<Double> damageMultiplier,
        Optional<Double> armor,
        Optional<Double> armorToughness,
        Optional<Double> speedMultiplier,
        Optional<Double> knockbackResistance,
        Optional<Double> followRange,
        Optional<Double> scale
) {
    public static final AttributeOverrides EMPTY = new AttributeOverrides(
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

    public static final Codec<AttributeOverrides> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("max_health").forGetter(AttributeOverrides::maxHealth),
            Codec.DOUBLE.optionalFieldOf("damage_multiplier").forGetter(AttributeOverrides::damageMultiplier),
            Codec.DOUBLE.optionalFieldOf("armor").forGetter(AttributeOverrides::armor),
            Codec.DOUBLE.optionalFieldOf("armor_toughness").forGetter(AttributeOverrides::armorToughness),
            Codec.DOUBLE.optionalFieldOf("speed_multiplier").forGetter(AttributeOverrides::speedMultiplier),
            Codec.DOUBLE.optionalFieldOf("knockback_resistance").forGetter(AttributeOverrides::knockbackResistance),
            Codec.DOUBLE.optionalFieldOf("follow_range").forGetter(AttributeOverrides::followRange),
            Codec.DOUBLE.optionalFieldOf("scale").forGetter(AttributeOverrides::scale)
    ).apply(instance, AttributeOverrides::new));

    /** True when no field is set, so callers can cheaply skip application. */
    public boolean isEmpty() {
        return maxHealth.isEmpty() && damageMultiplier.isEmpty() && armor.isEmpty() && armorToughness.isEmpty()
                && speedMultiplier.isEmpty() && knockbackResistance.isEmpty() && followRange.isEmpty() && scale.isEmpty();
    }

    /** Applies every configured override to {@code living}, healing it to full when max health changed. */
    public void applyTo(LivingEntity living) {
        maxHealth.ifPresent(value -> {
            if (setBase(living, Attributes.MAX_HEALTH, value)) living.setHealth(living.getMaxHealth());
        });
        damageMultiplier.ifPresent(factor -> multiplyBase(living, Attributes.ATTACK_DAMAGE, factor));
        armor.ifPresent(value -> setBase(living, Attributes.ARMOR, value));
        armorToughness.ifPresent(value -> setBase(living, Attributes.ARMOR_TOUGHNESS, value));
        speedMultiplier.ifPresent(factor -> multiplyBase(living, Attributes.MOVEMENT_SPEED, factor));
        knockbackResistance.ifPresent(value -> setBase(living, Attributes.KNOCKBACK_RESISTANCE, value));
        followRange.ifPresent(value -> setBase(living, Attributes.FOLLOW_RANGE, value));
        scale.ifPresent(value -> setBase(living, Attributes.SCALE, value));
    }

    private static boolean setBase(LivingEntity living, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance == null) {
            CampaignCore.LOGGER.warn("combat_override_missing_attribute uuid={} attribute={}",
                    living.getUUID(), attribute.unwrapKey().map(Object::toString).orElse("?"));
            return false;
        }
        instance.setBaseValue(Math.max(0, value));
        return true;
    }

    private static void multiplyBase(LivingEntity living, Holder<Attribute> attribute, double factor) {
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance == null) {
            CampaignCore.LOGGER.warn("combat_override_missing_attribute uuid={} attribute={}",
                    living.getUUID(), attribute.unwrapKey().map(Object::toString).orElse("?"));
            return;
        }
        instance.setBaseValue(Math.max(0, instance.getBaseValue() * factor));
    }
}
