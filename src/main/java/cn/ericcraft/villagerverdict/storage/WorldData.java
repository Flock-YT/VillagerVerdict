package cn.ericcraft.villagerverdict.storage;

import cn.ericcraft.villagerverdict.model.*;
import java.util.*;

public record WorldData(
    int version,
    WorldIdentity identity,
    List<CachedTrade> cachedTrades,
    List<TargetTrade> targets,
    List<TradingHallRegion> regions) {
  public WorldData {
    if (version != 4) throw new IllegalArgumentException("unsupported version");
    Objects.requireNonNull(identity);
    cachedTrades = List.copyOf(cachedTrades);
    targets = List.copyOf(targets);
    regions = List.copyOf(regions);
    if (cachedTrades.stream().map(CachedTrade::uuid).distinct().count() != cachedTrades.size())
      throw new IllegalArgumentException("duplicate UUID");
  }

  public static WorldData empty(WorldIdentity id) {
    return new WorldData(4, id, List.of(), List.of(), List.of());
  }

  /** Returns this world's configuration with all cached trades removed. */
  public WorldData clearVillagers() {
    return new WorldData(version, identity, List.of(), targets, regions);
  }
}
