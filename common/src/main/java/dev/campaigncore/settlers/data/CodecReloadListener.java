package dev.campaigncore.settlers.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.campaigncore.settlers.SettlersMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/// Generic codec-backed JSON datapack directory loader. The consumer receives the full parsed map on
/// each (re)load; malformed files are logged and skipped rather than aborting the reload.
public final class CodecReloadListener<T> extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    private final String directory;
    private final Codec<T> codec;
    private final Consumer<Map<ResourceLocation, T>> apply;

    public CodecReloadListener(String directory, Codec<T> codec, Consumer<Map<ResourceLocation, T>> apply) {
        super(GSON, directory);
        this.directory = directory;
        this.codec = codec;
        this.apply = apply;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, T> parsed = new HashMap<>();
        jsons.forEach((id, json) -> this.codec.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error ->
                        SettlersMod.LOGGER.error("Failed to parse {}/{}: {}", this.directory, id, error))
                .ifPresent(value -> parsed.put(id, value)));
        this.apply.accept(parsed);
        SettlersMod.LOGGER.info("Loaded {} {} definitions.", parsed.size(), this.directory);
    }
}
