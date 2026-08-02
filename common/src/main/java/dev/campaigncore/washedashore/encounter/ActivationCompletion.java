package dev.campaigncore.washedashore.encounter;

/**
 * What happens when a buildup meter reaches its threshold: an optional {@code guard} is checked, then
 * the optional {@code message} is sent and the named {@code action} (spawn/manifestation glue) runs.
 */
public record ActivationCompletion(
        String message,
        String guard,
        String action
) {
    public ActivationCompletion {
        if(action==null||action.isBlank())throw new IllegalArgumentException("on_full action is required");
    }
}
