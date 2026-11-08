package cn.ericcraft.villagerverdict.model;

import java.util.*;

public record Trade(
    ItemData baseInput,
    ItemData actualInput,
    ItemData secondInput,
    ItemData output,
    int uses,
    int maxUses) {
  public Trade {
    Objects.requireNonNull(baseInput);
    Objects.requireNonNull(actualInput);
    Objects.requireNonNull(output);
    if (maxUses < 0 || uses < 0) throw new IllegalArgumentException("stock");
  }

  public TradeKey key() {
    return new TradeKey(baseInput, actualInput, secondInput, output);
  }

  public boolean soldOut() {
    return uses >= maxUses;
  }

  public Map<String, Double> cost(boolean actual) {
    var m = new TreeMap<String, Double>();
    ItemData a = actual ? actualInput : baseInput;
    m.merge(a.resource(), (double) a.count() / output.count(), Double::sum);
    if (secondInput != null)
      m.merge(secondInput.resource(), (double) secondInput.count() / output.count(), Double::sum);
    return Collections.unmodifiableMap(m);
  }
}
