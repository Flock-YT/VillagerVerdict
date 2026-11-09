package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.VillagerRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

/** Two-column profession filter; edits parent settings draft until saved. */
public final class ProfessionScreen extends WorldScreen {
    private final Screen parent;
    private final Set<String> excluded;
    private final Consumer<Set<String>> save;
    private final List<String> professions;
    private int offset;

    public ProfessionScreen(Screen parent, Set<String> excluded, Consumer<Set<String>> save) {
        super(Component.translatable("vv.professions"));
        this.parent = parent;
        this.excluded = new HashSet<>(excluded);
        this.save = save;
        var level = Minecraft.getInstance().level;
        professions = level == null
            ? List.of()
            : level.registryAccess()
                .lookupOrThrow(Registries.VILLAGER_PROFESSION).listElements()
                .map(holder -> holder.getRegisteredName())
                .filter(id -> VillagerRecord.tradable(false, id))
                .sorted()
                .toList();
    }

    private int panelX() {
        return Math.max(12, width / 2 - 205);
    }

    private int panelWidth() {
        return Math.min(410, width - 24);
    }

    private int columns() {
        return panelWidth() >= 340 ? 2 : 1;
    }

    private int rowsPerPage() {
        return Math.max(1, (height - 114) / 24);
    }

    @Override
    protected void init() {
        int px = panelX();
        int pw = panelWidth();
        int innerX = px + 8;
        int innerW = pw - 16;
        int half = (innerW - 6) / 2;

        button("vv.selectAll", innerX, 50, half, () -> {
            excluded.clear();
            rebuildWidgets();
        });
        button("vv.selectNone", innerX + half + 6, 50, innerW - half - 6, () -> {
            excluded.addAll(professions);
            rebuildWidgets();
        });

        int cols = columns();
        int rows = rowsPerPage();
        int totalRows = (professions.size() + cols - 1) / cols;
        offset = Math.clamp(offset, 0, Math.max(0, totalRows - rows));

        int colW = cols == 1 ? innerW : (innerW - 6) / 2;
        int startIndex = offset * cols;
        int endIndex = Math.min(professions.size(), startIndex + rows * cols);
        for (int i = startIndex; i < endIndex; i++) {
            int local = i - startIndex;
            int col = local % cols;
            int row = local / cols;
            String id = professions.get(i);
            String namespace = id.substring(0, id.indexOf(':'));
            String path = id.substring(id.indexOf(':') + 1);
            int bx = innerX + col * (colW + 6);
            int bw = col == cols - 1 && cols == 2 ? innerW - colW - 6 : colW;
            addRenderableWidget(CycleButton.onOffBuilder(!excluded.contains(id))
                .create(bx, 76 + row * 24, bw, 20,
                    Component.translatable("entity." + namespace + ".villager." + path),
                    (button, enabled) -> {
                        if (enabled) excluded.remove(id);
                        else excluded.add(id);
                    }));
        }

        button("vv.save", px, height - 26, 84, () -> {
            save.accept(Set.copyOf(excluded));
            onClose();
        });
        button("gui.cancel", px + 90, height - 26, 80, this::onClose);
    }

    private void button(String key, int x, int y, int width, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable(key), b -> action.run())
            .bounds(x, y, width, 20).build());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        offset -= (int) Math.signum(vertical);
        rebuildWidgets();
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN
                || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) {
            offset += event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN
                ? rowsPerPage()
                : -rowsPerPage();
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        int px = panelX();
        int pw = panelWidth();
        ClientDisplayText.drawPanel(graphics, px, 22, pw, Math.max(150, height - 54));

        int helpY = 27;
        for (var line : font.split(Component.translatable("vv.professionsHelp"), pw - 16)) {
            if (helpY <= 38) {
                graphics.text(font, line, px + 8, helpY, 0xFF9FB8D8);
                helpY += 10;
            }
        }
        super.extractRenderState(graphics, x, y, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
