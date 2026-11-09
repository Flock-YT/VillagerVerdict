package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.analysis.ProfessionHighlights;
import cn.ericcraft.villagerverdict.analysis.TradeComparison;
import cn.ericcraft.villagerverdict.model.AnalysisResult;
import cn.ericcraft.villagerverdict.model.AnalysisResult.Verdict;
import cn.ericcraft.villagerverdict.model.ComparisonResult;
import cn.ericcraft.villagerverdict.model.ItemData;
import cn.ericcraft.villagerverdict.model.TargetTrade;
import cn.ericcraft.villagerverdict.model.Trade;
import cn.ericcraft.villagerverdict.model.TradingHallRegion;
import cn.ericcraft.villagerverdict.model.UiFormatting;
import cn.ericcraft.villagerverdict.model.VillagerRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Main management workspace with single-row navigation, plain-language details, and intuitive controls. */
public final class VerdictScreen extends WorldScreen {
    private static final String[] SORTS = {
        "vv.value", "vv.price", "vv.enchantment", "vv.distance", "vv.profession"
    };
    private static final String[] FILTERS = {
        "vv.filter.all", "vv.filter.keep", "vv.filter.discard", "vv.filter.unscanned"
    };
    private static final String[] TABS = {
        "vv.villagers", "vv.targets", "vv.regions", "vv.settings"
    };

    private final VerdictService s = VerdictService.INSTANCE;
    private int page;
    private boolean mobileDetailView;
    private int offset, detailOffset, sort, filter;
    private EditBox searchBox;
    private boolean restoreSearch;
    private int searchCursor;
    private String query = "", regionId = "";
    private String productQueryId = "";
    private int refresh;
    private int detailMax, lastClickY = -1, lastClickX;
    private final List<Icon> icons = new ArrayList<>();
    private final List<TargetIcon> targetIcons = new ArrayList<>();

    private record Icon(ItemData item, int x, int y) {}

    private record TargetIcon(ItemStack stack, int x, int y) {}

    public VerdictScreen() {
        super(Component.literal("Villager Verdict"));
    }

    private boolean isWide() {
        return width >= 640;
    }

    private int listTop() {
        if (page == 0 && (!regionId.isEmpty() || !productQueryId.isEmpty()) && !(!isWide() && mobileDetailView)) {
            return 94;
        }
        return 74;
    }

    private int listWidth() {
        return isWide() ? width / 2 - 16 : width - 24;
    }

    @Override
    protected void init() {
        int navTotal = width - 24;
        int bw = (navTotal - 9) / 4;
        for (int i = 0; i < TABS.length; i++) {
            int p = i;
            boolean active = i < 3 && page == i;
            int bx = 12 + i * (bw + 3);
            int w = i == 3 ? navTotal - 3 * (bw + 3) : bw;
            Component label = active
                ? Component.translatable(TABS[i]).withColor(0xFF55DD88)
                : Component.translatable(TABS[i]);
            button(label, bx, 24, w, () -> {
                if (p == 3) {
                    SettingsScreen.open(this);
                } else {
                    page = p;
                    mobileDetailView = false;
                    offset = detailOffset = 0;
                    rebuildWidgets();
                }
            });
        }

        if (page == 0) {
            if (!isWide() && mobileDetailView) {
                initMobileDetailBar();
            } else {
                initVillagerList();
                if (isWide()) initWideDetailButtons();
            }
        } else if (page == 1) {
            initTargetsPage();
        } else if (page == 2) {
            initRegionsPage();
        }

        initBottomBar();
    }

