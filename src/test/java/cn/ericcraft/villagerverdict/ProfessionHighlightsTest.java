package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.analysis.ProfessionHighlights;
import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProfessionHighlightsTest {
    private static ItemData item(String id, Map<String, Integer> enchantments) {
        return new ItemData("minecraft:" + id, 1, "{}", "{}", enchantments, id);
    }

    private static Trade selling(ItemData output) {
        var emerald = item("emerald", Map.of());
        return new Trade(emerald, emerald, null, output, 0, 12);
    }

    @Test
    void selectsRepresentativeSignatureGoods() {
        for (var entry : List.of(
                Map.entry("minecraft:armorer", "diamond_helmet"),
                Map.entry("minecraft:farmer", "golden_carrot"),
                Map.entry("minecraft:cleric", "ender_pearl"))) {
            var product = item(entry.getValue(), Map.of());
            assertEquals(List.of(new ProfessionHighlights.ItemEntry(product)),
                    ProfessionHighlights.select(entry.getKey(), List.of(selling(product))));
        }
    }

    @Test
    void rangedGearRequiresEnchantments() {
        var bow = item("bow", Map.of());
        assertTrue(ProfessionHighlights.select("minecraft:fletcher", List.of(selling(bow))).isEmpty());
        var enchanted = item("bow", Map.of("minecraft:unbreaking", 3));
        assertEquals(List.of(new ProfessionHighlights.ItemEntry(enchanted)),
                ProfessionHighlights.select("minecraft:fletcher", List.of(selling(enchanted))));
    }

    @Test
    void excludesLowerTierAndUnrelatedGoods() {
        assertTrue(ProfessionHighlights.select("minecraft:librarian",
                List.of(selling(item("bookshelf", Map.of())))).isEmpty());
        assertTrue(ProfessionHighlights.select("minecraft:armorer",
                List.of(selling(item("iron_leggings", Map.of())))).isEmpty());
    }

    @Test
    void mergesEnchantmentsAtHighestKnownLevelInFirstAppearanceOrder() {
        var trades = List.of(
                selling(item("enchanted_book", Map.of("minecraft:mending", 1))),
                selling(item("enchanted_book", Map.of("minecraft:protection", 3))),
                selling(item("enchanted_book", Map.of("minecraft:unbreaking", 3))),
                selling(item("enchanted_book", Map.of("minecraft:protection", 4))));
        assertEquals(List.of(new ProfessionHighlights.EnchantmentEntry("minecraft:mending", 1),
                        new ProfessionHighlights.EnchantmentEntry("minecraft:protection", 4),
                        new ProfessionHighlights.EnchantmentEntry("minecraft:unbreaking", 3)),
                ProfessionHighlights.select("minecraft:librarian", trades));
    }

    @Test
    void unknownProfessionsAndEmptyTradesHaveNoHighlights() {
        assertTrue(ProfessionHighlights.select("custom:armorer",
                List.of(selling(item("diamond_leggings", Map.of())))).isEmpty());
        assertTrue(ProfessionHighlights.select("minecraft:librarian", List.of()).isEmpty());
    }
    @Test
    void detailTradesPreserveEveryLevelPriceAndFullSnapshot() {
        var protection3 = selling(item("enchanted_book", Map.of("minecraft:protection", 3)));
        var protection4 = selling(item("enchanted_book", Map.of("minecraft:protection", 4)));
        var emeralds = new ItemData("minecraft:emerald", 16, "{}", "{}", Map.of(), "emerald");
        var discounted4 = new Trade(emeralds, item("emerald", Map.of()), item("book", Map.of()),
                protection4.output(), 12, 12);
        var ordinary = selling(item("bookshelf", Map.of()));
        var snapshot = List.of(ordinary, protection3, protection4, discounted4,
                selling(item("wheat_seeds", Map.of())));
        var record = AnalysisTest.v(1).withSnapshot(new TradeSnapshot(snapshot, 1));

        assertEquals(List.of(protection3, protection4, discounted4), ProfessionHighlights.trades(record));
        assertEquals(16, ProfessionHighlights.trades(record).getLast().baseInput().count());
        assertEquals(snapshot, record.snapshot().trades());
        assertTrue(new TargetTrade("query", "minecraft:enchanted_book", "minecraft:protection", 3, 0).matches(record));
    }

    @Test
    void detailEquipmentUsesOnlyTheProfessionsDiamondGoodsAndKeepsEnchantments() {
        var ench = Map.of("minecraft:protection", 4, "minecraft:unbreaking", 3);
        var helmet = selling(item("diamond_helmet", ench));
        var sword = selling(item("diamond_sword", Map.of("minecraft:sharpness", 5)));
        var axe = selling(item("diamond_axe", Map.of()));
        var offers = List.of(helmet, sword, axe, selling(item("iron_helmet", ench)),
                selling(item("iron_sword", Map.of())), selling(item("wheat_seeds", Map.of())));
        assertEquals(List.of(helmet), ProfessionHighlights.trades("minecraft:armorer", offers));
        assertEquals(ench, ProfessionHighlights.trades("minecraft:armorer", offers).getFirst().output().enchantments());
        assertEquals(List.of(sword, axe), ProfessionHighlights.trades("minecraft:weaponsmith", offers));
        assertTrue(ProfessionHighlights.trades("custom:armorer", offers).isEmpty());
        assertTrue(ProfessionHighlights.trades("minecraft:librarian", offers).isEmpty());
    }

}
