package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.UiFormatting;
import cn.ericcraft.villagerverdict.model.VillagerRecord;
import cn.ericcraft.villagerverdict.storage.Settings;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

/** Tabbed, plain-language settings workspace replacing raw developer-style text forms. */
public final class SettingsScreen extends WorldScreen {
    private static final String[] TAB_KEYS = {
        "vv.settings.tab.display",
        "vv.settings.tab.probe",
        "vv.settings.tab.rules",
        "vv.settings.tab.tools"
    };
    private static final int[] SCAN_SECOND_PRESETS = {10, 20, 40, 100}; // 0.5s, 1s, 2s, 5s

    private final Screen parent;
    private final VerdictService service = VerdictService.INSTANCE;

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
        super(Component.translatable("vv.settings"));
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
        return Math.max(12, width / 2 - 205);
    }

    private int panelWidth() {
        return Math.min(410, width - 24);
    }

    @Override
    protected void init() {
        int px = panelX();
        int pw = panelWidth();
        int tabW = (pw - 9) / 4;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            int idx = i;
            boolean active = tab == idx;
            var btn = addRenderableWidget(Button.builder(
                    active
                        ? Component.translatable(TAB_KEYS[i]).withColor(0xFF55DD88)
                        : Component.translatable(TAB_KEYS[i]),
                    ignored -> {
                        syncBoxes();
                        tab = idx;
                        rebuildWidgets();
                    })
                .bounds(px + i * (tabW + 3), 24, i == 3 ? pw - 3 * (tabW + 3) : tabW, 20)
                .build());
        }

        int innerX = px + 10;
        int innerW = pw - 20;

        if (tab == 0) initDisplayTab(innerX, innerW);
        else if (tab == 1) initProbeTab(innerX, innerW);
        else if (tab == 2) initRulesTab(innerX, innerW);
        else initToolsTab(innerX, innerW);

        int footerY = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("vv.save"), ignored -> saveAndClose())
            .bounds(px, footerY, 96, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(px + 102, footerY, 84, 20)
            .build());
    }

    private void initDisplayTab(int innerX, int innerW) {
        int half = (innerW - 6) / 2;
        addToggle("vv.markers", markers, v -> markers = v, innerX, 54, half);
        addToggle("vv.statusMarks", statusMarks, v -> statusMarks = v, innerX + half + 6, 54, innerW - half - 6);
        addToggle("vv.summaries", summaries, v -> summaries = v, innerX, 78, half);
        addToggle("vv.notifications", notifications, v -> notifications = v, innerX + half + 6, 78, innerW - half - 6);

        initColorRow(innerX, innerW, 120, green, v -> green = v);
        initColorRow(innerX, innerW, 146, yellow, v -> yellow = v);
        initColorRow(innerX, innerW, 172, red, v -> red = v);
    }

    private void initColorRow(int innerX, int innerW, int y, int current, IntConsumer setter) {
        if (innerW < 316) {
            var presets = UiFormatting.COLOR_PRESETS;
            var selected = presets.stream().filter(p -> p.argb() == current).findFirst();
            Component label = selected.map(p -> Component.translatable(p.key()))
                .orElseGet(() -> Component.literal(UiFormatting.formatColorHex(current)));
            var cycle = addRenderableWidget(Button.builder(label, ignored -> {
                int index = selected.map(p -> presets.indexOf(p)).orElse(-1);
                setter.accept(presets.get((index + 1) % presets.size()).argb());
                rebuildWidgets();
            }).bounds(innerX + 148, y, innerW - 148, 18).build());
            cycle.setTooltip(Tooltip.create(Component.translatable("vv.color.cycle")));
            return;
        }
        int swatchX = innerX + 148;
        int count = UiFormatting.COLOR_PRESETS.size();
        int sw = Math.min(22, Math.max(16, (innerW - 148 - (count - 1) * 3) / count));
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

    private void initProbeTab(int innerX, int innerW) {
        var autoScanBtn = addRenderableWidget(CycleButton.onOffBuilder(autoScan)
            .create(innerX, 54, innerW, 20, Component.translatable("vv.autoScan"), (b, v) -> autoScan = v));
        autoScanBtn.setTooltip(Tooltip.create(Component.translatable("vv.autoScan.help")));

        int ctrlX = innerX + innerW - 176;
        addRenderableWidget(Button.builder(Component.literal("-16"), ignored -> {
            radius = Math.clamp(radius - 16, 1, 1024);
            rebuildWidgets();
        }).bounds(ctrlX, 80, 26, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-4"), ignored -> {
            radius = Math.clamp(radius - 4, 1, 1024);
            rebuildWidgets();
        }).bounds(ctrlX + 29, 80, 22, 20).build());
        var radDisp = addRenderableWidget(Button.builder(
            Component.translatable("vv.settings.blocksValue", radius), ignored -> {})
            .bounds(ctrlX + 54, 80, 68, 20).build());
        radDisp.active = false;
        addRenderableWidget(Button.builder(Component.literal("+4"), ignored -> {
            radius = Math.clamp(radius + 4, 1, 1024);
            rebuildWidgets();
        }).bounds(ctrlX + 125, 80, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+16"), ignored -> {
            radius = Math.clamp(radius + 16, 1, 1024);
            rebuildWidgets();
        }).bounds(ctrlX + 150, 80, 26, 20).build());

        int pStart = innerX + 108;
        int pw = (innerW - 108 - 9) / 4;
        for (int i = 0; i < SCAN_SECOND_PRESETS.length; i++) {
            int ticks = SCAN_SECOND_PRESETS[i];
            boolean selected = scanTicks == ticks;
            String label = (selected ? "● " : "")
                + Component.translatable("vv.settings.secondsValue",
                    UiFormatting.formatDecimal(UiFormatting.ticksToSeconds(ticks))).getString();
            var btn = addRenderableWidget(Button.builder(
                    Component.literal(label).withColor(selected ? 0xFF55DD88 : 0xFFE6EDF7),
                    ignored -> {
                        scanTicks = ticks;
                        rebuildWidgets();
                    })
                .bounds(pStart + i * (pw + 3), 106, pw, 20)
                .build());
            btn.setTooltip(Tooltip.create(Component.translatable("vv.scanTicks.help")));
        }

        if (currentServer != null) {
            var serverAutoBtn = addRenderableWidget(CycleButton.onOffBuilder(autoServerEnabled)
                .create(innerX, 134, innerW, 20,
                    Component.translatable("vv.settings.autoServerHere"),
                    (btn, enabled) -> {
                        if (enabled && !autoServerAcknowledged) {
                            minecraft.gui.setScreen(new WorldConfirmScreen(
                                yes -> {
                                    autoServerEnabled = yes;
                                    if (yes) autoServerAcknowledged = true;
                                    minecraft.gui.setScreen(this);
                                },
                                Component.translatable("vv.probe"),
                                Component.translatable("vv.probeExplanation")));
                        } else {
                            autoServerEnabled = enabled;
                        }
                    }));
            serverAutoBtn.setTooltip(Tooltip.create(Component.translatable("vv.probe.help")));
        } else {
            var noneBtn = addRenderableWidget(Button.builder(
                Component.translatable("vv.settings.autoServerNone"), ignored -> {})
                .bounds(innerX, 134, innerW, 20).build());
            noneBtn.active = false;
        }

        long[] speedPresets = {100L, 300L, 1000L};
        String[] speedKeys = {
            "vv.settings.speedFast", "vv.settings.speedBalanced", "vv.settings.speedSafe"
        };
        int sw = (innerW - 8) / 3;
        for (int i = 0; i < 3; i++) {
            long ms = speedPresets[i];
            boolean selected = intervalMillis == ms;
            String txt = (selected ? "● " : "") + Component.translatable(speedKeys[i]).getString();
            addRenderableWidget(Button.builder(
                    Component.literal(font.plainSubstrByWidth(txt, sw - 6))
                        .withColor(selected ? 0xFF55DD88 : 0xFFE6EDF7),
                    ignored -> {
                        intervalMillis = ms;
                        rebuildWidgets();
                    })
                .bounds(innerX + i * (sw + 4), 172, i == 2 ? innerW - 2 * (sw + 4) : sw, 20)
                .build());
        }
    }

    private void initRulesTab(int innerX, int innerW) {
        var profBtn = addRenderableWidget(Button.builder(
                Component.translatable("vv.professions"),
                ignored -> minecraft.gui.setScreen(new ProfessionScreen(this, excludedProfessions, chosen -> {
                    excludedProfessions.clear();
                    excludedProfessions.addAll(chosen);
                })))
            .bounds(innerX, 74, innerW, 20)
            .build());
        profBtn.setTooltip(Tooltip.create(Component.translatable("vv.professionsHelp")));

        int ctrlX = innerX + innerW - 116;
        var minusBackup = addRenderableWidget(Button.builder(Component.literal("-"), ignored -> {
            backups = Math.max(1, backups - 1);
            rebuildWidgets();
        }).bounds(ctrlX, 106, 24, 20).build());
        minusBackup.active = backups > 1;
        var backupDisp = addRenderableWidget(Button.builder(
            Component.translatable("vv.targetEditor.keepCount", backups), ignored -> {})
            .bounds(ctrlX + 28, 106, 60, 20).build());
        backupDisp.active = false;
        backupDisp.setTooltip(Tooltip.create(Component.translatable("vv.backups.help")));
        addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
            backups = Math.min(16, backups + 1);
            rebuildWidgets();
        }).bounds(ctrlX + 92, 106, 24, 20).build());
    }

    private void initToolsTab(int innerX, int innerW) {
        int half = (innerW - 6) / 2;
        addRenderableWidget(Button.builder(
                Component.translatable("vv.shortcuts"),
                ignored -> ShortcutScreen.open(this))
            .bounds(innerX, 54, half, 20)
            .build());
        addRenderableWidget(Button.builder(
                Component.translatable("vv.data"),
                ignored -> DataScreen.open(this))
            .bounds(innerX + half + 6, 54, innerW - half - 6, 20)
            .build());

        int fieldW = Math.min(180, innerW / 2);
        datasetBox = new EditBox(font, innerX + innerW - fieldW, 114, fieldW, 18,
            Component.translatable("vv.dataset"));
        datasetBox.setMaxLength(64);
        datasetBox.setValue(dataset);
        datasetBox.setResponder(v -> {
            dataset = v;
            error = "";
        });
        datasetBox.setTooltip(Tooltip.create(Component.translatable("vv.dataset.help")));
        addRenderableWidget(datasetBox);

        int ctrlX = innerX + innerW - 116;
        int sec = Math.max(1, (int) Math.round(UiFormatting.millisToSeconds(timeoutMillis)));
        var minusTimeout = addRenderableWidget(Button.builder(Component.literal("-"), ignored -> {
            syncBoxes();
            timeoutMillis = UiFormatting.secondsToTimeoutMillis(Math.max(1, sec - 1));
            rebuildWidgets();
        }).bounds(ctrlX, 140, 24, 20).build());
        minusTimeout.active = sec > 1;
        var timeoutDisp = addRenderableWidget(Button.builder(
            Component.translatable("vv.settings.secondsValue", sec), ignored -> {})
            .bounds(ctrlX + 28, 140, 60, 20).build());
        timeoutDisp.active = false;
        addRenderableWidget(Button.builder(Component.literal("+"), ignored -> {
            syncBoxes();
            timeoutMillis = UiFormatting.secondsToTimeoutMillis(Math.min(60, sec + 1));
            rebuildWidgets();
        }).bounds(ctrlX + 92, 140, 24, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("vv.settings.resetDefaults"),
                ignored -> {
                    resetDraftToDefaults();
                    rebuildWidgets();
                })
            .bounds(innerX, 174, Math.min(200, innerW), 20)
            .build());
    }

    private void addToggle(String key, boolean value, Consumer<Boolean> setter, int x, int y, int w) {
        var btn = addRenderableWidget(CycleButton.onOffBuilder(value)
            .create(x, y, w, 20, Component.translatable(key), (b, v) -> setter.accept(v)));
        btn.setTooltip(Tooltip.create(Component.translatable(key + ".help")));
    }

    private void syncBoxes() {
        if (datasetBox != null) dataset = datasetBox.getValue();
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

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        int px = panelX();
        int pw = panelWidth();
        int tabW = (pw - 9) / 4;
        int activeBx = px + tab * (tabW + 3);
        int activeBw = tab == 3 ? pw - 3 * (tabW + 3) : tabW;
        g.fill(activeBx + 4, 44, activeBx + activeBw - 4, 46, 0xFF55DD88);
        int panelH = Math.max(154, height - 80);
        ClientDisplayText.drawPanel(g, px, 48, pw, panelH);

        int innerX = px + 10;
        int innerW = pw - 20;

        if (tab == 0) {
            g.text(font, font.plainSubstrByWidth(
                Component.translatable("vv.settings.displayTip").getString(), innerW),
                innerX, 104, 0xFF9FB8D8);
            drawColorRowHeader(g, innerX, 120, "vv.green", green);
            drawColorRowHeader(g, innerX, 146, "vv.yellow", yellow);
            drawColorRowHeader(g, innerX, 172, "vv.red", red);
        } else if (tab == 1) {
            g.text(font, Component.translatable("vv.radius"), innerX, 85, 0xFFE6EDF7);
            g.text(font, Component.translatable("vv.scanTicks"), innerX, 111, 0xFFE6EDF7);
            g.text(font, Component.translatable("vv.settings.speedLabel"), innerX, 159, 0xFF9FB8D8);
            g.text(font, font.plainSubstrByWidth(
                Component.translatable("vv.settings.probeTip").getString(), innerW),
                innerX, 196, 0xFF8CA4C4);
        } else if (tab == 2) {
            long totalProf = minecraft.level == null
                ? 13
                : minecraft.level.registryAccess()
                    .lookupOrThrow(Registries.VILLAGER_PROFESSION)
                    .listElements()
                    .filter(h -> VillagerRecord.tradable(false, h.getRegisteredName()))
                    .count();
            long enabledProf = Math.max(0, totalProf - excludedProfessions.size());
            g.text(font, font.plainSubstrByWidth(
                Component.translatable("vv.settings.professionsCount", enabledProf, totalProf).getString(),
                innerW),
                innerX, 58, 0xFF9FB8D8);
            g.text(font, Component.translatable("vv.backups"), innerX, 111, 0xFFE6EDF7);
            g.text(font, font.plainSubstrByWidth(
                Component.translatable("vv.backups.help").getString(), innerW),
                innerX, 130, 0xFF8CA4C4);
        } else {
            g.text(font, font.plainSubstrByWidth(
                Component.translatable("vv.settings.toolsDesc").getString(), innerW),
                innerX, 78, 0xFF8CA4C4);
            g.horizontalLine(innerX, innerX + innerW, 95, 0xFF425472);
            g.text(font, Component.translatable("vv.settings.advancedTitle"), innerX, 100, 0xFF9FB8D8);
            g.text(font, Component.translatable("vv.dataset"), innerX, 118, 0xFFE6EDF7);
            g.text(font, Component.translatable("vv.settings.timeoutSeconds"), innerX, 145, 0xFFE6EDF7);
        }

        super.extractRenderState(g, mx, my, delta);

        if (tab == 0) {
            drawSwatches(g, innerX, innerW, 120, green);
            drawSwatches(g, innerX, innerW, 146, yellow);
            drawSwatches(g, innerX, innerW, 172, red);
        }

        if (!error.isEmpty()) {
            g.text(font, font.plainSubstrByWidth(error, pw), px, height - 38, 0xFFFF6677);
        }
    }

    private void drawColorRowHeader(GuiGraphicsExtractor g, int innerX, int y, String labelKey, int argb) {
        g.text(font, font.plainSubstrByWidth(Component.translatable(labelKey).getString(), 120),
            innerX, y + 5, 0xFFE6EDF7);
        g.fill(innerX + 124, y, innerX + 142, y + 18, argb | 0xFF000000);
        g.outline(innerX + 124, y, 18, 18, 0xFFFFFFFF);
    }

    private void drawSwatches(GuiGraphicsExtractor g, int innerX, int innerW, int y, int selected) {
        if (innerW < 316) return;
        int swatchX = innerX + 148;
        int count = UiFormatting.COLOR_PRESETS.size();
        int sw = Math.min(22, Math.max(16, (innerW - 148 - (count - 1) * 3) / count));
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