    private void initVillagerList() {
        int y = 49;
        int available = width - 24;
        int sw = available * 42 / 100 - 3;
        int sow = available * 32 / 100 - 3;
        int fw = available - sw - sow - 8;

        var search = new EditBox(font, 12, y, sw, 20, Component.translatable("vv.searchHint"));
        search.setValue(query);
        search.setHint(Component.translatable("vv.searchHint"));
        search.setTooltip(Tooltip.create(Component.translatable("vv.search")));
        search.setResponder(v -> {
            query = v;
            offset = 0;
        });
        addRenderableWidget(search);
        searchBox = search;
        if (restoreSearch) {
            setFocused(search);
            search.setCursorPosition(Math.min(searchCursor, query.length()));
            restoreSearch = false;
        }

        Component sortText = Component.translatable(
            "vv.sort.label", Component.translatable(SORTS[sort]).getString());
        var sortBtn = button(
            Component.literal(font.plainSubstrByWidth(sortText.getString(), sow - 8)),
            12 + sw + 4, y, sow,
            () -> {
                sort = (sort + 1) % SORTS.length;
                rebuildWidgets();
            });
        sortBtn.setTooltip(Tooltip.create(sortText));

        Component filterText = Component.translatable(FILTERS[filter]);
        var filterBtn = button(
            Component.literal(font.plainSubstrByWidth(filterText.getString(), fw - 8)),
            12 + sw + sow + 8, y, fw,
            () -> {
                filter = (filter + 1) % FILTERS.length;
                offset = 0;
                rebuildWidgets();
            });
        filterBtn.setTooltip(Tooltip.create(filterText));

        if (!regionId.isEmpty() || !productQueryId.isEmpty()) {
            button(
                Component.translatable("vv.clearProductFilter"),
                width - 88, 72, 76,
                () -> {
                    regionId = "";
                    productQueryId = "";
                    offset = 0;
                    rebuildWidgets();
                });
        }

        int top = listTop();
        int bottomReserve = isWide() ? 58 : 66;
        int rows = Math.max(1, (height - top - bottomReserve) / 24);
        var records = filtered();
        offset = Math.clamp(offset, 0, Math.max(0, records.size() - rows));
        int lw = listWidth();
        for (int i = offset; i < Math.min(records.size(), offset + rows); i++) {
            var v = records.get(i);
            var verdict = viewAnalysis().verdicts().get(v.uuid());
            boolean selectedRow = v.uuid().equals(s.selected);
            String rowText = villagerRowLabel(v, verdict, selectedRow);
            var b = button(
                Component.literal(font.plainSubstrByWidth(rowText, lw - 12))
                    .withColor(color(v, verdict)),
                12,
                top + (i - offset) * 24,
                lw,
                () -> {
                    s.selected = v.uuid();
                    detailOffset = 0;
                    if (!isWide()) mobileDetailView = true;
                    rebuildWidgets();
                });
            String statusLabel = Component.translatable(
                ClientDisplayText.uiStatusKey(s.displayStatus(v, verdict))).getString();
            String tip = statusLabel + " · (" + (int) v.x() + ", " + (int) v.y() + ", " + (int) v.z() + ")"
                + (verdict == null ? "" : "\n" + reason(verdict.reason()));
            b.setTooltip(Tooltip.create(Component.literal(tip)));
        }
    }

    private void initWideDetailButtons() {
        int x = width / 2 + 4;
        int w = width - x - 12;
        int bw = (w - 8) / 3;
        var editBtn = button(Component.translatable("vv.edit"), x, height - 54, bw, this::editSelected);
        editBtn.setTooltip(Tooltip.create(Component.translatable("vv.edit.help")));
        editBtn.active = s.record(s.selected) != null;

        var navBtn = button(Component.translatable("vv.navigate"), x + bw + 4, height - 54, bw,
            this::locateSelected);
        navBtn.setTooltip(Tooltip.create(Component.translatable("vv.navigate.help")));
        navBtn.active = s.record(s.selected) != null;

        var nextBtn = button(Component.translatable("vv.next"), x + 2 * (bw + 4), height - 54,
            w - 2 * (bw + 4), () -> {
                s.navigateNext();
                detailOffset = 0;
                rebuildWidgets();
            });
        nextBtn.setTooltip(Tooltip.create(Component.translatable("vv.next.help")));
    }

    private void initMobileDetailBar() {
        int available = width - 24;
        int backW = Math.min(82, available * 24 / 100);
        int rest = available - backW - 12;
        int bw = rest / 3;

        button(Component.translatable("vv.backToList"), 12, 49, backW, () -> {
            mobileDetailView = false;
            rebuildWidgets();
        });

        var editBtn = button(
            Component.literal(font.plainSubstrByWidth(
                Component.translatable("vv.edit").getString(), bw - 6)),
            12 + backW + 4, 49, bw, this::editSelected);
        editBtn.setTooltip(Tooltip.create(Component.translatable("vv.edit.help")));
        editBtn.active = s.record(s.selected) != null;

        var navBtn = button(
            Component.literal(font.plainSubstrByWidth(
                Component.translatable("vv.navigate").getString(), bw - 6)),
            12 + backW + bw + 8, 49, bw, this::locateSelected);
        navBtn.setTooltip(Tooltip.create(Component.translatable("vv.navigate.help")));
        navBtn.active = s.record(s.selected) != null;

        var nextBtn = button(
            Component.literal(font.plainSubstrByWidth(
                Component.translatable("vv.next").getString(), rest - 2 * bw - 6)),
            12 + backW + 2 * bw + 12, 49, rest - 2 * bw,
            () -> {
                s.navigateNext();
                detailOffset = 0;
                rebuildWidgets();
            });
        nextBtn.setTooltip(Tooltip.create(Component.translatable("vv.next.help")));
    }

