package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.VillagerRecord;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

/** Edits the parent settings draft; cancelling either screen leaves saved settings alone. */
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
        professions = Minecraft.getInstance().level.registryAccess()
            .lookupOrThrow(Registries.VILLAGER_PROFESSION).listElements()
            .map(holder -> holder.getRegisteredName())
            .filter(id -> VillagerRecord.tradable(false, id)).sorted().toList();
    }

    protected void init() {
        int half = (width - 28) / 2;
        button("vv.selectAll", 12, 32, half, () -> {
            excluded.clear();
            rebuildWidgets();
        });
        button("vv.selectNone", 16 + half, 32, half, () -> {
            excluded.addAll(professions);
            rebuildWidgets();
        });
        offset = Math.clamp(offset, 0, Math.max(0, professions.size() - rows()));
        for (int i = offset; i < Math.min(professions.size(), offset + rows()); i++) {
            String id = professions.get(i);
            String namespace = id.substring(0, id.indexOf(':'));
            String path = id.substring(id.indexOf(':') + 1);
            addRenderableWidget(CycleButton.onOffBuilder(!excluded.contains(id))
                .create(12, 62 + (i - offset) * 24, width - 24, 20,
                    Component.translatable("entity." + namespace + ".villager." + path), (button, enabled) -> {
                        if (enabled) excluded.remove(id);
                        else excluded.add(id);
                    }));
        }
        button("vv.save", 12, height - 26, 80, () -> {
            save.accept(Set.copyOf(excluded));
            onClose();
        });
        button("gui.cancel", 98, height - 26, 80, this::onClose);
    }

    private int rows() {
        return Math.max(1, (height - 110) / 24);
    }

    private void button(String key, int x, int y, int width, Runnable action) {
        addRenderableWidget(Button.builder(Component.translatable(key), b -> action.run())
            .bounds(x, y, width, 20).build());
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        offset -= (int) Math.signum(vertical);
        rebuildWidgets();
        return true;
    }

    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN
            || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) {
            offset += event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN ? rows() : -rows();
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        graphics.text(font, Component.translatable("vv.professionsHelp"), 12, height - 42, 0xFFBBD0E8);
        super.extractRenderState(graphics, x, y, delta);
    }

    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
