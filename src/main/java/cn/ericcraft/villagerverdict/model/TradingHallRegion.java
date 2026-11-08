package cn.ericcraft.villagerverdict.model;

import java.util.*;

public record TradingHallRegion(
    String id,
    String name,
    String dimension,
    double minX,
    double minY,
    double minZ,
    double maxX,
    double maxY,
    double maxZ,
    List<TargetTrade> targets,
    int color) {
  public TradingHallRegion {
    targets = List.copyOf(targets);
    if (!Double.isFinite(minX)
        || !Double.isFinite(minY)
        || !Double.isFinite(minZ)
        || !Double.isFinite(maxX)
        || !Double.isFinite(maxY)
        || !Double.isFinite(maxZ)
        || minX > maxX
        || minY > maxY
        || minZ > maxZ) throw new IllegalArgumentException("bounds");
  }

  public boolean contains(VillagerRecord v) {
    return dimension.equals(v.world().dimension())
        && v.x() >= minX
        && v.x() <= maxX
        && v.y() >= minY
        && v.y() <= maxY
        && v.z() >= minZ
        && v.z() <= maxZ;
  }
}
