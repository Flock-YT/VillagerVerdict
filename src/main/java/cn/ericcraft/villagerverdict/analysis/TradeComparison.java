package cn.ericcraft.villagerverdict.analysis;

import cn.ericcraft.villagerverdict.model.*;
import java.util.*;

public final class TradeComparison {
  private TradeComparison() {}

  public static boolean replaces(Trade a, Trade b) {
    if (!a.output().dominates(b.output()) || !a.cost(false).keySet().equals(b.cost(false).keySet()))
      return false;
    if (a.output().higherThan(b.output())) return true;
    return a.cost(false).entrySet().stream()
        .allMatch(e -> e.getValue() <= b.cost(false).get(e.getKey()) + 1e-9);
  }

  public static Map<String, Double> difference(Trade a, Trade b, boolean actual) {
    var m = new TreeMap<String, Double>();
    b.cost(actual).forEach((k, v) -> m.put(k, a.cost(actual).getOrDefault(k, 0d) - v));
    return m;
  }

  public static int vectorOrder(Map<String, Double> a, Map<String, Double> b) {
    int c = a.keySet().toString().compareTo(b.keySet().toString());
    if (c != 0) return c;
    for (String key : a.keySet()) {
      c = Double.compare(a.get(key), b.get(key));
      if (c != 0) return c;
    }
    return 0;
  }

  public static Comparator<Trade> priceOrder() {
    return (a, b) -> {
      int c = vectorOrder(a.cost(false), b.cost(false));
      return c != 0 ? c : a.key().toString().compareTo(b.key().toString());
    };
  }
}
