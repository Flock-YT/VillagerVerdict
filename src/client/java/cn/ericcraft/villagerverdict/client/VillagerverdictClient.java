package cn.ericcraft.villagerverdict.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

public final class VillagerverdictClient implements ClientModInitializer {
  public void onInitializeClient() {
    var s = VerdictService.INSTANCE;
    s.init();
    var category =
        KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("villagerverdict", "controls"));
    var open = key("manage", InputConstants.KEY_F8, category);
    var scan = key("scan", InputConstants.KEY_F9, category);
    var mark = key("mark", InputConstants.KEY_F10, category);
    var next = key("navigate", InputConstants.UNKNOWN.getValue(), category);
    var probe = key("probe", InputConstants.UNKNOWN.getValue(), category);
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
          while (open.consumeClick()) mc.gui.setScreen(new VerdictScreen());
          while (scan.consumeClick()) s.scan();
          while (mark.consumeClick()) SettingsScreen.toggleMarkers();
          while (next.consumeClick()) s.navigateNext();
          while (probe.consumeClick()) SettingsScreen.toggleProbe();
        });
    ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> s.close());
    HudElementRegistry.attachElementBefore(
        VanillaHudElements.CHAT,
        Identifier.fromNamespaceAndPath("villagerverdict", "navigation"),
        (g, dt) -> {
          var r = s.record(s.selected);
          if (r != null && s.settings.markers()) {
            g.text(
                net.minecraft.client.Minecraft.getInstance().font,
                net.minecraft.network.chat.Component.translatable(
                    "vv.navigation",
                    (int) r.x(),
                    (int) r.y(),
                    (int) r.z(),
                    (int) Math.sqrt(s.distanceSquared(r))),
                8,
                8,
                0xFFFFFFFF);
            if (!s.isLoaded(r.uuid()))
              g.text(
                  net.minecraft.client.Minecraft.getInstance().font,
                  net.minecraft.network.chat.Component.translatable(
                      "vv.lastSeen", java.time.Instant.ofEpochMilli(r.lastSeen()).toString()),
                  8,
                  20,
                  0xFFFFCC55);
          }
        });
  }

  private KeyMapping key(String name, int code, KeyMapping.Category c) {
    return KeyMappingHelper.registerKeyMapping(
        new KeyMapping("key.villagerverdict." + name, InputConstants.Type.KEYBOARD, code, c));
  }
}
