package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.MinecraftTestBase;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChronicleEntryCodecTest extends MinecraftTestBase {
    @Test
    void chronicleEntryRoundTrips() {
        ChronicleEntry entry = new ChronicleEntry(48_000L, "resident_joined", "fisher");
        Tag encoded = ChronicleEntry.CODEC.encodeStart(NbtOps.INSTANCE, entry).result().orElseThrow();
        ChronicleEntry loaded = ChronicleEntry.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(entry, loaded);
    }
}
