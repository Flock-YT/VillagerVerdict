package cn.ericcraft.villagerverdict;

import cn.ericcraft.villagerverdict.client.*;
import cn.ericcraft.villagerverdict.model.*;
import cn.ericcraft.villagerverdict.storage.*;
import java.nio.file.*;
import java.util.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.*;
import net.minecraft.world.phys.EntityHitResult;

/**
 * Real integrated-server packets, native screen creation and rendering; no mocked merchant packets.
 */
public class ClientIntegrationTest implements FabricClientGameTest {
  public void runTest(ClientGameTestContext c) {
    try (var world = c.worldBuilder().create()) {
      var server = world.getServer();
      var connection = world.getConnection();
      c.waitFor(mc -> VerdictService.INSTANCE.ready() && VillagerverdictClient.shortcutManager().ready());
      c.runOnClient(
          mc -> {
            var shortcuts = VillagerverdictClient.shortcuts();
            if (shortcuts.size() != 5) throw new AssertionError("shortcut registry incomplete");
            if (shortcuts.get(0).mapping().getDefaultKey().getValue() != InputConstants.KEY_F8)
              throw new AssertionError("management shortcut is not F8");
            for (int i = 1; i < shortcuts.size(); i++)
              if (!shortcuts.get(i).mapping().getDefaultKey().equals(InputConstants.UNKNOWN))
                throw new AssertionError("non-management shortcut has a default binding");
          });
      server.runCommand("tp @p 0 -60 0");
      testShortcuts(c);
      UUID id =
          server.computeOnServer(
              s -> {
                var l = s.overworld();
                var v = new Villager(EntityTypes.VILLAGER, l);
                v.setPos(1, -60, 0);
                v.setNoAi(true);
                v.setVillagerData(
                    v.getVillagerData()
                        .withProfession(
                            l.registryAccess()
                                .lookupOrThrow(
                                    net.minecraft.core.registries.Registries.VILLAGER_PROFESSION)
                                .getOrThrow(VillagerProfession.LIBRARIAN))
                        .withLevel(5));
                var offers = new MerchantOffers();
                offers.add(
                    new MerchantOffer(
                        new ItemCost(Items.EMERALD, 5), new ItemStack(Items.BOOK), 12, 1, 0));
                v.setOffers(offers);
                l.addFreshEntity(v);
                return v.getUUID();
              });
      connection.waitForClientboundEntityUpdates(EntityTypes.VILLAGER);
      c.runOnClient(
          mc -> {
            VerdictService.INSTANCE.scan();
            if (VerdictService.INSTANCE.probe.running())
              throw new AssertionError("default enabled");
          });
      c.waitTicks(45);
      c.runOnClient(
          mc -> {
            if (VerdictService.INSTANCE.record(id).snapshot() != null)
              throw new AssertionError("disabled captured automatically");
            mc.gui.setScreen(null);
            var v = mc.level.getEntity(id);
            net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT
                .invoker()
                .interact(
                    mc.player,
                    mc.level,
                    InteractionHand.MAIN_HAND,
                    v,
                    new EntityHitResult(v, v.position()));
            mc.gameMode.interact(
                mc.player, v, new EntityHitResult(v, v.position()), InteractionHand.MAIN_HAND);
          });
      c.waitForScreen(MerchantScreen.class);
      c.waitFor(mc -> VerdictService.INSTANCE.record(id).snapshot() != null);
      c.takeScreenshot("manual-merchant");
      c.runOnClient(
          mc -> {
            if (VerdictService.INSTANCE.record(id).snapshot().trades().size() != 1)
              throw new AssertionError("capture lost offers");
            mc.player.closeContainer();
            VerdictService.INSTANCE.selected = id;
            mc.gui.setScreen(new VerdictScreen());
          });
      c.waitFor(mc -> VerdictService.INSTANCE.analysis.verdicts().containsKey(id));
      c.takeScreenshot("management-en");
      c.clickScreenButton("vv.details");
      c.takeScreenshot("details-en");
      c.runOnClient(
          mc -> {
            mc.options.guiScale().set(1);
            mc.resizeGui();
            mc.gui.setScreen(new VerdictScreen());
          });
      c.takeScreenshot("wide-management");
      var reload =
          c.computeOnClient(
              mc -> {
                mc.getLanguageManager().setSelected("zh_cn");
                mc.options.languageCode = "zh_cn";
                return mc.reloadResourcePacks();
              });
      c.waitFor(mc -> reload.isDone(), 600);
      c.waitFor(mc -> mc.gui.overlay() == null, 600);
      testNativeBindings(c, "zh");
      c.setScreen(VerdictScreen::new);
      c.takeScreenshot("management-zh");
      c.runOnClient(
          mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
          });
      c.clickScreenButton("vv.targets");
      c.clickScreenButton("vv.add");
      c.takeScreenshot("target-editor");
      c.clickScreenButton("vv.save");
      c.clickScreenButton("vv.regions");
      c.clickScreenButton("vv.add");
      c.takeScreenshot("region-editor");
      c.clickScreenButton("vv.save");
      c.clickScreenButton("vv.settings");
      c.takeScreenshot("settings");
      c.clickScreenButton("gui.cancel");
      c.runOnClient(
          mc -> {
            mc.gui.setScreen(null);
            var service = VerdictService.INSTANCE;
            var r = service.record(id);
            service.edit(
                new cn.ericcraft.villagerverdict.model.VillagerRecord(
                    r.world(),
                    r.uuid(),
                    r.profession(),
                    r.level(),
                    r.baby(),
                    r.x(),
                    r.y(),
                    r.z(),
                    r.lastSeen(),
                    null,
                    r.manual(),
                    r.note(),
                    r.tags()));
            service.probe.reset();
            service.enable(true);
          });
      c.waitFor(mc -> VerdictService.INSTANCE.record(id).snapshot() != null, 600);
      c.waitFor(mc -> mc.player.containerMenu == mc.player.inventoryMenu, 200);
      c.runOnClient(
          mc -> {
            if (mc.gui.screen() != null)
              throw new AssertionError("automatic merchant screen leaked");
            if (!mc.player.containerMenu.getCarried().isEmpty())
              throw new AssertionError("cursor changed");
            VerdictService.INSTANCE.enable(false);
            mc.gui.setScreen(new InventoryScreen(mc.player));
          });
      c.takeScreenshot("inventory-after-probe");
      c.runOnClient(
          mc -> {
            mc.gui.setScreen(null);
          });
      server.runCommand("setblock 0 -60 1 minecraft:chest");
      c.waitTick();
      connection.waitForClientboundPackets();
      c.runOnClient(
          mc -> {
            mc.gameMode.useItemOn(
                mc.player,
                InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(
                    new net.minecraft.world.phys.Vec3(.5, -59.5, 1.5),
                    net.minecraft.core.Direction.NORTH,
                    new net.minecraft.core.BlockPos(0, -60, 1),
                    false));
          });
      c.waitForScreen(ContainerScreen.class);
      c.takeScreenshot("chest-after-probe");
      c.runOnClient(mc -> mc.player.closeContainer());
      connection.waitForServerboundPackets();
      server.runOnServer(
          s -> {
            var v = (Villager) s.overworld().getEntity(id);
            if (v.getOffers().getFirst().getUses() != 0) throw new AssertionError("mod traded");
          });
      server.runOnServer(
          s -> {
            for (int i = 0; i < 300; i++) {
              var v = new Villager(EntityTypes.VILLAGER, s.overworld());
              v.setNoAi(true);
              v.setPos(5 + i % 20, -60, 5 + i / 20);
              s.overworld().addFreshEntity(v);
            }
          });
      connection.waitForClientboundEntityUpdates(EntityTypes.VILLAGER);
      c.waitFor(mc -> VerdictService.INSTANCE.records().size() >= 301, 400);
      c.waitTicks(30);
      c.runOnClient(
          mc -> {
            var service = VerdictService.INSTANCE;
            service.scan();
            if (service.stats().get("nontrading") < 300)
              throw new AssertionError("nontrading stats include wrong villagers");
            if (service.markers.size() < 301 || !service.markers.containsKey(id))
              throw new AssertionError("all in-range villagers should be marked");
            System.out.println(
                "VV_PERFORMANCE loaded="
                    + service.records().size()
                    + " scan_us="
                    + service.scanNanos / 1000
                    + " analysis_us="
                    + service.analysisNanos / 1000
                    + " markers_us="
                    + service.markerNanos / 1000
                    + " async_write_us="
                    + service.writeNanos() / 1000);
          });
      c.takeScreenshot("crowded-scene");
      server.runCommand("tp @p 0 -60 -10");
      connection.waitForClientboundPackets();
      c.waitTicks(2);
      var distanceIds = server.computeOnServer(s -> {
        var ids = new ArrayList<UUID>();
        for (double x : new double[] {47.5, 49.5}) {
          var v = new Villager(EntityTypes.VILLAGER, s.overworld());
          v.setNoAi(true);
          v.setPos(x, -60, -9.5);
          s.overworld().addFreshEntity(v);
          ids.add(v.getUUID());
        }
        return ids;
      });
      c.waitFor(mc -> distanceIds.stream().allMatch(uuid -> mc.level.getEntity(uuid) != null), 400);
      c.runOnClient(mc -> {
        var service = VerdictService.INSTANCE;
        var s = service.settings;
        service.settings(new Settings(s.version(), s.markers(), s.statusMarks(), s.summaries(),
            s.outlines(), s.throughWalls(), s.green(), s.yellow(), s.red(), 48, s.scanTicks(), false,
            1, 1, s.cacheMillis(), s.intervalMillis(), s.timeoutMillis(), s.backups(),
            s.notifications(), s.autoServers(), s.acknowledged(), s.dataset()));
        service.scan();
        service.analysis = AnalysisResult.empty();
        service.tick();
        if (service.markers.size() != 302 || !service.markers.containsKey(distanceIds.getFirst())
            || service.markers.containsKey(distanceIds.getLast())
            || !service.markers.get(distanceIds.getFirst()).summary())
          throw new AssertionError("scan boundary/fallback: markers=" + service.markers.size()
              + " nearby=" + service.nearby.size() + " player=" + mc.player.position()
              + " near=" + service.markers.get(distanceIds.getFirst())
              + " outside=" + service.markers.get(distanceIds.getLast()));
      });
      c.getInput().lookAt(new net.minecraft.core.BlockPos(47, -59, -10));
      c.takeScreenshot("far-marker");
      testDataClear(c);
      c.runOnClient(
          mc -> {
            var service = VerdictService.INSTANCE;
            if (service.probe.context() != null) throw new AssertionError("context leaked");
            System.out.println(
                "VV_INTEGRATION_PASS manual capture, silent capture, safe close, GUI forms,"
                    + " inventory, no purchases");
          });
    }
    try (var other = c.worldBuilder().create()) {
      c.waitFor(mc -> VerdictService.INSTANCE.ready());
      c.runOnClient(
          mc -> {
            if (!VerdictService.INSTANCE.records().isEmpty())
              throw new AssertionError("world data crossed identity");
            System.out.println("VV_WORLD_ISOLATION_PASS");
          });
    }
  }

  private void testShortcuts(ClientGameTestContext c) {
    Path shortcutsPath = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
        .resolve("villagerverdict/shortcuts.json");
    c.runOnClient(mc -> {
      try {
        mc.options.save();
        Path options = mc.gameDirectory.toPath().resolve("options.txt");
        Files.writeString(options,
            "key_key.villagerverdict.scan:" + InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F9).getName()
                + "\nkey_key.villagerverdict.mark:" + InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F10).getName()
                + "\n", StandardOpenOption.APPEND);
        mc.options.load();
        for (var shortcut : VillagerverdictClient.shortcuts())
          if (!shortcut.id().equals("manage") && !shortcut.mapping().isUnbound())
            throw new AssertionError("legacy options restored an internal binding");
        VillagerverdictClient.shortcutManager().load();
        mc.gui.setScreen(new ShortcutScreen(new VerdictScreen()));
        for (var child : mc.gui.screen().children())
          if (child instanceof net.minecraft.client.gui.components.Button button
              && !button.getMessage().getString().equals(
                  net.minecraft.network.chat.Component.translatable("gui.done").getString()) && button.active)
            throw new AssertionError("shortcut editor allowed edits before loading completed");
      } catch (java.io.IOException e) {
        throw new AssertionError(e);
      }
    });
    c.waitFor(mc -> VillagerverdictClient.shortcutManager().editable());
    c.runOnClient(mc -> mc.gui.setScreen(null));
    testNativeBindings(c, "en");
    c.getInput().pressKey(InputConstants.KEY_F8);
    c.waitForScreen(VerdictScreen.class);
    c.clickScreenButton("vv.shortcuts");
    c.waitForScreen(ShortcutScreen.class);
    c.takeScreenshot("shortcuts-en");
    c.clickScreenButton("key.villagerverdict.scan");
    c.getInput().pressKey(InputConstants.KEY_F9);
    c.runOnClient(mc -> {
      var scan = VillagerverdictClient.shortcuts().get(1).mapping();
      if (!scan.matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F9)))
        throw new AssertionError("shortcut capture failed");
      long nativeCount = Arrays.stream(mc.options.keyMappings)
          .filter(k -> k.getName().startsWith("key.villagerverdict.")).count();
      if (nativeCount != 1) throw new AssertionError("internal keys leaked into vanilla options");
    });
    var saving = c.computeOnClient(mc -> VillagerverdictClient.shortcutManager().flush());
    c.waitFor(mc -> saving.isDone());
    saving.join();
    try (var store = new JsonStore(e -> { throw new AssertionError(e); })) {
      var persisted = store.read(shortcutsPath, ShortcutBindings.class, ShortcutBindings::defaults).join();
      if (!persisted.bindings().get("scan").equals(
          InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F9).getName()))
        throw new AssertionError("internal shortcut not persisted");
    }
    // A fresh controller starts with no runtime key state, then restores only its own file.
    var restarted = c.computeOnClient(mc -> {
      var manager = new ShortcutManager(shortcutsPath, VillagerverdictClient.shortcuts());
      manager.load();
      return manager;
    });
    c.waitFor(mc -> restarted.ready());
    c.runOnClient(mc -> {
      if (!restarted.editable() || !VillagerverdictClient.shortcuts().get(1).mapping()
          .matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F9)))
        throw new AssertionError("new shortcut controller did not restore saved binding");
      restarted.close();
      mc.options.save();
      mc.options.load();
      if (!VillagerverdictClient.shortcuts().get(1).mapping()
          .matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F9)))
        throw new AssertionError("vanilla options interfered with internal shortcut");
    });
    c.clickScreenButton("key.villagerverdict.mark");
    c.getInput().pressKey(InputConstants.KEY_F9);
    c.runOnClient(mc -> {
      if (!VillagerverdictClient.shortcuts().get(1).mapping().isUnbound())
        throw new AssertionError("duplicate shortcut was not released");
    });
    c.clickScreenButton("gui.done");
    c.runOnClient(mc -> mc.gui.setScreen(null));
    boolean markers = c.computeOnClient(mc -> VerdictService.INSTANCE.settings.markers());
    c.getInput().pressKey(InputConstants.KEY_F9);
    c.runOnClient(mc -> {
      if (VerdictService.INSTANCE.settings.markers() == markers)
        throw new AssertionError("unregistered shortcut did not receive gameplay input");
    });
    c.getInput().pressKey(InputConstants.KEY_F9);
    c.getInput().pressKey(InputConstants.KEY_F8);
    c.waitForScreen(VerdictScreen.class);
    c.clickScreenButton("vv.shortcuts");
    c.clickScreenButton("key.villagerverdict.scan");
    c.getInput().pressKey(InputConstants.KEY_F8);
    c.waitForScreen(ShortcutScreen.class);
    c.runOnClient(mc -> {
      if (!VillagerverdictClient.shortcuts().getFirst().mapping().isUnbound())
        throw new AssertionError("duplicate management key was not released");
    });
    c.clickScreenButton("key.villagerverdict.scan");
    c.getInput().pressKey(InputConstants.KEY_ESCAPE);
    c.waitForScreen(ShortcutScreen.class);
    c.runOnClient(mc -> {
      if (!VillagerverdictClient.shortcuts().get(1).mapping().isUnbound())
        throw new AssertionError("Escape did not clear shortcut");
    });
    c.clickScreenButton("vv.restoreDefaults");
    c.runOnClient(mc -> {
      for (var shortcut : VillagerverdictClient.shortcuts())
        if (!shortcut.mapping().isDefault()) throw new AssertionError("defaults not restored");
    });
    c.clickScreenButton("gui.done");
    c.runOnClient(mc -> mc.gui.setScreen(null));
    // Native rebind must be visible to the in-mod editor, and survive options reload.
    c.runOnClient(mc -> {
      var nativeScreen = new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(null, mc.options);
      mc.gui.setScreen(nativeScreen);
      nativeScreen.selectedKey = VillagerverdictClient.shortcuts().getFirst().mapping();
    });
    c.getInput().pressKey(InputConstants.KEY_F7);
    c.clickScreenButton("gui.done");
    c.runOnClient(mc -> {
      mc.options.save();
      mc.options.load();
      if (!VillagerverdictClient.shortcuts().getFirst().mapping()
          .matches(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F7)))
        throw new AssertionError("management key not synchronized with native options");
    });
    c.getInput().pressKey(InputConstants.KEY_F7);
    c.waitForScreen(VerdictScreen.class);
    c.clickScreenButton("vv.shortcuts");
    c.clickScreenButton("vv.restoreDefaults");
    c.runOnClient(mc -> mc.gui.setScreen(null));
    System.out.println("VV_SHORTCUTS_PASS legacy isolation, native single entry, input, fresh controller, native sync, reset");
  }

  private void testNativeBindings(ClientGameTestContext c, String language) {
    c.runOnClient(mc -> {
      var nativeKeys = Arrays.stream(mc.options.keyMappings)
          .filter(k -> k.getName().startsWith("key.villagerverdict.")).toList();
      if (nativeKeys.size() != 1 || !nativeKeys.getFirst().getName().equals("key.villagerverdict.manage"))
        throw new AssertionError("vanilla controls should contain exactly the management key");
      if (!nativeKeys.getFirst().getCategory().label().getString().equals("Villager Verdict"))
        throw new AssertionError("native category translation is missing");
      mc.gui.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(null, mc.options));
      for (var child : mc.gui.screen().children())
        if (child instanceof net.minecraft.client.gui.screens.options.controls.KeyBindsList list)
          list.setScrollAmount(Double.MAX_VALUE);
    });
    c.takeScreenshot("native-controls-" + language);
    c.clickScreenButton("gui.done");
    c.runOnClient(mc -> mc.gui.setScreen(null));
  }

  private void testDataClear(ClientGameTestContext c) {
    var original = c.computeOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.selected = service.records().iterator().next().uuid();
      return new WorldData(2, service.identity(), List.copyOf(service.records()),
          List.copyOf(service.targets), List.copyOf(service.regions));
    });
    var sibling = withIdentity(original,
        new WorldIdentity(original.identity().server(), "other-dataset", "minecraft:the_nether"));
    var other = withIdentity(original,
        new WorldIdentity("other.example.invalid", "default", "minecraft:overworld"));
    Path directory = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
        .resolve("villagerverdict/worlds");
    try (var store = new JsonStore(e -> { throw new AssertionError(e); })) {
      for (var data : List.of(sibling, other))
        store.save(directory.resolve(JsonStore.fileKey(data.identity()) + ".json"), data);
      store.flush().join();
    }
    c.setScreen(VerdictScreen::new);
    c.clickScreenButton("vv.shortcuts");
    c.takeScreenshot("shortcuts-zh");
    c.clickScreenButton("gui.done");
    c.clickScreenButton("vv.data");
    waitDataScreen(c);
    c.takeScreenshot("world-data");
    c.clickScreenButton("vv.clearWorld");
    c.takeScreenshot("clear-world-confirm");
    c.clickScreenButton("gui.no");
    c.runOnClient(mc -> {
      if (VerdictService.INSTANCE.records().isEmpty()) throw new AssertionError("cancel cleared data");
    });
    c.clickScreenButton("vv.clearWorld");
    c.clickScreenButton("gui.yes");
    c.waitFor(mc -> VerdictService.INSTANCE.records().isEmpty());
    c.waitTicks(30);
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (!service.records().isEmpty() || !service.analysis.verdicts().isEmpty()
          || !service.regional.isEmpty() || !service.markers.isEmpty()
          || !service.nearby.isEmpty() || service.selected != null || service.probe.running())
        throw new AssertionError("clear left stale memory or asynchronous analysis");
      if (!service.targets.equals(original.targets()) || !service.regions.equals(original.regions()))
        throw new AssertionError("clear removed configuration");
    });
    try (var store = new JsonStore(e -> { throw new AssertionError(e); })) {
      var worlds = store.worlds(directory).join();
      if (worlds.stream().filter(w -> w.identity().equals(original.identity()))
          .anyMatch(w -> !w.villagers().isEmpty())) throw new AssertionError("clear not durable");
      if (!worlds.contains(sibling) || !worlds.contains(other))
        throw new AssertionError("world clear leaked to a sibling or other server");
    }
    c.clickScreenButton("gui.done");
    c.clickScreenButton("vv.scan");
    c.waitFor(mc -> VerdictService.INSTANCE.records().size() >= 303);
    c.runOnClient(mc -> {
      if (VerdictService.INSTANCE.records().stream().anyMatch(v -> v.snapshot() != null))
        throw new AssertionError("fresh scan restored old trade snapshots");
    });
    c.clickScreenButton("vv.data");
    waitDataScreen(c);
    c.clickScreenButton("vv.clearServer");
    c.clickScreenButton("gui.yes");
    c.waitFor(mc -> VerdictService.INSTANCE.records().isEmpty());
    try (var store = new JsonStore(e -> { throw new AssertionError(e); })) {
      var worlds = store.worlds(directory).join();
      if (worlds.stream().filter(w -> w.identity().server().equals(original.identity().server()))
          .anyMatch(w -> !w.villagers().isEmpty())) throw new AssertionError("server clear incomplete");
      if (!worlds.contains(other) || !worlds.contains(sibling.clearVillagers()))
        throw new AssertionError("server clear lost another server or sibling configuration");
    }
    var extraTarget = new TargetTrade("during-clear", "minecraft:book", "", 1, 0, 1);
    var clearing = c.computeOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      var future = service.clearWorldVillagers(service.identity());
      service.targets.add(extraTarget);
      service.changed();
      return future;
    });
    c.waitFor(mc -> clearing.isDone());
    clearing.join();
    var catalog = c.computeOnClient(mc -> VerdictService.INSTANCE.storedWorlds());
    c.waitFor(mc -> catalog.isDone());
    catalog.join();
    try (var store = new JsonStore(e -> { throw new AssertionError(e); })) {
      if (store.worlds(directory).join().stream()
          .filter(w -> w.identity().equals(original.identity()))
          .noneMatch(w -> w.targets().contains(extraTarget) && w.villagers().isEmpty()))
        throw new AssertionError("clear lost configuration edited while it was in progress");
    }
    c.runOnClient(mc -> mc.gui.setScreen(null));
    System.out.println("VV_DATA_CLEAR_PASS cancel, world/server isolation, concurrent config, disk, memory, rescan");
  }

  private void waitDataScreen(ClientGameTestContext c) {
    c.waitFor(mc -> mc.gui.screen() instanceof DataScreen screen
        && screen.children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.Button b
            && b.active && b.getMessage().getString().equals(
                net.minecraft.network.chat.Component.translatable("vv.clearWorld").getString())));
  }

  private WorldData withIdentity(WorldData source, WorldIdentity identity) {
    return new WorldData(2, identity, source.villagers().stream().map(v ->
        new VillagerRecord(identity, v.uuid(), v.profession(), v.level(), v.baby(), v.x(), v.y(), v.z(),
            v.lastSeen(), v.snapshot(), v.manual(), v.note(), v.tags())).toList(),
        source.targets(), source.regions());
  }
}
