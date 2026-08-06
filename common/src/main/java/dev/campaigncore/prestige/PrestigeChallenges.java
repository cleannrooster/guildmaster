package dev.campaigncore.prestige;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Maps each act to the fragment item that invokes its prestige challenge (the act's final boss at
 * full power). An act opts into prestige with one {@link #register} call at init; the shared
 * invoker/presence/wipe rules live in {@link PrestigeManager} and the act's own fight manager.
 */
public final class PrestigeChallenges {
    private static final Map<ResourceLocation,Supplier<Item>> FRAGMENTS=new LinkedHashMap<>();

    private PrestigeChallenges(){}

    public static void register(ResourceLocation actId,Supplier<Item> fragment){
        FRAGMENTS.put(actId,fragment);
    }
    /** The registered fragment for an act, or null when the act has no prestige challenge. */
    public static Item fragment(ResourceLocation actId){
        Supplier<Item> supplier=FRAGMENTS.get(actId);
        return supplier==null?null:supplier.get();
    }
}
