package cn.ericcraft.villagerverdict.model;

import java.util.List;
import java.util.Locale;

/** Environment-independent formatting, unit conversion, and validation helpers for management UIs. */
public final class UiFormatting {
    public enum RelativeUnit {
        JUST_NOW,
        MINUTES,
        HOURS,
        DAYS
    }

    public record RelativeTime(RelativeUnit unit, long value) {}

    public record ColorPreset(String key, int argb) {}

    public record ParsedGap(String itemId, String enchantmentId, String countPart, boolean priceUnmet) {}

    public static final List<ColorPreset> COLOR_PRESETS = List.of(
        new ColorPreset("vv.color.emerald", 0xFF55DD88),
        new ColorPreset("vv.color.teal", 0xFF44CCBB),
        new ColorPreset("vv.color.sky", 0xFF55AAFF),
        new ColorPreset("vv.color.periwinkle", 0xFF99AAFF),
        new ColorPreset("vv.color.gold", 0xFFFFCC55),
        new ColorPreset("vv.color.amber", 0xFFFF9944),
        new ColorPreset("vv.color.coral", 0xFFFF6677),
        new ColorPreset("vv.color.rose", 0xFFEE66AA),
        new ColorPreset("vv.color.lavender", 0xFFAA88FF)
    );

    private UiFormatting() {}

    public static RelativeTime relativeTime(long timestampMillis, long nowMillis) {
        long diff = Math.max(0L, nowMillis - timestampMillis);
        if (diff < 60_000L) return new RelativeTime(RelativeUnit.JUST_NOW, 0L);
        if (diff < 3_600_000L) return new RelativeTime(RelativeUnit.MINUTES, diff / 60_000L);
        if (diff < 86_400_000L) return new RelativeTime(RelativeUnit.HOURS, diff / 3_600_000L);
        return new RelativeTime(RelativeUnit.DAYS, diff / 86_400_000L);
    }

    public static double ticksToSeconds(int ticks) {
        return Math.max(1, ticks) / 20.0;
    }

    public static int secondsToTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0)
            throw new IllegalArgumentException("seconds must be positive");
        return Math.max(1, (int) Math.round(seconds * 20.0));
    }

    public static double millisToSeconds(long millis) {
        return Math.max(0L, millis) / 1000.0;
    }

    public static long secondsToTimeoutMillis(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 1.0)
            throw new IllegalArgumentException("timeout seconds must be at least 1");
        return Math.max(1000L, Math.round(seconds * 1000.0));
    }

    public static String formatDecimal(double value) {
        long rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.005) return Long.toString(rounded);
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        while (formatted.contains(".") && (formatted.endsWith("0") || formatted.endsWith("."))) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        return formatted;
    }

    public static String formatColorHex(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0x00FFFFFF);
    }

    public static VillagerRecord.Manual normalizeManualForUi(VillagerRecord.Manual manual) {
        if (manual == null || manual == VillagerRecord.Manual.AUTO) return VillagerRecord.Manual.AUTO;
        return VillagerRecord.Manual.KEEP;
    }

    public static ParsedGap parseGap(String raw) {
        if (raw == null) return null;
        int colon = raw.lastIndexOf(':');
        if (colon <= 0) return null;
        String head = raw.substring(0, colon).trim();
        String tail = raw.substring(colon + 1).trim();
        boolean priceUnmet = tail.equals("price condition unmet");
        if (head.startsWith("best price:")) {
            head = head.substring("best price:".length()).trim();
        }
        int slash = head.indexOf(" / ");
        if (slash > 0) {
            head = head.substring(0, slash).trim();
        }
        int brace = head.indexOf('{');
        if (brace > 0) {
            String item = head.substring(0, brace).trim();
            int endBrace = head.indexOf('}', brace);
            String inside = endBrace > brace + 1 ? head.substring(brace + 1, endBrace).trim() : "";
            String ench = "";
            if (!inside.isEmpty()) {
                int eq = inside.indexOf('=');
                ench = eq > 0 ? inside.substring(0, eq).trim() : inside;
            }
            return item.contains(":") ? new ParsedGap(item, ench, tail, priceUnmet) : null;
        }
        int space = head.indexOf(' ');
        String item = space > 0 ? head.substring(0, space).trim() : head;
        String ench = space > 0 ? head.substring(space + 1).trim() : "";
        return item.contains(":") ? new ParsedGap(item, ench, tail, priceUnmet) : null;
    }
}
