package cn.ericcraft.villagerverdict.model;

/** Presentation state keeps scan freshness separate from a review verdict. */
public enum DisplayStatus {
    KEEP("+"), REVIEW("?"), REDUNDANT("−"), PENDING("?"), STALE("?"), ANALYZING("?");

    private final String symbol;

    DisplayStatus(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    public String translationKey() {
        return "vv.status." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static DisplayStatus resolve(VillagerRecord record, AnalysisResult.Status verdict, long now, long ttl) {
        if (record.fixed()) return KEEP;
        if (record.snapshot() == null) return PENDING;
        if (record.snapshot().level() != record.level() || record.snapshot().stale(now, ttl)) return STALE;
        return verdict == null ? ANALYZING : valueOf(verdict.name());
    }
}
