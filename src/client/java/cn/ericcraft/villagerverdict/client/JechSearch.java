package cn.ericcraft.villagerverdict.client;

import java.util.function.BiPredicate;
import me.towdium.jecharacters.config.JechConfig;
import me.towdium.jecharacters.utils.Match;

final class JechSearch {
    private static JechConfig.Spell keyboard;
    private static int flags = -1;

    private JechSearch() {}

    static BiPredicate<String, String> matcher() {
        return JechSearch::contains;
    }

    private static boolean contains(String text, String query) {
        int currentFlags = (JechConfig.enableFZh2z ? 1 : 0)
            | (JechConfig.enableFSh2s ? 2 : 0)
            | (JechConfig.enableFCh2c ? 4 : 0)
            | (JechConfig.enableFAng2an ? 8 : 0)
            | (JechConfig.enableFIng2in ? 16 : 0)
            | (JechConfig.enableFEng2en ? 32 : 0)
            | (JechConfig.enableFU2v ? 64 : 0);
        if (keyboard != JechConfig.enumKeyboard || flags != currentFlags) {
            // PinIn 1.6's accelerator invalidates on query changes, not config changes.
            // An empty search discards that thread's stale query cache through the public API.
            Match.contains("", "");
            keyboard = JechConfig.enumKeyboard;
            flags = currentFlags;
        }
        return Match.contains(text, query);
    }
}
