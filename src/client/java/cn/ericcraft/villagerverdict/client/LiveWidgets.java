package cn.ericcraft.villagerverdict.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Reconciles a screen's visible controls without resetting input or tooltip state. */
final class LiveWidgets {
    private final Map<String, AbstractWidget> widgets = new LinkedHashMap<>();
    private final Map<AbstractWidget, Component> tooltips = new HashMap<>();
    private final Set<String> seen = new HashSet<>();
    private final Consumer<AbstractWidget> add;
    private final Consumer<AbstractWidget> remove;
    private boolean reattach;

    LiveWidgets(Consumer<AbstractWidget> add, Consumer<AbstractWidget> remove) {
        this.add = add;
        this.remove = remove;
    }

    void begin(boolean reattach) {
        this.reattach = reattach;
        seen.clear();
    }

    private <T extends AbstractWidget> T widget(String key, Class<T> type, Supplier<T> factory) {
        AbstractWidget existing = widgets.get(key);
        boolean fresh = existing == null;
        if (fresh) {
            existing = factory.get();
            widgets.put(key, existing);
        }
        if (seen.add(key) && (fresh || reattach)) add.accept(existing);
        return type.cast(existing);
    }

    EditBox editBox(String key, Supplier<EditBox> factory, int x, int y, int width) {
        var box = widget(key, EditBox.class, factory);
        box.setX(x);
        box.setY(y);
        box.setWidth(width);
        return box;
    }

    Button button(String key, Component message, int x, int y, int width, Runnable action) {
        return button(key, message, x, y, width, action, LiveButton::new);
    }

    Button trash(String key, Component message, int x, int y, Runnable action) {
        var button = button(key, message, x, y, 20, action, TrashButton::new);
        tooltip(button, message);
        return button;
    }

    private Button button(String key, Component message, int x, int y, int width, Runnable action,
            Supplier<LiveButton> factory) {
        var button = widget(key, LiveButton.class, factory);
        button.setRectangle(width, 20, x, y);
        if (!button.getMessage().equals(message)) button.setMessage(message);
        button.action = action;
        button.active = true;
        return button;
    }

    void tooltip(AbstractWidget widget, Component message) {
        if (!message.equals(tooltips.get(widget))) {
            widget.setTooltip(Tooltip.create(message));
            tooltips.put(widget, message);
        }
    }

    void end() {
        widgets.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) return false;
            remove.accept(entry.getValue());
            tooltips.remove(entry.getValue());
            return true;
        });
    }

    private static class LiveButton extends Button {
        private Runnable action = () -> {};

        private LiveButton() {
            super(0, 0, 20, 20, Component.empty(), ignored -> {}, DEFAULT_NARRATION);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            action.run();
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            extractDefaultSprite(graphics);
            extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
        }
    }

    private static final class TrashButton extends LiveButton {
        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
            extractDefaultSprite(g);
            int x = getX() + 5;
            int y = getY() + 4;
            int color = isHoveredOrFocused() ? 0xFFFF6677 : 0xFFE6EDF7;
            g.fill(x + 3, y, x + 7, y + 1, color);
            g.fill(x, y + 2, x + 10, y + 3, color);
            g.fill(x + 1, y + 4, x + 2, y + 11, color);
            g.fill(x + 8, y + 4, x + 9, y + 11, color);
            g.fill(x + 1, y + 11, x + 9, y + 12, color);
            g.fill(x + 4, y + 5, x + 5, y + 10, color);
            g.fill(x + 6, y + 5, x + 7, y + 10, color);
        }
    }
}
