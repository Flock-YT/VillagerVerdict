package cn.ericcraft.villagerverdict.storage;

import cn.ericcraft.villagerverdict.model.*;
import java.util.*;

public record WorldData(
    int version,
    WorldIdentity identity,
    List<CachedTrade> cachedTrades,
    List<TargetTrade> targets) {
  public WorldData {
    if (version != 5) throw new IllegalArgumentException("unsupported version");
    Objects.requireNonNull(identity);
    cachedTrades = List.copyOf(cachedTrades);
    targets = List.copyOf(targets);
    if (cachedTrades.stream().map(CachedTrade::uuid).distinct().count() != cachedTrades.size())
      throw new IllegalArgumentException("duplicate UUID");
  }

  public static WorldData empty(WorldIdentity id) {
    return new WorldData(5, id, List.of(), List.of());
  }

  /** Returns this world's configuration with all cached trades removed. */
  public WorldData clearVillagers() {
    return new WorldData(version, identity, List.of(), targets);
  }
}
