package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.analysis.LibrarianEnchantments;
import cn.ericcraft.villagerverdict.model.DisplayStatus;
import cn.ericcraft.villagerverdict.model.ItemData;
import cn.ericcraft.villagerverdict.model.TargetTrade;
import cn.ericcraft.villagerverdict.model.Trade;
import cn.ericcraft.villagerverdict.model.UiFormatting;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/** Localized human-friendly presentation helpers for management screens. */
public final class ClientDisplayText {
    private static final DateTimeFormatter LOCAL_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    private ClientDisplayText() {}

    public static void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, 0xA81D2534);
        g.outline(x, y, w, h, 0xFF425472);
    }

    public static ItemStack itemStack(String itemId) {
        if (itemId == null || itemId.isBlank()) return ItemStack.EMPTY;
        Identifier id = Identifier.tryParse(itemId.trim());
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(id)
            .filter(item -> item != Items.AIR)
            .map(Item::getDefaultInstance)
            .orElse(ItemStack.EMPTY);
    }

    public static String itemDisplayName(String itemId) {
        ItemStack stack = itemStack(itemId);
        if (!stack.isEmpty()) return stack.getHoverName().getString();
        if (itemId == null || itemId.isBlank()) return "";
        return itemId;
    }

    public static String enchantmentDisplayName(String enchantmentId) {
        if (enchantmentId == null || enchantmentId.isBlank()) return "";
        String normalized = enchantmentId.trim();
        var mc = Minecraft.getInstance();
        Identifier id = Identifier.tryParse(normalized);
        if (mc.level != null && id != null) {
            var value = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getValue(id);
            if (value != null) return value.description().getString();
        }
        return Component.translatable("enchantment." + normalized.replace(':', '.')).getString();
    }

    public static int enchantmentMaxLevel(String enchantmentId) {
        if (enchantmentId == null || enchantmentId.isBlank()) return 5;
        var mc = Minecraft.getInstance();
        Identifier id = Identifier.tryParse(enchantmentId.trim());
        if (mc.level != null && id != null) {
            var value = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getValue(id);
            if (value != null) return Math.max(1, value.getMaxLevel());
        }
        return 5;
    }

    public static String enchantmentFullName(String enchantmentId, int level) {
        if (enchantmentId == null || enchantmentId.isBlank()) return "";
        String normalized = enchantmentId.trim();
        var mc = Minecraft.getInstance();
        Identifier id = Identifier.tryParse(normalized);
        if (mc.level != null && id != null) {
            var holder = mc.level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .get(ResourceKey.create(Registries.ENCHANTMENT, id));
            if (holder.isPresent()) {
                return Enchantment.getFullname(holder.get(), level).getString();
            }
        }
        String base = enchantmentDisplayName(normalized);
        int max = enchantmentMaxLevel(normalized);
        if (level == 1 && max == 1) return base;
        return base + " " + Component.translatable("enchantment.level." + level).getString();
    }

    public static List<LibrarianEnchantments.CatalogEntry> tradeableEnchantments() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        return mc.level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .listElements()
            .filter(holder -> holder.is(EnchantmentTags.TRADEABLE))
            .map(holder -> new LibrarianEnchantments.CatalogEntry(
                holder.getRegisteredName(),
                Math.max(1, holder.value().getMaxLevel())))
            .sorted(Comparator
                .comparing((LibrarianEnchantments.CatalogEntry e) -> enchantmentDisplayName(e.id()))
                .thenComparing(LibrarianEnchantments.CatalogEntry::id))
            .toList();
    }

    public static String dimensionDisplayName(String dimensionId) {
        if (dimensionId == null || dimensionId.isBlank()) return "";
        return switch (dimensionId) {
            case "minecraft:overworld" -> Component.translatable("vv.dim.overworld").getString();
            case "minecraft:the_nether" -> Component.translatable("vv.dim.nether").getString();
            case "minecraft:the_end" -> Component.translatable("vv.dim.end").getString();
            default -> dimensionId;
        };
    }

    public static String relativeTimeText(long epochMillis) {
        var rel = UiFormatting.relativeTime(epochMillis, System.currentTimeMillis());
        return switch (rel.unit()) {
            case JUST_NOW -> Component.translatable("vv.time.justNow").getString();
            case MINUTES -> Component.translatable("vv.time.minutes", rel.value()).getString();
            case HOURS -> Component.translatable("vv.time.hours", rel.value()).getString();
            case DAYS -> Component.translatable("vv.time.days", rel.value()).getString();
        };
    }

    public static String exactLocalTimeText(long epochMillis) {
        return LOCAL_TIME.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String uiStatusKey(DisplayStatus status) {
        return "vv.ui.status." + status.name().toLowerCase(Locale.ROOT);
    }

    /** Localized, player-facing name of a villager status (Keep / Discard / Unscanned). */
    public static String statusLabel(DisplayStatus status) {
        return Component.translatable(uiStatusKey(status)).getString();
    }

    public static String productSummary(ItemData item) {
        if (item.enchantments().isEmpty()) return ItemCodecAdapter.displayName(item);
        String enchantments = ItemCodecAdapter.enchantmentText(item);
        return item.item().equals("minecraft:enchanted_book") ? enchantments
            : ItemCodecAdapter.displayName(item) + " (" + enchantments + ")";
    }

    /** Base price per offer, independent of discounts, demand and remaining stock. */
    public static String signatureTradeSummary(Trade trade) {
        String product = productSummary(trade.output());
        if (trade.output().count() > 1) product += " ×" + trade.output().count();
        int emeralds = trade.baseInput().item().equals("minecraft:emerald") ? trade.baseInput().count() : 0;
        if (trade.secondInput() != null && trade.secondInput().item().equals("minecraft:emerald"))
            emeralds += trade.secondInput().count();
        return Component.translatable("vv.trade.signature", product, emeralds).getString();
    }

    public static String targetSummary(TargetTrade t) {
        String itemName = itemDisplayName(t.item());
        String priceText = t.maxUnitPrice() == 0
            ? Component.translatable("vv.target.anyPrice").getString()
            : Component.translatable("vv.target.maxPriceShort", UiFormatting.formatDecimal(t.maxUnitPrice())).getString();
        if (t.enchantment() == null || t.enchantment().isBlank()) {
            return Component.translatable("vv.target.rowPlain", itemName, priceText).getString();
        }
        String enchName = enchantmentDisplayName(t.enchantment());
        return Component.translatable("vv.target.rowEnchanted",
            itemName, enchName, t.minLevel(), priceText).getString();
    }

    public static String formatGap(String rawGap) {
        var parsed = UiFormatting.parseGap(rawGap);
        if (parsed == null) return rawGap;
        String item = itemDisplayName(parsed.itemId());
        String subject = parsed.enchantmentId().isBlank()
            ? item
            : Component.translatable("vv.gap.subjectEnchanted",
                item, enchantmentDisplayName(parsed.enchantmentId())).getString();
        if (parsed.priceUnmet()) {
            return Component.translatable("vv.gap.priceUnmet", subject).getString();
        }
        return Component.translatable("vv.gap.count", subject, parsed.countPart()).getString();
    }
}
