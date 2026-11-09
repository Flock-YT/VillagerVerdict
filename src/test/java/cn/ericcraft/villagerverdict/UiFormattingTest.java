package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.model.UiFormatting;
import cn.ericcraft.villagerverdict.model.UiFormatting.RelativeUnit;
import cn.ericcraft.villagerverdict.model.VillagerRecord.Manual;
import org.junit.jupiter.api.Test;

class UiFormattingTest {
    @Test
    void relativeTimeCoversAllBucketsAndHandlesFutureTimestamps() {
        long now = 10_000_000L;
        assertEquals(new UiFormatting.RelativeTime(RelativeUnit.JUST_NOW, 0L),
            UiFormatting.relativeTime(now + 5_000L, now));
        assertEquals(new UiFormatting.RelativeTime(RelativeUnit.JUST_NOW, 0L),
            UiFormatting.relativeTime(now - 59_999L, now));
        assertEquals(new UiFormatting.RelativeTime(RelativeUnit.MINUTES, 1L),
            UiFormatting.relativeTime(now - 60_000L, now));
        assertEquals(new UiFormatting.RelativeTime(RelativeUnit.MINUTES, 59L),
            UiFormatting.relativeTime(now - 3_599_999L, now));
        assertEquals(new UiFormatting.RelativeTime(RelativeUnit.HOURS, 1L),
            UiFormatting.relativeTime(now - 3_600_000L, now));
        assertEquals(new UiFormatting.RelativeTime(RelativeUnit.HOURS, 23L),
            UiFormatting.relativeTime(now - 86_399_999L, now));
        assertEquals(new UiFormatting.RelativeTime(RelativeUnit.DAYS, 2L),
            UiFormatting.relativeTime(now - 2 * 86_400_000L, now));
    }

    @Test
    void unitConversionsValidateBoundsAndConvertAccurately() {
        assertEquals(1.0, UiFormatting.ticksToSeconds(20));
        assertEquals(20, UiFormatting.secondsToTicks(1.0));
        assertEquals(1, UiFormatting.secondsToTicks(0.01));
        assertThrows(IllegalArgumentException.class, () -> UiFormatting.secondsToTicks(0));
        assertThrows(IllegalArgumentException.class, () -> UiFormatting.secondsToTicks(Double.NaN));

        assertEquals(2.5, UiFormatting.millisToSeconds(2500L));
        assertEquals(3000L, UiFormatting.secondsToTimeoutMillis(3.0));
        assertThrows(IllegalArgumentException.class, () -> UiFormatting.secondsToTimeoutMillis(0.5));
    }

    @Test
    void decimalFormattingAndRangesAndColorsWorkAsExpected() {
        assertEquals("0", UiFormatting.formatDecimal(0.0));
        assertEquals("16", UiFormatting.formatDecimal(16.001));
        assertEquals("1.5", UiFormatting.formatDecimal(1.5));
        assertEquals("0.25", UiFormatting.formatDecimal(0.25));

        assertArrayEquals(new double[] {-32, 64}, UiFormatting.normalizedRange(64, -32));
        assertArrayEquals(new double[] {10, 20}, UiFormatting.normalizedRange(10, 20));
        assertThrows(IllegalArgumentException.class, () -> UiFormatting.normalizedRange(Double.POSITIVE_INFINITY, 0));

        assertEquals(0xFF55DD88, UiFormatting.parseColorHex("#55DD88"));
        assertEquals(0xFF55DD88, UiFormatting.parseColorHex("0055dd88"));
        assertEquals("#55DD88", UiFormatting.formatColorHex(0xFF55DD88));
        assertEquals("#55DD88", UiFormatting.formatColorHex(0x0055DD88));
        assertThrows(IllegalArgumentException.class, () -> UiFormatting.parseColorHex("#123"));
        assertEquals(9, UiFormatting.COLOR_PRESETS.size());
    }

    @Test
    void manualStatesAndGapStrings() {
        assertEquals(Manual.AUTO, UiFormatting.normalizeManualForUi(null));
        assertEquals(Manual.AUTO, UiFormatting.normalizeManualForUi(Manual.AUTO));
        assertEquals(Manual.KEEP, UiFormatting.normalizeManualForUi(Manual.KEEP));

        assertEquals(
            new UiFormatting.ParsedGap("minecraft:enchanted_book", "minecraft:mending", "0/1", false),
            UiFormatting.parseGap("minecraft:enchanted_book minecraft:mending: 0/1"));
        assertEquals(
            new UiFormatting.ParsedGap("minecraft:book", "", "0/1", false),
            UiFormatting.parseGap("minecraft:book : 0/1"));
        assertEquals(
            new UiFormatting.ParsedGap("minecraft:enchanted_book", "minecraft:mending", "price condition unmet", true),
            UiFormatting.parseGap("minecraft:enchanted_book minecraft:mending: price condition unmet"));
        assertEquals(
            new UiFormatting.ParsedGap("minecraft:enchanted_book", "minecraft:mending", "1/2", false),
            UiFormatting.parseGap("minecraft:enchanted_book{minecraft:mending=1} / minecraft:emerald: 1/2"));
        assertEquals(
            new UiFormatting.ParsedGap("minecraft:book", "", "0/1", false),
            UiFormatting.parseGap("best price: minecraft:book{}: 0/1"));
        assertNull(UiFormatting.parseGap("unexpected text"));
    }
}
