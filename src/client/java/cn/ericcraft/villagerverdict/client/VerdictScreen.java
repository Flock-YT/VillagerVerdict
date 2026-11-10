package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.analysis.ProfessionHighlights;
import cn.ericcraft.villagerverdict.analysis.TradeComparison;
import cn.ericcraft.villagerverdict.model.AnalysisResult;
import cn.ericcraft.villagerverdict.model.AnalysisResult.Verdict;
import cn.ericcraft.villagerverdict.model.ComparisonResult;
import cn.ericcraft.villagerverdict.model.DisplayStatus;
import cn.ericcraft.villagerverdict.model.ItemData;
import cn.ericcraft.villagerverdict.model.TargetTrade;
import cn.ericcraft.villagerverdict.model.UiFormatting;
import cn.ericcraft.villagerverdict.model.VillagerRecord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Main management workspace: three real tabs plus a separate settings button, a context-aware
 * "next step" guide line, status filter chips with counts, and a getting-started panel for new players.
 */
public final class VerdictScreen extends WorldScreen {
    private static final String[] SORTS = {
        "vv.value", "vv.price", "vv.enchantment", "vv.distance", "vv.profession"
    };
    private static final String[] FILTERS = {
        "vv.filter.all", "vv.filter.keep", "vv.filter.discard", "vv.filter.unscanned"
    };
    private static final String[] FILTER_HELP = {
        "vv.filter.all.help", "vv.start.keep", "vv.start.discard", "vv.start.unscanned"
    };
    private static final String[] TABS = {"vv.villagers", "vv.targets"};

    /** Top of the navigation row. */
    private static final int NAV_Y = 20;
    /** Baseline of the one-line "next step" guide. */
    private static final int GUIDE_Y = 46;
    /** First row of page content (toolbars). */
    private static final int CONTENT_Y = 58;

    private static final int COLOR_TEXT = 0xFFE6EDF7;
    private static final int COLOR_MUTED = 0xFF9FB8D8;
    private static final int COLOR_FAINT = 0xFF8CA4C4;
    private static final int COLOR_ACCENT = 0xFF55DD88;
    private static final int COLOR_GUIDE = 0xFFFFE08A;
    private static final int COLOR_WARN = 0xFFFFCC55;
    private static final int COLOR_ERROR = 0xFFFF6677;

    private final VerdictService s = VerdictService.INSTANCE;
    private int page;
    private boolean mobileDetailView;
    private int offset, detailOffset, sort, filter;
    private EditBox searchBox;
    private boolean restoreSearch;
    private int searchCursor;
    private String query = "";
    private String productQueryId = "";
    private int refresh;
    private int detailMax, lastClickY = -1, lastClickX;
    private final List<Icon> icons = new ArrayList<>();
    private final List<TargetIcon> targetIcons = new ArrayList<>();

    private record Icon(ItemData item, int x, int y) {}

    private record TargetIcon(ItemStack stack, int x, int y) {}

    private record Guide(String text, int color) {}

    public VerdictScreen() {
        super(Component.translatable("vv.title"));
    }

    // ---------------------------------------------------------------------------------------------
    // Layout helpers
    // ---------------------------------------------------------------------------------------------

    private boolean isWide() {
        return width >= 640;
    }

    private boolean mobileDetail() {
        return !isWide() && mobileDetailView;
    }

    private boolean filterActive() {
        return !productQueryId.isEmpty();
    }

    /** Bottom of the villager toolbar: one row on wide screens, two rows on narrow screens. */
    private int toolbarBottom() {
        return CONTENT_Y + (isWide() ? 24 : 46);
    }

    private int listTop() {
        if (page != 0) return CONTENT_Y + 24;
        if (mobileDetail()) return CONTENT_Y + 24;
        return toolbarBottom() + (filterActive() ? 22 : 0);
    }

    private int listBottom() {
        return height - (isWide() ? 54 : 44);
    }

