package dev.campaigncore.washedashore.encounter;

import dev.campaigncore.washedashore.encounter.EncounterDefinition.RaidMember;
import java.util.List;

/**
 * A data-defined wave horde: instead of a single boss, the encounter raises successive {@code waves}
 * of mobs from the ground, each wave spawning only once the previous one has been cleared. Members are
 * scattered within {@code spawnRadius} of the point they rise at, and the encounter judges a wave (and
 * ultimately itself) cleared by scanning {@code scanRadius} for surviving horde members.
 *
 * <p>This is the temporary stand-in used by the Devil's Crossing encounter while its authored boss (the
 * Thrasher) is unfinished; see {@link dev.campaigncore.washedashore.act.CrossingHordeManager}. Reverting
 * to the boss is a pure-data change: drop this block and point {@code on_full.action} back at a boss spawn.
 */
public record HordeProfile(int spawnRadius,int scanRadius,List<Wave> waves){
    /** One authored wave: the roster of mobs that rises together. */
    public record Wave(List<RaidMember> members){
        public Wave{
            members=members==null?List.of():List.copyOf(members);
            if(members.isEmpty())throw new IllegalArgumentException("a horde wave must list at least one member");
        }
    }
    public HordeProfile{
        if(spawnRadius<=0)throw new IllegalArgumentException("horde spawn_radius must be positive");
        if(scanRadius<=0)throw new IllegalArgumentException("horde scan_radius must be positive");
        waves=waves==null?List.of():List.copyOf(waves);
        if(waves.isEmpty())throw new IllegalArgumentException("a horde must list at least one wave");
    }
    public int waveCount(){return waves.size();}
}
