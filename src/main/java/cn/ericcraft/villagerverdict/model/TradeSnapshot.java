package cn.ericcraft.villagerverdict.model;

import java.util.*;

public record TradeSnapshot(
    List<Trade> trades, long checkedAt) {

  public TradeSnapshot {
    trades = List.copyOf(trades);
  }

  public String fingerprint() {
    var key = new StringBuilder();
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
