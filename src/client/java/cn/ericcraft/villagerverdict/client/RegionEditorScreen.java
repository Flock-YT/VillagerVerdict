package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.TargetTrade;
import cn.ericcraft.villagerverdict.model.TradingHallRegion;
import cn.ericcraft.villagerverdict.model.UiFormatting;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Single-page, beginner-friendly editor for trading hall regions: quick "around me" / two-corner helpers,
 * optional exact coordinates, and a live preview of the box size and how many villagers it contains.
 */
public final class RegionEditorScreen extends WorldScreen {
    private static final List<String> COMMON_DIMS = List.of(
        "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end"
    );

    private static final int QUICK_Y = 52;
    private static final int QUICK_LABEL_W = 70;
    private static final int COORD_Y = 86;
    private static final int COORD_LABEL_W = 86;
    private static final int COLOR_Y = 150;

    private final Screen parent;
    private final String regionId;
    private final boolean editing;
    private final List<TargetTrade> existingTargets;
    private final Consumer<TradingHallRegion> onSave;
    private final Runnable onDelete;

    private String nameText;
    private String dimensionId;
    private String minXText, minYText, minZText;
    private String maxXText, maxYText, maxZText;
    private int color;
    private String error = "";

    private EditBox nameBox;
    private EditBox minXBox, minYBox, minZBox;
    private EditBox maxXBox, maxYBox, maxZBox;

    public RegionEditorScreen(
            Screen parent,
            TradingHallRegion existing,
            String currentDimension,
            Consumer<TradingHallRegion> onSave,
            Runnable onDelete) {
        super(Component.translatable(
            existing == null ? "vv.regionEditor.titleNew" : "vv.regionEditor.titleEdit"));
        this.parent = parent;
        this.editing = existing != null;
        this.regionId = existing == null ? UUID.randomUUID().toString() : existing.id();
        this.existingTargets = existing == null ? List.of() : existing.targets();
        this.onSave = onSave;
        this.onDelete = onDelete;

        var mc = net.minecraft.client.Minecraft.getInstance();
        int px = mc.player == null ? 0 : (int) Math.floor(mc.player.getX());
        int py = mc.player == null ? 64 : (int) Math.floor(mc.player.getY());
        int pz = mc.player == null ? 0 : (int) Math.floor(mc.player.getZ());

        this.nameText = existing == null
            ? Component.translatable("vv.regionEditor.defaultName").getString()
            : existing.name();
        this.dimensionId = existing == null
            ? (currentDimension == null ? "minecraft:overworld" : currentDimension)
            : existing.dimension();
        this.minXText = existing == null ? Integer.toString(px - 16) : UiFormatting.formatDecimal(existing.minX());
        this.maxXText = existing == null ? Integer.toString(px + 16) : UiFormatting.formatDecimal(existing.maxX());
        this.minYText = existing == null ? Integer.toString(py - 8) : UiFormatting.formatDecimal(existing.minY());
        this.maxYText = existing == null ? Integer.toString(py + 12) : UiFormatting.formatDecimal(existing.maxY());
        this.minZText = existing == null ? Integer.toString(pz - 16) : UiFormatting.formatDecimal(existing.minZ());
        this.maxZText = existing == null ? Integer.toString(pz + 16) : UiFormatting.formatDecimal(existing.maxZ());
        this.color = existing == null ? 0xFF99AAFF : existing.color();
    }

    private int panelX() {
        return Math.max(12, width / 2 - 190);
    }

    private int panelWidth() {
        return Math.min(380, width - 24);
    }

