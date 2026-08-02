package dev.campaigncore.washedashore.encounter;

/**
 * One entry in a buildup meter's threshold table. {@code mode} selects when it triggers as the
 * meter changes from {@code before} to {@code now}:
 * <ul>
 *   <li>{@code equals}  — every tick {@code now == at}</li>
 *   <li>{@code cross}   — once when the meter rises past {@code at} ({@code before < at <= now})</li>
 *   <li>{@code modulo}  — while {@code now >= at} and {@code now % step == 0}</li>
 *   <li>{@code bucket}  — when {@code now} enters a new multiple of {@code step} below the threshold</li>
 * </ul>
 * When it fires, the optional {@code guard} is checked, then the optional {@code effect} runs and the
 * optional {@code message} is sent ({@code passLevel} appends the current meter value as an argument).
 */
public record ActivationMilestone(
        String mode,
        int at,
        int step,
        String message,
        boolean passLevel,
        String effect,
        String guard
) {
    public ActivationMilestone {
        if(mode==null||mode.isBlank())throw new IllegalArgumentException("milestone mode is required");
    }
}
