package cn.ericcraft.villagerverdict.analysis;

import cn.ericcraft.villagerverdict.model.*;
import java.util.*;

/** Transient comparison only; no change history is persisted. */
public record SnapshotDelta(boolean price, boolean stock, boolean unlocked) {
  public static SnapshotDelta between(TradeSnapshot before, TradeSnapshot after) {
    if (before == null) return new SnapshotDelta(false, false, !after.trades().isEmpty());
    boolean price = false, stock = false, unlocked = after.level() > before.level();
    for (var t : after.trades()) {
      var old =
          before.trades().stream()
              .filter(
                  b ->
                      b.output().resource().equals(t.output().resource())
                          && b.baseInput().resource().equals(t.baseInput().resource())
                          && Objects.equals(
                              b.secondInput() == null ? null : b.secondInput().resource(),
                              t.secondInput() == null ? null : t.secondInput().resource()))
              .findFirst();
      if (old.isEmpty()) unlocked = true;
      else {
        var b = old.get();
        price |= !b.cost(false).equals(t.cost(false)) || !b.cost(true).equals(t.cost(true));
        stock |= b.uses() != t.uses() || b.maxUses() != t.maxUses();
      }
    }
    return new SnapshotDelta(price, stock, unlocked);
  }
}
