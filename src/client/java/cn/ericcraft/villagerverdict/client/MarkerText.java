package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.analysis.ProfessionHighlights;
import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class MarkerText {
    private MarkerText() {}

    public static List<Component> lines(VillagerRecord record, AnalysisResult.Status status,
        boolean statusMarks, boolean summaries) {
        var lines = new ArrayList<Component>();
        if (!summaries) {
            if (statusMarks) lines.add(Component.literal(switch (status) {
                case KEEP -> "+";
                case REVIEW -> "?";
                case REDUNDANT -> "−";
            }));
            return List.copyOf(lines);
        }
        if (statusMarks) lines.add(Component.translatable("vv.status." + status.name().toLowerCase(Locale.ROOT)));
        lines.add(Component.translatable("vv.marker.level", record.level()));
        if (record.snapshot() != null) {
            var names = new LinkedHashSet<String>();
            for (var entry : ProfessionHighlights.select(record.profession(), record.snapshot().trades())) {
                Component text = switch (entry) {
                    case ProfessionHighlights.EnchantmentEntry enchantment -> enchantment(enchantment);
                    case ProfessionHighlights.ItemEntry product -> Component.literal(ItemCodecAdapter.displayName(product.item()));
                };
                if (names.add(text.getString())) lines.add(text);
            }
        }
        return List.copyOf(lines);
    }

    private static Component enchantment(ProfessionHighlights.EnchantmentEntry entry) {
        var value = Minecraft.getInstance().level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .getValue(Identifier.parse(entry.id()));
        Component name = value == null
            ? Component.translatable("enchantment." + entry.id().replace(':', '.')) : value.description().copy();
        if (entry.level() == 1 && value != null && value.getMaxLevel() == 1) return name;
        return Component.translatable("vv.marker.enchantment", name, entry.level());
    }
}
