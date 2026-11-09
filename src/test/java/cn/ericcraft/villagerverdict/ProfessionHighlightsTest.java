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
}
