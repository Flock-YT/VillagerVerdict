package cn.ericcraft.villagerverdict.model;

import java.util.Locale;
import java.util.function.BiPredicate;

/** Case-insensitive text search with an optional external matching implementation. */
public final class SearchText {
    private SearchText() {}

    public static boolean contains(String text, String query, BiPredicate<String, String> matcher) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        return needle.isEmpty() || matcher.test(text.toLowerCase(Locale.ROOT), needle);
    }
}
