package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.UiFormatting;
import cn.ericcraft.villagerverdict.model.VillagerRecord;
import cn.ericcraft.villagerverdict.storage.Settings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

/**
 * Tabbed settings workspace. Every option is one row: a label on the left, its control on the right,
 * and an always-visible plain-language explanation underneath, so nothing important hides in tooltips.
 */
public final class SettingsScreen extends WorldScreen {
    private static final String[] TAB_KEYS = {
        "vv.settings.tab.display",
        "vv.settings.tab.colors",
        "vv.settings.tab.probe",
        "vv.settings.tab.rules",
        "vv.settings.tab.tools"
    };
    private static final int TAB_DISPLAY = 0, TAB_COLORS = 1, TAB_PROBE = 2, TAB_RULES = 3, TAB_TOOLS = 4;
    private static final int[] SCAN_SECOND_PRESETS = {10, 20, 40, 100}; // 0.5s, 1s, 2s, 5s
    private static final long[] SPEED_PRESETS = {100L, 300L, 1000L};
    private static final String[] SPEED_KEYS = {
        "vv.settings.speedFast", "vv.settings.speedBalanced", "vv.settings.speedSafe"
    };

    /** First option row inside the panel. */
    private static final int ROW_Y = 54;
    /** Height of one option row (label/control line + explanation line + gap). */
    private static final int ROW_H = 34;
    private static final int TOGGLE_W = 64;

    private static final int COLOR_LABEL = 0xFFE6EDF7;
    private static final int COLOR_HELP = 0xFF8CA4C4;
    private static final int COLOR_SECTION = 0xFF9FB8D8;
    private static final int COLOR_ACCENT = 0xFF55DD88;

    /** A rendered option row: label (left), control starts at controlX, explanation below. */
    private record OptionRow(Component label, Component help, int y, int controlX) {}

    private final Screen parent;
    private final VerdictService service = VerdictService.INSTANCE;
    private final List<OptionRow> rows = new ArrayList<>();

    private int tab;
    private boolean markers;
    private boolean statusMarks;
    private boolean summaries;
    private boolean notifications;
    private int green;
    private int yellow;
    private int red;
    private int radius;
    private int scanTicks;
    private boolean autoScan;
    private int markerLimit;
    private int textRange;
    private long intervalMillis;
    private long timeoutMillis;
    private int backups;
    private String dataset;
    private final Set<String> excludedProfessions;
    private boolean autoServerEnabled;
    private boolean autoServerAcknowledged;
    private final boolean initialAutoServerEnabled;
    private boolean resetAll;
    private final String currentServer;
    private EditBox datasetBox;
    private String error = "";

    public SettingsScreen(Screen parent) {
        super(Component.translatable("vv.settings.title"));
        this.parent = parent;
        Settings s = service.settings;
        this.markers = s.markers();
        this.statusMarks = s.statusMarks();
        this.summaries = s.summaries();
        this.notifications = s.notifications();
        this.green = s.green();
        this.yellow = s.yellow();
        this.red = s.red();
        this.radius = s.radius();
        this.scanTicks = s.scanTicks();
        this.autoScan = s.autoScan();
        this.markerLimit = s.markerLimit();
        this.textRange = s.textRange();
        this.intervalMillis = s.intervalMillis();
        this.timeoutMillis = s.timeoutMillis();
        this.backups = s.backups();
        this.dataset = s.dataset();
        this.excludedProfessions = new HashSet<>(s.excludedProfessions());
        this.currentServer = service.identity() == null ? null : service.identity().server();
        this.autoServerEnabled = currentServer != null
            && s.autoServers().getOrDefault(currentServer, false);
        this.autoServerAcknowledged = currentServer != null
            && s.acknowledged().contains(currentServer);
        this.initialAutoServerEnabled = this.autoServerEnabled;
    }

    public static void open(Screen parent) {
        Minecraft.getInstance().gui.setScreen(new SettingsScreen(parent));
    }

    private int panelX() {
        return Math.max(12, width / 2 - 215);
    }

    private int panelWidth() {
        return Math.min(430, width - 24);
    }

    private int tabWidth() {
        return (panelWidth() - 4 * 3) / TAB_KEYS.length;
    }

