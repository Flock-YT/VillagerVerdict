package cn.ericcraft.villagerverdict;

import static org.junit.jupiter.api.Assertions.*;

import cn.ericcraft.villagerverdict.model.SearchText;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SearchTextTest {
    @Test
    void ordinarySearchPreservesChineseEnglishAndResourceIds() {
        assertTrue(SearchText.contains("经验修补 minecraft:MENDING", " 修补 ", String::contains));
        assertTrue(SearchText.contains("Diamond Sword", "SWORD", String::contains));
        assertTrue(SearchText.contains("minecraft:enchanted_book", "MINECRAFT:ENCHANTED", String::contains));
        assertFalse(SearchText.contains("经验修补", "jyxb", String::contains));
    }

    @Test
    void delegatesNormalizedTextWithoutDependingOnSystemLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertTrue(SearchText.contains("IRON 经验修补", " JYXB ", (text, query) -> {
                assertEquals("iron 经验修补", text);
                assertEquals("jyxb", query);
                return true;
            }));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void emptyQueriesSkipOptionalMatcherAndDecisionsAreNotCached() {
        var calls = new AtomicInteger();
        assertTrue(SearchText.contains("任意文本", "  ", (text, query) -> {
            fail("An empty query must not invoke an optional matcher");
            return false;
        }));
        assertFalse(SearchText.contains("文本", "wb", (text, query) -> calls.incrementAndGet() > 1));
        assertTrue(SearchText.contains("文本", "wb", (text, query) -> calls.incrementAndGet() > 1));
    }
}
