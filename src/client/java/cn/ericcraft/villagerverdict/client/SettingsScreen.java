package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.storage.Settings;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;

public final class SettingsScreen {
  private SettingsScreen() {}

  public static void open(Screen parent) {
    var service = VerdictService.INSTANCE;
    var s = service.settings;
    var excluded = new HashSet<>(s.excludedProfessions());
    var fields =
        FormScreen.fields(
            "vv.markers",
            "" + s.markers(),
            "vv.statusMarks",
            "" + s.statusMarks(),
            "vv.summaries",
            "" + s.summaries(),
            "vv.green",
            Integer.toHexString(s.green()),
            "vv.yellow",
            Integer.toHexString(s.yellow()),
            "vv.red",
            Integer.toHexString(s.red()),
            "vv.radius",
            "" + s.radius(),
            "vv.scanTicks",
            "" + s.scanTicks(),
            "vv.autoScan",
            "" + s.autoScan(),
            "vv.cacheMillis",
            "" + s.cacheMillis(),
            "vv.intervalMillis",
            "" + s.intervalMillis(),
            "vv.timeoutMillis",
            "" + s.timeoutMillis(),
            "vv.backups",
            "" + s.backups(),
            "vv.notifications",
            "" + s.notifications(),
            "vv.dataset",
            s.dataset());
    Minecraft.getInstance()
        .gui
        .setScreen(
            new FormScreen(
                parent,
                "vv.settings",
                fields,
                m -> {
                  service.settings(
                      new Settings(
                          3,
                          bool(m, "vv.markers"),
                          bool(m, "vv.statusMarks"),
                          bool(m, "vv.summaries"),
                          color(m, "vv.green"),
                          color(m, "vv.yellow"),
                          color(m, "vv.red"),
                          integer(m, "vv.radius"),
                          integer(m, "vv.scanTicks"),
                          bool(m, "vv.autoScan"),
                          s.markerLimit(),
                          s.textRange(),
                          Long.parseLong(m.get("vv.cacheMillis")),
                          Long.parseLong(m.get("vv.intervalMillis")),
                          Long.parseLong(m.get("vv.timeoutMillis")),
                          integer(m, "vv.backups"),
                          bool(m, "vv.notifications"),
                          s.autoServers(),
                          s.acknowledged(),
                          m.get("vv.dataset"), excluded));
                },
                null).action("vv.professions", form -> Minecraft.getInstance().gui.setScreen(
                    new ProfessionScreen(form, excluded, chosen -> {
                      excluded.clear();
                      excluded.addAll(chosen);
                    }))));
  }

  private static boolean bool(Map<String, String> m, String k) {
    String v = m.get(k);
    if (!v.equals("true") && !v.equals("false"))
      throw new IllegalArgumentException(k + ": true / false");
    return Boolean.parseBoolean(v);
  }

  private static int integer(Map<String, String> m, String k) {
    return Integer.parseInt(m.get(k));
  }

  private static int color(Map<String, String> m, String k) {
    return (int) Long.parseLong(m.get(k).replace("#", ""), 16) | 0xFF000000;
  }

  public static void toggleMarkers() {
    var service = VerdictService.INSTANCE;
    var s = service.settings;
    service.settings(
        new Settings(
            3,
            !s.markers(),
            s.statusMarks(),
            s.summaries(),
            s.green(),
            s.yellow(),
            s.red(),
            s.radius(),
            s.scanTicks(),
            s.autoScan(),
            s.markerLimit(),
            s.textRange(),
            s.cacheMillis(),
            s.intervalMillis(),
            s.timeoutMillis(),
            s.backups(),
            s.notifications(),
            s.autoServers(),
            s.acknowledged(),
            s.dataset(), s.excludedProfessions()));
  }

  public static void toggleProbe() {
    toggleProbe(message -> {});
  }

  public static void toggleProbe(Consumer<Component> feedback) {
    var service = VerdictService.INSTANCE;
    var identity = service.identity();
    if (identity == null || !service.ready()) {
      feedback.accept(Component.translatable("vv.feedback.unavailable"));
      return;
    }
    if (service.probe.running()) {
      service.pause();
      feedback.accept(Component.translatable("vv.feedback.probePaused"));
      return;
    }
    var mc = Minecraft.getInstance();
    if (!service.settings.acknowledged().contains(service.identity().server())) {
      Screen parent = mc.gui.screen();
      mc.gui.setScreen(
          new WorldConfirmScreen(
              yes -> {
                mc.gui.setScreen(parent);
                if (!yes) feedback.accept(Component.translatable("vv.feedback.probeCancelled"));
                else if (!service.ready() || !identity.equals(service.identity()))
                  feedback.accept(Component.translatable("vv.feedback.unavailable"));
                else {
                  service.enable(true);
                  feedback.accept(Component.translatable("vv.feedback.probeOn"));
                }
              },
              Component.translatable("vv.probe"),
              Component.translatable("vv.probeExplanation")));
    } else {
      service.enable(true);
      feedback.accept(Component.translatable("vv.feedback.probeOn"));
    }
  }
}
