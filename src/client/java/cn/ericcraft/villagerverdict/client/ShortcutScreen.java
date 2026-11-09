package cn.ericcraft.villagerverdict.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** In-mod editor for the Villager Verdict key mappings with plain action descriptions. */
public final class ShortcutScreen extends WorldScreen {
    private final Screen parent;
    private final List<VillagerverdictClient.Shortcut> shortcuts = VillagerverdictClient.shortcuts();
    private final ShortcutManager manager = VillagerverdictClient.shortcutManager();
    private int capturing = -1;
    private boolean editable;

    public ShortcutScreen(Screen parent) {
        super(Component.translatable("vv.shortcuts"));
        this.parent = parent;
    }

    public static void open(Screen parent) {
        net.minecraft.client.Minecraft.getInstance().gui.setScreen(new ShortcutScreen(parent));
    }

    private int panelX() {
        return Math.max(12, width / 2 - 205);
    }

    private int panelWidth() {
        return Math.min(410, width - 24);
    }

    @Override
    protected void init() {
        editable = manager.editable();
        int px = panelX();
        int pw = panelWidth();
        int innerX = px + 8;
        int innerW = pw - 16;
        int leftW = innerW * 58 / 100;
        int rightW = innerW - leftW - 6;

        int y = 56;
        for (int i = 0; i < shortcuts.size(); i++) {
            var shortcut = shortcuts.get(i);
            int index = i;
            String actionKey = "key.villagerverdict." + shortcut.id();
            var actionBtn = button(Component.translatable(actionKey),
                innerX, y, leftW, () -> capture(index));
            actionBtn.active = editable;
            actionBtn.setTooltip(Tooltip.create(Component.translatable(actionKey + ".desc")));

            Component keyLabel = capturing == index
                ? Component.translatable("vv.pressKey").withColor(0xFFFFCC55)
                : shortcut.mapping().isUnbound()
                    ? Component.translatable("vv.unbound").withColor(0xFF9FB8D8)
                    : shortcut.mapping().getTranslatedKeyMessage();
            var keyBtn = button(keyLabel, innerX + leftW + 6, y, rightW, () -> capture(index));
            keyBtn.active = editable;
            keyBtn.setTooltip(Tooltip.create(Component.translatable(actionKey + ".desc")));
            y += 26;
        }
        button(Component.translatable("vv.restoreDefaults"), px, height - 26, 130, () -> {
            VillagerverdictClient.resetShortcuts();
            capturing = -1;
            rebuildWidgets();
        }).active = editable;
        button(Component.translatable("gui.done"), px + pw - 74, height - 26, 74, this::onClose);
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(
            Button.builder(label, ignored -> action.run()).bounds(x, y, width, 20).build());
    }

    @Override
    public void tick() {
        if (editable != manager.editable()) rebuildWidgets();
    }

    private void capture(int index) {
        if (!manager.editable()) return;
        capturing = index;
        rebuildWidgets();
    }

    @Override
    public boolean isInputCaptured() {
        return capturing >= 0 || super.isInputCaptured();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (capturing < 0) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                onClose();
                return true;
            }
            return super.keyPressed(event);
        }
        InputConstants.Key key = event.key() == InputConstants.KEY_ESCAPE
            ? InputConstants.UNKNOWN
            : InputConstants.Type.KEYBOARD.getOrCreate(event.key());
        bindCapturedKey(key);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (capturing < 0) return super.mouseClicked(event, doubleClick);
        bindCapturedKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
        return true;
    }

    private void bindCapturedKey(InputConstants.Key key) {
        if (manager.editable()) manager.bind(shortcuts.get(capturing), key);
        capturing = -1;
        rebuildWidgets();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        int px = panelX();
        int pw = panelWidth();
        ClientDisplayText.drawPanel(graphics, px, 24, pw, Math.max(166, height - 56));

        int y = 29;
        for (var line : font.split(Component.translatable("vv.shortcutHelp"), pw - 16)) {
            if (y <= 44) {
                graphics.text(font, line, px + 8, y, 0xFF9FB8D8);
                y += 10;
            }
        }
        if (!editable) {
            graphics.text(
                font,
                Component.translatable(manager.ready() ? "vv.shortcutReadFailed" : "vv.loading"),
                px + 8,
                46,
                0xFFFFCC55);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