    @Override
    protected void init() {
        rows.clear();
        int px = panelX();
        int pw = panelWidth();
        int tabW = tabWidth();
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int idx = i;
            boolean active = tab == idx;
            int w = i == TAB_KEYS.length - 1 ? pw - (TAB_KEYS.length - 1) * (tabW + 3) : tabW;
            addRenderableWidget(Button.builder(
                    Component.translatable(TAB_KEYS[i]).withColor(active ? COLOR_ACCENT : 0xFFFFFFFF),
                    ignored -> {
                        syncBoxes();
                        tab = idx;
                        rebuildWidgets();
                    })
                .bounds(px + i * (tabW + 3), 24, w, 20)
                .build());
        }

        int innerX = px + 10;
        int innerW = pw - 20;

        switch (tab) {
            case TAB_DISPLAY -> initDisplayTab(innerX, innerW);
            case TAB_COLORS -> initColorsTab(innerX, innerW);
            case TAB_PROBE -> initProbeTab(innerX, innerW);
            case TAB_RULES -> initRulesTab(innerX, innerW);
            default -> initToolsTab(innerX, innerW);
        }

        int footerY = height - 26;
        var save = addRenderableWidget(Button.builder(Component.translatable("vv.save"), ignored -> saveAndClose())
            .bounds(px, footerY, 84, 20)
            .build());
        save.setTooltip(Tooltip.create(Component.translatable("vv.settings.save.help")));
        var cancel = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(px + 88, footerY, 76, 20)
            .build());
        cancel.setTooltip(Tooltip.create(Component.translatable("vv.settings.cancel.help")));
        int resetW = Math.min(140, font.width(Component.translatable("vv.settings.resetDefaults")) + 16);
        var reset = addRenderableWidget(Button.builder(
                Component.translatable("vv.settings.resetDefaults"),
                ignored -> {
                    syncBoxes();
                    resetDraftToDefaults();
                    rebuildWidgets();
                })
            .bounds(px + pw - resetW, footerY, resetW, 20)
            .build());
        reset.setTooltip(Tooltip.create(Component.translatable("vv.settings.resetDefaults.help")));
    }

    // ---------------------------------------------------------------------------------------------
    // Tabs
    // ---------------------------------------------------------------------------------------------

    private void initDisplayTab(int innerX, int innerW) {
        addToggleRow("vv.markers", markers, v -> markers = v, innerX, innerW, ROW_Y);
        addToggleRow("vv.statusMarks", statusMarks, v -> statusMarks = v, innerX, innerW, ROW_Y + ROW_H);
        addToggleRow("vv.summaries", summaries, v -> summaries = v, innerX, innerW, ROW_Y + 2 * ROW_H);
        addToggleRow("vv.notifications", notifications, v -> notifications = v, innerX, innerW, ROW_Y + 3 * ROW_H);
    }

    private void initColorsTab(int innerX, int innerW) {
        initColorRow("vv.green", innerX, innerW, ROW_Y, green, v -> green = v);
        initColorRow("vv.red", innerX, innerW, ROW_Y + ROW_H, red, v -> red = v);
        initColorRow("vv.yellow", innerX, innerW, ROW_Y + 2 * ROW_H, yellow, v -> yellow = v);
    }

    private void initColorRow(String key, int innerX, int innerW, int y, int current, IntConsumer setter) {
        int swatchX = innerX + 148;
        rows.add(new OptionRow(Component.translatable(key), Component.translatable(key + ".help"), y, innerX + 120));
        if (innerW < 316) {
            var presets = UiFormatting.COLOR_PRESETS;
            var selected = presets.stream().filter(p -> p.argb() == current).findFirst();
            Component label = selected.map(p -> Component.translatable(p.key()))
                .orElseGet(() -> Component.literal(UiFormatting.formatColorHex(current)));
            var cycle = addRenderableWidget(Button.builder(label, ignored -> {
                int index = selected.map(p -> presets.indexOf(p)).orElse(-1);
                setter.accept(presets.get((index + 1) % presets.size()).argb());
                rebuildWidgets();
            }).bounds(swatchX, y, innerW - 148, 18).build());
            cycle.setTooltip(Tooltip.create(Component.translatable("vv.color.cycle")));
            return;
        }
        int count = UiFormatting.COLOR_PRESETS.size();
        int sw = swatchWidth(innerW);
        for (int i = 0; i < count; i++) {
            var preset = UiFormatting.COLOR_PRESETS.get(i);
            int bx = swatchX + i * (sw + 3);
            var btn = addRenderableWidget(Button.builder(
                    Component.empty(),
                    ignored -> {
                        setter.accept(preset.argb());
                        rebuildWidgets();
                    })
                .bounds(bx, y, sw, 18)
                .build());
            btn.setTooltip(Tooltip.create(Component.translatable(preset.key())));
        }
    }

    private int swatchWidth(int innerW) {
        int count = UiFormatting.COLOR_PRESETS.size();
        return Math.min(22, Math.max(16, (innerW - 148 - (count - 1) * 3) / count));
    }

    private void initProbeTab(int innerX, int innerW) {
        int right = innerX + innerW;

        // 1. Auto-check trades in this world.
        int y = ROW_Y;
        if (currentServer != null) {
            int cx = right - TOGGLE_W;
            rows.add(new OptionRow(Component.translatable("vv.settings.autoServerHere"),
                Component.translatable("vv.settings.autoServerHere.help"), y, cx));
            var btn = addRenderableWidget(Button.builder(toggleLabel(autoServerEnabled), ignored -> {
                    boolean enable = !autoServerEnabled;
                    if (enable && !autoServerAcknowledged) {
                        minecraft.gui.setScreen(new WorldConfirmScreen(
                            yes -> {
                                autoServerEnabled = yes;
                                if (yes) autoServerAcknowledged = true;
                                minecraft.gui.setScreen(this);
                            },
                            Component.translatable("vv.probe"),
                            Component.translatable("vv.probeExplanation")));
                    } else {
                        autoServerEnabled = enable;
                        rebuildWidgets();
                    }
                })
                .bounds(cx, y, TOGGLE_W, 20)
                .build());
            btn.setTooltip(Tooltip.create(Component.translatable("vv.probe.help")));
        } else {
            int cx = right - TOGGLE_W;
            rows.add(new OptionRow(Component.translatable("vv.settings.autoServerHere"),
                Component.translatable("vv.settings.autoServerNone"), y, cx));
            var none = addRenderableWidget(Button.builder(Component.literal("—"), ignored -> {})
                .bounds(cx, y, TOGGLE_W, 20).build());
            none.active = false;
        }

        // 2. Auto-check speed.
        y += ROW_H;
        int speedTotal = Math.min(252, innerW - 110);
        int speedX = right - speedTotal;
        int sw = (speedTotal - 8) / 3;
        rows.add(new OptionRow(Component.translatable("vv.settings.speedLabel"),
            Component.translatable("vv.settings.speedLabel.help"), y, speedX));
        for (int i = 0; i < SPEED_PRESETS.length; i++) {
            long ms = SPEED_PRESETS[i];
            boolean selected = intervalMillis == ms;
            int w = i == 2 ? speedTotal - 2 * (sw + 4) : sw;
            String txt = (selected ? "● " : "") + Component.translatable(SPEED_KEYS[i]).getString();
            var btn = addRenderableWidget(Button.builder(
                    Component.literal(font.plainSubstrByWidth(txt, w - 6))
                        .withColor(selected ? COLOR_ACCENT : COLOR_LABEL),
                    ignored -> {
                        intervalMillis = ms;
                        rebuildWidgets();
                    })
                .bounds(speedX + i * (sw + 4), y, w, 20)
                .build());
            btn.setTooltip(Tooltip.create(Component.translatable(SPEED_KEYS[i] + ".help")));
        }

        // 3. Automatically find nearby villagers.
        y += ROW_H;
        addToggleRow("vv.autoScan", autoScan, v -> autoScan = v, innerX, innerW, y);

        // 4. Find interval.
        y += ROW_H;
        int presetTotal = Math.min(252, innerW - 110);
        int pStart = right - presetTotal;
        int pw = (presetTotal - 9) / 4;
        rows.add(new OptionRow(Component.translatable("vv.scanTicks"),
            Component.translatable("vv.scanTicks.help"), y, pStart));
        for (int i = 0; i < SCAN_SECOND_PRESETS.length; i++) {
            int ticks = SCAN_SECOND_PRESETS[i];
            boolean selected = scanTicks == ticks;
            int w = i == 3 ? presetTotal - 3 * (pw + 3) : pw;
            String label = (selected ? "● " : "")
                + Component.translatable("vv.settings.secondsValue",
                    UiFormatting.formatDecimal(UiFormatting.ticksToSeconds(ticks))).getString();
            var btn = addRenderableWidget(Button.builder(
                    Component.literal(font.plainSubstrByWidth(label, w - 6))
                        .withColor(selected ? COLOR_ACCENT : COLOR_LABEL),
                    ignored -> {
                        scanTicks = ticks;
                        rebuildWidgets();
                    })
                .bounds(pStart + i * (pw + 3), y, w, 20)
                .build());
            btn.active = autoScan;
            btn.setTooltip(Tooltip.create(Component.translatable("vv.scanTicks.help")));
        }
    }

    private void initRulesTab(int innerX, int innerW) {
        int right = innerX + innerW;

        // 1. Range.
        int y = ROW_Y;
        int ctrlX = right - 176;
        rows.add(new OptionRow(Component.translatable("vv.radius"), Component.translatable("vv.radius.help"), y, ctrlX));
        addRenderableWidget(Button.builder(Component.literal("-16"), ignored -> {
            radius = Math.clamp(radius - 16, 1, 1024);
            rebuildWidgets();
        }).bounds(ctrlX, y, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-4"), ignored -> {
            radius = Math.clamp(radius - 4, 1, 1024);
            rebuildWidgets();
        }).bounds(ctrlX + 29, y, 22, 20).build());
        var radDisp = addRenderableWidget(Button.builder(
            Component.translatable("vv.settings.blocksValue", radius), ignored -> {})
            .bounds(ctrlX + 54, y, 68, 20).build());
        radDisp.active = false;
        addRenderableWidget(Button.builder(Component.literal("+4"), ignored -> {
            radius = Math.clamp(radius + 4, 1, 1024);
            rebuildWidgets();
        }).bounds(ctrlX + 125, y, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+16"), ignored -> {
            radius = Math.clamp(radius + 16, 1, 1024);
            rebuildWidgets();
        }).bounds(ctrlX + 150, y, 26, 20).build());

        // 2. Professions.
        y += ROW_H;
        int profW = Math.min(176, Math.max(100, font.width(Component.translatable("vv.professions")) + 16));
        int profX = right - profW;
        rows.add(new OptionRow(
            Component.translatable("vv.settings.professionsCount", enabledProfessions(), totalProfessions()),
            Component.translatable("vv.professionsHelp"), y, profX));
        var profBtn = addRenderableWidget(Button.builder(
                Component.translatable("vv.professions"),
                ignored -> minecraft.gui.setScreen(new ProfessionScreen(this, excludedProfessions, chosen -> {
                    excludedProfessions.clear();
                    excludedProfessions.addAll(chosen);
                })))
            .bounds(profX, y, profW, 20)
            .build());
        profBtn.setTooltip(Tooltip.create(Component.translatable("vv.professionsHelp")));

        // 3. Backups per product.
        y += ROW_H;
        int bx = right - 116;
        rows.add(new OptionRow(Component.translatable("vv.backups"), Component.translatable("vv.backups.help"), y, bx));
        var minusBackup = addRenderableWidget(Button.builder(Component.literal("-"), ignored -> {
            backups = Math.max(1, backups - 1);
            rebuildWidgets();
        }).bounds(bx, y, 24, 20).build());
        minusBackup.active = backups > 1;
        var backupDisp = addRenderableWidget(Button.builder(
            Component.translatable("vv.targetEditor.keepCount", backups), ignored -> {})
            .bounds(bx + 28, y, 60, 20).build());
        backupDisp.active = false;
        var plusBackup = addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
            backups = Math.min(16, backups + 1);
            rebuildWidgets();
        }).bounds(bx + 92, y, 24, 20).build());
        plusBackup.active = backups < 16;
    }

    private void initToolsTab(int innerX, int innerW) {
        int right = innerX + innerW;
        int btnW = Math.min(150, innerW / 2);

        // 1. Shortcuts.
        int y = ROW_Y;
        rows.add(new OptionRow(Component.translatable("vv.settings.shortcutsLabel"),
            Component.translatable("vv.settings.shortcutsLabel.help"), y, right - btnW));
        addRenderableWidget(Button.builder(
                Component.translatable("vv.shortcuts"),
                ignored -> ShortcutScreen.open(this))
            .bounds(right - btnW, y, btnW, 20)
            .build());

        // 2. Saved trades.
        y += ROW_H;
        rows.add(new OptionRow(Component.translatable("vv.settings.dataLabel"),
            Component.translatable("vv.settings.dataLabel.help"), y, right - btnW));
        addRenderableWidget(Button.builder(
                Component.translatable("vv.data"),
                ignored -> DataScreen.open(this))
            .bounds(right - btnW, y, btnW, 20)
            .build());

        // Advanced section title is drawn at ROW_Y + 2 * ROW_H.
        // 3. Profile name.
        y += ROW_H + 14;
        int fieldW = Math.min(150, innerW / 2);
        rows.add(new OptionRow(Component.translatable("vv.dataset"), Component.translatable("vv.dataset.help"),
            y, right - fieldW));
        datasetBox = new EditBox(font, right - fieldW, y + 1, fieldW, 18, Component.translatable("vv.dataset"));
        datasetBox.setMaxLength(64);
        datasetBox.setValue(dataset);
        datasetBox.setResponder(v -> {
            dataset = v;
            error = "";
        });
        datasetBox.setTooltip(Tooltip.create(Component.translatable("vv.dataset.help")));
        addRenderableWidget(datasetBox);

        // 4. Timeout.
        y += ROW_H;
        int ctrlX = right - 116;
        rows.add(new OptionRow(Component.translatable("vv.settings.timeoutSeconds"),
            Component.translatable("vv.settings.timeoutSeconds.help"), y, ctrlX));
        int sec = Math.max(1, (int) Math.round(UiFormatting.millisToSeconds(timeoutMillis)));
        var minusTimeout = addRenderableWidget(Button.builder(Component.literal("-"), ignored -> {
            syncBoxes();
            timeoutMillis = UiFormatting.secondsToTimeoutMillis(Math.max(1, sec - 1));
            rebuildWidgets();
        }).bounds(ctrlX, y, 24, 20).build());
        minusTimeout.active = sec > 1;
        var timeoutDisp = addRenderableWidget(Button.builder(
            Component.translatable("vv.settings.secondsValue", sec), ignored -> {})
            .bounds(ctrlX + 28, y, 60, 20).build());
        timeoutDisp.active = false;
        var plusTimeout = addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
            syncBoxes();
            timeoutMillis = UiFormatting.secondsToTimeoutMillis(Math.min(60, sec + 1));
            rebuildWidgets();
        }).bounds(ctrlX + 92, y, 24, 20).build());
        plusTimeout.active = sec < 60;
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private Component toggleLabel(boolean value) {
        return value
            ? Component.translatable("vv.on").withColor(COLOR_ACCENT)
            : Component.translatable("vv.off").withColor(0xFFB0B8C4);
    }

    /** Adds an on/off option row whose label, explanation and tooltip come from {@code key} and {@code key.help}. */
    private void addToggleRow(String key, boolean value, Consumer<Boolean> setter, int innerX, int innerW, int y) {
        int cx = innerX + innerW - TOGGLE_W;
        rows.add(new OptionRow(Component.translatable(key), Component.translatable(key + ".help"), y, cx));
        var btn = addRenderableWidget(Button.builder(toggleLabel(value), ignored -> {
                setter.accept(!value);
                rebuildWidgets();
            })
            .bounds(cx, y, TOGGLE_W, 20)
            .build());
        btn.setTooltip(Tooltip.create(Component.translatable(key + ".help")));
    }

    private long totalProfessions() {
        return minecraft.level == null
            ? 13
            : minecraft.level.registryAccess()
                .lookupOrThrow(Registries.VILLAGER_PROFESSION)
                .listElements()
                .filter(h -> VillagerRecord.tradable(false, h.getRegisteredName()))
                .count();
    }

    private long enabledProfessions() {
        return Math.max(0, totalProfessions() - excludedProfessions.size());
    }

    private void syncBoxes() {
        if (datasetBox != null && tab == TAB_TOOLS) dataset = datasetBox.getValue();
    }

    private void resetDraftToDefaults() {
        Settings d = Settings.defaults();
        markers = d.markers();
        statusMarks = d.statusMarks();
        summaries = d.summaries();
        notifications = d.notifications();
        green = d.green();
        yellow = d.yellow();
        red = d.red();
        radius = d.radius();
        scanTicks = d.scanTicks();
        autoScan = d.autoScan();
        markerLimit = d.markerLimit();
        textRange = d.textRange();
        intervalMillis = d.intervalMillis();
        timeoutMillis = d.timeoutMillis();
        backups = d.backups();
        dataset = d.dataset();
        excludedProfessions.clear();
        autoServerEnabled = false;
        autoServerAcknowledged = false;
        resetAll = true;
    }

    private void saveAndClose() {
        syncBoxes();
        try {
            String trimmedDataset = dataset == null || dataset.isBlank() ? "default" : dataset.trim();
            Settings current = service.settings;
            var autoServers = new java.util.HashMap<>(current.autoServers());
            var acknowledged = new HashSet<>(current.acknowledged());
            if (resetAll) {
                autoServers.clear();
                acknowledged.clear();
            }
            if (currentServer != null && (autoServerEnabled != initialAutoServerEnabled || resetAll)) {
                if (autoServerEnabled) autoServers.put(currentServer, true);
                else autoServers.remove(currentServer);
                if (autoServerAcknowledged) acknowledged.add(currentServer);
            }
            Settings updated = new Settings(
                4,
                markers,
                statusMarks,
                summaries,
                green,
                yellow,
                red,
                radius,
                scanTicks,
                autoScan,
                markerLimit,
                textRange,
                intervalMillis,
                timeoutMillis,
                backups,
                notifications,
                autoServers,
                acknowledged,
                trimmedDataset,
                Set.copyOf(excludedProfessions));
            service.settings(updated);
            if (currentServer != null && (autoServerEnabled != initialAutoServerEnabled || resetAll)) {
                if (autoServerEnabled && autoServerAcknowledged) service.probe.run();
                else if (!autoServerEnabled) service.pause();
            }
            onClose();
        } catch (Exception e) {
            error = Component.translatable("vv.invalid").getString() + ": " + e.getMessage();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        int px = panelX();
        int pw = panelWidth();
        int tabW = tabWidth();
        int activeBx = px + tab * (tabW + 3);
        int activeBw = tab == TAB_KEYS.length - 1 ? pw - (TAB_KEYS.length - 1) * (tabW + 3) : tabW;
        g.fill(activeBx + 4, 44, activeBx + activeBw - 4, 46, COLOR_ACCENT);
        int panelH = Math.max(154, height - 80);
        ClientDisplayText.drawPanel(g, px, 48, pw, panelH);

        int innerX = px + 10;
        int innerW = pw - 20;

        Component hoverHelp = null;
        for (var row : rows) {
            int labelW = Math.max(30, row.controlX() - innerX - 6);
            g.text(font, font.plainSubstrByWidth(row.label().getString(), labelW), innerX, row.y() + 6, COLOR_LABEL);
            String help = row.help().getString();
            String shown = font.plainSubstrByWidth(help, innerW);
            g.text(font, shown, innerX, row.y() + 22, COLOR_HELP);
            boolean overLabel = mx >= innerX && mx < row.controlX() && my >= row.y() && my < row.y() + 20;
            boolean overHelp = mx >= innerX && mx < innerX + innerW && my >= row.y() + 21 && my < row.y() + 32;
            if ((overHelp && shown.length() < help.length())
                    || (overLabel && font.width(row.label()) > labelW)) {
                hoverHelp = Component.literal(row.label().getString() + "\n" + help);
            }
        }

        if (tab == TAB_COLORS) {
            drawCurrentSwatch(g, innerX, ROW_Y, green);
            drawCurrentSwatch(g, innerX, ROW_Y + ROW_H, red);
            drawCurrentSwatch(g, innerX, ROW_Y + 2 * ROW_H, yellow);
        } else if (tab == TAB_TOOLS) {
            int sy = ROW_Y + 2 * ROW_H;
            g.horizontalLine(innerX, innerX + innerW, sy - 2, 0xFF425472);
            g.text(font, font.plainSubstrByWidth(
                Component.translatable("vv.settings.advancedTitle").getString(), innerW),
                innerX, sy + 2, COLOR_SECTION);
        } else if (tab == TAB_PROBE) {
            int tipY = ROW_Y + 4 * ROW_H;
            if (tipY + 10 < 48 + panelH) {
                g.text(font, font.plainSubstrByWidth(
                    Component.translatable("vv.settings.probeTip").getString(), innerW),
                    innerX, tipY, COLOR_SECTION);
            }
        } else if (tab == TAB_DISPLAY) {
            int tipY = ROW_Y + 4 * ROW_H;
            if (tipY + 10 < 48 + panelH) {
                g.text(font, font.plainSubstrByWidth(
                    Component.translatable("vv.settings.displayTip").getString(), innerW),
                    innerX, tipY, COLOR_SECTION);
            }
        }

        super.extractRenderState(g, mx, my, delta);

        if (tab == TAB_COLORS) {
            drawSwatches(g, innerX, innerW, ROW_Y, green);
            drawSwatches(g, innerX, innerW, ROW_Y + ROW_H, red);
            drawSwatches(g, innerX, innerW, ROW_Y + 2 * ROW_H, yellow);
        }

        if (!error.isEmpty()) {
            int ex = px + 170;
            int resetW = Math.min(140, font.width(Component.translatable("vv.settings.resetDefaults")) + 16);
            int ew = Math.max(20, pw - 170 - resetW - 6);
            g.text(font, font.plainSubstrByWidth(error, ew), ex, height - 20, 0xFFFF6677);
        }

        if (hoverHelp != null) {
            g.setTooltipForNextFrame(font.split(hoverHelp, 260), mx, my);
        }
    }

    private void drawCurrentSwatch(GuiGraphicsExtractor g, int innerX, int y, int argb) {
        g.fill(innerX + 124, y, innerX + 142, y + 18, argb | 0xFF000000);
        g.outline(innerX + 124, y, 18, 18, 0xFFFFFFFF);
    }

    private void drawSwatches(GuiGraphicsExtractor g, int innerX, int innerW, int y, int selected) {
        if (innerW < 316) return;
        int swatchX = innerX + 148;
        int count = UiFormatting.COLOR_PRESETS.size();
        int sw = swatchWidth(innerW);
        for (int i = 0; i < count; i++) {
            var preset = UiFormatting.COLOR_PRESETS.get(i);
            int bx = swatchX + i * (sw + 3);
            g.fill(bx + 2, y + 2, bx + sw - 2, y + 16, preset.argb() | 0xFF000000);
            if (selected == preset.argb()) {
                g.outline(bx, y, sw, 18, 0xFFFFFFFF);
                g.centeredText(font, "✓", bx + sw / 2, y + 5, 0xFF101820);
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    public static void toggleMarkers() {
        var service = VerdictService.INSTANCE;
        var s = service.settings;
        service.settings(
            new Settings(
                4,
                !s.markers(),
                s.statusMarks(),
                s.summaries(),
                s.green(),
                s.yellow(),
                s.red(),
                s.radius(),
                s.scanTicks(),
                s.autoScan(),
                s.markerLimit(),
                s.textRange(),
                s.intervalMillis(),
                s.timeoutMillis(),
                s.backups(),
                s.notifications(),
                s.autoServers(),
                s.acknowledged(),
                s.dataset(),
                s.excludedProfessions()));
    }

    public static void toggleProbe() {
        toggleProbe(message -> {});
    }

    public static void toggleProbe(Consumer<Component> feedback) {
        var service = VerdictService.INSTANCE;
        var identity = service.identity();
        if (identity == null || !service.ready()) {
            feedback.accept(Component.translatable("vv.feedback.unavailable"));
            return;
        }
        if (service.probe.running()) {
            service.enable(false);
            feedback.accept(Component.translatable("vv.feedback.probePaused"));
            return;
        }
        var mc = Minecraft.getInstance();
        if (!service.settings.acknowledged().contains(service.identity().server())) {
            Screen parent = mc.gui.screen();
            mc.gui.setScreen(
                new WorldConfirmScreen(
                    yes -> {
                        mc.gui.setScreen(parent);
                        if (!yes) feedback.accept(Component.translatable("vv.feedback.probeCancelled"));
                        else if (!service.ready() || !identity.equals(service.identity()))
                            feedback.accept(Component.translatable("vv.feedback.unavailable"));
                        else {
                            service.enable(true);
                            feedback.accept(Component.translatable("vv.feedback.probeOn"));
                        }
                    },
                    Component.translatable("vv.probe"),
                    Component.translatable("vv.probeExplanation")));
        } else {
            service.enable(true);
            feedback.accept(Component.translatable("vv.feedback.probeOn"));
        }
    }
}
