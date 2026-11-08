package cn.ericcraft.villagerverdict.model;

import java.util.*;

public record VillagerRecord(
    WorldIdentity world,
    UUID uuid,
    String profession,
    int level,
    boolean baby,
    double x,
    double y,
    double z,
    long lastSeen,
    TradeSnapshot snapshot,
    Manual manual,
    String note,
    Set<String> tags) {
  public enum Manual {
    AUTO,
    KEEP,
    PROTECT,
    REMOVE
  }

  public VillagerRecord {
    Objects.requireNonNull(world);
    Objects.requireNonNull(uuid);
    Objects.requireNonNull(profession);
    Objects.requireNonNull(manual);
    Objects.requireNonNull(note);
    tags = Set.copyOf(tags);
  }

  public boolean tradable() {
    return !baby && !profession.endsWith(":none") && !profession.endsWith(":nitwit");
  }

  public boolean fixed() {
    return manual == Manual.KEEP || manual == Manual.PROTECT;
  }

  public VillagerRecord withSnapshot(TradeSnapshot s) {
    return new VillagerRecord(
        world, uuid, profession, s.level(), baby, x, y, z, lastSeen, s, manual, note, tags);
  }

  public VillagerRecord withManual(Manual m, String n, Set<String> t) {
    return new VillagerRecord(
        world, uuid, profession, level, baby, x, y, z, lastSeen, snapshot, m, n, t);
  }
}
