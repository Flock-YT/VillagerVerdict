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

/** Lists persisted worlds and clears villager records without removing targets, regions, or settings. */
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

    private int panelX() {
        return Math.max(12, width / 2 - 205);
    }

    private int panelWidth() {
        return Math.min(410, width - 24);
    }

    @Override
    protected void init() {
        int px = panelX();
        int pw = panelWidth();
        int innerX = px + 8;
        int innerW = pw - 16;
        int buttonWidth = (innerW - 8) / 3;

        var refresh = button("vv.refresh", innerX, 30, buttonWidth, () -> reload(Component.empty()));
        var world = button("vv.clearWorld", innerX + buttonWidth + 4, 30, buttonWidth, () -> confirm(false));
        var server = button("vv.clearServer", innerX + 2 * (buttonWidth + 4), 30,
            innerW - 2 * (buttonWidth + 4), () -> confirm(true));
        refresh.active = !busy;
        world.active = server.active = !busy && selected != null;

        button("gui.done", px + pw - 74, height - 26, 74, this::onClose);
        var previous = button("vv.previousPage", px, height - 26, 72, () -> scroll(-rows()));
        var next = button("vv.nextPage", px + 76, height - 26, 72, () -> scroll(rows()));
        previous.active = offset > 0;
        next.active = offset + rows() < worlds.size();

        for (int i = offset; i < Math.min(worlds.size(), offset + rows()); i++) {
            var item = worlds.get(i);
            String name = serverName(item.identity());
            if (item.current()) {
                name = "★ " + Component.translatable("vv.currentWorld").getString() + " · " + name;
            }
            boolean isSelected = item.identity().equals(selected);
            var text = Component.literal(font.plainSubstrByWidth(name, innerW - 14))
                .withColor(isSelected ? 0xFF55DD88 : 0xFFFFFFFF);
            var row = addRenderableWidget(Button.builder(text, ignored -> {
                selected = item.identity();
                rebuildWidgets();
            }).bounds(innerX, 56 + (i - offset) * 36, innerW, 20).build());
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
        return Math.max(1, (height - 118) / 36);
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
                    : serverName(identity) + " · "
                        + ClientDisplayText.dimensionDisplayName(identity.dimension()))));
    }

    private String serverName(WorldIdentity identity) {
        String server = identity.server();
        if (!server.startsWith("singleplayer:")) return server;
        try {
            return Component.translatable("vv.localWorld",
                Path.of(server.substring(13)).getFileName()).getString();
        } catch (RuntimeException ignored) {
            return server;
        }
    }

    private String identityLabel(WorldIdentity identity) {
        return serverName(identity) + " · " + identity.dataset()
            + " · " + ClientDisplayText.dimensionDisplayName(identity.dimension());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        int px = panelX();
        int pw = panelWidth();
        ClientDisplayText.drawPanel(graphics, px, 22, pw, Math.max(152, height - 54));

        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int innerX = px + 10;
        int innerW = pw - 20;
        for (int i = offset; i < Math.min(worlds.size(), offset + rows()); i++) {
            var item = worlds.get(i);
            String detail = Component.translatable("vv.worldDataDetails",
                item.identity().dataset(),
                ClientDisplayText.dimensionDisplayName(item.identity().dimension()),
                item.cachedTrades()).getString();
            graphics.text(font, font.plainSubstrByWidth(detail, innerW), innerX,
                78 + (i - offset) * 36, 0xFF9FB8D8);
        }
        int sy = height - 54;
        for (var line : font.split(status, innerW)) {
            if (sy < height - 34) {
                graphics.text(font, line, innerX, sy, 0xFFFFCC55);
                sy += 10;
            }
        }
    }

    private void scroll(int amount) {
        offset = Math.clamp(offset + amount, 0, Math.max(0, worlds.size() - rows()));
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amountX, double amountY) {
        scroll(-(int) Math.signum(amountY));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN
                || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) {
            scroll(event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN ? rows() : -rows());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
