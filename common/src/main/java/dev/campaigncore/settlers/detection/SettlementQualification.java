package dev.campaigncore.settlers.detection;

import java.util.List;

public record SettlementQualification(
        boolean accepted,
        String reason,
        InfrastructureScan infrastructure,
        List<String> failures
) {
    public SettlementQualification {
        reason = reason == null ? "" : reason;
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public static SettlementQualification accepted(
            String reason,
            InfrastructureScan infrastructure
    ) {
        return new SettlementQualification(
                true,
                reason,
                infrastructure,
                List.of()
        );
    }

    public static SettlementQualification accepted(String reason) {
        return accepted(reason, InfrastructureScan.EMPTY);
    }

    public static SettlementQualification rejected(
            String reason,
            InfrastructureScan infrastructure,
            List<String> failures
    ) {
        return new SettlementQualification(
                false,
                reason,
                infrastructure,
                failures
        );
    }

    public static SettlementQualification rejected(
            String reason,
            InfrastructureScan infrastructure
    ) {
        return rejected(reason, infrastructure, List.of(reason));
    }
}