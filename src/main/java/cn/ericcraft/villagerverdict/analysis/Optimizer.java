package cn.ericcraft.villagerverdict.analysis;

import static cn.ericcraft.villagerverdict.analysis.TradeComparison.*;
import static cn.ericcraft.villagerverdict.model.AnalysisResult.*;

import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import java.util.stream.*;

/** Deterministic greedy multicover with a reverse redundancy pass; never claims optimality. */
public final class Optimizer {
  private record Demand(String id, Trade trade, int count, boolean priceGuarantee) {}

  public AnalysisResult analyze(Collection<VillagerRecord> input, int backups) {
    if (backups < 1) throw new IllegalArgumentException("backups");
    var recommended = new LinkedHashSet<UUID>();
    var verdicts = new LinkedHashMap<UUID, Verdict>();
    var gaps = new ArrayList<String>();
    var professions = input.stream().collect(Collectors.groupingBy(
        VillagerRecord::profession, TreeMap::new, Collectors.toList()));
    for (var group : professions.values()) {
      // Keep the source snapshots intact; ordinary trades never enter optimization.
      var filtered = group.stream().map(v -> v.snapshot() == null ? v
          : v.withSnapshot(new TradeSnapshot(ProfessionHighlights.trades(v), v.snapshot().checkedAt()))).toList();
      var result = analyzeProfession(filtered, backups);
      recommended.addAll(result.recommended());
      verdicts.putAll(result.verdicts());
      gaps.addAll(result.gaps());
    }
    return new AnalysisResult(recommended, verdicts, gaps);
  }

  private AnalysisResult analyzeProfession(Collection<VillagerRecord> input, int backups) {
    var records = input.stream().sorted(Comparator.comparing(v -> v.uuid().toString())).toList();
    var known = records.stream().filter(v -> v.snapshot() != null).toList();
    var all =
        known.stream()
            .flatMap(v -> v.snapshot().trades().stream())
            .distinct()
            .sorted(priceOrder())
            .toList();
    var demands = new ArrayList<Demand>();
    for (Trade t : all)
      if (all.stream().noneMatch(a -> !a.equals(t) && replaces(a, t) && !replaces(t, a))) {
        if (demands.stream().noneMatch(d -> coversValue(d.trade, t) && coversValue(t, d.trade)))
          demands.add(new Demand(t.output().item() + " " + t.output().enchantments(), t, backups, false));
      }
    // Commodity backups may cost more, but at least one retained trader must preserve
    // each nondominated price vector. Use the same replacement rule as the verdict pass.
    var priceDemands = new ArrayList<Demand>();
    for (var t : all) {
      if (all.stream().anyMatch(a -> replaces(a, t) && !replaces(t, a))) continue;
      if (priceDemands.stream().anyMatch(d -> replaces(d.trade, t) && replaces(t, d.trade))) continue;
      priceDemands.add(new Demand("best price: " + t.output().name() + t.output().enchantments(),
          t, 1, true));
    }
    demands.addAll(priceDemands);
    var covers = new LinkedHashMap<UUID, Set<Integer>>();
    for (var v : known) {
      var set = new LinkedHashSet<Integer>();
      for (int i = 0; i < demands.size(); i++) {
        var d = demands.get(i);
        if (d.trade != null
            && v.snapshot().trades().stream().anyMatch(a -> d.priceGuarantee ? replaces(a, d.trade) : coversValue(a, d.trade))) set.add(i);
      }
      covers.put(v.uuid(), set);
    }
    var selected = new LinkedHashSet<UUID>();
    known.stream().filter(VillagerRecord::fixed).forEach(v -> selected.add(v.uuid()));
    int[] counts = new int[demands.size()];
    selected.forEach(id -> covers.getOrDefault(id, Set.of()).forEach(i -> counts[i]++));
    while (true) {
      VillagerRecord best = null;
      int gain = 0;
      for (var v : known)
        if (!selected.contains(v.uuid())) {
          int g =
              (int)
                  covers.get(v.uuid()).stream()
                      .filter(i -> counts[i] < demands.get(i).count)
                      .count();
          if (g > gain || g > 0 && g == gain && prefer(v, best) < 0) {
            gain = g;
            best = v;
          }
        }
      if (best == null) break;
      selected.add(best.uuid());
      covers.get(best.uuid()).forEach(i -> counts[i]++);
    }
    var reversed = new ArrayList<>(records);
    Collections.reverse(reversed);
    for (var v : reversed)
      if (selected.contains(v.uuid())
          && !v.fixed()
          && covers.getOrDefault(v.uuid(), Set.of()).stream()
              .allMatch(i -> counts[i] > demands.get(i).count)) {
        selected.remove(v.uuid());
        covers.getOrDefault(v.uuid(), Set.of()).forEach(i -> counts[i]--);
      }
    var gaps = new ArrayList<String>();
    for (int i = 0; i < counts.length; i++) {
      var d = demands.get(i);
      if (counts[i] < d.count)
        gaps.add(d.id + ": " + counts[i] + "/" + d.count);
    }
    var verdicts = new LinkedHashMap<UUID, Verdict>();
    for (var v : known) {
      if (v.fixed()) {
        verdicts.put(
            v.uuid(),
            new Verdict(
                Status.KEEP,
                "manual protection / keep",
                List.of(),
                0,
                covers.getOrDefault(v.uuid(), Set.of()).size()));
        continue;
      }
      var reasons = new ArrayList<ComparisonResult>();
      int unique = 0;
      for (var t : v.snapshot().trades()) {
        VillagerRecord alt = null;
        Trade trade = null;
        for (var b : known)
          if (!b.uuid().equals(v.uuid()) && selected.contains(b.uuid()))
            for (var a : b.snapshot().trades())
              if (replaces(a, t) && (alt == null || prefer(b, alt) < 0)) {
                alt = b;
                trade = a;
              }
        if (alt == null) unique++;
        else
          reasons.add(
              new ComparisonResult(
                  alt.uuid(),
                  alt.x(),
                  alt.y(),
                  alt.z(),
                  t,
                  trade,
                  difference(trade, t, false),
                  difference(trade, t, true),
                  trade.output().higherThan(t.output())));
      }
      var status = selected.contains(v.uuid()) ? Status.KEEP : Status.DISCARD;
      String reason =
          status == Status.KEEP
              ? "coverage / unique value"
              : "all known trades have replacements";
      if (status == Status.DISCARD && v.snapshot().trades().isEmpty()) reason = "no signature goods";
      verdicts.put(
          v.uuid(),
          new Verdict(
              status,
              reason,
              reasons,
              unique,
              covers.getOrDefault(v.uuid(), Set.of()).size()));
    }
    return new AnalysisResult(selected, verdicts, gaps);
  }

  private boolean coversValue(Trade a, Trade b) {
    return a.output().dominates(b.output())
        && a.cost(false).keySet().equals(b.cost(false).keySet());
  }

  private int prefer(VillagerRecord a, VillagerRecord b) {
    if (b == null) return -1;
    // Lexicographic, resource-aware vector: no exchange rate between different currencies.
    var ap = a.snapshot().trades().stream().sorted(priceOrder()).toList();
    var bp = b.snapshot().trades().stream().sorted(priceOrder()).toList();
    int c;
    for (int i = 0; i < Math.min(ap.size(), bp.size()); i++) {
      c = vectorOrder(ap.get(i).cost(false), bp.get(i).cost(false));
      if (c != 0) return c;
    }
    return a.uuid().toString().compareTo(b.uuid().toString());
  }
}
