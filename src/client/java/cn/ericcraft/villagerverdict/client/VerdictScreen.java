package cn.ericcraft.villagerverdict.client;

import static cn.ericcraft.villagerverdict.client.FormScreen.fields;

import cn.ericcraft.villagerverdict.analysis.TradeComparison;
import cn.ericcraft.villagerverdict.model.*;
import cn.ericcraft.villagerverdict.model.AnalysisResult.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class VerdictScreen extends Screen {
  private final VerdictService s = VerdictService.INSTANCE;
  private int page, offset, detailOffset, sort, filter;
  private EditBox searchBox;
  private boolean restoreSearch;
  private int searchCursor;
  private String query = "", regionId = "";
  private boolean nearbyOnly;
  private static final String[] SORTS = {
    "vv.value", "vv.price", "vv.enchantment", "vv.distance", "vv.profession"
  };
  private final List<Icon> icons = new ArrayList<>();

  private record Icon(ItemData item, int x, int y) {}

  public VerdictScreen() {
    super(Component.literal("Villager Verdict"));
  }

  private int top() {
    return 56 + ((7 + navigationColumns() - 1) / navigationColumns()) * 24;
  }

  private int navigationColumns() {
    return width >= 640 ? 7 : width >= 460 ? 4 : 3;
  }

  private int listWidth() {
    return width >= 640 ? width / 2 - 16 : width - 24;
  }

  protected void init() {
    String[] names = {
      "vv.villagers",
      "vv.details",
      "vv.targets",
      "vv.regions",
      "vv.settings",
      "vv.shortcuts",
      "vv.data"
    };
    int columns = navigationColumns();
    int bw = (width - 24) / columns;
    for (int i = 0; i < names.length; i++) {
      int p = i;
      button(
          Component.translatable(names[i]),
          12 + (i % columns) * bw,
          26 + (i / columns) * 24,
          bw - 3,
          () -> {
            if (p == 4) SettingsScreen.open(this);
            else if (p == 5) ShortcutScreen.open(this);
            else if (p == 6) DataScreen.open(this);
            else {
              page = p;
              offset = detailOffset = 0;
              rebuildWidgets();
            }
          });
    }
    if (page == 0) {
      int y = top() - 24;
      int available = width - 24,
          sw = available * 35 / 100 - 3,
          sow = available * 23 / 100 - 3,
          nw = available * 24 / 100 - 3;
      var search = new EditBox(font, 12, y, sw, 20, Component.translatable("vv.search"));
      search.setValue(query);
      search.setHint(Component.translatable("vv.search"));
      search.setResponder(
          v -> {
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
      button(
          Component.translatable(SORTS[sort]),
          12 + sw + 4,
          y,
          sow,
          () -> {
            sort = (sort + 1) % SORTS.length;
            rebuildWidgets();
          });
      button(
          Component.translatable(nearbyOnly ? "vv.nearby" : "vv.all"),
          12 + sw + sow + 8,
          y,
          nw,
          () -> {
            nearbyOnly = !nearbyOnly;
            regionId = "";
            rebuildWidgets();
          });
      button(
          Component.translatable("vv.filter", filter),
          12 + sw + sow + nw + 12,
          y,
          available - sw - sow - nw - 12,
          () -> {
            filter = (filter + 1) % 4;
            offset = 0;
            rebuildWidgets();
          });
    }
    if (page == 0) {
      int rows = Math.max(1, (height - top() - 60) / 25);
      var records = filtered();
      offset = Math.clamp(offset, 0, Math.max(0, records.size() - rows));
      for (int i = offset; i < Math.min(records.size(), offset + rows); i++) {
        var v = records.get(i);
        var verdict = viewAnalysis().verdicts().get(v.uuid());
        String label =
            (verdict == null ? "?" : symbol(verdict.status()))
                + " "
                + profession(v)
                + " L"
                + v.level()
                + " · "
                + (int) Math.sqrt(s.distanceSquared(v))
                + "m · "
                + v.uuid().toString().substring(0, 6);
        var b =
            button(
                Component.literal(label).withColor(color(verdict)),
                12,
                top() + (i - offset) * 25,
                listWidth(),
                () -> {
                  s.selected = v.uuid();
                  detailOffset = 0;
                  if (width < 640) page = 1;
                  rebuildWidgets();
                });
        b.setTooltip(
            Tooltip.create(
                Component.literal(
                    v.uuid()
                        + "\n"
                        + v.x()
                        + ", "
                        + v.y()
                        + ", "
                        + v.z()
                        + "\n"
                        + (verdict == null ? "" : reason(verdict.reason())))));
      }
    }
    if (page == 1 || page == 0 && width >= 640) {
      int x = page == 0 ? width / 2 + 4 : 12;
      int w = width - x - 12;
      button(
          Component.translatable("vv.edit"),
          x,
          height - 54,
          Math.min(80, w / 3),
          this::editSelected);
      button(
          Component.translatable("vv.navigate"),
          x + Math.min(84, w / 3 + 4),
          height - 54,
          Math.min(80, w / 3),
          () -> {
            minecraft.gui.setScreen(null);
          });
      button(
          Component.translatable("vv.next"),
          x + Math.min(168, w * 2 / 3 + 8),
          height - 54,
          Math.min(80, w / 3 - 8),
          () -> {
            s.navigateNext();
            rebuildWidgets();
          });
    }
    if (page == 2) {
      button(Component.translatable("vv.add"), 12, top() - 22, 90, () -> target(null, null));
      button(
          Component.literal(
              currentRegion() == null
                  ? Component.translatable("vv.all").getString()
                  : currentRegion().name()),
          110,
          top() - 22,
          Math.max(70, width - 122),
          () -> {
            var ids = new ArrayList<String>();
            ids.add("");
            s.regions.forEach(r -> ids.add(r.id()));
            regionId = ids.get((ids.indexOf(regionId) + 1) % ids.size());
            offset = 0;
            rebuildWidgets();
          });
      targetRows();
    }
    if (page == 3) {
      button(Component.translatable("vv.add"), 12, top() - 22, 90, () -> region(null));
      int row = 0;
      for (var r :
          s.regions.stream().skip(offset).limit(Math.max(1, (height - top() - 60) / 25)).toList()) {
        var result = s.regional.get(r.id());
        button(
            Component.literal(
                    r.name()
                        + " · "
                        + (result == null
                            ? ""
                            : result.recommended().size() + " / " + result.verdicts().size()))
                .withColor(r.color()),
            12,
            top() + row++ * 25,
            width - 210,
            () -> {
              regionId = r.id();
              page = 0;
              offset = 0;
              rebuildWidgets();
            });
        button(
            Component.translatable("vv.targets"),
            width - 192,
            top() + (row - 1) * 25,
            76,
            () -> {
              regionId = r.id();
              page = 2;
              offset = 0;
              rebuildWidgets();
            });
        button(
            Component.translatable("vv.edit"),
            width - 112,
            top() + (row - 1) * 25,
            90,
            () -> region(r));
      }
    }
    button(
        Component.translatable("vv.scan"),
        12,
        height - 26,
        Math.min(76, width / 5),
        () -> {
          s.scan();
          rebuildWidgets();
        });
    button(
        Component.translatable("vv.probe"),
        94,
        height - 26,
        Math.min(76, width / 5),
        SettingsScreen::toggleProbe);
    button(
        Component.translatable("vv.pause"),
        176,
        height - 26,
        Math.min(65, width / 5),
        () -> s.pause());
    button(
        Component.translatable("vv.off"),
        247,
        height - 26,
        Math.min(55, width / 5),
        () -> s.enable(false));
    if (width > 370)
      button(Component.translatable("gui.done"), width - 74, height - 26, 62, this::onClose);
  }

  private AnalysisResult viewAnalysis() {
    return s.regional.getOrDefault(regionId, s.analysis);
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
    var r = currentRegion();
    if (r == null) s.targets = list;
    else {
      var n =
          new TradingHallRegion(
              r.id(),
              r.name(),
              r.dimension(),
              r.minX(),
              r.minY(),
              r.minZ(),
              r.maxX(),
              r.maxY(),
              r.maxZ(),
              list,
              r.color());
      s.regions.removeIf(a -> a.id().equals(r.id()));
      s.regions.add(n);
    }
    s.changed();
  }

  private void targetRows() {
    int row = 0;
    for (var t :
        currentTargets().stream()
            .skip(offset)
            .limit(Math.max(1, (height - top() - 60) / 25))
            .toList())
      button(
          Component.literal(
              t.item() + " " + t.enchantment() + " ≥" + t.minLevel() + " ×" + t.backups()),
          12,
          top() + row++ * 25,
          width - 24,
          () -> target(t, null));
  }

  private Button button(Component text, int x, int y, int w, Runnable action) {
    return addRenderableWidget(
        Button.builder(text, b -> action.run()).bounds(x, y, Math.max(20, w), 20).build());
  }

  public void tick() {
    if (++refresh % 20 == 0) {
      restoreSearch = searchBox != null && searchBox.isFocused();
      if (restoreSearch) searchCursor = searchBox.getCursorPosition();
      rebuildWidgets();
    }
  }

  private int refresh;

  private List<VillagerRecord> filtered() {
    var list =
        s.records().stream()
            .filter(v -> !nearbyOnly || s.nearby.contains(v.uuid()))
            .filter(
                v ->
                    regionId.isEmpty()
                        || s.regions.stream()
                            .filter(r -> r.id().equals(regionId))
                            .anyMatch(r -> r.contains(v)))
            .filter(
                v ->
                    filter == 0
                        || viewAnalysis().verdicts().get(v.uuid()) != null
                            && viewAnalysis().verdicts().get(v.uuid()).status().ordinal()
                                == filter - 1)
            .filter(v -> search(v).contains(query.toLowerCase(Locale.ROOT)))
            .toList();
    Comparator<VillagerRecord> order =
        switch (sort) {
          case 1 ->
              Comparator.comparing(
                      (VillagerRecord v) ->
                          priceGroup(v))
                  .thenComparing(
                      (a, b) -> TradeComparison.vectorOrder(priceVector(a), priceVector(b)));
          case 2 -> Comparator.<VillagerRecord>comparingInt(this::enchantment).reversed();
          case 3 -> Comparator.comparingDouble(s::distanceSquared);
          case 4 -> Comparator.comparing(VillagerRecord::profession);
          default ->
              Comparator.<VillagerRecord>comparingInt(v -> v.fixed() ? 0 : 1)
                  .thenComparingInt(v -> viewAnalysis().recommended().contains(v.uuid()) ? 0 : 1)
                  .thenComparing(
                      Comparator.<VillagerRecord>comparingInt(
                              v ->
                                  viewAnalysis().verdicts().containsKey(v.uuid())
                                      ? viewAnalysis().verdicts().get(v.uuid()).uniqueTrades()
                                      : 0)
                          .reversed())
                  .thenComparing(
                      Comparator.<VillagerRecord>comparingInt(
                              v -> v.snapshot() == null ? 0 : v.snapshot().trades().size())
                          .reversed());
        };
    return list.stream().sorted(order.thenComparing(v -> v.uuid().toString())).toList();
  }

  private String search(VillagerRecord v) {
    return (profession(v)
            + v.profession()
            + v.uuid()
            + v.level()
            + v.note()
            + v.tags()
            + (v.snapshot() == null
                ? ""
                : v.snapshot().trades().stream()
                    .map(
                        t ->
                            t.toString()
                                + ItemCodecAdapter.displayName(t.output())
                                + ItemCodecAdapter.enchantmentText(t.output()))
                    .toList()))
        .toLowerCase(Locale.ROOT);
  }

  private Map<String, Double> priceVector(VillagerRecord v) {
    if (v.snapshot() == null) return Map.of();
    String group = priceGroup(v);
    return v.snapshot().trades().stream()
        .filter(t -> new TreeSet<>(t.cost(false).keySet()).toString().equals(group))
        .map(t -> t.cost(false))
        .min(TradeComparison::vectorOrder)
        .orElse(Map.of());
  }

  private String priceGroup(VillagerRecord v) {
    return v.snapshot() == null
        ? "~"
        : v.snapshot().trades().stream()
            .map(t -> new TreeSet<>(t.cost(false).keySet()).toString())
            .sorted()
            .findFirst()
            .orElse("~");
  }

  private int enchantment(VillagerRecord v) {
    return v.snapshot() == null
        ? 0
        : v.snapshot().trades().stream()
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

  private String symbol(Status status) {
    return switch (status) {
      case KEEP -> "+";
      case REVIEW -> "?";
      case REDUNDANT -> "−";
    };
  }

  private int color(Verdict verdict) {
    return verdict == null
        ? s.settings.yellow()
        : switch (verdict.status()) {
          case KEEP -> s.settings.green();
          case REVIEW -> s.settings.yellow();
          case REDUNDANT -> s.settings.red();
        };
  }

  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
    g.fill(0, 0, width, height, 0xF0181E2B);
    g.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
    icons.clear();
    if (page == 0) {
      if (width >= 640) details(g, width / 2 + 4, top(), width / 2 - 16, height - top() - 62);
      var st = s.stats();
      String text =
          Component.translatable(
                  "vv.stats",
                  st.get("success"),
                  st.get("pending"),
                  st.get("failed"),
                  st.get("active"),
                  st.get("nontrading"))
              .getString();
      g.text(font, font.plainSubstrByWidth(text, width - 24), 12, height - 40, 0xFFBBD0E8);
    }
    if (page == 1) details(g, 12, top() - 20, width - 24, height - top() - 42);
    super.extractRenderState(g, mx, my, delta);
    for (var i : icons) {
      var stack = ItemCodecAdapter.decode(i.item);
      g.item(stack, i.x, i.y);
      g.itemDecorations(font, stack, i.x, i.y);
      if (mx >= i.x && mx < i.x + 16 && my >= i.y && my < i.y + 16)
        g.setTooltipForNextFrame(font, stack, mx, my);
    }
    if (!s.error.isEmpty())
      g.text(font, font.plainSubstrByWidth(s.error, width - 24), 12, height - 66, 0xFFFF6677);
  }

  private void details(GuiGraphicsExtractor g, int x, int y, int w, int h) {
    var v = s.record(s.selected);
    g.enableScissor(x, y, x + w, y + h);
    g.fill(x, y, x + w, y + h, 0x99303A50);
    if (v == null) {
      g.text(font, Component.translatable("vv.select"), x + 6, y + 6, 0xFFFFFFFF);
      g.disableScissor();
      return;
    }
    var lines = new ArrayList<String>();
    lines.add(
        profession(v)
            + " · L"
            + v.level()
            + " · "
            + Component.translatable("vv.manual." + v.manual()).getString());
    lines.add(v.uuid().toString());
    lines.add(
        (int) v.x()
            + ", "
            + (int) v.y()
            + ", "
            + (int) v.z()
            + " · "
            + Component.translatable(s.isLoaded(v.uuid()) ? "vv.loaded" : "vv.unloaded")
                .getString());
    lines.add(
        Component.translatable(
                "vv.lastSeen", java.time.Instant.ofEpochMilli(v.lastSeen()).toString())
            .getString());
    lines.add(v.note() + " " + v.tags());
    var verdict = viewAnalysis().verdicts().get(v.uuid());
    if (verdict != null) {
      lines.add(
          Component.translatable("vv.status." + verdict.status().name().toLowerCase(Locale.ROOT))
                  .getString()
              + " · "
              + Component.translatable("vv.confidence." + verdict.confidence()).getString());
      lines.add(reason(verdict.reason()));
    }
    var snap = v.snapshot();
    if (snap == null) lines.add(Component.translatable("vv.uninspected").getString());
    else {
      lines.add(
          Component.translatable("vv.complete." + snap.completeness()).getString()
              + " · "
              + java.time.Instant.ofEpochMilli(snap.checkedAt()));
      if (snap.stale(System.currentTimeMillis(), s.settings.cacheMillis()))
        lines.add(Component.translatable("vv.stale").getString());
    }
    int yy = y + 6 - detailOffset;
    for (String l : lines) {
      for (var wrapped : font.split(Component.literal(l), Math.max(24, w - 14))) {
        g.text(font, wrapped, x + 6, yy, 0xFFCCDAED);
        yy += 12;
      }
    }
    if (snap != null)
      for (var t : snap.trades()) {
        if (yy >= y && yy + 18 < y + h) {
          icons.add(new Icon(t.actualInput(), x + 6, yy));
          if (t.secondInput() != null) icons.add(new Icon(t.secondInput(), x + 28, yy));
          icons.add(new Icon(t.output(), x + 64, yy));
        }
        g.text(font, "→", x + 50, yy + 3, 0xFFFFFFFF);
        yy += 20;
        String text =
            t.baseInput().count()
                + " → "
                + t.actualInput().count()
                + " ("
                + Component.translatable("vv.baseActual").getString()
                + ") · "
                + ItemCodecAdapter.displayName(t.output())
                + " ×"
                + t.output().count()
                + " · "
                + (t.maxUses() - t.uses())
                + "/"
                + t.maxUses();
        for (var wrapped :
            font.split(
                Component.literal(
                    text
                        + (t.secondInput() == null
                            ? ""
                            : " + "
                                + ItemCodecAdapter.displayName(t.secondInput())
                                + " ×"
                                + t.secondInput().count())
                        + " "
                        + ItemCodecAdapter.enchantmentText(t.output())),
                Math.max(24, w - 14))) {
          g.text(font, wrapped, x + 6, yy, 0xFFFFFFFF);
          yy += 12;
        }
      }
    if (verdict != null)
      for (var r : verdict.replacements()) {
        String text =
            "↪ "
                + r.replacement().toString().substring(0, 8)
                + " ("
                + (int) r.x()
                + ","
                + (int) r.y()
                + ","
                + (int) r.z()
                + ") "
                + ItemCodecAdapter.displayName(r.original().output())
                + " Δbase="
                + difference(r.baseDifference())
                + " Δnow="
                + difference(r.actualDifference())
                + " "
                + (s.canConfirm(r)
                    ? Component.translatable("vv.confirmed").getString()
                    : Component.translatable("vv.cached").getString());
        int begin = yy;
        for (var wrapped : font.split(Component.literal(text), Math.max(24, w - 14))) {
          g.text(font, wrapped, x + 6, yy, 0xFFAA99FF);
          yy += 12;
        }
        if (lastClickY >= begin && lastClickY < yy && lastClickX >= x && lastClickX < x + w) {
          s.selected = r.replacement();
          detailOffset = 0;
          lastClickY = -1;
        }
      }
    for (var gap : viewAnalysis().gaps()) {
      for (var wrapped :
          font.split(
              Component.literal(
                  Component.translatable("vv.gap").getString()
                      + ": "
                      + gap.replace(
                          "price condition unmet",
                          Component.translatable("vv.priceUnmet").getString())),
              Math.max(24, w - 14))) {
        g.text(font, wrapped, x + 6, yy, 0xFFFFCC55);
        yy += 12;
      }
    }
    g.disableScissor();
    detailMax = Math.max(0, yy + detailOffset - y - h + 12);
  }

  private String reason(String value) {
    String text =
        value
            .replace(
                "manual protection / keep", Component.translatable("vv.reason.manual").getString())
            .replace(
                "coverage / unique value", Component.translatable("vv.reason.coverage").getString())
            .replace(
                "all known trades have replacements",
                Component.translatable("vv.reason.redundant").getString())
            .replace(
                "stale, unavailable replacement or coverage gap",
                Component.translatable("vv.reason.review").getString())
            .replace(
                "uninspected or completeness unknown",
                Component.translatable("vv.reason.uninspected").getString())
            .replace(
                "unknown future trades", Component.translatable("vv.reason.unknown").getString())
            .replace("manual exclusion", Component.translatable("vv.reason.excluded").getString());
    return text;
  }

  private String difference(Map<String, Double> values) {
    return values.entrySet().stream()
        .map(
            e ->
                e.getKey().split("\\|", 2)[0]
                    + " "
                    + String.format(Locale.ROOT, "%+.2f", e.getValue()))
        .collect(java.util.stream.Collectors.joining(", "));
  }

  private int detailMax, lastClickY = -1, lastClickX;

  public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
    if (super.mouseClicked(e, doubleClick)) return true;
    lastClickX = (int) e.x();
    lastClickY = (int) e.y();
    return true;
  }

  public boolean mouseScrolled(double x, double y, double a, double b) {
    if (page == 1 || page == 0 && width >= 640 && x > width / 2) {
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
    minecraft.gui.setScreen(
        new FormScreen(
            this,
            "vv.edit",
            fields(
                "vv.manual",
                v.manual().name(),
                "vv.note",
                v.note(),
                "vv.tags",
                String.join(",", v.tags())),
            m -> {
              var tags = new HashSet<>(Arrays.asList(m.get("vv.tags").split(",")));
              tags.remove("");
              s.edit(
                  v.withManual(
                      VillagerRecord.Manual.valueOf(m.get("vv.manual").toUpperCase(Locale.ROOT)),
                      m.get("vv.note"),
                      tags));
            },
            null));
  }

  private void target(TargetTrade t, TradingHallRegion region) {
    String id = t == null ? UUID.randomUUID().toString() : t.id();
    minecraft.gui.setScreen(
        new FormScreen(
            this,
            "vv.targets",
            fields(
                "vv.item",
                t == null ? "minecraft:enchanted_book" : t.item(),
                "vv.enchantmentId",
                t == null ? "" : t.enchantment(),
                "vv.minLevel",
                t == null ? "1" : "" + t.minLevel(),
                "vv.maxPrice",
                t == null ? "0" : "" + t.maxUnitPrice(),
                "vv.backups",
                t == null ? "1" : "" + t.backups()),
            m -> {
              var n =
                  new TargetTrade(
                      id,
                      m.get("vv.item"),
                      m.get("vv.enchantmentId"),
                      Integer.parseInt(m.get("vv.minLevel")),
                      Double.parseDouble(m.get("vv.maxPrice")),
                      Integer.parseInt(m.get("vv.backups")));
              updateTarget(id, n);
            },
            t == null
                ? null
                : () -> {
                  updateTarget(id, null);
                }));
  }

  private void region(TradingHallRegion r) {
    String id = r == null ? UUID.randomUUID().toString() : r.id();
    var p = minecraft.player;
    String dim = s.identity() == null ? "minecraft:overworld" : s.identity().dimension();
    var f =
        fields(
            "vv.name",
            r == null ? "Trading Hall" : r.name(),
            "vv.dimension",
            r == null ? dim : r.dimension(),
            "vv.minX",
            "" + (r == null ? (int) (p == null ? 0 : p.getX()) - 8 : r.minX()),
            "vv.minY",
            "" + (r == null ? (int) (p == null ? 0 : p.getY()) - 4 : r.minY()),
            "vv.minZ",
            "" + (r == null ? (int) (p == null ? 0 : p.getZ()) - 8 : r.minZ()),
            "vv.maxX",
            "" + (r == null ? (int) (p == null ? 0 : p.getX()) + 8 : r.maxX()),
            "vv.maxY",
            "" + (r == null ? (int) (p == null ? 0 : p.getY()) + 8 : r.maxY()),
            "vv.maxZ",
            "" + (r == null ? (int) (p == null ? 0 : p.getZ()) + 8 : r.maxZ()),
            "vv.regionColor",
            Integer.toHexString(r == null ? 0xFF99AAFF : r.color()));
    minecraft.gui.setScreen(
        new FormScreen(
            this,
            "vv.regions",
            f,
            m -> {
              var n =
                  new TradingHallRegion(
                      id,
                      m.get("vv.name"),
                      m.get("vv.dimension"),
                      Double.parseDouble(m.get("vv.minX")),
                      Double.parseDouble(m.get("vv.minY")),
                      Double.parseDouble(m.get("vv.minZ")),
                      Double.parseDouble(m.get("vv.maxX")),
                      Double.parseDouble(m.get("vv.maxY")),
                      Double.parseDouble(m.get("vv.maxZ")),
                      r == null ? List.of() : r.targets(),
                      (int) Long.parseLong(m.get("vv.regionColor"), 16));
              s.regions.removeIf(a -> a.id().equals(id));
              s.regions.add(n);
              s.changed();
            },
            r == null
                ? null
                : () -> {
                  s.regions.removeIf(a -> a.id().equals(id));
                  regionId = "";
                  s.changed();
                }));
  }

  public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
    if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN
        || e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) {
      return mouseScrolled(
          page == 1 ? width : 0,
          top(),
          0,
          e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN ? -5 : 5);
    }
    return super.keyPressed(e);
  }

  public boolean isPauseScreen() {
    return false;
  }
}
