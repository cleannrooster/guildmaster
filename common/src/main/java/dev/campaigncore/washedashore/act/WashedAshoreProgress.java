package dev.campaigncore.washedashore.act;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.HashSet;
import java.util.Set;
import dev.campaigncore.washedashore.recovery.ProneRecoveryData;

public final class WashedAshoreProgress {
    private WashedAshoreStage stage = WashedAshoreStage.NOT_STARTED;
    private final Set<ResourceLocation> defeatedBosses = new HashSet<>();
    private final Set<ResourceLocation> discoveredLandmarks = new HashSet<>();
    private boolean introPlayed;
    private RegionalQuestStage dreadQuest = RegionalQuestStage.LOCKED;
    private RegionalQuestStage crossingQuest = RegionalQuestStage.LOCKED;
    private int crossingInvestigation;
    private int dreadLevel;
    private int dreadManifestTicks;
    private boolean fragmentDreadInvocation;
    private boolean fragmentCrossingInvocation;
    private boolean questKeybindUsed;
    private ProneRecoveryData proneRecovery = new ProneRecoveryData();

    public WashedAshoreStage stage() { return stage; }
    public boolean introPlayed() { return introPlayed; }
    public RegionalQuestStage dreadQuest() { return dreadQuest; }
    public RegionalQuestStage crossingQuest() { return crossingQuest; }
    public int crossingInvestigation() { return crossingInvestigation; }
    public int dreadLevel() { return dreadLevel; }
    public int dreadManifestTicks() { return dreadManifestTicks; }
    public boolean fragmentDreadInvocation() { return fragmentDreadInvocation; }
    public boolean fragmentCrossingInvocation() { return fragmentCrossingInvocation; }
    public boolean questKeybindUsed() { return questKeybindUsed; }
    public ProneRecoveryData proneRecovery() { return proneRecovery; }
    public Set<ResourceLocation> defeatedBosses() { return Set.copyOf(defeatedBosses); }
    public Set<ResourceLocation> discoveredLandmarks() { return Set.copyOf(discoveredLandmarks); }
    public void setStage(WashedAshoreStage value) { stage = value; }
    public void advanceTo(WashedAshoreStage value) { if (value.ordinal() > stage.ordinal()) stage = value; }
    public void markIntroPlayed() { introPlayed = true; }
    public boolean defeat(ResourceLocation id) { return defeatedBosses.add(id); }
    public boolean discover(ResourceLocation id) { return discoveredLandmarks.add(id); }
    public void setDreadQuest(RegionalQuestStage value) { dreadQuest = value; }
    public void setCrossingQuest(RegionalQuestStage value) { crossingQuest = value; }
    public int investigateCrossing(int amount) { crossingInvestigation = Math.max(0, Math.min(100, crossingInvestigation + amount)); return crossingInvestigation; }
    public int changeDreadLevel(int amount) { dreadLevel = Math.max(0, Math.min(100, dreadLevel + amount)); return dreadLevel; }
    public int advanceDreadManifest(int ticks) { dreadManifestTicks += ticks; return dreadManifestTicks; }
    public void setDreadLevel(int value) { dreadLevel = Math.max(0, Math.min(100, value)); }
    public void setCrossingInvestigation(int value) { crossingInvestigation = Math.max(0, Math.min(100, value)); }
    public void resetDreadManifest() { dreadManifestTicks = 0; }
    public void beginFragmentDreadInvocation() { fragmentDreadInvocation = true; }
    public void beginFragmentCrossingInvocation() { fragmentCrossingInvocation = true; }
    public void clearFragmentDreadInvocation() { fragmentDreadInvocation = false; }
    public void clearFragmentCrossingInvocation() { fragmentCrossingInvocation = false; }
    public void markQuestKeybindUsed() { questKeybindUsed = true; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("stage", stage.name());
        tag.putBoolean("intro_played", introPlayed);
        tag.putString("dread_quest", dreadQuest.name());
        tag.putString("crossing_quest", crossingQuest.name());
        tag.putInt("crossing_investigation", crossingInvestigation);
        tag.putInt("dread_level", dreadLevel);
        tag.putInt("dread_manifest_ticks", dreadManifestTicks);
        tag.putBoolean("fragment_dread_invocation",fragmentDreadInvocation);
        tag.putBoolean("fragment_crossing_invocation",fragmentCrossingInvocation);
        tag.putBoolean("quest_keybind_used",questKeybindUsed);
        tag.put("prone_recovery",proneRecovery.save());
        tag.putString("defeated", defeatedBosses.stream().map(ResourceLocation::toString).sorted().reduce("", (a,b) -> a + (a.isEmpty()?"":";") + b));
        tag.putString("discovered", discoveredLandmarks.stream().map(ResourceLocation::toString).sorted().reduce("", (a,b) -> a + (a.isEmpty()?"":";") + b));
        return tag;
    }

    public static WashedAshoreProgress load(CompoundTag tag) {
        WashedAshoreProgress result = new WashedAshoreProgress();
        try { result.stage = WashedAshoreStage.valueOf(tag.getString("stage")); } catch (IllegalArgumentException ignored) {}
        result.introPlayed = tag.getBoolean("intro_played");
        try { result.dreadQuest = RegionalQuestStage.valueOf(tag.getString("dread_quest")); } catch (IllegalArgumentException ignored) {}
        try { result.crossingQuest = RegionalQuestStage.valueOf(tag.getString("crossing_quest")); } catch (IllegalArgumentException ignored) {}
        result.crossingInvestigation = tag.getInt("crossing_investigation");
        result.dreadLevel = tag.getInt("dread_level");
        result.dreadManifestTicks = tag.getInt("dread_manifest_ticks");
        result.fragmentDreadInvocation=tag.getBoolean("fragment_dread_invocation");
        result.fragmentCrossingInvocation=tag.getBoolean("fragment_crossing_invocation");
        result.questKeybindUsed=tag.getBoolean("quest_keybind_used");
        if(tag.contains("prone_recovery"))result.proneRecovery=ProneRecoveryData.load(tag.getCompound("prone_recovery"));
        parseIds(tag.getString("defeated"), result.defeatedBosses);
        parseIds(tag.getString("discovered"), result.discoveredLandmarks);
        return result;
    }
    private static void parseIds(String raw, Set<ResourceLocation> out) {
        if (raw.isBlank()) return;
        for (String value : raw.split(";")) {
            ResourceLocation id = ResourceLocation.tryParse(value);
            if (id != null) out.add(id);
        }
    }
}
