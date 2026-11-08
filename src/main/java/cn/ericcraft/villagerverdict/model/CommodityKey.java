package cn.ericcraft.villagerverdict.model;

import java.util.*;

public record CommodityKey(String outputResource, Set<String> inputResources) {
  public CommodityKey {
    inputResources = Set.copyOf(inputResources);
  }

  public static CommodityKey of(Trade t) {
    return new CommodityKey(t.output().resource(), t.cost(false).keySet());
  }
}
