package cn.ericcraft.villagerverdict.storage;

import java.util.*;

public record Settings(
    int version,
    boolean markers,
    boolean statusMarks,
    boolean summaries,
    boolean outlines,
    boolean throughWalls,
    int green,
    int yellow,
    int red,
    int radius,
    int scanTicks,
    boolean autoScan,
    int markerLimit,
    int textRange,
    long cacheMillis,
    long intervalMillis,
    long timeoutMillis,
    int backups,
    boolean notifications,
    Map<String, Boolean> autoServers,
    Set<String> acknowledged,
    String dataset) {
  public Settings {
    autoServers = Map.copyOf(autoServers);
    acknowledged = Set.copyOf(acknowledged);
    if (version == 1) {
      statusMarks = true;
      version = 2;
    }
    if (version == 2) {
      autoScan = true;
      version = 3;
    }
    if (version != 3
        || radius < 1
        || radius > 1024
        || scanTicks < 1
        || markerLimit < 1
        || markerLimit > 2048
        || textRange < 1
        || cacheMillis < 1000
        || intervalMillis < 2000
        || timeoutMillis < 1000
        || backups < 1) throw new IllegalArgumentException("settings");
  }

  public static Settings defaults() {
    return new Settings(
        3,
        true,
        true,
        true,
        true,
        false,
        0xFF55DD88,
        0xFFFFCC55,
        0xFFFF6677,
        80,
        20,
        true,
        128,
        32,
        1800000,
        2000,
        5000,
        1,
        true,
        Map.of(),
        Set.of(),
        "default");
  }

  public Settings auto(String server, boolean enabled, boolean ack) {
    var m = new HashMap<>(autoServers);
    m.put(server, enabled);
    var a = new HashSet<>(acknowledged);
    if (ack) a.add(server);
    return new Settings(
        version,
        markers,
        statusMarks,
        summaries,
        outlines,
        throughWalls,
        green,
        yellow,
        red,
        radius,
        scanTicks,
        autoScan,
        markerLimit,
        textRange,
        cacheMillis,
        intervalMillis,
        timeoutMillis,
        backups,
        notifications,
        m,
        a,
        dataset);
  }
}
