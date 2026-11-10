package cn.ericcraft.villagerverdict.analysis;

import cn.ericcraft.villagerverdict.model.Trade;
import cn.ericcraft.villagerverdict.model.VillagerRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Evaluates max-level enchanted book coverage across scanned librarian villagers.
 * An enchantment is considered owned only when at least one librarian offers it at or above its
 * maximum level; lower levels are recorded as partial progress but still classified as missing.
 */
public final class LibrarianEnchantments {
    public static final String LIBRARIAN_ID = "minecraft:librarian";

    public record CatalogEntry(String id, int maxLevel) {
        public CatalogEntry {
            Objects.requireNonNull(id);
            if (id.isBlank()) throw new IllegalArgumentException("id");
            if (maxLevel < 1) throw new IllegalArgumentException("maxLevel");
        }
    }

    public record EnchantmentStatus(
            String id,
            int maxLevel,
            int highestObservedLevel,
            Integer lowestMaxLevelEmeralds,
            int maxLevelSellerCount,
            UUID bestSellerUuid) {
        public EnchantmentStatus {
            Objects.requireNonNull(id);
            if (maxLevel < 1) throw new IllegalArgumentException("maxLevel");
            if (highestObservedLevel < 0) throw new IllegalArgumentException("highestObservedLevel");
            if (maxLevelSellerCount < 0) throw new IllegalArgumentException("maxLevelSellerCount");
        }

        public boolean owned() {
            return highestObservedLevel >= maxLevel;
        }

        public boolean missing() {
            return highestObservedLevel < maxLevel;
        }
    }

    public record Summary(
            List<EnchantmentStatus> all,
            List<EnchantmentStatus> missing,
            List<EnchantmentStatus> owned) {
        public Summary {
            all = List.copyOf(all);
            missing = List.copyOf(missing);
            owned = List.copyOf(owned);
        }
    }

    private LibrarianEnchantments() {}

    public static Summary analyze(
            Collection<CatalogEntry> catalog, Collection<VillagerRecord> records) {
        Objects.requireNonNull(catalog);
        Objects.requireNonNull(records);

        var deduplicated = new LinkedHashMap<String, CatalogEntry>();
        for (var entry : catalog) {
            if (entry == null) continue;
            deduplicated.merge(
                entry.id(),
                entry,
                (oldEntry, newEntry) ->
                    newEntry.maxLevel() > oldEntry.maxLevel() ? newEntry : oldEntry);
        }

        var librarians = records.stream()
            .filter(Objects::nonNull)
            .filter(VillagerRecord::tradable)
            .filter(v -> LIBRARIAN_ID.equals(v.profession()) && v.snapshot() != null)
            .sorted(Comparator.comparing(v -> v.uuid().toString()))
            .toList();

        var all = new ArrayList<EnchantmentStatus>(deduplicated.size());
        var missing = new ArrayList<EnchantmentStatus>();
        var owned = new ArrayList<EnchantmentStatus>();

        for (var entry : deduplicated.values()) {
            String enchId = entry.id();
            int maxLevel = entry.maxLevel();
            int highestObservedLevel = 0;
            Integer lowestMaxLevelEmeralds = null;
            var maxLevelSellers = new LinkedHashSet<UUID>();
            UUID bestMaxSeller = null;
            int bestMaxPrice = Integer.MAX_VALUE;
            UUID bestPartialSeller = null;
            int bestPartialLevel = 0;
            int bestPartialPrice = Integer.MAX_VALUE;

            for (var v : librarians) {
                for (var trade : ProfessionHighlights.trades(v)) {
                    Integer level = trade.output().enchantments().get(enchId);
                    if (level == null || level <= 0) continue;
                    int price = baseEmeraldPrice(trade);
                    if (level > highestObservedLevel) {
                        highestObservedLevel = level;
                    }
                    if (level >= maxLevel) {
                        maxLevelSellers.add(v.uuid());
                        if (lowestMaxLevelEmeralds == null || price < lowestMaxLevelEmeralds) {
                            lowestMaxLevelEmeralds = price;
                        }
                        if (bestMaxSeller == null || price < bestMaxPrice) {
                            bestMaxPrice = price;
                            bestMaxSeller = v.uuid();
                        }
                    } else if (level > bestPartialLevel
                            || (level == bestPartialLevel && price < bestPartialPrice)) {
                        bestPartialLevel = level;
                        bestPartialPrice = price;
                        bestPartialSeller = v.uuid();
                    }
                }
            }

            UUID bestSeller = bestMaxSeller != null ? bestMaxSeller : bestPartialSeller;
            var status = new EnchantmentStatus(
                enchId,
                maxLevel,
                highestObservedLevel,
                lowestMaxLevelEmeralds,
                maxLevelSellers.size(),
                bestSeller);
            all.add(status);
            if (status.owned()) {
                owned.add(status);
            } else {
                missing.add(status);
            }
        }

        return new Summary(all, missing, owned);
    }

    public static int baseEmeraldPrice(Trade trade) {
        int emeralds = trade.baseInput().item().equals("minecraft:emerald")
            ? trade.baseInput().count()
            : 0;
        if (trade.secondInput() != null && trade.secondInput().item().equals("minecraft:emerald")) {
            emeralds += trade.secondInput().count();
        }
        return emeralds;
    }
}