    private void initTargetsPage() {
        button(Component.translatable("vv.add"), 12, 49, 80, () -> openTargetEditor(null));
        String scopeName = currentRegion() == null
            ? Component.translatable("vv.target.scopeGlobal").getString()
            : Component.translatable("vv.target.scopeRegion", currentRegion().name()).getString();
        button(
            Component.translatable("vv.target.scope", scopeName),
            98, 49, Math.max(80, width - 110),
            () -> {
                var ids = new ArrayList<String>();
                ids.add("");
                s.regions.forEach(r -> ids.add(r.id()));
                regionId = ids.get((ids.indexOf(regionId) + 1) % ids.size());
                productQueryId = "";
                offset = 0;
                rebuildWidgets();
            });

        var targets = currentTargets();
        int rows = Math.max(1, (height - 114) / 24);
        offset = Math.clamp(offset, 0, Math.max(0, targets.size() - rows));
        for (int i = offset; i < Math.min(targets.size(), offset + rows); i++) {
            var t = targets.get(i);
            int rowY = 76 + (i - offset) * 24;
            String summary = ClientDisplayText.targetSummary(t);
            button(
                Component.literal(font.plainSubstrByWidth(summary, width - 144)),
                34, rowY, width - 132,
                () -> openTargetEditor(t));
            button(Component.translatable("vv.target.viewVillagers"), width - 94, rowY, 82, () -> {
                productQueryId = t.id();
                page = 0;
                mobileDetailView = false;
                query = "";
                filter = 0;
                offset = detailOffset = 0;
                s.selected = filtered().stream().map(VillagerRecord::uuid).findFirst().orElse(null);
                rebuildWidgets();
            });
        }
    }

    private void initRegionsPage() {
        button(Component.translatable("vv.add"), 12, 49, 80, () -> openRegionEditor(null));
        int rows = Math.max(1, (height - 114) / 24);
        offset = Math.clamp(offset, 0, Math.max(0, s.regions.size() - rows));
        int act1 = 62;
        int act2 = 62;
        int act3 = 54;
        int actionsTotal = act1 + act2 + act3 + 12;

        for (int i = offset; i < Math.min(s.regions.size(), offset + rows); i++) {
            var r = s.regions.get(i);
            int rowY = 76 + (i - offset) * 24;
            var result = s.regional.get(r.id());
            String dimName = ClientDisplayText.dimensionDisplayName(r.dimension());
            String label = result == null
                ? r.name() + " · " + dimName
                : Component.translatable("vv.regionSummary",
                    r.name() + " (" + dimName + ")",
                    result.recommended().size(),
                    result.verdicts().size()).getString();
            int mainW = Math.max(60, width - 24 - actionsTotal);
            button(
                Component.literal(font.plainSubstrByWidth(label, mainW - 10)).withColor(r.color()),
                12, rowY, mainW,
                () -> {
                    regionId = r.id();
                    productQueryId = "";
                    page = 0;
                    mobileDetailView = false;
                    offset = 0;
                    rebuildWidgets();
                });

            int rx = 12 + mainW + 4;
            button(Component.translatable("vv.regionViewVillagers"), rx, rowY, act1, () -> {
                regionId = r.id();
                productQueryId = "";
                page = 0;
                mobileDetailView = false;
                offset = 0;
                rebuildWidgets();
            });
            button(Component.translatable("vv.regionTargets"), rx + act1 + 4, rowY, act2, () -> {
                regionId = r.id();
                productQueryId = "";
                page = 1;
                offset = 0;
                rebuildWidgets();
            });
            button(Component.translatable("vv.regionEdit"), rx + act1 + act2 + 8, rowY, act3,
                () -> openRegionEditor(r));
        }
    }

