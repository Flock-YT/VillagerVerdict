package cn.ericcraft.villagerverdict.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

public final class VillagerverdictClient implements ClientModInitializer {
  public record Shortcut(String id, KeyMapping mapping) {}

  private static final List<Shortcut> SHORTCUTS = new ArrayList<>();
  private static ShortcutManager shortcutManager;

  public static ShortcutManager shortcutManager() {
    return shortcutManager;
  }

  public static List<Shortcut> shortcuts() {
    return List.copyOf(SHORTCUTS);
  }

  public static void resetShortcuts() {
    shortcutManager.reset();
  }

  public void onInitializeClient() {
    var s = VerdictService.INSTANCE;
    s.init();
    var category =
        KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("villagerverdict", "controls"));
    var open = key("manage", InputConstants.KEY_F8, category);
    var scan = key("scan", InputConstants.UNKNOWN.getValue(), category);
    var mark = key("mark", InputConstants.UNKNOWN.getValue(), category);
    var next = key("navigate", InputConstants.UNKNOWN.getValue(), category);
    var cancelNavigate = key("cancelNavigate", InputConstants.UNKNOWN.getValue(), category);
    var probe = key("probe", InputConstants.UNKNOWN.getValue(), category);
    shortcutManager = new ShortcutManager(
        net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
            .resolve("villagerverdict/shortcuts.json"), SHORTCUTS);
    ClientLifecycleEvents.CLIENT_STARTED.register(mc -> shortcutManager.load());
    ClientEntityEvents.ENTITY_LOAD.register((e, l) -> s.load(e));
    ClientEntityEvents.ENTITY_UNLOAD.register((e, l) -> s.unload(e));
    UseEntityCallback.EVENT.register(
        (player, level, hand, entity, hit) -> {
          if (level.isClientSide()) s.manual(entity);
          return InteractionResult.PASS;
        });
    net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register(
        (player, level, hand, hit) -> {
          if (level.isClientSide()) s.humanAction();
          return InteractionResult.PASS;
        });
    net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
        (player, level, hand, entity, hit) -> {
          if (level.isClientSide()) s.humanAction();
          return InteractionResult.PASS;
        });
    ClientTickEvents.END_CLIENT_TICK.register(
        mc -> {
          s.tick();
          while (open.consumeClick())
            if (mc.gui.screen() == null) mc.gui.setScreen(new VerdictScreen());
          boolean internal = shortcutManager.editable() && mc.player != null && mc.gui.screen() == null;
          while (scan.consumeClick()) if (internal) ShortcutActions.scan();
          while (mark.consumeClick()) if (internal) ShortcutActions.toggleMarkers();
          while (next.consumeClick()) if (internal) ShortcutActions.navigateNext();
          while (cancelNavigate.consumeClick()) if (internal) ShortcutActions.cancelNavigation();
          while (probe.consumeClick()) if (internal) ShortcutActions.toggleProbe();
        });
    ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
      shortcutManager.close();
      s.close();
    });
  }

  private KeyMapping key(String name, int code, KeyMapping.Category c) {
    var mapping = new KeyMapping("key.villagerverdict." + name, InputConstants.Type.KEYBOARD, code, c);
    if (name.equals("manage")) KeyMappingHelper.registerKeyMapping(mapping);
    SHORTCUTS.add(new Shortcut(name, mapping));
    return mapping;
  }
}
