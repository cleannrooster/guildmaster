package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.MinecraftTestBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidentEntryCodecTest extends MinecraftTestBase {
    private static final ResourceLocation FARMER =
            ResourceLocation.fromNamespaceAndPath("settlers", "farmer");

    @Test
    void oldDataWithoutWorkTypeLoads() {
        // A pre-work_type roster entry: it has a work position but no persisted type.
        ResidentEntry legacy = new ResidentEntry(FARMER, null, new BlockPos(1, 2, 3),
                new BlockPos(4, 5, 6), null, new BlockPos(7, 8, 9));
        Tag encoded = ResidentEntry.CODEC.encodeStart(NbtOps.INSTANCE, legacy).result().orElseThrow();
        // The absence of the type is exactly what an older save looks like.
        assertFalse(((CompoundTag) encoded).contains("work_type"), "legacy entry must not write work_type");

        ResidentEntry loaded = ResidentEntry.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
        assertTrue(loaded.work().isPresent());
        assertEquals(new BlockPos(4, 5, 6), loaded.work().get());
        assertTrue(loaded.workType().isEmpty(), "missing work_type decodes to empty");
    }

    @Test
    void workTypeRoundTrips() {
        ResidentEntry entry = new ResidentEntry(FARMER);
        entry.setWork(AnchorType.CROP_TENDING, new BlockPos(10, 64, -20));
        Tag encoded = ResidentEntry.CODEC.encodeStart(NbtOps.INSTANCE, entry).result().orElseThrow();
        ResidentEntry loaded = ResidentEntry.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(AnchorType.CROP_TENDING, loaded.workType().orElseThrow());
        assertEquals(new BlockPos(10, 64, -20), loaded.work().orElseThrow());
    }

    @Test
    void clearWorkDropsBoth() {
        ResidentEntry entry = new ResidentEntry(FARMER);
        entry.setWork(AnchorType.FISHING, new BlockPos(0, 0, 0));
        entry.clearWork();
        assertTrue(entry.work().isEmpty());
        assertTrue(entry.workType().isEmpty());
    }

    @Test
    void pendingJobOfferRoundTrips() {
        ResidentEntry entry = new ResidentEntry(ResourceLocation.fromNamespaceAndPath("settlers", "civilian"));
        entry.setWork(AnchorType.CROP_TENDING, new BlockPos(4, 64, 7));
        entry.setJobOffer(FARMER);
        Tag encoded = ResidentEntry.CODEC.encodeStart(NbtOps.INSTANCE, entry).result().orElseThrow();
        ResidentEntry loaded = ResidentEntry.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();
        assertEquals(FARMER, loaded.jobOffer().orElseThrow());
    }
}
