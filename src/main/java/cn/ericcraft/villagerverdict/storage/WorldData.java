package cn.ericcraft.villagerverdict.storage;

import cn.ericcraft.villagerverdict.model.*;
import java.util.*;

public record WorldData(
    int version,
    WorldIdentity identity,
    List<VillagerRecord> villagers,
    List<TargetTrade> targets,
    List<TradingHallRegion> regions) {
  public WorldData {
    if (version < 1 || version > 2) throw new IllegalArgumentException("unsupported version");
    Objects.requireNonNull(identity);
    villagers = List.copyOf(villagers);
    targets = List.copyOf(targets);
    regions = List.copyOf(regions);
    if (villagers.stream().anyMatch(v -> !v.world().equals(identity)))
      throw new IllegalArgumentException("world mismatch");
    if (villagers.stream().map(VillagerRecord::uuid).distinct().count() != villagers.size())
      throw new IllegalArgumentException("duplicate UUID");
  }

  public static WorldData empty(WorldIdentity id) {
    return new WorldData(2, id, List.of(), List.of(), List.of());
  }
}
