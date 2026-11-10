package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.SearchText;
import java.util.function.BiPredicate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/** The JECH adapter is only loaded when its optional mod is present. */
public final class ClientSearch {
    private static final boolean PINYIN = FabricLoader.getInstance().isModLoaded("jecharacters");
    private static final BiPredicate<String, String> MATCHER = PINYIN ? JechSearch.matcher() : String::contains;

    private ClientSearch() {}

    public static boolean contains(String text, String query) {
        return SearchText.contains(text, query, MATCHER);
    }

    public static Component hint(String key) {
        return Component.translatable(PINYIN ? "vv.search.withPinyin" : "vv.search.withoutPinyin",
            Component.translatable(key));
    }
}
