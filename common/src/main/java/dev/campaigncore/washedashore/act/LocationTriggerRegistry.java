package dev.campaigncore.washedashore.act;

import java.util.List;

/** Holds the datapack-loaded, ordered location triggers; swapped atomically on resource reload. */
public final class LocationTriggerRegistry {
    private volatile List<LocationTrigger> triggers=List.of();
    public List<LocationTrigger> all(){return triggers;}
    public synchronized void replace(List<LocationTrigger> values){triggers=List.copyOf(values);}
}
