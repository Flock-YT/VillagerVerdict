package cn.ericcraft.villagerverdict.model;

import java.util.Objects;
import java.util.UUID;

/** Trade history only; active position, annotations and verdicts are never cached. */
public record CachedTrade(UUID uuid, String profession, TradeSnapshot snapshot) {
    public CachedTrade {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(profession);
        Objects.requireNonNull(snapshot);
    }

    public static CachedTrade from(VillagerRecord record) {
        return new CachedTrade(record.uuid(), record.profession(), record.snapshot());
    }

    public TradeSnapshot forProfession(String currentProfession) {
        return profession.equals(currentProfession) ? snapshot : null;
    }
}
