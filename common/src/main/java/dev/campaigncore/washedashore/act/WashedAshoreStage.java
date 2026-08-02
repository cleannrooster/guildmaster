package dev.campaigncore.washedashore.act;
public enum WashedAshoreStage {
    NOT_STARTED, WASHED_ASHORE, GUIDE_FOUND, UNDERTAKER_DISCOVERED, UNDERTAKER_DEFEATED,
    SETTLEMENT_REACHED, REGIONAL_OBJECTIVES, RAVEN_ROUTE_REVEALED, RAVEN_DEFEATED, ACT_COMPLETE;
    public boolean atLeast(WashedAshoreStage other) { return ordinal() >= other.ordinal(); }
}
