package dev.campaigncore.settlers.settlement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/// One durable line in a settlement's history. Event is a stable localization key fragment; subject
/// carries the role, resident, or structure involved without persisting rendered English text.
public record ChronicleEntry(long gameTime, String event, String subject) {
    public static final Codec<ChronicleEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("game_time").forGetter(ChronicleEntry::gameTime),
            Codec.STRING.fieldOf("event").forGetter(ChronicleEntry::event),
            Codec.STRING.optionalFieldOf("subject", "").forGetter(ChronicleEntry::subject)
    ).apply(instance, ChronicleEntry::new));
}
