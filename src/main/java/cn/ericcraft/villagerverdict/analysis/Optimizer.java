package cn.ericcraft.villagerverdict.analysis;

import static cn.ericcraft.villagerverdict.analysis.TradeComparison.*;
import static cn.ericcraft.villagerverdict.model.AnalysisResult.*;

import cn.ericcraft.villagerverdict.model.*;
import java.util.*;
import java.util.stream.*;

/** Deterministic greedy multicover with a reverse redundancy pass; never claims optimality. */
public final class Optimizer {
  private record Demand(String id, Trade trade, TargetTrade target, int count) {}

  public AnalysisResult analyze(
      Collection<VillagerRecord> input,
      List<TargetTrade> targets,
      Set<UUID> loaded,
      long now,
      long ttl,
      int backups) {
    var records = input.stream().sorted(Comparator.comparing(v -> v.uuid().toString())).toList();
    var known = records.stream().filter(v -> v.snapshot() != null).toList();
    var all =
        known.stream()
            .flatMap(v -> v.snapshot().trades().stream())
            .distinct()
            .sorted(priceOrder())
            .toList();
    var demands = new ArrayList<Demand>();
    if (targets.isEmpty()) {
      for (Trade t : all)
        if (all.stream().noneMatch(a -> !a.equals(t) && replaces(a, t) && !replaces(t, a))) {
          if (demands.stream().noneMatch(d -> coversValue(d.trade, t) && coversValue(t, d.trade)))
            demands.add(
                new Demand(
                    t.output().name() + t.output().enchantments() + " / " + t.baseInput().item(),
                    t,
                    null,
                    backups));
        }
    } else
      for (TargetTrade t : targets) {
        var matches = all.stream().filter(t::matches).toList();
        var highest =
            matches.stream()
                .filter(a -> matches.stream().noneMatch(b -> b.output().higherThan(a.output())))
                .toList();
        // Keep the highest known level even if its price exceeds the target.
        for (Trade a : highest)
          if (demands.stream()
              .noneMatch(
                  d ->
                      d.id.startsWith(t.id() + "|")
                          && coversValue(d.trade, a)
                          && coversValue(a, d.trade)))
            demands.add(
                new Demand(
                    t.id() + "|" + a.output().name() + a.output().enchantments(),
                    a,
                    t,
                    t.backups()));
        if (matches.isEmpty()) demands.add(new Demand(t.id(), null, t, t.backups()));
      }
    var covers = new LinkedHashMap<UUID, Set<Integer>>();
    for (var v : known) {
      var set = new LinkedHashSet<Integer>();
      for (int i = 0; i < demands.size(); i++) {
        var d = demands.get(i);
        if (d.trade != null
            && v.snapshot().trades().stream().anyMatch(a -> coversValue(a, d.trade))) set.add(i);
      }
      covers.put(v.uuid(), set);
    }
    var selected = new LinkedHashSet<UUID>();
    records.stream().filter(VillagerRecord::fixed).forEach(v -> selected.add(v.uuid()));
    int[] counts = new int[demands.size()];
    selected.forEach(id -> covers.getOrDefault(id, Set.of()).forEach(i -> counts[i]++));
    while (true) {
      VillagerRecord best = null;
      int gain = 0;
      for (var v : known)
        if (!selected.contains(v.uuid()) && v.manual() != VillagerRecord.Manual.REMOVE) {
          int g =
              (int)
                  covers.get(v.uuid()).stream()
                      .filter(i -> counts[i] < demands.get(i).count)
                      .count();
          if (g > gain || g > 0 && g == gain && prefer(v, best, loaded, now, ttl) < 0) {
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
        gaps.add(
            (d.target == null ? d.id : d.target.item() + " " + d.target.enchantment())
                + ": "
                + counts[i]
                + "/"
                + d.count);
      if (d.target != null
          && d.trade != null
          && known.stream()
                  .filter(v -> selected.contains(v.uuid()))
                  .filter(
                      v ->
                          v.snapshot().trades().stream()
                              .anyMatch(t -> coversValue(t, d.trade) && d.target.priceMet(t)))
                  .count()
              < d.count)
        gaps.add(d.target.item() + " " + d.target.enchantment() + ": price condition unmet");
    }
    var verdicts = new LinkedHashMap<UUID, Verdict>();
    for (var v : records) {
      if (v.fixed()) {
        verdicts.put(
            v.uuid(),
            new Verdict(
                Status.KEEP,
                Confidence.HIGH,
                "manual protection / keep",
                List.of(),
                0,
                covers.getOrDefault(v.uuid(), Set.of()).size()));
        continue;
      }
      if (v.snapshot() == null || v.snapshot().trades().isEmpty()) {
        verdicts.put(
            v.uuid(),
            new Verdict(
                Status.REVIEW,
                Confidence.LOW,
                "uninspected or completeness unknown",
                List.of(),
                0,
                0));
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
              if (replaces(a, t) && (alt == null || prefer(b, alt, loaded, now, ttl) < 0)) {
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
                  trade.output().higherThan(t.output()),
                  loaded.contains(alt.uuid()) && !alt.snapshot().stale(now, ttl)));
      }
      boolean stale = v.snapshot().stale(now, ttl);
      boolean confirmed = reasons.stream().allMatch(ComparisonResult::confirmed);
      var confidence =
          stale || v.snapshot().completeness() != TradeSnapshot.Completeness.CURRENT
              ? Confidence.LOW
              : confirmed && loaded.contains(v.uuid()) ? Confidence.HIGH : Confidence.MEDIUM;
      var status =
          stale
              ? Status.REVIEW
              : selected.contains(v.uuid())
                  ? Status.KEEP
                  : stale || unique > 0 || !confirmed ? Status.REVIEW : Status.REDUNDANT;
      String reason =
          status == Status.KEEP
              ? "coverage / unique value"
              : status == Status.REDUNDANT
                  ? "all known trades have replacements"
                  : "stale, unavailable replacement or coverage gap";
      if (v.manual() == VillagerRecord.Manual.REMOVE) reason = "manual exclusion; " + reason;
      if (confidence == Confidence.LOW) reason += "; unknown future trades";
      verdicts.put(
          v.uuid(),
          new Verdict(
              status,
              confidence,
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

  private int prefer(VillagerRecord a, VillagerRecord b, Set<UUID> loaded, long now, long ttl) {
    if (b == null) return -1;
    int c =
        Boolean.compare(
            !loaded.contains(a.uuid()) || a.snapshot().stale(now, ttl),
            !loaded.contains(b.uuid()) || b.snapshot().stale(now, ttl));
    if (c != 0) return c;
    // Lexicographic, resource-aware vector: no exchange rate between different currencies.
    var ap = a.snapshot().trades().stream().sorted(priceOrder()).toList();
    var bp = b.snapshot().trades().stream().sorted(priceOrder()).toList();
    for (int i = 0; i < Math.min(ap.size(), bp.size()); i++) {
      c = vectorOrder(ap.get(i).cost(false), bp.get(i).cost(false));
      if (c != 0) return c;
    }
    return a.uuid().toString().compareTo(b.uuid().toString());
  }
}
