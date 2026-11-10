package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.analysis.LibrarianEnchantments;
import cn.ericcraft.villagerverdict.analysis.LibrarianEnchantments.CatalogEntry;
import cn.ericcraft.villagerverdict.analysis.LibrarianEnchantments.EnchantmentStatus;
import cn.ericcraft.villagerverdict.model.ItemData;
import cn.ericcraft.villagerverdict.model.Trade;
import cn.ericcraft.villagerverdict.model.TradeSnapshot;
import cn.ericcraft.villagerverdict.model.VillagerRecord;
import cn.ericcraft.villagerverdict.model.WorldIdentity;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LibrarianEnchantmentsTest {
    private static final WorldIdentity WORLD =
        new WorldIdentity("test", "default", "minecraft:overworld");

    private static ItemData item(String id, int count, Map<String, Integer> enchantments) {
        return new ItemData("minecraft:" + id, count, "{}", "{}", enchantments, id);
    }

    private static Trade bookTrade(int baseEmeralds, int actualEmeralds, String enchantment, int level) {
        return new Trade(
            item("emerald", baseEmeralds, Map.of()),
            item("emerald", actualEmeralds, Map.of()),
            item("book", 1, Map.of()),
            item("enchanted_book", 1, Map.of(enchantment, level)),
            0,
            12);
    }

    private static VillagerRecord librarian(int id, Trade... trades) {
        return new VillagerRecord(
            WORLD,
            new UUID(0, id),
            "minecraft:librarian",
            5,
            false,
            id,
            64,
            0,
            100,
            new TradeSnapshot(List.of(trades), 100),
            VillagerRecord.Manual.AUTO,
            "",
            Set.of());
    }

    @Test
    void requiresMaxLevelToCountAsOwnedAndMarksLowerLevelsMissing() {
        var catalog = List.of(
            new CatalogEntry("minecraft:mending", 1),
            new CatalogEntry("minecraft:unbreaking", 3),
            new CatalogEntry("minecraft:protection", 4),
            new CatalogEntry("minecraft:binding_curse", 1));

        var v1 = librarian(
            1,
            bookTrade(10, 1, "minecraft:mending", 1),
            bookTrade(17, 5, "minecraft:protection", 3));
        var v2 = librarian(
            2,
            bookTrade(14, 14, "minecraft:unbreaking", 3));

        var summary = LibrarianEnchantments.analyze(catalog, List.of(v1, v2));

        assertEquals(4, summary.all().size());
        assertEquals(
            List.of("minecraft:mending", "minecraft:unbreaking"),
            summary.owned().stream().map(EnchantmentStatus::id).toList());
        assertEquals(
            List.of("minecraft:protection", "minecraft:binding_curse"),
            summary.missing().stream().map(EnchantmentStatus::id).toList());

        var protection = summary.missing().getFirst();
        assertEquals("minecraft:protection", protection.id());
        assertEquals(4, protection.maxLevel());
        assertEquals(3, protection.highestObservedLevel());
        assertNull(protection.lowestMaxLevelEmeralds());
        assertEquals(0, protection.maxLevelSellerCount());
        assertEquals(v1.uuid(), protection.bestSellerUuid());
        assertTrue(protection.missing());
        assertFalse(protection.owned());

        var curse = summary.missing().get(1);
        assertEquals("minecraft:binding_curse", curse.id());
        assertEquals(0, curse.highestObservedLevel());
        assertNull(curse.bestSellerUuid());
    }

    @Test
    void tracksLowestBasePriceAndUniqueSellerCountForMaxLevelBooks() {
        var catalog = List.of(new CatalogEntry("minecraft:protection", 4));

        var expensive = librarian(1, bookTrade(32, 1, "minecraft:protection", 4));
        var cheapWithTwoOffers = librarian(
            2,
            bookTrade(18, 18, "minecraft:protection", 4),
            bookTrade(24, 24, "minecraft:protection", 4));
        var lowerLevel = librarian(3, bookTrade(5, 1, "minecraft:protection", 3));

        var summary = LibrarianEnchantments.analyze(
            catalog, List.of(expensive, cheapWithTwoOffers, lowerLevel));

        assertEquals(1, summary.owned().size());
        assertTrue(summary.missing().isEmpty());

        var status = summary.owned().getFirst();
        assertTrue(status.owned());
        assertEquals(4, status.highestObservedLevel());
        assertEquals(18, status.lowestMaxLevelEmeralds());
        assertEquals(2, status.maxLevelSellerCount());
        assertEquals(cheapWithTwoOffers.uuid(), status.bestSellerUuid());
    }

    @Test
    void ignoresNonLibrarianEquipmentAndUnscannedVillagers() {
        var catalog = List.of(
            new CatalogEntry("minecraft:protection", 4),
            new CatalogEntry("minecraft:power", 5));

        var armorerWithMaxProtection = new VillagerRecord(
            WORLD,
            new UUID(0, 10),
            "minecraft:armorer",
            5,
            false,
            10,
            64,
            0,
            100,
            new TradeSnapshot(
                List.of(new Trade(
                    item("emerald", 20, Map.of()),
                    item("emerald", 20, Map.of()),
                    null,
                    item("diamond_chestplate", 1, Map.of("minecraft:protection", 4)),
                    0,
                    12)),
                100),
            VillagerRecord.Manual.AUTO,
            "",
            Set.of());

        var fletcherWithMaxPower = new VillagerRecord(
            WORLD,
            new UUID(0, 11),
            "minecraft:fletcher",
            5,
            false,
            11,
            64,
            0,
            100,
            new TradeSnapshot(
                List.of(new Trade(
                    item("emerald", 18, Map.of()),
                    item("emerald", 18, Map.of()),
                    null,
                    item("bow", 1, Map.of("minecraft:power", 5)),
                    0,
                    12)),
                100),
            VillagerRecord.Manual.AUTO,
            "",
            Set.of());

        var unscannedLibrarian = new VillagerRecord(
            WORLD,
            new UUID(0, 12),
            "minecraft:librarian",
            5,
            false,
            12,
            64,
            0,
            100,
            null,
            VillagerRecord.Manual.AUTO,
            "",
            Set.of());

        var summary = LibrarianEnchantments.analyze(
            catalog, List.of(armorerWithMaxProtection, fletcherWithMaxPower, unscannedLibrarian));

        assertTrue(summary.owned().isEmpty());
        assertEquals(2, summary.missing().size());
        assertTrue(summary.missing().stream().allMatch(s -> s.highestObservedLevel() == 0));
    }
}
