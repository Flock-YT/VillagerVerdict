package cn.ericcraft.villagerverdict.analysis;

import cn.ericcraft.villagerverdict.model.ItemData;
import cn.ericcraft.villagerverdict.model.Trade;
import java.util.*;

/** Known signature goods, independent of unlock level, prices and Minecraft rendering. */
public final class ProfessionHighlights {
    public sealed interface Entry permits EnchantmentEntry, ItemEntry {}

    public record EnchantmentEntry(String id, int level) implements Entry {}

    public record ItemEntry(ItemData item) implements Entry {}

    private ProfessionHighlights() {}

    public static List<Entry> select(String profession, List<Trade> trades) {
        var entries = new LinkedHashMap<String, Entry>();
        for (var trade : trades) {
            var output = trade.output();
            if (!signature(profession, output)) continue;
            if (profession.equals("minecraft:librarian")) {
                new TreeMap<>(output.enchantments()).forEach((id, level) -> {
                    var old = entries.get(id);
                    if (old == null || ((EnchantmentEntry) old).level() < level)
                        entries.put(id, new EnchantmentEntry(id, level));
                });
            } else {
                // Components retain map destinations, potion effects and custom names.
                entries.putIfAbsent(output.item() + "|" + output.valueComponents(), new ItemEntry(output));
            }
        }
        return List.copyOf(entries.values());
    }

    private static boolean signature(String profession, ItemData item) {
        String id = item.item();
        return switch (profession) {
            case "minecraft:librarian" -> id.equals("minecraft:enchanted_book");
            case "minecraft:armorer" -> Set.of("minecraft:diamond_helmet", "minecraft:diamond_chestplate",
                "minecraft:diamond_leggings", "minecraft:diamond_boots").contains(id);
            case "minecraft:weaponsmith" -> Set.of("minecraft:diamond_sword", "minecraft:diamond_axe").contains(id);
            case "minecraft:toolsmith" -> Set.of("minecraft:diamond_pickaxe", "minecraft:diamond_axe",
                "minecraft:diamond_shovel", "minecraft:diamond_hoe").contains(id);
            case "minecraft:fletcher" -> id.equals("minecraft:tipped_arrow")
                || Set.of("minecraft:bow", "minecraft:crossbow").contains(id) && !item.enchantments().isEmpty();
            case "minecraft:fisherman" -> id.equals("minecraft:fishing_rod") && !item.enchantments().isEmpty();
            case "minecraft:farmer" -> Set.of("minecraft:golden_carrot", "minecraft:glistering_melon_slice").contains(id);
            case "minecraft:cleric" -> Set.of("minecraft:ender_pearl", "minecraft:experience_bottle").contains(id);
            case "minecraft:cartographer" -> id.equals("minecraft:filled_map");
            case "minecraft:leatherworker" -> Set.of("minecraft:saddle", "minecraft:leather_horse_armor").contains(id);
            case "minecraft:mason" -> Set.of("minecraft:quartz_block", "minecraft:quartz_pillar").contains(id)
                || id.startsWith("minecraft:") && id.endsWith("_glazed_terracotta");
            case "minecraft:shepherd" -> id.equals("minecraft:painting")
                || id.startsWith("minecraft:") && id.endsWith("_banner");
            case "minecraft:butcher" -> Set.of("minecraft:cooked_chicken", "minecraft:cooked_porkchop").contains(id);
            default -> false;
        };
    }
}
