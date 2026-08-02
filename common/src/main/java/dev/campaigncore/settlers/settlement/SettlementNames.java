package dev.campaigncore.settlers.settlement;

import net.minecraft.util.RandomSource;

/// Frontier-flavored settlement name generator. Deliberately small: names only need to make each
/// settlement referable and memorable during the beta.
public final class SettlementNames {
    private static final String[] PREFIXES = {
            "Ashwater", "Briarstone", "Coldwater", "Dunwall", "Elderberry", "Fallowspring",
            "Graywood", "Hartsford", "Iron", "Kestrel", "Larkspur", "Millstone",
            "Netherby", "Ironoak", "Pinewood", "Ravensmoor", "Saltbrook", "Thornstem",
            "Umberlea", "Western", "Eastern", "Northern", "Southern", "Wolfden", "Yarrow",
            "Barrowholm", "Holten", "Ashfall", "Lavender", "Barley", "Thistlecreek", "Maiven's",
            "Saltstone", "Saltspyre", "Tasali", "Whitewater", "Oakshade", "Sable", "Whisperwillow",
            "Wolfsbane", "Reedwhistle", "Thistlethorn", "Lioneye's", "Devil's", "Emperor's", "Sleepy"
    };
    private static final String[] SUFFIXES = {
            "Crossing", "Rest", "Hollow", "Reach", "Landing", "Outpost",
            "Waystation", "Ford", "Camp", "Yard", "Post", "Halt", "Terminus",
            "Terminal", "Cairn", "Haven", "Sanctuary", "Springs", "Watch", "Encampment",
            "Vale", "Shelter", "Stead", "Homestead", "Keep", "March", "Vigil", "Wells"
    };

    private SettlementNames() {
    }

    public static String generate(RandomSource random) {
        return PREFIXES[random.nextInt(PREFIXES.length)] + " " + SUFFIXES[random.nextInt(SUFFIXES.length)];
    }
}
