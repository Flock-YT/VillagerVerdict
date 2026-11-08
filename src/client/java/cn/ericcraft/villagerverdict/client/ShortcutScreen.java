package cn.ericcraft.villagerverdict.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** In-mod editor for the Villager Verdict key mappings. */
public final class ShortcutScreen extends Screen {
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

    protected void init() {
        editable = manager.editable();
        int y = 60;
        for (int i = 0; i < shortcuts.size(); i++) {
            var shortcut = shortcuts.get(i);
            int index = i;
            button(Component.translatable("key.villagerverdict." + shortcut.id()),
                12, y, width / 2 - 20, () -> capture(index)).active = editable;
            button(capturing == index ? Component.translatable("vv.pressKey")
                : shortcut.mapping().getTranslatedKeyMessage(),
                width / 2, y, width / 2 - 12, () -> capture(index)).active = editable;
            y += 28;
        }
        button(Component.translatable("vv.restoreDefaults"), 12, height - 26, 140, () -> {
            VillagerverdictClient.resetShortcuts();
            capturing = -1;
            rebuildWidgets();
        }).active = editable;
        button(Component.translatable("gui.done"), width - 74, height - 26, 62, this::onClose);
    }

    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(label, ignored -> action.run()).bounds(x, y, width, 22).build());
    }

    public void tick() {
        if (editable != manager.editable()) rebuildWidgets();
    }

    private void capture(int index) {
        if (!manager.editable()) return;
        capturing = index;
        rebuildWidgets();
    }

    public boolean isInputCaptured() {
        return capturing >= 0 || super.isInputCaptured();
    }

    public boolean keyPressed(KeyEvent event) {
        if (capturing < 0) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                onClose();
                return true;
            }
            return super.keyPressed(event);
        }
        if (!manager.editable()) {
            capturing = -1;
            return true;
        }
        var shortcut = shortcuts.get(capturing);
        InputConstants.Key key = event.key() == InputConstants.KEY_ESCAPE
            ? InputConstants.UNKNOWN : InputConstants.Type.KEYBOARD.getOrCreate(event.key());
        manager.bind(shortcut, key);
        capturing = -1;
        rebuildWidgets();
        return true;
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xF0181E2B);
        graphics.centeredText(font, title, width / 2, 12, 0xFFFFFFFF);
        int y = 28;
        for (var line : font.split(Component.translatable("vv.shortcutHelp"), width - 24)) {
            graphics.text(font, line, 12, y, 0xFFBBD0E8);
            y += 10;
        }
        if (!editable)
            graphics.text(font, Component.translatable(manager.ready() ? "vv.shortcutReadFailed" : "vv.loading"),
                12, 48, 0xFFFFCC55);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
