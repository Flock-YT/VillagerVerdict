package cn.ericcraft.villagerverdict.model;

import cn.ericcraft.villagerverdict.analysis.ProfessionHighlights;
import java.util.*;

public record TargetTrade(
    String id, String item, String enchantment, int minLevel, double maxUnitPrice) {
  public TargetTrade {
    if (minLevel < 0 || !Double.isFinite(maxUnitPrice) || maxUnitPrice < 0)
      throw new IllegalArgumentException("target limits");
    Objects.requireNonNull(item);
    Objects.requireNonNull(enchantment);
  }

  /** Saved product queries never participate in retention analysis. */
  public boolean matches(VillagerRecord record) {
    return ProfessionHighlights.trades(record).stream().anyMatch(t -> matches(t) && priceMet(t));
  }

  public boolean matches(Trade t) {
    return t.output().item().equals(item)
        && (enchantment.isBlank()
            || t.output().enchantments().containsKey(enchantment)
                && t.output().enchantments().get(enchantment) >= minLevel);
  }

  public boolean priceMet(Trade t) {
    return maxUnitPrice == 0 || t.cost(false).values().stream().allMatch(p -> p <= maxUnitPrice);
  }
}