    private int listWidth() {
        return isWide() ? width / 2 - 16 : width - 24;
    }

    private int settingsButtonWidth() {
        int textW = font.width(Component.translatable("vv.settings")) + 20;
        return Math.clamp(textW, 60, 110);
    }

    private int tabWidth() {
        int area = width - 24 - settingsButtonWidth() - 12;
        return Math.max(50, Math.min(120, (area - 3 * (TABS.length - 1)) / TABS.length));
    }

    // ---------------------------------------------------------------------------------------------
    // Widgets
    // ---------------------------------------------------------------------------------------------

    @Override
    protected void init() {
        int tabW = tabWidth();
        for (int i = 0; i < TABS.length; i++) {
            int p = i;
            Component label = page == i
                ? Component.translatable(TABS[i]).withColor(COLOR_ACCENT)
                : Component.translatable(TABS[i]);
            button(label, 12 + i * (tabW + 3), NAV_Y, tabW, () -> {
                page = p;
                mobileDetailView = false;
                offset = detailOffset = 0;
                rebuildWidgets();
            });
        }
        int setW = settingsButtonWidth();
        var settingsBtn = button(Component.translatable("vv.settings"), width - 12 - setW, NAV_Y, setW,
            () -> SettingsScreen.open(this));
        settingsBtn.setTooltip(Tooltip.create(Component.translatable("vv.settings.help")));

        if (page == 0) {
            if (mobileDetail()) {
                initMobileDetailBar();
            } else {
                initVillagerList();
                if (isWide()) initWideDetailButtons();
            }
        } else if (page == 1) {
            initTargetsPage();
        }

        initBottomBar();
    }

    private void initVillagerList() {
        int available = width - 24;
        int searchW, sortW, chipsX, chipsY, chipsW;
        if (isWide()) {
            searchW = available * 30 / 100;
            sortW = available * 18 / 100;
            chipsX = 12 + searchW + sortW + 8;
            chipsY = CONTENT_Y;
            chipsW = available - searchW - sortW - 8;
        } else {
            sortW = available * 36 / 100;
            searchW = available - sortW - 4;
            chipsX = 12;
            chipsY = CONTENT_Y + 22;
            chipsW = available;
        }

        var search = new EditBox(font, 12, CONTENT_Y, searchW, 20, Component.translatable("vv.searchHint"));
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
            Component.literal(font.plainSubstrByWidth(sortText.getString(), sortW - 8)),
            12 + searchW + 4, CONTENT_Y, sortW,
            () -> {
                sort = (sort + 1) % SORTS.length;
                rebuildWidgets();
            });
        sortBtn.setTooltip(Tooltip.create(Component.translatable("vv.sort.help")));

        // Status filter chips with live counts.
        long[] counts = statusCounts();
        int chipW = (chipsW - 3 * 3) / 4;
        for (int i = 0; i < FILTERS.length; i++) {
            int f = i;
            boolean selected = filter == i;
            String text = (selected ? "● " : "")
                + Component.translatable(FILTERS[i]).getString() + " " + counts[i];
            int w = i == 3 ? chipsW - 3 * (chipW + 3) : chipW;
            var chip = button(
                Component.literal(font.plainSubstrByWidth(text, w - 6))
                    .withColor(selected ? COLOR_ACCENT : COLOR_TEXT),
                chipsX + i * (chipW + 3), chipsY, w,
                () -> {
                    filter = f;
                    offset = 0;
                    rebuildWidgets();
                });
            chip.setTooltip(Tooltip.create(Component.translatable(FILTER_HELP[i])));
        }

        if (filterActive()) {
            int clearW = Math.min(110, font.width(Component.translatable("vv.clearProductFilter")) + 16);
            button(Component.translatable("vv.clearProductFilter"),
                width - 12 - clearW, toolbarBottom(), clearW,
                () -> {
                    productQueryId = "";
                    offset = 0;
                    rebuildWidgets();
                });
        }

