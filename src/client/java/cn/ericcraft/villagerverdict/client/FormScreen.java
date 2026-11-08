package cn.ericcraft.villagerverdict.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Keyboard-accessible native EditBoxes; scrolling retains unsaved values. */
public final class FormScreen extends Screen {
  private final Screen parent;
  private final LinkedHashMap<String, String> values;
  private final Consumer<Map<String, String>> save;
  private final Runnable delete;
  private int offset;
  private String error = "";

  public FormScreen(
      Screen parent,
      String title,
      LinkedHashMap<String, String> values,
      Consumer<Map<String, String>> save,
      Runnable delete) {
    super(Component.translatable(title));
    this.parent = parent;
    this.values = values;
    this.save = save;
    this.delete = delete;
  }

  protected void init() {
    addRenderableWidget(
        Button.builder(
                Component.literal("↑"),
                b -> {
                  offset--;
                  rebuildWidgets();
                })
            .bounds(width - 56, 6, 22, 20)
            .build());
    addRenderableWidget(
        Button.builder(
                Component.literal("↓"),
                b -> {
                  offset++;
                  rebuildWidgets();
                })
            .bounds(width - 30, 6, 22, 20)
            .build());
    int visible = Math.max(1, (height - 90) / 42);
    var keys = new ArrayList<>(values.keySet());
    offset = Math.clamp(offset, 0, Math.max(0, keys.size() - visible));
    for (int i = offset; i < Math.min(keys.size(), offset + visible); i++) {
      String key = keys.get(i);
      if (values.get(key).equals("true") || values.get(key).equals("false")) {
        final String k = key;
        addRenderableWidget(
            CycleButton.onOffBuilder(Boolean.parseBoolean(values.get(key)))
                .create(
                    Math.max(12, width / 2 - 145),
                    48 + (i - offset) * 42,
                    Math.min(290, width - 24),
                    20,
                    Component.translatable(key),
                    (b, v) -> values.put(k, v.toString())));
        continue;
      }
      if (key.equals("vv.manual")) {
        final String k = key;
        addRenderableWidget(
            CycleButton.<String>builder(
                    v -> Component.translatable("vv.manual." + v), values.get(key))
                .withValues("AUTO", "KEEP", "PROTECT", "REMOVE")
                .create(
                    Math.max(12, width / 2 - 145),
                    48 + (i - offset) * 42,
                    Math.min(290, width - 24),
                    20,
                    Component.translatable(key),
                    (b, v) -> values.put(k, v)));
        continue;
      }
      var box =
          new EditBox(
              font,
              Math.max(12, width / 2 - 145),
              48 + (i - offset) * 42,
              Math.min(290, width - 24),
              20,
              Component.translatable(key));
      box.setMaxLength(8192);
      box.setValue(values.get(key));
      box.setResponder(v -> values.put(key, v));
      box.setTooltip(Tooltip.create(Component.translatable(key + ".help")));
      addRenderableWidget(box);
    }
    button(
        "vv.save",
        12,
        height - 26,
        80,
        () -> {
          try {
            save.accept(values);
            minecraft.gui.setScreen(parent);
          } catch (Exception e) {
            error = Component.translatable("vv.invalid").getString() + ": " + e.getMessage();
          }
        });
    button("gui.cancel", 98, height - 26, 80, () -> minecraft.gui.setScreen(parent));
    if (delete != null)
      button(
          "vv.delete",
          184,
          height - 26,
          80,
          () -> {
            delete.run();
            minecraft.gui.setScreen(parent);
          });
  }

  private void button(String key, int x, int y, int w, Runnable r) {
    addRenderableWidget(
        Button.builder(Component.translatable(key), b -> r.run()).bounds(x, y, w, 20).build());
  }

  public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float d) {
    g.fill(0, 0, width, height, 0xF0181E2B);
    g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
    int visible = Math.max(1, (height - 90) / 42);
    var keys = new ArrayList<>(values.keySet());
    for (int i = offset; i < Math.min(keys.size(), offset + visible); i++)
      g.text(
          font,
          Component.translatable(keys.get(i)),
          Math.max(12, width / 2 - 145),
          36 + (i - offset) * 42,
          0xFFBBD0E8);
    if (!error.isEmpty())
      g.text(font, font.plainSubstrByWidth(error, width - 16), 8, height - 40, 0xFFFF6677);
    super.extractRenderState(g, mx, my, d);
  }

  public boolean mouseScrolled(double x, double y, double a, double b) {
    offset += (int) -Math.signum(b);
    rebuildWidgets();
    return true;
  }

  public boolean keyPressed(net.minecraft.client.input.KeyEvent e) {
    if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN
        || e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) {
      offset += e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN ? 3 : -3;
      rebuildWidgets();
      return true;
    }
    return super.keyPressed(e);
  }

  public void onClose() {
    minecraft.gui.setScreen(parent);
  }

  public boolean isPauseScreen() {
    return false;
  }

  public static LinkedHashMap<String, String> fields(String... pairs) {
    var m = new LinkedHashMap<String, String>();
    for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], pairs[i + 1]);
    return m;
  }
}
