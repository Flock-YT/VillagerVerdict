package cn.ericcraft.villagerverdict.model;

/** The three user-facing states for a villager. */
public enum DisplayStatus {
    KEEP("+"), DISCARD("−"), UNSCANNED("?");

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

    public static DisplayStatus resolve(VillagerRecord record, AnalysisResult.Status verdict) {
        if (record.snapshot() == null) return UNSCANNED;
        if (record.fixed()) return KEEP;
        return verdict == null ? UNSCANNED : valueOf(verdict.name());
    }
}