    private void initBottomBar() {
        int y = height - 26;
        int doneW = 64;
        int cancelW = s.navigationActive() ? Math.min(100, (width - 24) / 4) : 0;
        int avail = width - 24 - doneW - 8 - (cancelW > 0 ? cancelW + 4 : 0);

        int scanW = Math.min(92, Math.max(40, avail * 28 / 100));
        var scanBtn = button(Component.translatable("vv.scan"), 12, y, scanW, () -> {
            s.scan();
            rebuildWidgets();
        });
        scanBtn.setTooltip(Tooltip.create(Component.translatable("vv.scan.help")));

        boolean running = s.probe.running();
        int probeW = Math.min(156, Math.max(40, avail - scanW - 4));
        Component probeLabel = Component.translatable(running ? "vv.probeRunning" : "vv.probeStart")
            .withColor(running ? 0xFF55DD88 : 0xFFFFFFFF);
        var probeBtn = button(probeLabel, 12 + scanW + 4, y, probeW, () -> {
            SettingsScreen.toggleProbe(ShortcutActions::show);
            rebuildWidgets();
        });
        probeBtn.setTooltip(Tooltip.create(Component.translatable("vv.probe.help")));

        if (s.navigationActive()) {
            var cancelBtn = button(Component.translatable("vv.cancelNavigate"),
                width - doneW - 16 - cancelW, y, cancelW, () -> {
                    s.cancelNavigation();
                    rebuildWidgets();
                });
            cancelBtn.setTooltip(Tooltip.create(Component.translatable("vv.cancelNavigate.help")));
        }
        button(Component.translatable("gui.done"), width - doneW - 12, y, doneW, this::onClose);
    }

    private String villagerRowLabel(VillagerRecord v, Verdict verdict, boolean selectedRow) {
        StringBuilder sb = new StringBuilder();
        if (selectedRow) sb.append("▸ ");
        sb.append(s.displayStatus(v, verdict).symbol())
            .append(" ")
            .append(profession(v))
            .append(" Lv.")
            .append(v.level())
            .append(" · ")
            .append((int) Math.sqrt(s.distanceSquared(v)))
            .append("m");
        if (v.snapshot() != null && !v.snapshot().trades().isEmpty()) {
            var entries = ProfessionHighlights.select(v.profession(), v.snapshot().trades());
            if (!entries.isEmpty()) {
                String highlight = switch (entries.getFirst()) {
                    case ProfessionHighlights.EnchantmentEntry ench ->
                        ClientDisplayText.enchantmentDisplayName(ench.id())
                            + (ench.level() > 1 ? " " + ench.level() : "");
                    case ProfessionHighlights.ItemEntry prod ->
                        ItemCodecAdapter.displayName(prod.item());
                };
                sb.append(" · ").append(highlight);
            }
        }
        return sb.toString();
    }

    private void locateSelected() {
        s.startNavigation(s.selected);
        minecraft.gui.setScreen(null);
    }

    private AnalysisResult viewAnalysis() {
        return regionId.isEmpty() ? s.analysis : s.regional.getOrDefault(regionId, AnalysisResult.empty());
    }

    private TradingHallRegion currentRegion() {
        return s.regions.stream().filter(r -> r.id().equals(regionId)).findFirst().orElse(null);
    }

    private List<TargetTrade> currentTargets() {
        var r = currentRegion();
        return r == null ? s.targets : r.targets();
    }

    private void updateTarget(String id, TargetTrade t) {
        var list = new ArrayList<>(currentTargets());
        list.removeIf(a -> a.id().equals(id));
        if (t != null) list.add(t);
        else if (id.equals(productQueryId)) productQueryId = "";
        var r = currentRegion();
        if (r == null) {
            s.targets = list;
        } else {
            var n = new TradingHallRegion(
                r.id(), r.name(), r.dimension(),
                r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ(),
                list, r.color());
            s.regions.removeIf(a -> a.id().equals(r.id()));
            s.regions.add(n);
        }
        s.changed();
    }

    private Button button(Component text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(
            Button.builder(text, b -> action.run()).bounds(x, y, Math.max(20, w), 20).build());
    }

    @Override
    public void tick() {
        if (++refresh % 20 == 0) {
            if (page == 0 && !productQueryId.isEmpty()) {
                var matches = filtered();
                if (matches.stream().noneMatch(v -> v.uuid().equals(s.selected)))
                    s.selected = matches.isEmpty() ? null : matches.getFirst().uuid();
            }
            restoreSearch = searchBox != null && searchBox.isFocused();
            if (restoreSearch) searchCursor = searchBox.getCursorPosition();
            rebuildWidgets();
        }
    }