    @Override
    protected void init() {
        int px = panelX();
        int pw = panelWidth();
        int innerX = px + 8;
        int innerW = pw - 16;

        int nameW = innerW * 52 / 100;
        int dimW = innerW - nameW - 6;

        nameBox = new EditBox(font, innerX, 30, nameW, 18, Component.translatable("vv.name"));
        nameBox.setMaxLength(128);
        nameBox.setValue(nameText);
        nameBox.setHint(Component.translatable("vv.name"));
        nameBox.setResponder(v -> {
            nameText = v;
            error = "";
        });
        addRenderableWidget(nameBox);

        String dimName = ClientDisplayText.dimensionDisplayName(dimensionId);
        var dimBtn = addRenderableWidget(Button.builder(
                Component.literal(font.plainSubstrByWidth(
                    Component.translatable("vv.regionEditor.setDimHere", dimName).getString(), dimW - 8)),
                ignored -> {
                    syncBoxes();
                    String playerDim = minecraft.level == null
                        ? null
                        : minecraft.level.dimension().identifier().toString();
                    if (playerDim != null && !playerDim.equals(dimensionId)) {
                        dimensionId = playerDim;
                    } else {
                        int idx = COMMON_DIMS.indexOf(dimensionId);
                        dimensionId = COMMON_DIMS.get((idx + 1) % COMMON_DIMS.size());
                    }
                    rebuildWidgets();
                })
            .bounds(innerX + nameW + 6, 30, dimW, 18)
            .build());
        dimBtn.setTooltip(Tooltip.create(Component.translatable("vv.regionEditor.dim.help", dimensionId)));

        int quickStartX = innerX + QUICK_LABEL_W;
        int quickAvail = innerW - QUICK_LABEL_W;
        int qw = (quickAvail - 16) / 5;
        quickButton("vv.regionEditor.radius16", "vv.regionEditor.radius.help", 16,
            quickStartX, qw, () -> setAroundPlayer(16));
        quickButton("vv.regionEditor.radius32", "vv.regionEditor.radius.help", 32,
            quickStartX + qw + 4, qw, () -> setAroundPlayer(32));
        quickButton("vv.regionEditor.radius64", "vv.regionEditor.radius.help", 64,
            quickStartX + 2 * (qw + 4), qw, () -> setAroundPlayer(64));
        quickButton("vv.regionEditor.cornerStart", "vv.regionEditor.corner.help", 0,
            quickStartX + 3 * (qw + 4), qw, () -> setCornerToPlayer(true));
        quickButton("vv.regionEditor.cornerEnd", "vv.regionEditor.corner.help", 0,
            quickStartX + 4 * (qw + 4), quickAvail - 4 * (qw + 4), () -> setCornerToPlayer(false));

        int coordStart = innerX + COORD_LABEL_W;
        int boxW = (innerW - COORD_LABEL_W - 16) / 2;
        minXBox = coordBox(coordStart, COORD_Y, boxW, minXText, v -> minXText = v);
        maxXBox = coordBox(coordStart + boxW + 16, COORD_Y, boxW, maxXText, v -> maxXText = v);

        minYBox = coordBox(coordStart, COORD_Y + 20, boxW, minYText, v -> minYText = v);
        maxYBox = coordBox(coordStart + boxW + 16, COORD_Y + 20, boxW, maxYText, v -> maxYText = v);

        minZBox = coordBox(coordStart, COORD_Y + 40, boxW, minZText, v -> minZText = v);
        maxZBox = coordBox(coordStart + boxW + 16, COORD_Y + 40, boxW, maxZText, v -> maxZText = v);

        int swatchX = innerX + 94;
        int presets = UiFormatting.COLOR_PRESETS.size();
        int sw = Math.min(22, Math.max(16, (innerW - 94 - (presets - 1) * 3) / presets));
        for (int i = 0; i < presets; i++) {
            var preset = UiFormatting.COLOR_PRESETS.get(i);
            int bx = swatchX + i * (sw + 3);
            var btn = addRenderableWidget(Button.builder(
                    Component.empty(),
                    ignored -> {
                        syncBoxes();
                        color = preset.argb();
                        rebuildWidgets();
                    })
                .bounds(bx, COLOR_Y, sw, 18)
                .build());
            btn.setTooltip(Tooltip.create(Component.translatable(preset.key())));
        }

        int footerY = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("vv.save"), ignored -> saveAndClose())
            .bounds(px, footerY, 84, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(px + 90, footerY, 80, 20)
            .build());
        if (editing && onDelete != null) {
            addRenderableWidget(Button.builder(Component.translatable("vv.delete"), ignored -> {
                    onDelete.run();
                    onClose();
                })
                .bounds(px + pw - 80, footerY, 80, 20)
                .build());
        }
    }

    private void quickButton(String key, String helpKey, int radius, int x, int w, Runnable action) {
        var btn = addRenderableWidget(Button.builder(
                Component.translatable(key),
                ignored -> action.run())
            .bounds(x, QUICK_Y, w, 18)
            .build());
        btn.setTooltip(Tooltip.create(radius > 0
            ? Component.translatable(helpKey, radius)
            : Component.translatable(helpKey)));
    }

    private EditBox coordBox(int x, int y, int w, String value, Consumer<String> responder) {
        var box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setMaxLength(16);
        box.setValue(value);
        box.setResponder(v -> {
            responder.accept(v);
            error = "";
        });
        addRenderableWidget(box);
        return box;
    }

    private void syncBoxes() {
        if (nameBox != null) nameText = nameBox.getValue();
        if (minXBox != null) minXText = minXBox.getValue();
        if (maxXBox != null) maxXText = maxXBox.getValue();
        if (minYBox != null) minYText = minYBox.getValue();
        if (maxYBox != null) maxYText = maxYBox.getValue();
        if (minZBox != null) minZText = minZBox.getValue();
        if (maxZBox != null) maxZText = maxZBox.getValue();
    }

    private void setAroundPlayer(int radius) {
        syncBoxes();
        var p = minecraft.player;
        int x = p == null ? 0 : (int) Math.floor(p.getX());
        int y = p == null ? 64 : (int) Math.floor(p.getY());
        int z = p == null ? 0 : (int) Math.floor(p.getZ());
        if (minecraft.level != null) {
            dimensionId = minecraft.level.dimension().identifier().toString();
        }
        minXText = Integer.toString(x - radius);
        maxXText = Integer.toString(x + radius);
        minYText = Integer.toString(y - Math.min(16, radius / 2));
        maxYText = Integer.toString(y + Math.min(24, radius));
        minZText = Integer.toString(z - radius);
        maxZText = Integer.toString(z + radius);
        error = "";
        rebuildWidgets();
    }

    private void setCornerToPlayer(boolean start) {
        syncBoxes();
        var p = minecraft.player;
        if (p == null) return;
        String x = Integer.toString((int) Math.floor(p.getX()));
        String y = Integer.toString((int) Math.floor(p.getY()));
        String z = Integer.toString((int) Math.floor(p.getZ()));
        if (start) {
            minXText = x;
            minYText = y;
            minZText = z;
        } else {
            maxXText = x;
            maxYText = y;
            maxZText = z;
        }
        if (minecraft.level != null) {
            dimensionId = minecraft.level.dimension().identifier().toString();
        }
        error = "";
        rebuildWidgets();
    }

    private void saveAndClose() {
        syncBoxes();
        try {
            String trimmedName = nameText == null || nameText.isBlank()
                ? Component.translatable("vv.regionEditor.defaultName").getString()
                : nameText.trim();
            double[] xr = UiFormatting.normalizedRange(
                Double.parseDouble(minXText.trim()), Double.parseDouble(maxXText.trim()));
            double[] yr = UiFormatting.normalizedRange(
                Double.parseDouble(minYText.trim()), Double.parseDouble(maxYText.trim()));
            double[] zr = UiFormatting.normalizedRange(
                Double.parseDouble(minZText.trim()), Double.parseDouble(maxZText.trim()));
            var region = new TradingHallRegion(
                regionId,
                trimmedName,
                dimensionId,
                xr[0], yr[0], zr[0],
                xr[1], yr[1], zr[1],
                existingTargets,
                color);
            onSave.accept(region);
            onClose();
        } catch (Exception e) {
            error = Component.translatable("vv.invalid").getString() + ": " + e.getMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        int px = panelX();
        int pw = panelWidth();
        int panelH = Math.max(176, height - 56);
        ClientDisplayText.drawPanel(g, px, 24, pw, panelH);

        int innerX = px + 8;
        int innerW = pw - 16;
        g.text(font, font.plainSubstrByWidth(
            Component.translatable("vv.regionEditor.quickLabel").getString(), QUICK_LABEL_W - 4),
            innerX, QUICK_Y + 5, 0xFF9FB8D8);
        g.text(font, font.plainSubstrByWidth(
            Component.translatable("vv.regionEditor.manualLabel").getString(), innerW),
            innerX, COORD_Y - 11, 0xFF9FB8D8);
        g.text(font, Component.translatable("vv.regionEditor.rangeX"), innerX + 8, COORD_Y + 5, 0xFFE6EDF7);
        g.text(font, Component.translatable("vv.regionEditor.rangeY"), innerX + 8, COORD_Y + 25, 0xFFE6EDF7);
        g.text(font, Component.translatable("vv.regionEditor.rangeZ"), innerX + 8, COORD_Y + 45, 0xFFE6EDF7);

        int coordStart = innerX + COORD_LABEL_W;
        int boxW = (innerW - COORD_LABEL_W - 16) / 2;
        g.centeredText(font, "~", coordStart + boxW + 8, COORD_Y + 5, 0xFFBBD0E8);
        g.centeredText(font, "~", coordStart + boxW + 8, COORD_Y + 25, 0xFFBBD0E8);
        g.centeredText(font, "~", coordStart + boxW + 8, COORD_Y + 45, 0xFFBBD0E8);

        g.text(font, font.plainSubstrByWidth(
            Component.translatable("vv.regionColor").getString(), 66),
            innerX, COLOR_Y + 5, 0xFF9FB8D8);
        g.fill(innerX + 70, COLOR_Y, innerX + 88, COLOR_Y + 18, color | 0xFF000000);
        g.outline(innerX + 70, COLOR_Y, 18, 18, 0xFFFFFFFF);

        super.extractRenderState(g, mx, my, delta);

        // Draw colored swatch fills after super so the color sits visibly inside each swatch button.
        int swatchX = innerX + 94;
        int presets = UiFormatting.COLOR_PRESETS.size();
        int sw = Math.min(22, Math.max(16, (innerW - 94 - (presets - 1) * 3) / presets));
        for (int i = 0; i < presets; i++) {
            var preset = UiFormatting.COLOR_PRESETS.get(i);
            int bx = swatchX + i * (sw + 3);
            g.fill(bx + 2, COLOR_Y + 2, bx + sw - 2, COLOR_Y + 16, preset.argb() | 0xFF000000);
            if (color == preset.argb()) {
                g.outline(bx, COLOR_Y, sw, 18, 0xFFFFFFFF);
                g.centeredText(font, "✓", bx + sw / 2, COLOR_Y + 5, 0xFF101820);
            }
        }

        String previewLine = buildSizePreview();
        int py = COLOR_Y + 26;
        if (!previewLine.isEmpty()) {
            for (var line : font.split(Component.literal(previewLine), innerW)) {
                if (py + 9 < height - 30) g.text(font, line, innerX, py, 0xFF55DD88);
                py += 10;
            }
        }
        for (var line : font.split(Component.translatable("vv.regionEditor.tip"), innerW)) {
            if (py + 9 < height - 30) g.text(font, line, innerX, py, 0xFF8CA4C4);
            py += 10;
        }
        if (!error.isEmpty()) {
            g.text(font, font.plainSubstrByWidth(error, pw), px, height - 38, 0xFFFF6677);
        }
    }

    private String buildSizePreview() {
        try {
            double[] xr = UiFormatting.normalizedRange(
                Double.parseDouble(minXText.trim()), Double.parseDouble(maxXText.trim()));
            double[] yr = UiFormatting.normalizedRange(
                Double.parseDouble(minYText.trim()), Double.parseDouble(maxYText.trim()));
            double[] zr = UiFormatting.normalizedRange(
                Double.parseDouble(minZText.trim()), Double.parseDouble(maxZText.trim()));
            int dx = Math.max(1, (int) Math.round(xr[1] - xr[0]) + 1);
            int dy = Math.max(1, (int) Math.round(yr[1] - yr[0]) + 1);
            int dz = Math.max(1, (int) Math.round(zr[1] - zr[0]) + 1);
            var draft = new TradingHallRegion(regionId, "", dimensionId,
                xr[0], yr[0], zr[0], xr[1], yr[1], zr[1], List.of(), color);
            var service = VerdictService.INSTANCE;
            long inside = service.records().stream()
                .filter(service::enabled)
                .filter(draft::contains)
                .count();
            return Component.translatable("vv.regionEditor.preview",
                dx, dy, dz, ClientDisplayText.dimensionDisplayName(dimensionId), inside).getString();
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
