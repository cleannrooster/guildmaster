package dev.campaigncore.washedashore.act;

import dev.campaigncore.washedashore.encounter.EncounterAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class WashedAshoreInstance {
    /** Canonical layout-slot names that data (encounter anchors, location triggers) may reference. */
    public static final Set<String> SLOTS = Set.of(
            "beach","guide","graveyard","settlement","raven","dark_forest","devils_crossing","other_settlement","sculk_surface");
    private UUID actInstanceId = UUID.randomUUID();
    private WashedAshoreGenerationStatus generationStatus = WashedAshoreGenerationStatus.UNINITIALIZED;
    private BlockPos beachSpawn, guideLandmark, undertakerGraveyard, settlement, ravenArena;
    private BlockPos beachSearchCenter;
    private BlockPos darkForest, devilsCrossing, otherSettlement, sculkSurface;
    private final Map<ResourceLocation, EncounterAnchor> encounters = new LinkedHashMap<>();
    private final Set<ResourceLocation> completedWorldObjectives = new HashSet<>();
    private int layoutVersion = 2;
    private int generationAttempts;
    /** Waves of the temporary Devil's Crossing horde already raised (world-level; see CrossingHordeManager). */
    private int crossingHordeWave;
    /** World-level state for the Sculk Surface arena/encounter (see SculkSurfaceManager). */
    private int sculkMobDeaths;
    private long sculkEncounterStartTick;
    private int sculkWavesSpawned;
    private UUID sculkRaven;
    /** Persisted timing/count baseline for the distant-settlement raid's mercy completion. */
    private long settlementRaidStartTick=-1;
    private int settlementRaidInitialCount;
    /** True only for the canonical storyline generated from the world's initial spawn. */
    private boolean worldSpawnStoryline=true;

    public UUID actInstanceId(){return actInstanceId;} public WashedAshoreGenerationStatus generationStatus(){return generationStatus;}
    public BlockPos beachSpawn(){return beachSpawn;} public BlockPos guideLandmark(){return guideLandmark;}
    public BlockPos beachSearchCenter(){return beachSearchCenter;}
    public BlockPos undertakerGraveyard(){return undertakerGraveyard;} public BlockPos settlement(){return settlement;}
    public BlockPos ravenArena(){return ravenArena;} public Map<ResourceLocation,EncounterAnchor> encounters(){return encounters;}
    public BlockPos darkForest(){return darkForest;} public BlockPos devilsCrossing(){return devilsCrossing;}
    public BlockPos otherSettlement(){return otherSettlement;}
    public BlockPos sculkSurface(){return sculkSurface;}
    public Set<ResourceLocation> completedWorldObjectives(){return completedWorldObjectives;}
    public int crossingHordeWave(){return crossingHordeWave;}
    public void setCrossingHordeWave(int wave){crossingHordeWave=Math.max(0,wave);}
    public int sculkMobDeaths(){return sculkMobDeaths;}
    public void setSculkMobDeaths(int value){sculkMobDeaths=Math.max(0,value);}
    public long sculkEncounterStartTick(){return sculkEncounterStartTick;}
    public void setSculkEncounterStartTick(long tick){sculkEncounterStartTick=Math.max(0,tick);}
    public int sculkWavesSpawned(){return sculkWavesSpawned;}
    public void setSculkWavesSpawned(int value){sculkWavesSpawned=Math.max(0,value);}
    public UUID sculkRaven(){return sculkRaven;}
    public void setSculkRaven(UUID uuid){sculkRaven=uuid;}
    public long settlementRaidStartTick(){return settlementRaidStartTick;}
    public void setSettlementRaidStartTick(long tick){settlementRaidStartTick=tick;}
    public int settlementRaidInitialCount(){return settlementRaidInitialCount;}
    public void setSettlementRaidInitialCount(int count){settlementRaidInitialCount=Math.max(0,count);}
    public boolean worldSpawnStoryline(){return worldSpawnStoryline;}
    public void setWorldSpawnStoryline(boolean value){worldSpawnStoryline=value;}
    /** Resolves a canonical {@link #SLOTS} name to its selected world position, or null if unset. */
    public BlockPos slot(String name){
        return switch(name){
            case "beach"->beachSpawn;
            case "guide"->guideLandmark;
            case "graveyard"->undertakerGraveyard;
            case "settlement"->settlement;
            case "raven"->ravenArena;
            case "dark_forest"->darkForest;
            case "devils_crossing"->devilsCrossing;
            case "other_settlement"->otherSettlement;
            case "sculk_surface"->sculkSurface;
            default->null;
        };
    }
    public int layoutVersion(){return layoutVersion;} public int generationAttempts(){return generationAttempts;}
    public void setLayoutVersion(int version){layoutVersion=version;}
    public void setStatus(WashedAshoreGenerationStatus status){generationStatus=status;}
    public void setLayout(BlockPos beach,BlockPos guide,BlockPos graveyard,BlockPos settlement,BlockPos raven){
        this.beachSpawn=beach;this.guideLandmark=guide;this.undertakerGraveyard=graveyard;this.settlement=settlement;this.ravenArena=raven;
    }
    public void setBeachSpawn(BlockPos beach){this.beachSpawn=beach;}
    public void setBeachSearchCenter(BlockPos center){this.beachSearchCenter=center;}
    public void setSettlement(BlockPos position){this.settlement=position;}
    public void setOtherSettlement(BlockPos position){this.otherSettlement=position;}
    public void setDevilsCrossing(BlockPos position){this.devilsCrossing=position;}
    public void setUndertakerGraveyard(BlockPos position){this.undertakerGraveyard=position;}
    public void setSculkSurface(BlockPos position){this.sculkSurface=position;}
    public void setRegionalLayout(BlockPos forest, BlockPos crossing, BlockPos other) {
        darkForest=forest;devilsCrossing=crossing;otherSettlement=other;
    }
    public int nextAttempt(){return ++generationAttempts;}
    public boolean hasLayout(){return beachSpawn!=null&&guideLandmark!=null&&undertakerGraveyard!=null&&settlement!=null&&ravenArena!=null;}
    public void reset(){
        actInstanceId=UUID.randomUUID();generationStatus=WashedAshoreGenerationStatus.UNINITIALIZED;
        beachSpawn=guideLandmark=undertakerGraveyard=settlement=ravenArena=darkForest=devilsCrossing=otherSettlement=beachSearchCenter=sculkSurface=null;
        encounters.clear();completedWorldObjectives.clear();generationAttempts=0;crossingHordeWave=0;
        sculkMobDeaths=0;sculkEncounterStartTick=0;sculkWavesSpawned=0;sculkRaven=null;
        settlementRaidStartTick=-1;settlementRaidInitialCount=0;
        worldSpawnStoryline=true;
    }
    public void restore(UUID id,WashedAshoreGenerationStatus status,int version,int attempts){actInstanceId=id;generationStatus=status;layoutVersion=version;generationAttempts=attempts;}
}
