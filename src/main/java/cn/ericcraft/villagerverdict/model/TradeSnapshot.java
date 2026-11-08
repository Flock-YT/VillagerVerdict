package cn.ericcraft.villagerverdict.model;

import java.util.*;

public record TradeSnapshot(
    List<Trade> trades, int level, long checkedAt, Completeness completeness) {
  public enum Completeness {
    CURRENT,
    UNLOCKED_LEVELS_REMAIN,
    UNKNOWN
  }

  public TradeSnapshot {
    trades = List.copyOf(trades);
    Objects.requireNonNull(completeness);
  }

  public boolean stale(long now, long ttl) {
    return now < checkedAt || now - checkedAt >= ttl;
  }

  public String fingerprint() {
    var key = new StringBuilder().append(level).append(";");
    for (var t : trades) {
      for (var item : Arrays.asList(t.baseInput(), t.actualInput(), t.secondInput(), t.output())) {
        String json =
            item == null ? "null" : item.codecJson() + "#" + item.resource() + "#" + item.count();
        key.append(json.length()).append(':').append(json);
      }
      key.append(';').append(t.uses()).append('/').append(t.maxUses()).append(';');
    }
    try {
      return HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(key.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }
}