    private List<VillagerRecord> filtered() {
        var productQuery = currentTargets().stream().filter(t -> t.id().equals(productQueryId)).findFirst().orElse(null);
        var list = s.records().stream()
            .filter(s::enabled)
            .filter(v -> regionId.isEmpty()
                || s.regions.stream()
                    .filter(r -> r.id().equals(regionId))
                    .anyMatch(r -> r.contains(v)))
            .filter(v -> filter == 0
                || s.displayStatus(v, viewAnalysis().verdicts().get(v.uuid())).ordinal() == filter - 1)
            .filter(v -> productQuery == null || productQuery.matches(v))
            .filter(v -> search(v).contains(query.toLowerCase(Locale.ROOT)))
            .toList();

        Comparator<VillagerRecord> order = switch (sort) {
            case 1 -> Comparator.comparing(this::priceGroup)
                .thenComparing((a, b) -> TradeComparison.vectorOrder(priceVector(a), priceVector(b)));
            case 2 -> Comparator.<VillagerRecord>comparingInt(this::enchantment).reversed();
            case 3 -> Comparator.comparingDouble(s::distanceSquared);
            case 4 -> Comparator.comparing(VillagerRecord::profession);
            default -> Comparator.<VillagerRecord>comparingInt(v -> v.fixed() ? 0 : 1)
                .thenComparingInt(v -> viewAnalysis().recommended().contains(v.uuid()) ? 0 : 1)
                .thenComparing(Comparator.<VillagerRecord>comparingInt(
                    v -> viewAnalysis().verdicts().containsKey(v.uuid())
                        ? viewAnalysis().verdicts().get(v.uuid()).uniqueTrades()
                        : 0).reversed())
                .thenComparing(Comparator.<VillagerRecord>comparingInt(
                    v -> v.snapshot() == null ? 0 : ProfessionHighlights.trades(v).size()).reversed());
        };
        return list.stream().sorted(order.thenComparing(v -> v.uuid().toString())).toList();
    }

    private String search(VillagerRecord v) {
        return (profession(v)
            + (minecraft.level == null || minecraft.level.getEntity(v.uuid()) == null ? ""
                : minecraft.level.getEntity(v.uuid()).getName().getString())
            + v.profession()
            + v.level()
            + v.note()
            + v.tags()
            + (v.snapshot() == null
                ? ""
                : ProfessionHighlights.trades(v).stream()
                    .map(t -> t.toString()
                        + ItemCodecAdapter.displayName(t.output())
                        + ItemCodecAdapter.enchantmentText(t.output()))
                    .toList()))
            .toLowerCase(Locale.ROOT);
    }

    private Map<String, Double> priceVector(VillagerRecord v) {
        if (v.snapshot() == null) return Map.of();
        String group = priceGroup(v);
        return ProfessionHighlights.trades(v).stream()
            .filter(t -> new TreeSet<>(t.cost(false).keySet()).toString().equals(group))
            .map(t -> t.cost(false))
            .min(TradeComparison::vectorOrder)
            .orElse(Map.of());
    }

    private String priceGroup(VillagerRecord v) {
        return v.snapshot() == null
            ? "~"
            : ProfessionHighlights.trades(v).stream()
                .map(t -> new TreeSet<>(t.cost(false).keySet()).toString())
                .sorted()
                .findFirst()
                .orElse("~");
    }

    private int enchantment(VillagerRecord v) {
        return v.snapshot() == null
            ? 0
            : ProfessionHighlights.trades(v).stream()
                .flatMap(t -> t.output().enchantments().values().stream())
                .mapToInt(i -> i)
                .max()
                .orElse(0);
    }

    private String profession(VillagerRecord v) {
        return v.baby()
            ? Component.translatable("vv.baby").getString()
            : Component.translatable(
                "entity.minecraft.villager."
                    + v.profession().substring(v.profession().indexOf(':') + 1))
                .getString();
    }

    private int color(VillagerRecord record, Verdict verdict) {
        return switch (s.displayStatus(record, verdict)) {
            case KEEP -> s.settings.green();
            case DISCARD -> s.settings.red();
            default -> s.settings.yellow();
        };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);

        // Highlight active top tab cleanly with a bottom accent line.
        int navTotal = width - 24;
        int bw = (navTotal - 9) / 4;
        if (page >= 0 && page < 3) {
            int bx = 12 + page * (bw + 3);
            g.fill(bx + 4, 44, bx + bw - 4, 46, 0xFF55DD88);
        }

