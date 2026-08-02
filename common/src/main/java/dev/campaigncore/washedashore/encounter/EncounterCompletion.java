package dev.campaigncore.washedashore.encounter;

import dev.campaigncore.washedashore.act.WashedAshoreStage;
import java.util.List;

/**
 * Data-defined consequences applied to each nearby player when an encounter's boss dies.
 * {@code advanceTo} stages are applied in order, then named {@code handlers} run the
 * special glue that isn't worth expressing as data (see EncounterManager completion effects).
 */
public record EncounterCompletion(
        String message,
        List<WashedAshoreStage> advanceTo,
        List<String> handlers
) {
    public EncounterCompletion {
        message = (message==null||message.isBlank()) ? "encounter_complete" : message;
        advanceTo = advanceTo==null ? List.of() : List.copyOf(advanceTo);
        handlers = handlers==null ? List.of() : List.copyOf(handlers);
    }
    public static final EncounterCompletion DEFAULT = new EncounterCompletion(null,null,null);
}
