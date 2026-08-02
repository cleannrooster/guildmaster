package dev.campaigncore.settlers.expansion;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record RuntimeTemplateResult(
        boolean success,
        String message,
        BoundingBox bounds
) {
    public RuntimeTemplateResult {
        message = message == null ? "" : message;
    }

    public static RuntimeTemplateResult failure(
            String message
    ) {
        return new RuntimeTemplateResult(
                false,
                message,
                null
        );
    }

    public static RuntimeTemplateResult success(
            BoundingBox bounds
    ) {
        return new RuntimeTemplateResult(
                true,
                "Template placed successfully.",
                bounds
        );
    }
}