package cn.ericcraft.villagerverdict.model;

import java.util.*;

public record TargetTrade(
    String id, String item, String enchantment, int minLevel, double maxUnitPrice, int backups) {
  public TargetTrade {
    if (backups < 1 || minLevel < 0 || !Double.isFinite(maxUnitPrice) || maxUnitPrice < 0)
      throw new IllegalArgumentException("target limits");
    Objects.requireNonNull(item);
    Objects.requireNonNull(enchantment);
  }

  public boolean matches(Trade t) {
    return t.output().item().equals(item)
        && (enchantment.isBlank()
            || t.output().enchantments().getOrDefault(enchantment, 0) >= minLevel);
  }

  public boolean priceMet(Trade t) {
    return maxUnitPrice == 0 || t.cost(false).values().stream().allMatch(p -> p <= maxUnitPrice);
  }
}
