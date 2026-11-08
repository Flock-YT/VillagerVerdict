package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.WorldIdentity;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** Lists persisted worlds and clears villager records without removing configuration. */
public final class DataScreen extends WorldScreen {
    private final Screen parent;
    private final VerdictService service = VerdictService.INSTANCE;
    private List<VerdictService.StoredWorld> worlds = List.of();
    private WorldIdentity selected;
    private int offset;
    private Component status = Component.empty();
    private boolean initialized, busy;

    public DataScreen(Screen parent) {
        super(Component.translatable("vv.data"));
        this.parent = parent;
    }

    public static void open(Screen parent) {
        net.minecraft.client.Minecraft.getInstance().gui.setScreen(new DataScreen(parent));
    }

    protected void init() {
        int buttonWidth = (width - 32) / 3;
        var refresh = button("vv.refresh", 12, 34, buttonWidth, () -> reload(Component.empty()));
        var world = button("vv.clearWorld", 16 + buttonWidth, 34, buttonWidth, () -> confirm(false));
        var server = button("vv.clearServer", 20 + 2 * buttonWidth, 34, buttonWidth, () -> confirm(true));
        refresh.active = !busy;
        world.active = server.active = !busy && selected != null;
        button("gui.done", width - 74, height - 26, 62, this::onClose);
        var previous = button("vv.previousPage", 12, height - 26, 66, () -> scroll(-rows()));
        var next = button("vv.nextPage", 82, height - 26, 66, () -> scroll(rows()));
        previous.active = offset > 0;
        next.active = offset + rows() < worlds.size();
        for (int i = offset; i < Math.min(worlds.size(), offset + rows()); i++) {
            var item = worlds.get(i);
            String name = serverName(item.identity());
            if (item.current()) name = Component.translatable("vv.currentWorld").getString() + " · " + name;
            var text = Component.literal(font.plainSubstrByWidth(name, width - 52))
                .withColor(item.identity().equals(selected) ? 0xFF99AAFF : 0xFFFFFFFF);
            var row = addRenderableWidget(Button.builder(text, ignored -> {
                selected = item.identity();
                rebuildWidgets();
            }).bounds(12, 62 + (i - offset) * 40, width - 24, 20).build());
            row.active = !busy;
            row.setTooltip(Tooltip.create(Component.literal(identityLabel(item.identity()))));
        }
        if (!initialized) {
            initialized = true;
            reload(Component.empty());
        }
    }

    private Button button(String key, int x, int y, int width, Runnable action) {
        return addRenderableWidget(Button.builder(Component.translatable(key), ignored -> action.run())
            .bounds(x, y, width, 20).build());
    }

    private int rows() {
        return Math.max(1, (height - 116) / 40);
    }

    private void reload(Component completion) {
        busy = true;
        status = Component.translatable("vv.loading");
        rebuildWidgets();
        service.storedWorlds().whenComplete((items, failure) -> minecraft.execute(() -> {
            busy = false;
            if (failure != null) {
                status = Component.translatable("vv.dataReadFailed");
            } else {
                worlds = items;
                if (selected == null || worlds.stream().noneMatch(w -> w.identity().equals(selected)))
                    selected = worlds.isEmpty() ? null : worlds.getFirst().identity();
                offset = Math.clamp(offset, 0, Math.max(0, worlds.size() - rows()));
                status = !completion.getString().isEmpty() ? completion
                    : Component.translatable(worlds.isEmpty() ? "vv.noData" : "vv.dataHelp");
            }
            rebuildWidgets();
        }));
    }

    private void confirm(boolean server) {
        if (busy || selected == null) return;
        WorldIdentity identity = selected;
        minecraft.gui.setScreen(new WorldConfirmScreen(yes -> {
            if (!yes) {
                minecraft.gui.setScreen(this);
                return;
            }
            busy = true;
            status = Component.translatable("vv.clearing");
            minecraft.gui.setScreen(this);
            var operation = server ? service.clearServerVillagers(identity.server())
                : service.clearWorldVillagers(identity);
            operation.whenComplete((count, failure) -> minecraft.execute(() -> {
                if (failure != null) {
                    busy = false;
                    status = Component.translatable("vv.clearFailed");
                    rebuildWidgets();
                } else {
                    reload(Component.translatable("vv.cleared", count));
                }
            }));
        }, Component.translatable(server ? "vv.clearServer" : "vv.clearWorld"),
            Component.translatable(server ? "vv.clearServerConfirm" : "vv.clearWorldConfirm",
                server ? serverName(identity)
                    : serverName(identity) + " / " + identity.dataset() + " / " + identity.dimension())));
    }

    private String serverName(WorldIdentity identity) {
        String server = identity.server();
        if (!server.startsWith("singleplayer:")) return server;
        try {
            return Component.translatable("vv.localWorld", Path.of(server.substring(13)).getFileName()).getString();
        } catch (RuntimeException ignored) {
            return server;
        }
    }

    private String identityLabel(WorldIdentity identity) {
        return identity.server() + " / " + identity.dataset() + " / " + identity.dimension();
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        for (int i = offset; i < Math.min(worlds.size(), offset + rows()); i++) {
            var item = worlds.get(i);
            String detail = Component.translatable("vv.worldDataDetails", item.identity().dataset(),
                item.identity().dimension(), item.villagers()).getString();
            graphics.text(font, font.plainSubstrByWidth(detail, width - 36), 18,
                86 + (i - offset) * 40, 0xFFBBD0E8);
        }
        graphics.text(font, font.plainSubstrByWidth(status.getString(), width - 24),
            12, height - 44, 0xFFFFCC55);
    }

    private void scroll(int amount) {
        offset = Math.clamp(offset + amount, 0, Math.max(0, worlds.size() - rows()));
        rebuildWidgets();
    }

    public boolean mouseScrolled(double x, double y, double amountX, double amountY) {
        scroll(-(int) Math.signum(amountY));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN
            || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) {
            scroll(event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN ? rows() : -rows());
            return true;
        }
        return super.keyPressed(event);
    }

    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
