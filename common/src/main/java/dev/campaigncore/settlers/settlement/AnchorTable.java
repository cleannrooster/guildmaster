package dev.campaigncore.settlers.settlement;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The settlement's typed anchor positions — the only spatial vocabulary behaviors are allowed to
/// consume. Generated once at conversion (plus situation-scoped temporary anchors later); never
/// rescanned per tick.
public final class AnchorTable {
    public static final Codec<AnchorTable> CODEC =
            Codec.unboundedMap(AnchorType.CODEC, BlockPos.CODEC.listOf())
                    .xmap(AnchorTable::new, table -> table.anchors);

    private final Map<AnchorType, List<BlockPos>> anchors;

    public AnchorTable() {
        this.anchors = new EnumMap<>(AnchorType.class);
    }

    private AnchorTable(Map<AnchorType, List<BlockPos>> anchors) {
        this.anchors = new EnumMap<>(AnchorType.class);
        anchors.forEach((type, positions) -> this.anchors.put(type, new ArrayList<>(positions)));
    }

    public void add(AnchorType type, BlockPos pos) {
        List<BlockPos> positions = this.anchors.computeIfAbsent(type, ignored -> new ArrayList<>());
        BlockPos immutable = pos.immutable();
        if (!positions.contains(immutable)) {
            positions.add(immutable);
        }
    }

    public List<BlockPos> all(AnchorType type) {
        return Collections.unmodifiableList(this.anchors.getOrDefault(type, List.of()));
    }

    public Optional<BlockPos> first(AnchorType type) {
        List<BlockPos> positions = this.anchors.getOrDefault(type, List.of());
        return positions.isEmpty() ? Optional.empty() : Optional.of(positions.getFirst());
    }

    public Optional<BlockPos> random(AnchorType type, RandomSource random) {
        List<BlockPos> positions = this.anchors.getOrDefault(type, List.of());
        return positions.isEmpty() ? Optional.empty() : Optional.of(positions.get(random.nextInt(positions.size())));
    }

    public Map<AnchorType, List<BlockPos>> view() {
        return Collections.unmodifiableMap(this.anchors);
    }

    public int count(AnchorType type) {
        return this.anchors.getOrDefault(type, List.of()).size();
    }

    /// Atomically replaces this table's contents after a complete structure scan.
    public void replaceWith(AnchorTable replacement) {
        this.anchors.clear();
        replacement.anchors.forEach((type, positions) ->
                this.anchors.put(type, new ArrayList<>(positions)));
    }

    public boolean contains(AnchorType type, BlockPos pos) {
        return this.anchors.getOrDefault(type, List.of()).contains(pos);
    }
}
