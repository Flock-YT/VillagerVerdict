package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.analysis.ProfessionHighlights;
import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProfessionHighlightsTest {
    private static ItemData item(String id, Map<String, Integer> enchantments) {
        return new ItemData("minecraft:" + id, 1, "{}", "{}", enchantments, id);
    }

    private static Trade selling(ItemData output) {
        var emerald = item("emerald", Map.of());
        return new Trade(emerald, emerald, null, output, 0, 12);
    }

    @ParameterizedTest
    @CsvSource({
        "armorer,diamond_helmet", "armorer,diamond_chestplate", "armorer,diamond_leggings", "armorer,diamond_boots",
        "weaponsmith,diamond_sword", "weaponsmith,diamond_axe",
        "toolsmith,diamond_pickaxe", "toolsmith,diamond_axe", "toolsmith,diamond_shovel", "toolsmith,diamond_hoe",
        "fletcher,tipped_arrow", "farmer,golden_carrot", "farmer,glistering_melon_slice",
        "cleric,ender_pearl", "cleric,experience_bottle", "cartographer,filled_map",
        "leatherworker,saddle", "leatherworker,leather_horse_armor",
        "mason,quartz_block", "mason,quartz_pillar", "mason,blue_glazed_terracotta",
        "shepherd,blue_banner", "shepherd,painting", "butcher,cooked_chicken", "butcher,cooked_porkchop"
    })
    void selectsSignatureGoods(String profession, String id) {
        var product = item(id, Map.of());
        assertEquals(List.of(new ProfessionHighlights.ItemEntry(product)),
            ProfessionHighlights.select("minecraft:" + profession, List.of(selling(product))));
    }

    @ParameterizedTest
    @CsvSource({"fletcher,bow", "fletcher,crossbow", "fisherman,fishing_rod"})
    void rangedGearRequiresEnchantments(String profession, String id) {
        assertTrue(ProfessionHighlights.select("minecraft:" + profession,
            List.of(selling(item(id, Map.of())))).isEmpty());
        var enchanted = item(id, Map.of("minecraft:unbreaking", 3));
        assertEquals(List.of(new ProfessionHighlights.ItemEntry(enchanted)),
            ProfessionHighlights.select("minecraft:" + profession, List.of(selling(enchanted))));
    }

    @ParameterizedTest
    @CsvSource({
        "librarian,bookshelf", "librarian,red_candle", "armorer,iron_leggings", "weaponsmith,iron_sword",
        "toolsmith,stone_pickaxe", "fletcher,arrow", "fisherman,cooked_cod", "farmer,bread",
        "cleric,redstone", "cartographer,map", "leatherworker,leather_helmet",
        "mason,blue_terracotta", "shepherd,blue_wool", "butcher,rabbit_stew"
    })
    void excludesLowerTierAndUnrelatedGoods(String profession, String id) {
        assertTrue(ProfessionHighlights.select("minecraft:" + profession,
            List.of(selling(item(id, Map.of())))).isEmpty());
    }

    @Test
    void mergesEnchantmentsAtHighestKnownLevelWithoutChangingFirstAppearanceOrder() {
        var trades = List.of(
            selling(item("enchanted_book", Map.of("minecraft:mending", 1))),
            selling(item("enchanted_book", Map.of("minecraft:protection", 3))),
            selling(item("enchanted_book", Map.of("minecraft:unbreaking", 3))),
            selling(item("enchanted_book", Map.of("minecraft:protection", 4))),
            selling(item("enchanted_book", Map.of("minecraft:protection", 2))));
        assertEquals(List.of(new ProfessionHighlights.EnchantmentEntry("minecraft:mending", 1),
            new ProfessionHighlights.EnchantmentEntry("minecraft:protection", 4),
            new ProfessionHighlights.EnchantmentEntry("minecraft:unbreaking", 3)),
            ProfessionHighlights.select("minecraft:librarian", trades));
    }

    @Test
    void expandsMultiEnchantmentBooksInDeterministicOrder() {
        assertEquals(List.of(new ProfessionHighlights.EnchantmentEntry("minecraft:mending", 1),
            new ProfessionHighlights.EnchantmentEntry("minecraft:protection", 4)),
            ProfessionHighlights.select("minecraft:librarian", List.of(selling(item("enchanted_book",
                Map.of("minecraft:protection", 4, "minecraft:mending", 1))))));
    }

    @Test
    void ignoresSignatureItemsInTradeInputs() {
        var diamond = item("diamond_leggings", Map.of());
        var emerald = item("emerald", Map.of());
        assertTrue(ProfessionHighlights.select("minecraft:armorer",
            List.of(new Trade(diamond, diamond, null, emerald, 0, 12))).isEmpty());
    }

    @Test
    void mergesEquipmentWithDifferentEnchantmentsAndPreservesOutputOrder() {
        var legs = item("diamond_leggings", Map.of("minecraft:protection", 3));
        var boots = item("diamond_boots", Map.of());
        var betterLegs = item("diamond_leggings", Map.of("minecraft:protection", 4));
        var selected = ProfessionHighlights.select("minecraft:armorer",
            List.of(selling(legs), selling(boots), selling(betterLegs), selling(boots)));
        assertEquals(List.of(new ProfessionHighlights.ItemEntry(legs), new ProfessionHighlights.ItemEntry(boots)), selected);
        assertThrows(UnsupportedOperationException.class, () -> selected.clear());
    }

    @Test
    void keepsDifferentMapDestinationsAndPotionEffects() {
        var first = new ItemData("minecraft:filled_map", 1, "{}", "monument", Map.of(), "Ocean Explorer Map");
        var second = new ItemData("minecraft:filled_map", 1, "{}", "mansion", Map.of(), "Woodland Explorer Map");
        assertEquals(2, ProfessionHighlights.select("minecraft:cartographer", List.of(selling(first), selling(second))).size());
        var arrow = new ItemData("minecraft:tipped_arrow", 1, "{}", "poison", Map.of(), "Arrow of Poison");
        var otherArrow = new ItemData("minecraft:tipped_arrow", 1, "{}", "slowness", Map.of(), "Arrow of Slowness");
        assertEquals(2, ProfessionHighlights.select("minecraft:fletcher", List.of(selling(arrow), selling(otherArrow))).size());
    }

    @Test
    void unknownProfessionsAndEmptyTradesHaveNoHighlights() {
        assertTrue(ProfessionHighlights.select("custom:armorer", List.of(selling(item("diamond_leggings", Map.of())))).isEmpty());
        assertTrue(ProfessionHighlights.select("minecraft:librarian", List.of()).isEmpty());
    }
}