        icons.clear();
        targetIcons.clear();

        if (page == 0) {
            if ((!regionId.isEmpty() || !productQueryId.isEmpty()) && !(!isWide() && mobileDetailView)) {
                var productQuery = currentTargets().stream().filter(t -> t.id().equals(productQueryId)).findFirst().orElse(null);
                String caption = productQuery != null ? ClientDisplayText.targetSummary(productQuery)
                    : currentRegion() == null ? "" : currentRegion().name();
                g.text(font, font.plainSubstrByWidth(caption, width - 116), 12, 78, 0xFF9FB8D8);
            }
            if (isWide()) {
                int panelTop = listTop();
                details(g, width / 2 + 4, panelTop, width / 2 - 16, height - panelTop - 62);
            } else if (mobileDetailView) {
                details(g, 12, 74, width - 24, height - 108);
            } else if (filtered().isEmpty()) {
                ClientDisplayText.drawPanel(g, 12, listTop(), width - 24, 68);
                int ey = listTop() + 10;
                for (var line : font.split(Component.translatable("vv.emptyVillagers"), width - 40)) {
                    g.text(font, line, 20, ey, 0xFF9FB8D8);
                    ey += 11;
                }
            }
            if (isWide() && filtered().isEmpty()) {
                int top = listTop();
                ClientDisplayText.drawPanel(g, 12, top, listWidth(), 72);
                int ey = top + 10;
                for (var line : font.split(Component.translatable("vv.emptyVillagers"), listWidth() - 16)) {
                    g.text(font, line, 20, ey, 0xFF9FB8D8);
                    ey += 11;
                }
            }
            if (!(!isWide() && mobileDetailView)) {
                var st = s.stats();
                String statsText = Component.translatable(
                    "vv.stats",
                    st.get("success"),
                    st.get("pending"),
                    st.get("failed")).getString();
                if (st.getOrDefault("active", 0L) > 0L) {
                    statsText += Component.translatable("vv.statsReading").getString();
                }
                String visible = font.plainSubstrByWidth(statsText, width - 24);
                int statsY = height - 42;
                g.fill(10, statsY - 2, 16 + font.width(visible), statsY + 11, 0xA81D2534);
                g.text(font, visible, 13, statsY, 0xFFBBD0E8);
            }
        } else if (page == 1) {
            var targets = currentTargets();
            if (targets.isEmpty()) {
                ClientDisplayText.drawPanel(g, 12, 76, width - 24, 68);
                int ey = 86;
                for (var line : font.split(Component.translatable("vv.emptyTargets"), width - 40)) {
                    g.text(font, line, 20, ey, 0xFFBBD0E8);
                    ey += 11;
                }
            } else {
                int rows = Math.max(1, (height - 114) / 24);
                for (int i = offset; i < Math.min(targets.size(), offset + rows); i++) {
                    ItemStack stack = ClientDisplayText.itemStack(targets.get(i).item());
                    if (!stack.isEmpty()) {
                        targetIcons.add(new TargetIcon(stack, 14, 78 + (i - offset) * 24));
                    }
                }
            }
        } else if (page == 2) {
            if (s.regions.isEmpty()) {
                ClientDisplayText.drawPanel(g, 12, 76, width - 24, 64);
                int ey = 86;
                for (var line : font.split(Component.translatable("vv.emptyRegions"), width - 40)) {
                    g.text(font, line, 20, ey, 0xFFBBD0E8);
                    ey += 11;
                }
            }
        }

        super.extractRenderState(g, mx, my, delta);

