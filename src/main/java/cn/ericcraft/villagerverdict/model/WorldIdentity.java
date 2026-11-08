package cn.ericcraft.villagerverdict.model;

import java.util.Objects;

public record WorldIdentity(String server, String dataset, String dimension) {
  public WorldIdentity {
    Objects.requireNonNull(server);
    Objects.requireNonNull(dataset);
    Objects.requireNonNull(dimension);
  }
}