        int top = listTop();
        int rows = Math.max(1, (listBottom() - top) / 24);
        var records = filtered();
        offset = Math.clamp(offset, 0, Math.max(0, records.size() - rows));
        int lw = listWidth();
        for (int i = offset; i < Math.min(records.size(), offset + rows); i++) {
            var v = records.get(i);
            var verdict = s.analysis.verdicts().get(v.uuid());
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
            String tip = ClientDisplayText.statusLabel(s.displayStatus(v, verdict))
                + " · (" + (int) v.x() + ", " + (int) v.y() + ", " + (int) v.z() + ")"
                + (verdict == null ? "" : "\n" + reason(verdict.reason()));
            b.setTooltip(Tooltip.create(Component.literal(tip)));
        }
    }

    private void initWideDetailButtons() {
        int x = width / 2 + 4;
        int w = width - x - 12;
        int bw = (w - 8) / 3;
        int y = height - 50;
        boolean hasSelection = s.record(s.selected) != null;

        var navBtn = button(fit("vv.navigate", bw), x, y, bw, this::locateSelected);
        navBtn.setTooltip(Tooltip.create(Component.translatable("vv.navigate.help")));
        navBtn.active = hasSelection;

        var editBtn = button(fit("vv.edit", bw), x + bw + 4, y, bw, this::editSelected);
        editBtn.setTooltip(Tooltip.create(Component.translatable("vv.edit.help")));
        editBtn.active = hasSelection;

        var nextBtn = button(fit("vv.next", w - 2 * (bw + 4)), x + 2 * (bw + 4), y, w - 2 * (bw + 4), () -> {
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
        boolean hasSelection = s.record(s.selected) != null;

        button(Component.translatable("vv.backToList"), 12, CONTENT_Y, backW, () -> {
            mobileDetailView = false;
            rebuildWidgets();
        });

        var navBtn = button(fit("vv.navigate", bw), 12 + backW + 4, CONTENT_Y, bw, this::locateSelected);
        navBtn.setTooltip(Tooltip.create(Component.translatable("vv.navigate.help")));
        navBtn.active = hasSelection;

        var editBtn = button(fit("vv.edit", bw), 12 + backW + bw + 8, CONTENT_Y, bw, this::editSelected);
        editBtn.setTooltip(Tooltip.create(Component.translatable("vv.edit.help")));
        editBtn.active = hasSelection;

        var nextBtn = button(fit("vv.next", rest - 2 * bw), 12 + backW + 2 * bw + 12, CONTENT_Y, rest - 2 * bw,
            () -> {
                s.navigateNext();
                detailOffset = 0;
                rebuildWidgets();
            });
        nextBtn.setTooltip(Tooltip.create(Component.translatable("vv.next.help")));
    }

    private void initTargetsPage() {
        int addW = Math.min(170, font.width(Component.translatable("vv.target.add")) + 20);
        var addBtn = button(Component.translatable("vv.target.add"), 12, CONTENT_Y, addW, () -> openTargetEditor(null));
        addBtn.setTooltip(Tooltip.create(Component.translatable("vv.target.add.help")));

        var targets = s.targets;
        int top = listTop();
        int rows = Math.max(1, (height - top - 32) / 24);
        offset = Math.clamp(offset, 0, Math.max(0, targets.size() - rows));
        int viewW = Math.min(110, font.width(Component.translatable("vv.target.viewVillagers")) + 16);
        for (int i = offset; i < Math.min(targets.size(), offset + rows); i++) {
            var t = targets.get(i);
            int rowY = top + (i - offset) * 24;
            String summary = ClientDisplayText.targetSummary(t);
            int mainW = width - 24 - 22 - viewW - 4;
            var row = button(
                Component.literal(font.plainSubstrByWidth(summary, mainW - 10)),
                34, rowY, mainW,
                () -> openTargetEditor(t));
            row.setTooltip(Tooltip.create(Component.translatable("vv.target.editHint")));
            var view = button(Component.translatable("vv.target.viewVillagers"), width - 12 - viewW, rowY, viewW, () -> {
                productQueryId = t.id();
                page = 0;
                mobileDetailView = false;
                query = "";
                filter = 0;
                offset = detailOffset = 0;
                s.selected = filtered().stream().map(VillagerRecord::uuid).findFirst().orElse(null);
                rebuildWidgets();
            });
            view.setTooltip(Tooltip.create(Component.translatable("vv.target.viewVillagers.help")));
        }
    }


    private void initBottomBar() {
        int y = height - 26;
        int doneW = 64;
        int cancelW = s.navigationActive() ? Math.min(100, (width - 24) / 4) : 0;
        int avail = width - 24 - doneW - 8 - (cancelW > 0 ? cancelW + 4 : 0);

        int scanW = Math.min(116, Math.max(56, avail * 38 / 100));
        var scanBtn = button(Component.translatable("vv.scan"), 12, y, scanW, () -> {
            s.scan();
            rebuildWidgets();
        });
        scanBtn.setTooltip(Tooltip.create(Component.translatable("vv.scan.help")));

        boolean running = s.probe.running();
        int probeW = Math.min(170, Math.max(40, avail - scanW - 4));
        String probeKey = running ? "vv.probeRunning" : "vv.probeStart";
        var probeBtn = button(
            Component.literal(font.plainSubstrByWidth(Component.translatable(probeKey).getString(), probeW - 8))
                .withColor(running ? COLOR_ACCENT : 0xFFFFFFFF),
            12 + scanW + 4, y, probeW, () -> {
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

    private Component fit(String key, int w) {
        return Component.translatable(key);
    }

    private Button button(Component text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(
            Button.builder(text, b -> action.run()).bounds(x, y, Math.max(20, w), 20).build());
    }

    // ---------------------------------------------------------------------------------------------
    // Data helpers
    // ---------------------------------------------------------------------------------------------

    private String villagerRowLabel(VillagerRecord v, Verdict verdict, boolean selectedRow) {
        StringBuilder sb = new StringBuilder();
        if (selectedRow) sb.append("▸ ");
        sb.append(ClientDisplayText.statusLabel(s.displayStatus(v, verdict)))
            .append(" · ")
            .append(profession(v))
            .append(" Lv.")
            .append(v.level())
            .append(" · ")
            .append(Component.translatable("vv.unit.blocks", (int) Math.sqrt(s.distanceSquared(v))).getString());
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

    private TargetTrade currentProductQuery() {
        return s.targets.stream().filter(t -> t.id().equals(productQueryId)).findFirst().orElse(null);
    }

    private void updateTarget(String id, TargetTrade t) {
        var list = new ArrayList<>(s.targets);
        list.removeIf(a -> a.id().equals(id));
        if (t != null) list.add(t);
        else if (id.equals(productQueryId)) productQueryId = "";
        s.targets = list;
        s.changed();
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

    /** Villagers matching product query and search — before the status filter is applied. */
    private List<VillagerRecord> baseFiltered() {
        var productQuery = currentProductQuery();
        String q = query.toLowerCase(Locale.ROOT);
        return s.records().stream()
            .filter(s::enabled)
            .filter(v -> productQuery == null || productQuery.matches(v))
            .filter(v -> q.isEmpty() || search(v).contains(q))
            .toList();
    }

    /** Counts for the status chips: all, keep, discard, unscanned. */
    private long[] statusCounts() {
        long[] counts = new long[4];
        for (var v : baseFiltered()) {
            counts[0]++;
            counts[1 + s.displayStatus(v, s.analysis.verdicts().get(v.uuid())).ordinal()]++;
        }
        return counts;
    }

    private List<VillagerRecord> filtered() {
        var list = baseFiltered().stream()
            .filter(v -> filter == 0
                || s.displayStatus(v, s.analysis.verdicts().get(v.uuid())).ordinal() == filter - 1)
            .toList();

        Comparator<VillagerRecord> order = switch (sort) {
            case 1 -> Comparator.comparing(this::priceGroup)
                .thenComparing((a, b) -> TradeComparison.vectorOrder(priceVector(a), priceVector(b)));
            case 2 -> Comparator.<VillagerRecord>comparingInt(this::enchantment).reversed();
            case 3 -> Comparator.comparingDouble(s::distanceSquared);
            case 4 -> Comparator.comparing(VillagerRecord::profession);
            default -> Comparator.<VillagerRecord>comparingInt(v -> v.fixed() ? 0 : 1)
                .thenComparingInt(v -> s.analysis.recommended().contains(v.uuid()) ? 0 : 1)
                .thenComparing(Comparator.<VillagerRecord>comparingInt(
                    v -> s.analysis.verdicts().containsKey(v.uuid())
                        ? s.analysis.verdicts().get(v.uuid()).uniqueTrades()
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
        return statusColor(s.displayStatus(record, verdict));
    }

    private int statusColor(DisplayStatus status) {
        return switch (status) {
            case KEEP -> s.settings.green();
            case DISCARD -> s.settings.red();
            default -> s.settings.yellow();
        };
    }

    /** One-line, context-aware hint telling the player what to do next. */
    private Guide guideLine() {
        if (!s.error.isEmpty()) return new Guide(s.error, COLOR_ERROR);
        if (s.navigationActive()) return guide(COLOR_GUIDE, "vv.guide.navigating");
        if (!s.ready() || s.identity() == null) return guide(COLOR_MUTED, "vv.guide.loading");
        if (page == 1) return guide(COLOR_GUIDE, "vv.guide.targets");

        var st = s.stats();
        long success = st.getOrDefault("success", 0L);
        long pending = st.getOrDefault("pending", 0L);
        long failed = st.getOrDefault("failed", 0L);
        long active = st.getOrDefault("active", 0L);
        long total = success + pending + failed + active;
        if (total == 0) {
            return guide(COLOR_GUIDE, s.settings.autoScan() ? "vv.guide.noneAuto" : "vv.guide.none");
        }
        if (s.probe.running() && pending + active > 0) {
            return guide(COLOR_GUIDE, "vv.guide.probing", pending + active);
        }
        if (pending > 0) return guide(COLOR_GUIDE, "vv.guide.pending", pending);
        if (failed > 0) return guide(COLOR_WARN, "vv.guide.failed", failed);
        long keep = 0, discard = 0;
        for (var v : s.records()) {
            if (!s.enabled(v)) continue;
            var status = s.displayStatus(v, s.analysis.verdicts().get(v.uuid()));
            if (status == DisplayStatus.KEEP) keep++;
            else if (status == DisplayStatus.DISCARD) discard++;
        }
        return guide(COLOR_ACCENT, "vv.guide.done", total, keep, discard);
    }

    private static Guide guide(int color, String key, Object... args) {
        return new Guide(Component.translatable(key, args).getString(), color);
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.centeredText(font, title, width / 2, 6, 0xFFFFFFFF);

        // Accent line under the active tab.
        int tabW = tabWidth();
        int bx = 12 + page * (tabW + 3);
        g.fill(bx + 4, NAV_Y + 21, bx + tabW - 4, NAV_Y + 23, COLOR_ACCENT);

        // Next-step guide line (full text on hover when truncated).
        Guide guide = guideLine();
        String fullGuide = "➜ " + guide.text();
        String shownGuide = font.plainSubstrByWidth(fullGuide, width - 24);
        g.text(font, shownGuide, 12, GUIDE_Y, guide.color());
        boolean guideHover = shownGuide.length() < fullGuide.length()
            && my >= GUIDE_Y - 1 && my < GUIDE_Y + 10 && mx >= 12 && mx < width - 12;

        icons.clear();
        targetIcons.clear();

        List<String> statsHoverLines = null;
        int statsX0 = 0, statsX1 = 0, statsY = height - 40;

        if (page == 0) {
            if (filterActive() && !mobileDetail()) {
                var productQuery = currentProductQuery();
                String caption = productQuery != null
                    ? Component.translatable("vv.filterBanner.product", ClientDisplayText.targetSummary(productQuery)).getString()
                    : "";
                int clearW = Math.min(110, font.width(Component.translatable("vv.clearProductFilter")) + 16);
                g.text(font, font.plainSubstrByWidth(caption, width - 36 - clearW), 12, toolbarBottom() + 6, COLOR_MUTED);
            }

            var records = mobileDetail() ? List.<VillagerRecord>of() : filtered();
            boolean noneNearby = s.records().stream().noneMatch(s::enabled);
            if (isWide()) {
                int panelTop = listTop();
                int panelH = listBottom() - panelTop;
                if (records.isEmpty()) {
                    drawEmptyList(g, 12, panelTop, listWidth(), noneNearby);
                }
                details(g, width / 2 + 4, panelTop, width / 2 - 16, panelH);
            } else if (mobileDetail()) {
                details(g, 12, listTop(), width - 24, height - listTop() - 32);
            } else if (records.isEmpty()) {
                if (noneNearby) {
                    gettingStarted(g, 12, listTop(), width - 24, listBottom() - listTop());
                } else {
                    drawEmptyList(g, 12, listTop(), width - 24, false);
                }
            }

            if (!mobileDetail()) {
                var st = s.stats();
                String statsText = Component.translatable(
                    "vv.stats",
                    st.get("success"),
                    st.get("pending"),
                    st.get("failed")).getString();
                if (st.getOrDefault("active", 0L) > 0L) {
                    statsText += Component.translatable("vv.statsReading").getString();
                }
                var gaps = s.analysis.gaps();
                if (!gaps.isEmpty()) {
                    statsText += Component.translatable("vv.gapSummary", gaps.size()).getString();
                }
                int maxW = isWide() ? listWidth() : width - 24;
                String visible = font.plainSubstrByWidth(statsText, maxW);
                g.fill(10, statsY - 2, 16 + font.width(visible), statsY + 11, 0xA81D2534);
                g.text(font, visible, 13, statsY, gaps.isEmpty() ? 0xFFBBD0E8 : COLOR_WARN);
                statsX0 = 10;
                statsX1 = 16 + font.width(visible);
                if (!gaps.isEmpty() || visible.length() < statsText.length()) {
                    statsHoverLines = new ArrayList<>();
                    statsHoverLines.add(statsText);
                    if (!gaps.isEmpty()) {
                        statsHoverLines.add(Component.translatable("vv.start.gaps").getString());
                        for (var gap : gaps) statsHoverLines.add("• " + ClientDisplayText.formatGap(gap));
                    }
                }
            }
        } else if (page == 1) {
            var targets = s.targets;
            int top = listTop();
            if (targets.isEmpty()) {
                drawMessagePanel(g, 12, top, width - 24, "vv.emptyTargets");
            } else {
                int rows = Math.max(1, (height - top - 32) / 24);
                for (int i = offset; i < Math.min(targets.size(), offset + rows); i++) {
                    ItemStack stack = ClientDisplayText.itemStack(targets.get(i).item());
                    if (!stack.isEmpty()) {
                        targetIcons.add(new TargetIcon(stack, 14, top + 2 + (i - offset) * 24));
                    }
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

        if (guideHover) {
            g.setTooltipForNextFrame(font.split(Component.literal(guide.text()), 280), mx, my);
        } else if (statsHoverLines != null && mx >= statsX0 && mx < statsX1 && my >= statsY - 2 && my < statsY + 11) {
            var lines = new ArrayList<net.minecraft.util.FormattedCharSequence>();
            for (var line : statsHoverLines) lines.addAll(font.split(Component.literal(line), 280));
            g.setTooltipForNextFrame(lines, mx, my);
        }
    }

    private void drawMessagePanel(GuiGraphicsExtractor g, int x, int y, int w, String key) {
        var lines = font.split(Component.translatable(key), w - 16);
        int h = Math.max(30, lines.size() * 11 + 16);
        ClientDisplayText.drawPanel(g, x, y, w, h);
        int ey = y + 8;
        for (var line : lines) {
            g.text(font, line, x + 8, ey, 0xFFBBD0E8);
            ey += 11;
        }
    }

    private void drawEmptyList(GuiGraphicsExtractor g, int x, int y, int w, boolean noneNearby) {
        drawMessagePanel(g, x, y, w, noneNearby ? "vv.emptyNearby" : "vv.emptyVillagers");
    }

    /** Beginner guide with a colour legend; shown when nothing is selected. */
    private void gettingStarted(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
        ClientDisplayText.drawPanel(g, x, y, w, h);
        int innerW = Math.max(40, w - 16);
        int yy = y + 8 - detailOffset;
        yy = drawWrappedLine(g, Component.translatable("vv.start.title").getString(), x + 8, yy, innerW, 0xFFFFFFFF);
        yy += 2;
        yy = drawWrappedLine(g, Component.translatable("vv.start.step1").getString(), x + 8, yy, innerW, COLOR_TEXT);
        yy = drawWrappedLine(g, Component.translatable("vv.start.step2").getString(), x + 8, yy, innerW, COLOR_TEXT);
        yy = drawWrappedLine(g, Component.translatable("vv.start.step3").getString(), x + 8, yy, innerW, COLOR_TEXT);
        String[] legend = {"vv.start.keep", "vv.start.discard", "vv.start.unscanned"};
        DisplayStatus[] statuses = {DisplayStatus.KEEP, DisplayStatus.DISCARD, DisplayStatus.UNSCANNED};
        for (int i = 0; i < legend.length; i++) {
            int c = statusColor(statuses[i]);
            g.text(font, "●", x + 16, yy, c);
            yy = drawWrappedLine(g, Component.translatable(legend[i]).getString(), x + 28, yy, innerW - 20, c);
        }
        yy += 2;
        yy = drawWrappedLine(g, Component.translatable("vv.start.click").getString(), x + 8, yy, innerW, COLOR_MUTED);

        var gaps = s.analysis.gaps();
        if (!gaps.isEmpty()) {
            yy += 4;
            yy = drawWrappedLine(g, Component.translatable("vv.start.gaps").getString(), x + 8, yy, innerW, COLOR_WARN);
            for (var gap : gaps) {
                yy = drawWrappedLine(g, "• " + ClientDisplayText.formatGap(gap), x + 16, yy, innerW - 8, COLOR_WARN);
            }
        }
        g.disableScissor();
        detailMax = Math.max(0, yy + detailOffset - y - h + 10);
    }

    private void details(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        var v = s.record(s.selected);
        if (v == null || !s.enabled(v)) {
            gettingStarted(g, x, y, w, h);
            return;
        }
        g.enableScissor(x, y, x + w, y + h);
        ClientDisplayText.drawPanel(g, x, y, w, h);

        int yy = y + 8 - detailOffset;
        int innerW = Math.max(24, w - 16);

        // 1. Who: profession and level (and manual lock, if any).
        String header = profession(v) + " · Lv." + v.level();
        if (v.manual() == VillagerRecord.Manual.KEEP) {
            header += " · " + Component.translatable("vv.manual.KEEP").getString();
        }
        yy = drawWrappedLine(g, header, x + 8, yy, innerW, 0xFFFFFFFF);

        // 2. Verdict and plain-language reason.
        yy += 2;
        var verdict = s.analysis.verdicts().get(v.uuid());
        var displayStatus = s.displayStatus(v, verdict);
        int statusColor = color(v, verdict);
        var snap = v.snapshot();
        String why;
        if (snap == null) why = Component.translatable("vv.uninspected").getString();
        else if (verdict == null) why = Component.translatable("vv.analyzing").getString();
        else why = reason(verdict.reason());
        yy = drawWrappedLine(g, Component.translatable("vv.detail.status",
            ClientDisplayText.statusLabel(displayStatus), why).getString(), x + 8, yy, innerW, statusColor);

        // 3. Personal note and tags (only when present).
        if (!v.note().isBlank()) {
            yy = drawWrappedLine(g, Component.translatable("vv.detail.note", v.note()).getString(),
                x + 8, yy, innerW, COLOR_TEXT);
        }
        if (!v.tags().isEmpty()) {
            yy = drawWrappedLine(g, Component.translatable("vv.detail.tags", String.join(", ", v.tags())).getString(),
                x + 8, yy, innerW, COLOR_FAINT);
        }

        // 4. Signature goods with base emerald prices only.
        if (snap != null) {
            yy += 4;
            yy = drawWrappedLine(g, Component.translatable("vv.detail.trades",
                ClientDisplayText.relativeTimeText(snap.checkedAt())).getString(), x + 8, yy, innerW, COLOR_MUTED);
            yy += 1;
            var trades = ProfessionHighlights.trades(v);
            if (trades.isEmpty()) {
                yy = drawWrappedLine(g, Component.translatable("vv.detail.noSignatureTrades").getString(),
                    x + 8, yy, innerW, COLOR_MUTED);
            }
            for (var t : trades) {
                if (yy >= y && yy + 16 < y + h) {
                    icons.add(new Icon(t.output(), x + 8, yy));
                }
                int rowTop = yy;
                yy = drawWrappedLine(g, ClientDisplayText.signatureTradeSummary(t),
                    x + 28, yy, Math.max(24, innerW - 20), 0xFFFFFFFF);
                yy = Math.max(yy, rowTop + 18) + 4;
            }
        }

        // 5. Villagers that can replace this one (clickable).
        if (verdict != null && !verdict.replacements().isEmpty()) {
            yy += 4;
            yy = drawWrappedLine(g, Component.translatable("vv.detail.replacements").getString(),
                x + 8, yy, innerW, COLOR_MUTED);
            for (var r : verdict.replacements()) {
                String deltaSummary = summarizePriceDelta(r);
                String repText = Component.translatable("vv.replacement.line",
                    ClientDisplayText.productSummary(r.original().output()),
                    ClientDisplayText.productSummary(r.alternative().output()),
                    (int) r.x(), (int) r.y(), (int) r.z(), deltaSummary).getString();
                int begin = yy;
                yy = drawWrappedLine(g, "• " + repText, x + 8, yy, innerW, 0xFFB8A8FF);
                if (lastClickY >= begin && lastClickY < yy && lastClickX >= x && lastClickX < x + w) {
                    s.selected = r.replacement();
                    detailOffset = 0;
                    lastClickY = -1;
                }
            }
        }

        // 6. Where and when (small print).
        yy += 4;
        yy = drawWrappedLine(g, Component.translatable("vv.detail.location",
            (int) v.x(), (int) v.y(), (int) v.z(),
            ClientDisplayText.relativeTimeText(v.lastSeen())).getString(), x + 8, yy, innerW, COLOR_FAINT);

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

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (super.mouseClicked(e, doubleClick)) return true;
        lastClickX = (int) e.x();
        lastClickY = (int) e.y();
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double a, double b) {
        boolean onDetails = (page == 0 && mobileDetail())
            || (page == 0 && isWide() && x > width / 2.0)
            || (page == 0 && !isWide() && filtered().isEmpty());
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


    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN
                || e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) {
            return mouseScrolled(
                mobileDetail() ? 0 : width,
                listTop(),
                0,
                e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN ? -5 : 5);
        }
        return super.keyPressed(e);
    }
}