        for (var i : icons) {
            var stack = ItemCodecAdapter.decode(i.item);
            g.item(stack, i.x, i.y);
            g.itemDecorations(font, stack, i.x, i.y);
            if (mx >= i.x && mx < i.x + 16 && my >= i.y && my < i.y + 16) {
                g.setTooltipForNextFrame(font, stack, mx, my);
            }
        }
        for (var ti : targetIcons) {
            g.item(ti.stack(), ti.x(), ti.y());
        }
        if (!s.error.isEmpty()) {
            g.text(font, font.plainSubstrByWidth(s.error, width - 24), 12, height - 56, 0xFFFF6677);
        }
    }

    private void details(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        var v = s.record(s.selected);
        g.enableScissor(x, y, x + w, y + h);
        ClientDisplayText.drawPanel(g, x, y, w, h);
        if (v == null || !s.enabled(v)) {
            int sy = y + 10;
            for (var line : font.split(Component.translatable("vv.select"), Math.max(40, w - 16))) {
                g.text(font, line, x + 8, sy, 0xFFBBD0E8);
                sy += 12;
            }
            g.disableScissor();
            detailMax = 0;
            return;
        }

        int yy = y + 8 - detailOffset;
        int innerW = Math.max(24, w - 16);

        // 1. Header: profession, level, manual decision mode
        String header = profession(v) + " · Lv." + v.level() + " · "
            + Component.translatable("vv.manual." + v.manual()).getString();
        yy = drawWrappedLine(g, header, x + 8, yy, innerW, 0xFFFFFFFF);

        // 2. Position, load status, and relative last-seen time (no raw UUID or ISO UTC!)
        String locLine = "(" + (int) v.x() + ", " + (int) v.y() + ", " + (int) v.z() + ") · "
            + Component.translatable("vv.loaded").getString()
            + " · " + Component.translatable(
                "vv.lastSeen", ClientDisplayText.relativeTimeText(v.lastSeen())).getString();
        yy = drawWrappedLine(g, locLine, x + 8, yy, innerW, 0xFF9FB8D8);

        // 3. Notes & tags (shown ONLY when non-empty; never render [])
        if (!v.note().isBlank()) {
            yy = drawWrappedLine(g,
                Component.translatable("vv.note").getString() + "：" + v.note(),
                x + 8, yy, innerW, 0xFFE6EDF7);
        }
        if (!v.tags().isEmpty()) {
            yy = drawWrappedLine(g,
                String.join(", ", v.tags()),
                x + 8, yy, innerW, 0xFF8CA4C4);
        }

        // 4. Verdict & plain-language reasoning
        yy += 2;
        var verdict = viewAnalysis().verdicts().get(v.uuid());
        var displayStatus = s.displayStatus(v, verdict);
        int statusColor = color(v, verdict);
        String statusText = Component.translatable(ClientDisplayText.uiStatusKey(displayStatus)).getString();
        yy = drawWrappedLine(g, "● " + statusText, x + 8, yy, innerW, statusColor);

        if (verdict != null) {
            yy = drawWrappedLine(g, reason(verdict.reason()), x + 8, yy, innerW, 0xFFE6EDF7);
        }

        var snap = v.snapshot();
        if (snap == null) {
            yy = drawWrappedLine(g, Component.translatable("vv.uninspected").getString(),
                x + 8, yy, innerW, 0xFFFFCC55);
        } else {
            String syncText = Component.translatable(
                "vv.syncedAt", ClientDisplayText.relativeTimeText(snap.checkedAt())).getString();
            yy = drawWrappedLine(g, syncText, x + 8, yy, innerW, 0xFF9FB8D8);
        }

        // 5. Trades with icons & plain pricing
        if (snap != null) {
            yy += 3;
            for (var t : snap.trades()) {
                if (yy >= y && yy + 18 < y + h) {
                    icons.add(new Icon(t.actualInput(), x + 8, yy));
                    if (t.secondInput() != null) icons.add(new Icon(t.secondInput(), x + 28, yy));
                    icons.add(new Icon(t.output(), x + (t.secondInput() == null ? 44 : 64), yy));
                }
                g.text(font, "→", x + (t.secondInput() == null ? 29 : 49), yy + 4, 0xFF9FB8D8);
                yy += 20;

                String costNow = t.actualInput().count() + " " + ItemCodecAdapter.displayName(t.actualInput());
                if (t.secondInput() != null) {
                    costNow += " + " + t.secondInput().count() + " " + ItemCodecAdapter.displayName(t.secondInput());
                }
                String outName = ItemCodecAdapter.displayName(t.output());
                String ench = ItemCodecAdapter.enchantmentText(t.output());
                if (!ench.isEmpty()) outName += " (" + ench + ")";

                String tradeLine = t.actualInput().count() < t.baseInput().count()
                    ? Component.translatable("vv.tradeLineDiscount",
                        costNow,
                        t.baseInput().count(),
                        outName,
                        t.output().count(),
                        t.maxUses() - t.uses(),
                        t.maxUses()).getString()
                    : Component.translatable("vv.tradeLineNormal",
                        costNow,
                        outName,
                        t.output().count(),
                        t.maxUses() - t.uses(),
                        t.maxUses()).getString();
                yy = drawWrappedLine(g, tradeLine, x + 8, yy, innerW, 0xFFFFFFFF);
            }
        }

        // 6. Replacement suggestions (clickable!)
        if (verdict != null && !verdict.replacements().isEmpty()) {
            yy += 3;
            for (var r : verdict.replacements()) {
                String deltaSummary = summarizePriceDelta(r);
                String repText = Component.translatable("vv.replacement.line",
                    ClientDisplayText.productSummary(r.original().output()),
                    ClientDisplayText.productSummary(r.alternative().output()),
                    (int) r.x(), (int) r.y(), (int) r.z(), deltaSummary).getString();
                int begin = yy;
                yy = drawWrappedLine(g, repText, x + 8, yy, innerW, 0xFFB8A8FF);
                if (lastClickY >= begin && lastClickY < yy && lastClickX >= x && lastClickX < x + w) {
                    s.selected = r.replacement();
                    detailOffset = 0;
                    lastClickY = -1;
                }
            }
        }

        // 7. Unmet gaps translated cleanly
        for (var gap : viewAnalysis().gaps()) {
            String msg = Component.translatable("vv.gap").getString()
                + ": " + ClientDisplayText.formatGap(gap);
            yy = drawWrappedLine(g, msg, x + 8, yy, innerW, 0xFFFFCC55);
        }

        g.disableScissor();
        detailMax = Math.max(0, yy + detailOffset - y - h + 10);
    }

    private int drawWrappedLine(GuiGraphicsExtractor g, String text, int x, int y, int maxW, int color) {
        for (var wrapped : font.split(Component.literal(text), maxW)) {
            g.text(font, wrapped, x, y, color);
            y += 12;
        }
        return y;
    }

    private String summarizePriceDelta(ComparisonResult r) {
        if (r.actualDifference().isEmpty()) {
            return Component.translatable("vv.replacement.samePrice").getString();
        }
        double sum = r.actualDifference().values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum) < 0.01) {
            return Component.translatable("vv.replacement.samePrice").getString();
        }
        String formatted = UiFormatting.formatDecimal(Math.abs(sum));
        return sum < 0
            ? Component.translatable("vv.replacement.cheaper", formatted).getString()
            : Component.translatable("vv.replacement.pricier", formatted).getString();
    }

    private String reason(String value) {
        return value
            .replace("manual protection / keep", Component.translatable("vv.reason.manual").getString())
            .replace("coverage / unique value", Component.translatable("vv.reason.coverage").getString())
            .replace("all known trades have replacements", Component.translatable("vv.reason.redundant").getString())
            .replace("no signature goods", Component.translatable("vv.reason.noSignature").getString());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        lastClickX = (int) e.x();
        lastClickY = (int) e.y();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double a, double b) {
        boolean onDetails = (page == 0 && !isWide() && mobileDetailView)
            || (page == 0 && isWide() && x > width / 2.0);
        if (onDetails) {
            detailOffset = Math.clamp(detailOffset - (int) (b * 24), 0, detailMax);
        } else {
            offset = Math.max(0, offset - (int) Math.signum(b));
            rebuildWidgets();
        }
        return true;
    }

    private void editSelected() {
        var v = s.record(s.selected);
        if (v == null) return;
        minecraft.gui.setScreen(new VillagerEditScreen(this, v, updated -> s.edit(updated)));
    }

    private void openTargetEditor(TargetTrade t) {
        minecraft.gui.setScreen(new TargetEditorScreen(
            this,
            t,
            saved -> updateTarget(saved.id(), saved),
            t == null ? null : () -> updateTarget(t.id(), null)));
    }

    private void openRegionEditor(TradingHallRegion r) {
        String dim = s.identity() == null ? "minecraft:overworld" : s.identity().dimension();
        minecraft.gui.setScreen(new RegionEditorScreen(
            this,
            r,
            dim,
            saved -> {
                s.regions.removeIf(a -> a.id().equals(saved.id()));
                s.regions.add(saved);
                s.changed();
            },
            r == null
                ? null
                : () -> {
                    s.regions.removeIf(a -> a.id().equals(r.id()));
                    if (r.id().equals(regionId)) regionId = "";
                    s.changed();
                }));
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN
                || e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) {
            return mouseScrolled(
                !isWide() && mobileDetailView ? 0 : width,
                listTop(),
                0,
                e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN ? -5 : 5);
        }
        return super.keyPressed(e);
    }
}
