package dev.campaigncore.settlers.detection;

public record InfrastructureScan(
        int structurePieces,
        int beds,
        int farmland,
        int plantedCrops,
        int composters,
        int hayBales,
        int workstations,
        int bells
) {
    public static final InfrastructureScan EMPTY =
            new InfrastructureScan(0, 0, 0, 0, 0, 0, 0, 0);

    public boolean hasFoodInfrastructure() {
        return farmland >= 8
                || composters > 0
                || hayBales > 0;

    }
}
