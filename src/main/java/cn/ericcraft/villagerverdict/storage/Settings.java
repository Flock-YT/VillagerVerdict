package cn.ericcraft.villagerverdict.storage;

import java.util.*;

public record Settings(
    int version,
    boolean markers,
    boolean statusMarks,
    boolean summaries,
    int green,
    int yellow,
    int red,
    int radius,
    int scanTicks,
    boolean autoScan,
    int markerLimit,
    int textRange,
    long intervalMillis,
    long timeoutMillis,
    int backups,
    boolean notifications,
    Map<String, Boolean> autoServers,
    Set<String> acknowledged,
    String dataset,
    Set<String> excludedProfessions) {
  public Settings {
    autoServers = Map.copyOf(autoServers);
    acknowledged = Set.copyOf(acknowledged);
    excludedProfessions = Set.copyOf(excludedProfessions);
    if (version != 4
        || radius < 1
        || radius > 1024
        || scanTicks < 1
        || markerLimit < 1
        || markerLimit > 2048
        || textRange < 1
        || intervalMillis < 100
        || timeoutMillis < 1000
        || backups < 1) throw new IllegalArgumentException("settings");
  }

  public static Settings defaults() {
    return new Settings(
        4,
        true,
        true,
        true,
        0xFF55DD88,
        0xFFFFCC55,
        0xFFFF6677,
        80,
        20,
        true,
        128,
        32,
        100,
        5000,
        1,
        true,
        Map.of(),
        Set.of(),
        "default",
        Set.of());
  }

  public boolean professionEnabled(String profession) {
    return !excludedProfessions.contains(profession);
  }

  public Settings withExcludedProfessions(Set<String> excluded) {
    return new Settings(version, markers, statusMarks, summaries, green, yellow, red, radius,
        scanTicks, autoScan, markerLimit, textRange, intervalMillis, timeoutMillis,
        backups, notifications, autoServers, acknowledged, dataset, excluded);
  }

  public Settings auto(String server, boolean enabled, boolean ack) {
    var m = new HashMap<>(autoServers);
    if (enabled) m.put(server, true);
    else m.remove(server);
    var a = new HashSet<>(acknowledged);
    if (ack) a.add(server);
    return new Settings(
        version,
        markers,
        statusMarks,
        summaries,
        green,
        yellow,
        red,
        radius,
        scanTicks,
        autoScan,
        markerLimit,
        textRange,
        intervalMillis,
        timeoutMillis,
        backups,
        notifications,
        m,
        a,
        dataset,
        excludedProfessions);
  }
}
