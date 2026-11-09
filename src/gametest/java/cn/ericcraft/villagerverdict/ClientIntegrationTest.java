package cn.ericcraft.villagerverdict;

import cn.ericcraft.villagerverdict.client.*;
import cn.ericcraft.villagerverdict.model.*;
import cn.ericcraft.villagerverdict.storage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
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
      testShortcutFeedback(c, server);
      testContinuousThreeStates(c, server, connection);
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
      server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_sword");
      server.runCommand("item replace entity @p weapon.offhand with minecraft:shield");
      connection.waitForClientboundPackets();
      c.waitFor(mc -> mc.player.getMainHandItem().is(Items.DIAMOND_SWORD)
          && mc.player.getOffhandItem().is(Items.SHIELD));
      c.runOnClient(mc -> {
        if (VerdictService.INSTANCE.record(id).snapshot() != null)
          throw new AssertionError("disabled captured automatically");
        mc.gui.setScreen(null);
        VerdictService.INSTANCE.enable(true);
      });
      c.waitFor(mc -> VerdictService.INSTANCE.record(id).snapshot() != null, 600);
      c.waitFor(mc -> mc.player.containerMenu == mc.player.inventoryMenu, 200);
      c.runOnClient(mc -> {
        var service = VerdictService.INSTANCE;
        if (mc.gui.screen() != null) throw new AssertionError("fresh automatic screen leaked");
        service.enable(false);
        if (!mc.player.getMainHandItem().is(Items.DIAMOND_SWORD)
            || !mc.player.getOffhandItem().is(Items.SHIELD))
          throw new AssertionError("automatic probe changed held item");
        var r = service.record(id);
        service.edit(new VillagerRecord(r.world(), r.uuid(), r.profession(), r.level(), r.baby(),
            r.x(), r.y(), r.z(), r.lastSeen(), null, r.manual(), r.note(), r.tags()));
        service.probe.reset();
      });
      connection.waitForServerboundPackets();
      server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
      server.runCommand("item replace entity @p weapon.offhand with minecraft:air");
      connection.waitForClientboundPackets();
      c.waitFor(mc -> mc.player.getMainHandItem().isEmpty() && mc.player.getOffhandItem().isEmpty());
      System.out.println("VV_FRESH_AUTO_PROBE_PASS captured with both hands occupied before any manual interaction");
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
      clickButtonContaining(c, "Lv.");
      c.takeScreenshot("details-en");
      clickButtonPrefix(c, net.minecraft.network.chat.Component.translatable("vv.edit").getString());
      c.waitForScreen(VillagerEditScreen.class);
      c.runOnClient(this::assertTransparent);
      c.takeScreenshot("villager-edit-en");
      c.clickScreenButton("gui.cancel");
      c.clickScreenButton("vv.backToList");
      c.runOnClient(
          mc -> {
            mc.options.guiScale().set(1);
            mc.resizeGui();
            mc.gui.setScreen(new VerdictScreen());
          });
      c.takeScreenshot("wide-management");
      testTransparentScreens(c, server, "en");
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
      testTransparentScreens(c, server, "zh");
      c.runOnClient(
          mc -> {
            mc.options.guiScale().set(2);
            mc.resizeGui();
          });
      c.clickScreenButton("vv.targets");
      c.clickScreenButton("vv.add");
      c.waitForScreen(TargetEditorScreen.class);
      c.runOnClient(this::assertTransparent);
      c.takeScreenshot("target-editor");
      clickButtonContaining(c, net.minecraft.network.chat.Component.translatable("vv.targetEditor.changeItem").getString());
      c.runOnClient(this::assertTransparent);
      c.takeScreenshot("target-item-picker");
      c.clickScreenButton("vv.targetEditor.backToForm");
      c.clickScreenButton("vv.targetEditor.anyEnchant");
      c.runOnClient(this::assertTransparent);
      c.takeScreenshot("target-enchant-picker");
      c.clickScreenButton("vv.targetEditor.backToForm");
      c.clickScreenButton("vv.save");
      c.takeScreenshot("targets-populated-zh");
      c.clickScreenButton("vv.regions");
      c.clickScreenButton("vv.add");
      c.waitForScreen(RegionEditorScreen.class);
      c.runOnClient(this::assertTransparent);
      c.takeScreenshot("region-editor");
      c.clickScreenButton("vv.regionEditor.radius16");
      c.clickScreenButton("vv.save");
      c.takeScreenshot("regions-populated-zh");
      c.clickScreenButton("vv.villagers");
      c.runOnClient(mc -> VerdictService.INSTANCE.selected = id);
      clickButtonContaining(c, "Lv.");
      clickButtonPrefix(c, net.minecraft.network.chat.Component.translatable("vv.edit").getString());
      c.waitForScreen(VillagerEditScreen.class);
      c.takeScreenshot("villager-edit-zh");
      c.clickScreenButton("gui.cancel");
      c.clickScreenButton("vv.backToList");
      c.clickScreenButton("vv.settings");
      c.waitForScreen(SettingsScreen.class);
      c.takeScreenshot("settings");
      c.clickScreenButton("vv.settings.tab.probe");
      c.takeScreenshot("settings-probe-zh");
      c.clickScreenButton("vv.settings.tab.rules");
      c.takeScreenshot("settings-rules-zh");
      c.clickScreenButton("vv.settings.tab.tools");
      c.takeScreenshot("settings-tools-zh");
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
      testFastHeldItemProbes(c, server, connection);
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
      testManualCaptureAfterProfessionChange(c, server, connection, id);
      testEligibilityAndOutlines(c, server, connection, id);
      testMultilineMarkers(c, server, connection);
      server.runOnServer(
          s -> {
            for (int i = 0; i < 300; i++) {
              var v = new Villager(EntityTypes.VILLAGER, s.overworld());
              v.setNoAi(true);
              setProfession(v, VillagerProfession.LIBRARIAN);
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
            if (service.stats().get("nontrading") != 0)
              throw new AssertionError("ineligible villagers entered stats");
            if (service.markers.size() < 301 || !service.markers.containsKey(id))
              throw new AssertionError("all in-range workers should be marked");
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
          setProfession(v, VillagerProfession.LIBRARIAN);
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
            s.green(), s.yellow(), s.red(), 48, s.scanTicks(), false,
            1, 1, s.intervalMillis(), s.timeoutMillis(), s.backups(),
            s.notifications(), s.autoServers(), s.acknowledged(), s.dataset(), s.excludedProfessions()));
        service.scan();
        service.analysis = AnalysisResult.empty();
        service.tick();
        if (service.markers.size() != 302 || !service.markers.containsKey(distanceIds.getFirst())
            || service.markers.containsKey(distanceIds.getLast())
            || service.markers.get(distanceIds.getFirst()).lines().isEmpty())
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
    var other = c.worldBuilder().create();
    WorldData disconnected;
    try {
      c.waitFor(mc -> VerdictService.INSTANCE.ready());
      c.runOnClient(
          mc -> {
            if (!VerdictService.INSTANCE.records().isEmpty())
              throw new AssertionError("world data crossed identity");
            System.out.println("VV_WORLD_ISOLATION_PASS");
          });
      var originalSettings = c.computeOnClient(mc -> VerdictService.INSTANCE.settings);
      var dataset = testClearWhileLeaving(c, "dataset", () -> c.runOnClient(mc -> {
        var service = VerdictService.INSTANCE;
        service.settings(withDataset(service.settings, "clear-switch"));
        service.tick();
      }));
      c.waitFor(mc -> VerdictService.INSTANCE.ready());
      c.runOnClient(mc -> VerdictService.INSTANCE.settings(originalSettings));
      c.waitFor(mc -> VerdictService.INSTANCE.ready()
          && VerdictService.INSTANCE.identity().equals(dataset.identity()));
      assertActiveConfiguration(c, dataset);

      var dimension = testClearWhileLeaving(c, "dimension", () ->
          other.getServer().runCommand("execute as @p in minecraft:the_nether run tp @s 0 100 0"));
      c.waitFor(mc -> VerdictService.INSTANCE.ready());
      other.getServer().runCommand("execute as @p in minecraft:overworld run tp @s 0 -60 0");
      c.waitFor(mc -> VerdictService.INSTANCE.ready()
          && VerdictService.INSTANCE.identity().equals(dimension.identity()));
      assertActiveConfiguration(c, dimension);

      disconnected = testClearWhileLeaving(c, "disconnect", other::close);
    } finally {
      if (c.computeOnClient(mc -> mc.level != null)) other.close();
    }
    try (var reopened = other.getWorldSave().open()) {
      c.waitFor(mc -> VerdictService.INSTANCE.ready());
      assertActiveConfiguration(c, disconnected);
    }
    c.runOnClient(mc -> {
      if (mc.level != null) throw new AssertionError("fallback test requires no loaded world");
      var parent = mc.gui.screen();
      var vanilla = new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {},
          net.minecraft.network.chat.Component.literal("Test"), net.minecraft.network.chat.Component.literal("Message"));
      mc.gui.setScreen(vanilla);
      var baseline = new net.minecraft.client.renderer.state.gui.GuiRenderState();
      vanilla.extractBackground(new net.minecraft.client.gui.GuiGraphicsExtractor(mc, baseline, -1, -1), -1, -1, 0);
      int[] vanillaElements = {0};
      baseline.forEachElement(element -> vanillaElements[0]++,
          net.minecraft.client.renderer.state.gui.GuiRenderState.TraverseRange.ALL);
      for (var screen : List.of(new VerdictScreen(), new WorldConfirmScreen(yes -> {},
          net.minecraft.network.chat.Component.literal("Test"), net.minecraft.network.chat.Component.literal("Message")))) {
        mc.gui.setScreen(screen);
        var state = new net.minecraft.client.renderer.state.gui.GuiRenderState();
        screen.extractBackground(new net.minecraft.client.gui.GuiGraphicsExtractor(mc, state, -1, -1), -1, -1, 0);
        int[] elements = {0};
        state.forEachElement(element -> elements[0]++,
            net.minecraft.client.renderer.state.gui.GuiRenderState.TraverseRange.ALL);
        if (elements[0] == 0 || elements[0] != vanillaElements[0]
            || !privateField(state, "firstStratumAfterBlur").equals(privateField(baseline, "firstStratumAfterBlur")))
          throw new AssertionError("no-world background differs from the native menu");
      }
      mc.gui.setScreen(parent);
    });
    System.out.println("VV_MENU_BACKGROUND_PASS no-world native background retained");
    System.out.println("VV_CLEAR_TRANSITIONS_PASS dataset, dimension, disconnect, reload, configuration, empty records");
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
    openShortcutsViaSettings(c);
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
    openShortcutsViaSettings(c);
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
    openShortcutsViaSettings(c);
    c.clickScreenButton("vv.restoreDefaults");
    c.runOnClient(mc -> mc.gui.setScreen(null));
    testMouseShortcuts(c, shortcutsPath);
    System.out.println("VV_SHORTCUTS_PASS legacy isolation, native single entry, input, fresh controller, native sync, mouse, reset");
  }

  private void testMouseShortcuts(ClientGameTestContext c, Path path) {
    c.setScreen(() -> new ShortcutScreen(new VerdictScreen()));
    c.clickScreenButton("key.villagerverdict.scan");
    c.getInput().pressMouse(4);
    c.clickScreenButton("key.villagerverdict.mark");
    c.getInput().pressMouse(5);
    c.runOnClient(mc -> {
      var shortcuts = VillagerverdictClient.shortcuts();
      if (!shortcuts.get(1).mapping().matches(InputConstants.Type.MOUSE.getOrCreate(4))
          || !shortcuts.get(2).mapping().matches(InputConstants.Type.MOUSE.getOrCreate(5)))
        throw new AssertionError("side mouse buttons were not captured");
    });
    c.takeScreenshot("shortcuts-mouse");
    var saving = c.computeOnClient(mc -> VillagerverdictClient.shortcutManager().flush());
    c.waitFor(mc -> saving.isDone());
    saving.join();
    try (var store = new JsonStore(e -> { throw new AssertionError(e); })) {
      var bindings = store.read(path, ShortcutBindings.class, ShortcutBindings::defaults).join().bindings();
      if (!InputConstants.Type.MOUSE.getOrCreate(4).getName().equals(bindings.get("scan"))
          || !InputConstants.Type.MOUSE.getOrCreate(5).getName().equals(bindings.get("mark")))
        throw new AssertionError("mouse bindings were not persisted");
    }
    c.runOnClient(mc -> VillagerverdictClient.shortcutManager().load());
    c.waitFor(mc -> VillagerverdictClient.shortcutManager().editable());
    c.runOnClient(mc -> {
      if (!VillagerverdictClient.shortcuts().get(1).mapping().matches(InputConstants.Type.MOUSE.getOrCreate(4))
          || !VillagerverdictClient.shortcuts().get(2).mapping().matches(InputConstants.Type.MOUSE.getOrCreate(5)))
        throw new AssertionError("mouse bindings were not restored");
    });
    c.clickScreenButton("key.villagerverdict.navigate");
    c.getInput().pressMouse(4);
    c.runOnClient(mc -> {
      if (!VillagerverdictClient.shortcuts().get(1).mapping().isUnbound()
          || !VillagerverdictClient.shortcuts().get(3).mapping().matches(InputConstants.Type.MOUSE.getOrCreate(4)))
        throw new AssertionError("duplicate mouse binding was not released");
      mc.gui.setScreen(null);
    });
    boolean markers = c.computeOnClient(mc -> VerdictService.INSTANCE.settings.markers());
    c.getInput().pressMouse(5);
    c.runOnClient(mc -> {
      if (VerdictService.INSTANCE.settings.markers() == markers)
        throw new AssertionError("mouse binding did not receive gameplay input");
    });
    c.getInput().pressMouse(5);
    c.setScreen(() -> new ShortcutScreen(new VerdictScreen()));
    c.clickScreenButton("vv.restoreDefaults");
    c.runOnClient(mc -> mc.gui.setScreen(null));
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
      return new WorldData(4, service.identity(), service.records().stream().filter(v -> v.snapshot() != null).map(CachedTrade::from).toList(),
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
    openShortcutsViaSettings(c);
    c.takeScreenshot("shortcuts-zh");
    c.clickScreenButton("gui.done");
    c.clickScreenButton("vv.data");
    waitDataScreen(c);
    c.takeScreenshot("world-data");
    c.clickScreenButton("vv.clearWorld");
    c.runOnClient(this::assertTransparent);
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
          .anyMatch(w -> !w.cachedTrades().isEmpty())) throw new AssertionError("clear not durable");
      if (!worlds.contains(sibling) || !worlds.contains(other))
        throw new AssertionError("world clear leaked to a sibling or other server");
    }
    c.clickScreenButton("gui.done");
    c.clickScreenButton("gui.cancel");
    c.clickScreenButton("vv.scan");
    c.waitFor(mc -> VerdictService.INSTANCE.records().size() >= 303);
    c.runOnClient(mc -> {
      if (VerdictService.INSTANCE.records().stream().anyMatch(v -> v.snapshot() != null))
        throw new AssertionError("fresh scan restored old trade snapshots");
    });
    openDataViaSettings(c);
    waitDataScreen(c);
    c.clickScreenButton("vv.clearServer");
    c.clickScreenButton("gui.yes");
    c.waitFor(mc -> VerdictService.INSTANCE.records().isEmpty());
    try (var store = new JsonStore(e -> { throw new AssertionError(e); })) {
      var worlds = store.worlds(directory).join();
      if (worlds.stream().filter(w -> w.identity().server().equals(original.identity().server()))
          .anyMatch(w -> !w.cachedTrades().isEmpty())) throw new AssertionError("server clear incomplete");
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
          .noneMatch(w -> w.targets().contains(extraTarget) && w.cachedTrades().isEmpty()))
        throw new AssertionError("clear lost configuration edited while it was in progress");
    }
    c.runOnClient(mc -> mc.gui.setScreen(null));
    testClearQuarantine(c);
    System.out.println("VV_DATA_CLEAR_PASS cancel, world/server isolation, concurrent config, disk, memory, rescan");
  }

  private void testClearQuarantine(ClientGameTestContext c) {
    var villagers = c.computeOnClient(mc -> {
      var result = new ArrayList<Villager>();
      for (var entity : mc.level.entitiesForRendering())
        if (entity instanceof Villager v) result.add(v);
      return result;
    });
    var clearing = c.computeOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.probe.configure(service.settings.intervalMillis(), 60000);
      service.manual(villagers.get(0));
      if (service.probe.context() == null) throw new AssertionError("test request not established");
      var future = service.clearWorldVillagers(service.identity());
      // Queue the bound scan key while the completion callback cannot yet run on this thread.
      var scanKey = InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F9);
      VillagerverdictClient.shortcutManager().bind(VillagerverdictClient.shortcuts().get(1), scanKey);
      net.minecraft.client.KeyMapping.click(scanKey);
      net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(mc);
      var overlay = (net.minecraft.network.chat.Component) privateField(mc.gui.hud, "overlayMessageString");
      if (!overlay.getString().equals(net.minecraft.network.chat.Component.translatable("vv.feedback.scanUnavailable").getString()))
        throw new AssertionError("blocked scan claimed success");
      VillagerverdictClient.resetShortcuts();
      service.manual(villagers.get(1));
      if (service.probe.context() != null) throw new AssertionError("interaction entered pending clear");
      return future;
    });
    c.waitFor(mc -> clearing.isDone());
    clearing.join();
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.manual(villagers.get(1));
      if (service.probe.context() != null) throw new AssertionError("clear erased response quarantine");
      service.open(77, true);
      if (service.probe.offers(77) || !service.records().isEmpty())
        throw new AssertionError("late response accepted after clear");
    });
    var originalSettings = c.computeOnClient(mc -> VerdictService.INSTANCE.settings);
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.settings(withDataset(originalSettings, "quarantine-switch"));
      service.tick();
    });
    c.waitFor(mc -> VerdictService.INSTANCE.ready());
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.manual(villagers.get(1));
      if (service.probe.context() != null) throw new AssertionError("dataset switch erased response quarantine");
      service.probe.reset();
      service.settings(originalSettings);
    });
    c.waitFor(mc -> VerdictService.INSTANCE.ready()
        && VerdictService.INSTANCE.identity().dataset().equals(originalSettings.dataset()));
    System.out.println("VV_CLEAR_QUARANTINE_PASS pending request, completion, manual interaction, late response, dataset switch");
  }

  private WorldData testClearWhileLeaving(ClientGameTestContext c, String transition, Runnable leave) {
    var expected = c.computeOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      // An inactive cache must never be resurrected by a stale save after clearing.
      @SuppressWarnings("unchecked")
      var cache = (Map<UUID, CachedTrade>) privateField(service, "tradeCache");
      var id = UUID.randomUUID();
      cache.put(id, new CachedTrade(id, "minecraft:librarian", new TradeSnapshot(List.of(), 1)));
      var target = new TargetTrade("clear-" + transition, "minecraft:book", "", 1, 0, 1);
      var region = new TradingHallRegion("clear-" + transition, transition, service.identity().dimension(),
          0, 0, 0, 10, 10, 10, List.of(target), 0xFF55DD88);
      var targets = new ArrayList<>(service.targets);
      var regions = new ArrayList<>(service.regions);
      targets.add(target);
      regions.add(region);
      return new WorldData(4, service.identity(), List.of(), targets, regions);
    });
    CompletableFuture<Integer> clearing;
    try (var barrier = holdWorldStore(c)) {
      clearing = c.computeOnClient(mc -> {
        var service = VerdictService.INSTANCE;
        var future = transition.equals("dataset") ? service.clearWorldVillagers(service.identity())
            : service.clearServerVillagers(service.identity().server());
        service.targets = new ArrayList<>(expected.targets());
        service.regions = new ArrayList<>(expected.regions());
        service.changed();
        return future;
      });
      leave.run();
      c.waitFor(mc -> !expected.identity().equals(VerdictService.INSTANCE.identity()));
      if (clearing.isDone()) throw new AssertionError("clear completed before leaving " + transition);
    }
    c.waitFor(mc -> clearing.isDone());
    clearing.join();
    // Listing on the service writer drains configuration snapshots queued during the clear.
    var catalog = c.computeOnClient(mc -> VerdictService.INSTANCE.storedWorlds());
    c.waitFor(mc -> catalog.isDone());
    catalog.join();
    Path path = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
        .resolve("villagerverdict/worlds/" + JsonStore.fileKey(expected.identity()) + ".json");
    try (var store = new JsonStore(e -> { throw new AssertionError(e); })) {
      if (!expected.equals(store.read(path, WorldData.class, () -> null).join()))
        throw new AssertionError("configuration lost or records resurrected during " + transition);
    }
    return expected;
  }

  private void assertActiveConfiguration(ClientGameTestContext c, WorldData expected) {
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (!expected.identity().equals(service.identity()) || !expected.targets().equals(service.targets)
          || !expected.regions().equals(service.regions) || !service.records().isEmpty())
        throw new AssertionError("cleared world did not reload its edited configuration");
    });
  }

  private static CachedTrade cachedTrade(VerdictService service, UUID id) {
    @SuppressWarnings("unchecked")
    var cache = (Map<UUID, CachedTrade>) privateField(service, "tradeCache");
    return cache.get(id);
  }

  private static Object privateField(Object instance, String name) {
    try {
      var field = instance.getClass().getDeclaredField(name);
      field.setAccessible(true);
      return field.get(instance);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private void assertOverlay(ClientGameTestContext c, String key, Object... args) {
    c.runOnClient(mc -> {
      var actual = (net.minecraft.network.chat.Component) privateField(mc.gui.hud, "overlayMessageString");
      var expected = net.minecraft.network.chat.Component.translatable(key, args).getString();
      if (actual == null || !actual.getString().equals(expected)
          || (int) privateField(mc.gui.hud, "overlayMessageTime") <= 0)
        throw new AssertionError("action bar mismatch: expected " + expected + ", got " + actual);
    });
  }

  private void testShortcutFeedback(ClientGameTestContext c, TestServerContext server) {
    var original = c.computeOnClient(mc -> VerdictService.INSTANCE.settings);
    c.runOnClient(mc -> {
      int[] keys = {InputConstants.KEY_F8, InputConstants.KEY_F9, InputConstants.KEY_F10,
          InputConstants.KEY_F6, InputConstants.KEY_F7};
      for (int i = 0; i < keys.length; i++)
        VillagerverdictClient.shortcutManager().bind(VillagerverdictClient.shortcuts().get(i),
            InputConstants.Type.KEYBOARD.getOrCreate(keys[i]));
    });
    c.getInput().pressKey(InputConstants.KEY_F10);
    assertOverlay(c, "vv.feedback.markersOff");
    c.takeScreenshot("feedback-markers-en");
    c.getInput().pressKey(InputConstants.KEY_F10);
    assertOverlay(c, "vv.feedback.markersOn");
    c.getInput().pressKey(InputConstants.KEY_F9);
    assertOverlay(c, "vv.feedback.scan", 0);
    c.getInput().pressKey(InputConstants.KEY_F6);
    assertOverlay(c, "vv.feedback.noTarget");
    c.getInput().pressKey(InputConstants.KEY_F7);
    c.waitForScreen(WorldConfirmScreen.class);
    c.runOnClient(mc -> {
      if (VerdictService.INSTANCE.probe.running()) throw new AssertionError("probe enabled before consent");
      assertTransparent(mc);
    });
    c.takeScreenshot("transparent-probe-confirm-en");
    c.clickScreenButton("gui.no");
    assertOverlay(c, "vv.feedback.probeCancelled");
    c.getInput().pressKey(InputConstants.KEY_F7);
    c.waitForScreen(WorldConfirmScreen.class);
    c.clickScreenButton("gui.yes");
    assertOverlay(c, "vv.feedback.probeOn");
    c.runOnClient(mc -> {
      if (!VerdictService.INSTANCE.probe.running()) throw new AssertionError("probe did not start");
    });
    c.getInput().pressKey(InputConstants.KEY_F7);
    assertOverlay(c, "vv.feedback.probePaused");
    c.runOnClient(mc -> {
      if (VerdictService.INSTANCE.probe.running()) throw new AssertionError("probe did not pause");
      mc.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal("quiet background"), false);
    });
    c.waitTicks(25);
    c.runOnClient(mc -> {
      VerdictService.INSTANCE.navigateNext();
      if (!((net.minecraft.network.chat.Component) privateField(mc.gui.hud, "overlayMessageString"))
          .getString().equals("quiet background")) throw new AssertionError("background action emitted feedback");
    });
    UUID worker = server.computeOnServer(s -> {
      var v = new Villager(EntityTypes.VILLAGER, s.overworld());
      v.setNoAi(true);
      setProfession(v, VillagerProfession.LIBRARIAN);
      v.setPos(2, -60, 0);
      s.overworld().addFreshEntity(v);
      return v.getUUID();
    });
    c.waitFor(mc -> mc.level.getEntity(worker) != null);
    c.getInput().pressKey(InputConstants.KEY_F9);
    assertOverlay(c, "vv.feedback.scan", 1);
    c.getInput().pressKey(InputConstants.KEY_F6);
    int distance = c.computeOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (!worker.equals(service.selected)) throw new AssertionError("shortcut selected wrong villager");
      return (int) Math.sqrt(service.distanceSquared(service.record(worker)));
    });
    assertOverlay(c, "vv.feedback.located", 2, -60, 0, distance);
    c.takeScreenshot("feedback-located-en");
    c.setScreen(() -> new ShortcutScreen(new VerdictScreen()));
    c.clickScreenButton("key.villagerverdict.mark");
    c.getInput().pressKey(InputConstants.KEY_F10);
    c.runOnClient(mc -> {
      if (!VerdictService.INSTANCE.settings.markers()) throw new AssertionError("capture triggered gameplay action");
      var message = (net.minecraft.network.chat.Component) privateField(mc.gui.hud, "overlayMessageString");
      if (!message.getString().equals(net.minecraft.network.chat.Component.translatable(
          "vv.feedback.located", 2, -60, 0, distance).getString()))
        throw new AssertionError("capture emitted gameplay feedback");
    });
    c.clickScreenButton("gui.done");
    c.runOnClient(mc -> mc.gui.setScreen(null));
    server.runOnServer(s -> setProfession((Villager) s.overworld().getEntity(worker), VillagerProfession.NONE));
    c.waitFor(mc -> VerdictService.INSTANCE.record(worker) == null);
    server.runOnServer(s -> s.overworld().getEntity(worker).discard());
    c.waitFor(mc -> mc.level.getEntity(worker) == null);
    c.runOnClient(mc -> {
      VerdictService.INSTANCE.probe.reset();
      VerdictService.INSTANCE.settings(original);
      VillagerverdictClient.resetShortcuts();
    });
    System.out.println("VV_SHORTCUT_FEEDBACK_PASS real keys, action bar, scan, target, consent, pause, capture, quiet background");
  }

  private void testContinuousThreeStates(ClientGameTestContext c, TestServerContext server,
      TestServerConnection connection) {
    var original = c.computeOnClient(mc -> VerdictService.INSTANCE.settings);
    var ids = server.computeOnServer(s -> {
      var result = new ArrayList<UUID>();
      var registry = s.overworld().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
      for (int i = 0; i < 3; i++) {
        var v = new Villager(EntityTypes.VILLAGER, s.overworld());
        v.setNoAi(true);
        setProfession(v, VillagerProfession.LIBRARIAN);
        v.setPos(i == 2 ? 15 : i + 1, -60, 0);
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(book,
            ench -> ench.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS),
                v.getX() == 1 ? 1 : 3));
        var offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), book, 12, 1, 0));
        v.setOffers(offers);
        s.overworld().addFreshEntity(v);
        result.add(v.getUUID());
      }
      return result;
    });
    c.waitFor(mc -> ids.stream().allMatch(id -> mc.level.getEntity(id) != null));
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.scan();
      if (ids.stream().anyMatch(id -> service.displayStatus(service.record(id), null) != DisplayStatus.UNSCANNED))
        throw new AssertionError("new workers were not unscanned");
      mc.gui.setScreen(new VerdictScreen());
    });
    clickButtonContaining(c, net.minecraft.network.chat.Component.translatable("vv.probeStart").getString());
    c.waitForScreen(WorldConfirmScreen.class);
    c.clickScreenButton("gui.yes");
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (!service.probe.running() || !service.settings.autoServers().getOrDefault(service.identity().server(), false))
        throw new AssertionError("starting from menu did not remember auto-start");
      mc.gui.setScreen(null);
    });
    c.waitFor(mc -> ids.subList(0, 2).stream().allMatch(id -> VerdictService.INSTANCE.record(id).snapshot() != null), 400);
    c.waitFor(mc -> VerdictService.INSTANCE.probe.context() == null);
    c.waitFor(mc -> VerdictService.INSTANCE.analysis.verdicts().size() == 2);
    long firstRead = c.computeOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (service.displayStatus(service.record(ids.get(0)), service.analysis.verdicts().get(ids.get(0))) != DisplayStatus.DISCARD
          || service.displayStatus(service.record(ids.get(1)), service.analysis.verdicts().get(ids.get(1))) != DisplayStatus.KEEP
          || service.displayStatus(service.record(ids.get(2)), null) != DisplayStatus.UNSCANNED)
        throw new AssertionError("initial three-state decision incorrect");
      return service.record(ids.get(0)).snapshot().checkedAt();
    });
    c.setScreen(VerdictScreen::new);
    for (String filter : List.of("vv.filter.all", "vv.filter.keep", "vv.filter.discard")) {
      c.clickScreenButton(filter);
      c.runOnClient(mc -> {
        long rows = mc.gui.screen().children().stream()
            .filter(child -> child instanceof net.minecraft.client.gui.components.Button button
                && button.getMessage().getString().contains("Lv.")).count();
        if (rows != 1) throw new AssertionError("three-state filter does not match rows: " + rows);
      });
    }
    c.takeScreenshot("three-states-unscanned-filter");
    testSettingsAndNarrowColors(c);
    c.runOnClient(mc -> mc.gui.setScreen(null));
    server.runOnServer(s -> {
      var v = (Villager) s.overworld().getEntity(ids.get(0));
      v.setVillagerData(v.getVillagerData().withLevel(2));
      var book = new ItemStack(Items.ENCHANTED_BOOK);
      var registry = s.overworld().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
      net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(book,
          ench -> ench.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS), 5));
      v.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), book, 12, 1, 0));
    });
    connection.waitForClientboundPackets();
    c.runOnClient(mc -> {
      VerdictService.INSTANCE.scan();
      if (VerdictService.INSTANCE.record(ids.get(0)).snapshot() == null)
        throw new AssertionError("level change cleared successful scan");
      VerdictService.INSTANCE.enable(true);
    });
    c.waitFor(mc -> VerdictService.INSTANCE.record(ids.get(0)).snapshot().checkedAt() > firstRead
        && VerdictService.INSTANCE.analysis.verdicts().get(ids.get(0)).status() == AnalysisResult.Status.KEEP
        && VerdictService.INSTANCE.markers.get(ids.get(0)).color() == VerdictService.INSTANCE.settings.green(), 900);
    c.waitFor(mc -> VerdictService.INSTANCE.probe.context() == null);
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (service.record(ids.get(0)).snapshot().trades().size() != 2
          || service.markers.get(ids.get(0)).color() != service.settings.green())
        throw new AssertionError("continuous scan did not update red worker to green: "
            + service.record(ids.get(0)).snapshot() + " marker=" + service.markers.get(ids.get(0)));
      mc.gui.setScreen(new VerdictScreen());
    });
    clickButtonContaining(c, net.minecraft.network.chat.Component.translatable("vv.probeRunning").getString());
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (service.probe.running() || service.settings.autoServers().getOrDefault(service.identity().server(), false))
        throw new AssertionError("pause from menu left auto-start enabled");
      service.settings(original);
      mc.gui.setScreen(null);
    });
    server.runOnServer(s -> ids.forEach(id -> s.overworld().getEntity(id).discard()));
    c.waitFor(mc -> ids.stream().allMatch(id -> mc.level.getEntity(id) == null));
    // Isolate these fixtures from the rest of the suite.
    c.runOnClient(mc -> {
      @SuppressWarnings("unchecked")
      var records = (Map<UUID, VillagerRecord>) privateField(VerdictService.INSTANCE, "records");
      ids.forEach(records::remove);
      VerdictService.INSTANCE.probe.reset();
      VerdictService.INSTANCE.changed();
    });
    System.out.println("VV_THREE_STATES_PASS continuous reads, cooldown, level change, red-to-green, filters, unified auto-start");
  }

  private void testSettingsAndNarrowColors(ClientGameTestContext c) {
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.enable(false);
      var previous = service.settings;
      var autos = new HashMap<>(previous.autoServers());
      autos.put("other-test-server", true);
      var acknowledged = new HashSet<>(previous.acknowledged());
      acknowledged.add("other-test-server");
      service.settings(new Settings(previous.version(), previous.markers(), previous.statusMarks(), previous.summaries(),
          previous.green(), previous.yellow(), previous.red(), previous.radius(), previous.scanTicks(), previous.autoScan(),
          7, 11, previous.intervalMillis(), previous.timeoutMillis(), previous.backups(), previous.notifications(),
          autos, acknowledged, previous.dataset(), previous.excludedProfessions()));
      service.probe.run();
      SettingsScreen.open(new VerdictScreen());
    });
    c.clickScreenButton("vv.save");
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (!service.probe.running() || service.settings.autoServers().getOrDefault(service.identity().server(), false))
        throw new AssertionError("saving unrelated settings stopped manual probe or enabled auto-start");
      SettingsScreen.open(null);
      mc.gui.screen().resize(320, 240);
      var screen = mc.gui.screen();
      for (var child : screen.children()) {
        if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
            && (widget.getX() < 0 || widget.getX() + widget.getWidth() > 320))
          throw new AssertionError("settings control overflows narrow window");
      }
    });
    c.takeScreenshot("narrow-settings-colors");
    c.clickScreenButton("vv.settings.tab.tools");
    c.clickScreenButton("vv.settings.resetDefaults");
    c.clickScreenButton("vv.save");
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (!service.settings.equals(Settings.defaults()) || service.probe.running())
        throw new AssertionError("reset did not restore every setting or stop auto-read");
    });
  }

  private void assertTransparent(net.minecraft.client.Minecraft mc) {
    var screen = mc.gui.screen();
    var state = new net.minecraft.client.renderer.state.gui.GuiRenderState();
    var graphics = new net.minecraft.client.gui.GuiGraphicsExtractor(mc, state, -1, -1);
    Object noBlur = privateField(state, "firstStratumAfterBlur");
    screen.extractBackground(graphics, -1, -1, 0);
    state.forEachElement(element -> { throw new AssertionError("world background contains a draw operation"); },
        net.minecraft.client.renderer.state.gui.GuiRenderState.TraverseRange.ALL);
    screen.extractRenderState(graphics, -1, -1, 0);
    if (!noBlur.equals(privateField(state, "firstStratumAfterBlur")) || screen.isPauseScreen())
      throw new AssertionError("mod screen blurred or paused the world");
    state.forEachElement(element -> {
      var bounds = element.bounds();
      if (bounds != null && bounds.width() >= screen.width && bounds.height() >= screen.height)
        throw new AssertionError("mod screen paints a full-screen element");
    }, net.minecraft.client.renderer.state.gui.GuiRenderState.TraverseRange.ALL);
  }

  private void testTransparentScreens(ClientGameTestContext c, TestServerContext server, String locale) {
    int originalScale = c.computeOnClient(mc -> mc.options.guiScale().get());
    for (String lighting : List.of("day", "night")) {
      server.runCommand("time set " + lighting);
      c.waitTicks(3);
      for (int scale : new int[] {1, 2}) {
        c.runOnClient(mc -> {
          mc.options.guiScale().set(scale);
          mc.resizeGui();
          mc.gui.setScreen(new VerdictScreen());
          assertTransparent(mc);
        });
        c.takeScreenshot("transparent-management-" + locale + "-" + lighting + "-" + scale);
      }
    }
    c.setScreen(VerdictScreen::new);
    clickButtonContaining(c, "Lv.");
    c.runOnClient(this::assertTransparent);
    c.takeScreenshot("transparent-details-" + locale);
    c.clickScreenButton("vv.backToList");

    for (String page : List.of("vv.targets", "vv.regions")) {
      c.clickScreenButton(page);
      c.runOnClient(this::assertTransparent);
      c.takeScreenshot("transparent-" + page.substring(3) + "-" + locale);
    }

    c.clickScreenButton("vv.settings");
    c.waitForScreen(SettingsScreen.class);
    c.runOnClient(this::assertTransparent);
    c.takeScreenshot("transparent-settings-" + locale);

    c.clickScreenButton("vv.settings.tab.probe");
    c.runOnClient(this::assertTransparent);
    c.takeScreenshot("transparent-settings-probe-" + locale);

    c.clickScreenButton("vv.settings.tab.rules");
    c.runOnClient(this::assertTransparent);
    c.takeScreenshot("transparent-settings-rules-" + locale);
    c.clickScreenButton("vv.professions");
    c.waitForScreen(ProfessionScreen.class);
    c.runOnClient(this::assertTransparent);
    c.clickScreenButton("gui.cancel");

    c.clickScreenButton("vv.settings.tab.tools");
    c.runOnClient(this::assertTransparent);
    c.takeScreenshot("transparent-settings-tools-" + locale);

    c.clickScreenButton("vv.shortcuts");
    c.waitForScreen(ShortcutScreen.class);
    c.runOnClient(this::assertTransparent);
    c.takeScreenshot("transparent-shortcuts-" + locale);
    c.clickScreenButton("gui.done");

    c.clickScreenButton("vv.data");
    c.waitForScreen(DataScreen.class);
    c.runOnClient(this::assertTransparent);
    c.takeScreenshot("transparent-data-" + locale);
    c.clickScreenButton("gui.done");

    c.runOnClient(mc -> {
      mc.options.guiScale().set(originalScale);
      mc.resizeGui();
      mc.gui.setScreen(new VerdictScreen());
    });
    server.runCommand("time set day");
    if (locale.equals("zh")) {
      c.runOnClient(mc -> {
        mc.gui.setScreen(null);
        VillagerverdictClient.shortcutManager().bind(VillagerverdictClient.shortcuts().get(2),
            InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F10));
      });
      c.getInput().pressKey(InputConstants.KEY_F10);
      assertOverlay(c, "vv.feedback.markersOff");
      c.takeScreenshot("feedback-markers-zh");
      c.getInput().pressKey(InputConstants.KEY_F10);
      assertOverlay(c, "vv.feedback.markersOn");
      c.runOnClient(mc -> {
        VillagerverdictClient.resetShortcuts();
        mc.gui.setScreen(new VerdictScreen());
      });
    }
    System.out.println("VV_TRANSPARENT_PASS " + locale + " day/night, wide/narrow, all management pages, no blur/pause/full-screen fill");
  }

  private void testManualCaptureAfterProfessionChange(ClientGameTestContext c,
      TestServerContext server, TestServerConnection connection, UUID source) {
    var originalSettings = c.computeOnClient(mc -> VerdictService.INSTANCE.settings);
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      var s = service.settings;
      service.settings(new Settings(s.version(), s.markers(), s.statusMarks(), s.summaries(),
          s.green(), s.yellow(), s.red(), s.radius(), s.scanTicks(), false,
          s.markerLimit(), s.textRange(), s.intervalMillis(), s.timeoutMillis(),
          s.backups(), s.notifications(), s.autoServers(), s.acknowledged(), s.dataset(), s.excludedProfessions()));
    });
    UUID worker = server.computeOnServer(s -> {
      var v = new Villager(EntityTypes.VILLAGER, s.overworld());
      v.setNoAi(true);
      v.setPos(2, -60, 0);
      setProfession(v, VillagerProfession.LIBRARIAN);
      v.setVillagerData(v.getVillagerData().withLevel(5));
      s.overworld().addFreshEntity(v);
      return v.getUUID();
    });
    c.waitFor(mc -> mc.level.getEntity(worker) != null);
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.scan();
      service.edit(service.record(worker).withSnapshot(service.record(source).snapshot())
          .withManual(VillagerRecord.Manual.KEEP, "retain note", Set.of("retain-tag")));
    });
    server.runOnServer(s -> {
      var v = (Villager) s.overworld().getEntity(worker);
      setProfession(v, VillagerProfession.FARMER);
      var offers = new MerchantOffers();
      offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3),
          new ItemStack(Items.WHEAT), 12, 1, 0));
      v.setOffers(offers);
    });
    c.waitFor(mc -> ((Villager) mc.level.getEntity(worker)).getVillagerData()
        .profession().is(VillagerProfession.FARMER));
    c.waitFor(mc -> VerdictService.INSTANCE.probe.eligible(worker));
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (!service.record(worker).profession().equals("minecraft:librarian")
          || service.record(worker).snapshot() == null)
        throw new AssertionError("regression setup must retain the unscanned old profession");
      var v = mc.level.getEntity(worker);
      net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(
          mc.player, mc.level, InteractionHand.MAIN_HAND, v, new EntityHitResult(v, v.position()));
      if (!service.record(worker).profession().equals("minecraft:farmer")
          || service.record(worker).snapshot() != null)
        throw new AssertionError("manual probe did not refresh profession and clear old snapshot");
      mc.gameMode.interact(mc.player, v, new EntityHitResult(v, v.position()), InteractionHand.MAIN_HAND);
    });
    c.waitForScreen(MerchantScreen.class);
    c.waitFor(mc -> VerdictService.INSTANCE.record(worker).snapshot() != null);
    c.runOnClient(mc -> {
      var r = VerdictService.INSTANCE.record(worker);
      if (!r.snapshot().trades().getFirst().output().item().equals("minecraft:wheat")
          || r.manual() != VillagerRecord.Manual.KEEP || !r.note().equals("retain note")
          || !r.tags().equals(Set.of("retain-tag")))
        throw new AssertionError("manual capture lost new offers or existing annotations");
      mc.player.closeContainer();
    });
    connection.waitForServerboundPackets();
    server.runOnServer(s -> setProfession((Villager) s.overworld().getEntity(worker), VillagerProfession.NONE));
    c.waitFor(mc -> VerdictService.INSTANCE.record(worker) == null);
    c.runOnClient(mc -> VerdictService.INSTANCE.settings(originalSettings));
    server.runOnServer(s -> s.overworld().getEntity(worker).discard());
    c.waitFor(mc -> mc.level.getEntity(worker) == null);
    System.out.println("VV_MANUAL_PROFESSION_PASS unscanned profession, old snapshot invalidation, real offers, annotations");
  }

  private static void setProfession(Villager v,
      net.minecraft.resources.ResourceKey<VillagerProfession> profession) {
    v.setVillagerData(v.getVillagerData().withProfession(v.level().registryAccess()
        .lookupOrThrow(net.minecraft.core.registries.Registries.VILLAGER_PROFESSION)
        .getOrThrow(profession)));
  }

  private void captureTrades(ClientGameTestContext c, UUID id) {
    c.runOnClient(mc -> {
      var v = mc.level.getEntity(id);
      net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(
          mc.player, mc.level, InteractionHand.MAIN_HAND, v, new EntityHitResult(v, v.position()));
      mc.gameMode.interact(mc.player, v, new EntityHitResult(v, v.position()), InteractionHand.MAIN_HAND);
    });
    c.waitForScreen(MerchantScreen.class);
    c.waitFor(mc -> VerdictService.INSTANCE.record(id) != null && VerdictService.INSTANCE.record(id).snapshot() != null);
    c.runOnClient(mc -> mc.player.closeContainer());
  }

  private static List<String> labelText(net.minecraft.client.renderer.entity.state.EntityRenderState state) {
    var lines = state.getData(MarkerRenderer.LINES);
    return lines == null ? List.of() : lines.stream().map(net.minecraft.network.chat.Component::getString).toList();
  }

  private void testProfessionFilter(ClientGameTestContext c, UUID librarian, UUID armorer) {
    var original = c.computeOnClient(mc -> VerdictService.INSTANCE.settings);
    var snapshot = c.computeOnClient(mc -> VerdictService.INSTANCE.record(armorer).snapshot());
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.probe.reset();
      service.manual(mc.level.getEntity(armorer));
      if (service.probe.context() == null) throw new AssertionError("no active probe for filter test");
      service.selected = armorer;
      service.analyze();
      service.settings(original.withExcludedProfessions(Set.of("minecraft:armorer")));
      service.tick();
      if (service.probe.context() != null || service.selected != null
          || service.markers.containsKey(armorer) || service.nearby.contains(armorer))
        throw new AssertionError("disabled profession retained live state");
      if (service.record(armorer) != null || !snapshot.equals(cachedTrade(service, armorer).snapshot()))
        throw new AssertionError("profession filter deleted cached trades");
      service.manual(mc.level.getEntity(armorer));
      if (service.probe.context() != null) throw new AssertionError("disabled manual capture started");
      service.navigateNext();
      if (armorer.equals(service.selected)) throw new AssertionError("navigation selected disabled worker");
    });
    c.waitFor(mc -> VerdictService.INSTANCE.analysis.verdicts().containsKey(librarian)
        && !VerdictService.INSTANCE.analysis.verdicts().containsKey(armorer));
    c.waitTicks(25);
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (service.markers.containsKey(armorer) || service.analysis.verdicts().containsKey(armorer))
        throw new AssertionError("old asynchronous analysis restored disabled profession");
      long expected = service.records().stream().filter(service::enabled).filter(v -> service.isLoaded(v.uuid())).count();
      if (service.stats().values().stream().mapToLong(Long::longValue).sum() != expected)
        throw new AssertionError("disabled profession counted in stats");
      SettingsScreen.open(null);
    });
    c.clickScreenButton("vv.settings.tab.rules");
    c.clickScreenButton("vv.professions");
    c.runOnClient(mc -> mc.gui.toastManager().clear());
    c.takeScreenshot("profession-filter-zh");
    c.clickScreenButton("vv.selectNone");
    c.clickScreenButton("vv.save");
    c.clickScreenButton("gui.cancel");
    c.runOnClient(mc -> {
      if (VerdictService.INSTANCE.settings.excludedProfessions().size() != 1)
        throw new AssertionError("cancelled settings saved profession draft");
      SettingsScreen.open(null);
    });
    c.clickScreenButton("vv.settings.tab.rules");
    c.clickScreenButton("vv.professions");
    c.clickScreenButton("vv.selectAll");
    c.clickScreenButton("vv.selectNone");
    c.clickScreenButton("vv.save");
    c.clickScreenButton("vv.save");
    c.waitFor(mc -> VerdictService.INSTANCE.analysis.verdicts().isEmpty()
        && VerdictService.INSTANCE.markers.isEmpty() && VerdictService.INSTANCE.nearby.isEmpty());
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (service.stats().values().stream().anyMatch(count -> count != 0L))
        throw new AssertionError("all-disabled professions counted in stats");
      if (service.record(armorer) != null || !snapshot.equals(cachedTrade(service, armorer).snapshot()))
        throw new AssertionError("all-disabled professions lost cache");
      service.probe.run();
    });
    c.waitTicks(30);
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      if (service.probe.context() != null || service.record(armorer) != null || !snapshot.equals(cachedTrade(service, armorer).snapshot()))
        throw new AssertionError("disabled worker was automatically probed");
      service.pause();
      service.settings(original);
    });
    c.waitFor(mc -> VerdictService.INSTANCE.analysis.verdicts().containsKey(armorer)
        && VerdictService.INSTANCE.markers.containsKey(armorer));
    c.runOnClient(mc -> {
      if (!snapshot.equals(VerdictService.INSTANCE.record(armorer).snapshot()))
        throw new AssertionError("re-enabling discarded fresh cache");
    });
    System.out.println("VV_PROFESSION_FILTER_PASS cancellation, stale analysis, stats, UI draft, all-disabled, cached re-enable");
  }

  private Settings markerSettings(Settings s, boolean markers, boolean status, boolean summary) {
    return new Settings(s.version(), markers, status, summary, s.green(), s.yellow(), s.red(),
        s.radius(), s.scanTicks(), s.autoScan(), s.markerLimit(), s.textRange(),
        s.intervalMillis(), s.timeoutMillis(), s.backups(), s.notifications(), s.autoServers(), s.acknowledged(), s.dataset(), s.excludedProfessions());
  }

  private void testMultilineMarkers(ClientGameTestContext c, TestServerContext server, TestServerConnection connection) {
    var original = c.computeOnClient(mc -> VerdictService.INSTANCE.settings);
    var ids = server.computeOnServer(s -> {
      var result = new ArrayList<UUID>();
      var registry = s.overworld().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
      for (int i = 0; i < 3; i++) {
        var v = new Villager(EntityTypes.VILLAGER, s.overworld());
        v.setNoAi(true);
        v.setPos(i == 0 ? -1 : i, -60, i == 0 ? 1 : 2);
        setProfession(v, i == 1 ? VillagerProfession.ARMORER : VillagerProfession.LIBRARIAN);
        var offers = new MerchantOffers();
        if (i == 0) {
          v.setCustomName(net.minecraft.network.chat.Component.literal("Alice").withColor(0x55AAFF));
          v.setCustomNameVisible(true);
          for (int book = 0; book < 3; book++) {
            var stack = new ItemStack(Items.ENCHANTED_BOOK);
            var ench = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            if (book == 0) {
              ench.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.MENDING), 1);
              ench.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 3);
            } else if (book == 1) ench.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 3);
            else ench.set(registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION), 4);
            stack.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, ench.toImmutable());
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), stack, 12, 1, 0));
          }
        } else if (i == 1) {
          for (var item : List.of(Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS))
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5), new ItemStack(item), 12, 1, 0));
        }
        v.setOffers(offers);
        s.overworld().addFreshEntity(v);
        result.add(v.getUUID());
      }
      return result;
    });
    UUID librarian = ids.get(0), armorer = ids.get(1), uninspected = ids.get(2);
    c.waitFor(mc -> ids.stream().allMatch(id -> mc.level.getEntity(id) != null));
    c.runOnClient(mc -> VerdictService.INSTANCE.scan());
    captureTrades(c, librarian);
    captureTrades(c, armorer);
    testProfessionFilter(c, librarian, armorer);
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.edit(service.record(librarian).withManual(VillagerRecord.Manual.KEEP, "", Set.of()));
      service.edit(service.record(armorer).withManual(VillagerRecord.Manual.KEEP, "", Set.of()));
    });
    c.waitFor(mc -> VerdictService.INSTANCE.markers.containsKey(librarian)
        && VerdictService.INSTANCE.markers.get(librarian).lines().getFirst().getString().equals("保留")
        && VerdictService.INSTANCE.markers.containsKey(armorer)
        && VerdictService.INSTANCE.markers.get(armorer).lines().getFirst().getString().equals("保留"));
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      var v = (Villager) mc.level.getEntity(librarian);
      var renderer = mc.getEntityRenderDispatcher().getRenderer(v);
      var state = renderer.createRenderState(v, 0);
      var expected = List.of("保留", "等级：1", "经验修补", "保护4", "耐久3");
      if (!labelText(state).equals(expected)) throw new AssertionError("Chinese marker lines: " + labelText(state));
      if (!state.nameTag.getString().equals("Alice") || state.nameTag.getStyle().getColor().getValue() != 0x55AAFF)
        throw new AssertionError("custom name lost text or color");
      if (state.nameTagAttachment.y <= state.boundingBoxHeight + .35 + 4 * MarkerRenderer.LINE_HEIGHT)
        throw new AssertionError("custom name overlaps marker block");
      if (!labelText(mc.getEntityRenderDispatcher().getRenderer(mc.level.getEntity(armorer))
          .createRenderState(mc.level.getEntity(armorer), 0)).equals(List.of("保留", "等级：1", "钻石护腿", "钻石靴子")))
        throw new AssertionError("armorer signature goods wrong");
      if (labelText(mc.getEntityRenderDispatcher().getRenderer(mc.level.getEntity(uninspected))
          .createRenderState(mc.level.getEntity(uninspected), 0)).size() != 2)
        throw new AssertionError("uninspected trades were invented");
      var noGoods = service.record(armorer).withSnapshot(new TradeSnapshot(List.of(), System.currentTimeMillis()));
      if (MarkerText.lines(noGoods, AnalysisResult.Status.DISCARD, true, true).size() != 2)
        throw new AssertionError("empty signature goods invented lines");
      for (boolean markers : new boolean[] {false, true})
        for (boolean status : new boolean[] {false, true})
          for (boolean summary : new boolean[] {false, true}) {
            service.settings(markerSettings(original, markers, status, summary));
            service.tick();
            reuseRenderState(mc, v, state);
            var wanted = !markers || !status && !summary ? List.<String>of()
                : !summary ? List.of("+") : status ? expected : expected.subList(1, expected.size());
            if (!labelText(state).equals(wanted)) throw new AssertionError("marker switches/reused state: " + labelText(state));
          }
      service.settings(original);
      service.tick();
      var playerState = mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player, 0);
      playerState.setData(MarkerRenderer.LINES, state.getData(MarkerRenderer.LINES));
      reuseRenderState(mc, mc.player, playerState);
      if (playerState.getData(MarkerRenderer.LINES) != null) throw new AssertionError("non-villager inherited marker lines");
      assertSubmittedMarkerRows(mc, v, expected);
    });
    server.runCommand("tp @p -1 -60 -4");
    connection.waitForClientboundPackets();
    c.getInput().lookAt(new net.minecraft.core.BlockPos(-1, -58, 1));
    c.takeScreenshot("multiline-marker-near-zh");
    server.runCommand("fill -2 -60 -1 0 -55 -1 minecraft:stone");
    connection.waitForClientboundPackets();
    c.waitTicks(3);
    c.runOnClient(mc -> {
      var v = (Villager) mc.level.getEntity(librarian);
      var state = mc.getEntityRenderDispatcher().getRenderer(v).createRenderState(v, 0);
      if (mc.player.hasLineOfSight(v) || labelText(state).size() != 5 || !state.appearsGlowing())
        throw new AssertionError("wall marker missing");
    });
    c.takeScreenshot("multiline-marker-wall-zh");
    server.runCommand("fill -2 -60 -1 0 -55 -1 minecraft:air");
    server.runCommand("tp @p -1 -60 -18");
    connection.waitForClientboundPackets();
    c.getInput().lookAt(new net.minecraft.core.BlockPos(-1, -58, 1));
    c.takeScreenshot("multiline-marker-far-zh");
    server.runCommand("tp @p -1 -60 -6");
    var crowd = server.computeOnServer(s -> {
      var result = new ArrayList<UUID>();
      for (int i = 0; i < 8; i++) {
        var v = new Villager(EntityTypes.VILLAGER, s.overworld());
        v.setNoAi(true);
        setProfession(v, VillagerProfession.LIBRARIAN);
        v.setPos(-4 + i, -60, 4 + i % 2);
        s.overworld().addFreshEntity(v);
        result.add(v.getUUID());
      }
      return result;
    });
    c.waitFor(mc -> crowd.stream().allMatch(id -> mc.level.getEntity(id) != null));
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.scan();
      for (var id : crowd)
        service.edit(service.record(id).withSnapshot(service.record(librarian).snapshot())
            .withManual(VillagerRecord.Manual.KEEP, "layout fixture", Set.of()));
    });
    c.waitFor(mc -> crowd.stream().allMatch(id -> VerdictService.INSTANCE.markers.get(id) != null
        && VerdictService.INSTANCE.markers.get(id).lines().size() == 5
        && VerdictService.INSTANCE.markers.get(id).lines().getFirst().getString().equals("保留")));
    c.getInput().lookAt(new net.minecraft.core.BlockPos(0, -58, 4));
    c.takeScreenshot("multiline-marker-crowded-zh");
    server.runOnServer(s -> crowd.forEach(id -> setProfession((Villager) s.overworld().getEntity(id), VillagerProfession.NONE)));
    c.waitFor(mc -> crowd.stream().allMatch(id -> VerdictService.INSTANCE.record(id) == null));
    server.runOnServer(s -> crowd.forEach(id -> s.overworld().getEntity(id).discard()));
    c.waitFor(mc -> crowd.stream().allMatch(id -> mc.level.getEntity(id) == null));
    server.runCommand("tp @p -1 -60 -18");
    connection.waitForClientboundPackets();
    c.getInput().lookAt(new net.minecraft.core.BlockPos(-1, -58, 1));
    var reload = c.computeOnClient(mc -> {
      mc.getLanguageManager().setSelected("en_us");
      mc.options.languageCode = "en_us";
      return mc.reloadResourcePacks();
    });
    c.waitFor(mc -> reload.isDone() && mc.gui.overlay() == null, 600);
    c.waitFor(mc -> VerdictService.INSTANCE.markers.get(librarian).lines().getFirst().getString().equals("Keep"));
    c.runOnClient(mc -> {
      var lines = VerdictService.INSTANCE.markers.get(librarian).lines().stream().map(net.minecraft.network.chat.Component::getString).toList();
      if (!lines.equals(List.of("Keep", "Level: 1", "Mending", "Protection 4", "Unbreaking 3")))
        throw new AssertionError("English marker lines: " + lines);
    });
    c.takeScreenshot("multiline-marker-far-en");
    var back = c.computeOnClient(mc -> {
      mc.getLanguageManager().setSelected("zh_cn");
      mc.options.languageCode = "zh_cn";
      return mc.reloadResourcePacks();
    });
    c.waitFor(mc -> back.isDone() && mc.gui.overlay() == null, 600);
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      var s = service.settings;
      service.settings(new Settings(s.version(), s.markers(), s.statusMarks(), s.summaries(),
          s.green(), s.yellow(), s.red(), s.radius(), s.scanTicks(), false, s.markerLimit(),
          s.textRange(), s.intervalMillis(), s.timeoutMillis(), s.backups(),
          s.notifications(), s.autoServers(), s.acknowledged(), s.dataset(), s.excludedProfessions()));
    });
    server.runOnServer(s -> setProfession((Villager) s.overworld().getEntity(librarian), VillagerProfession.FARMER));
    c.waitFor(mc -> ((Villager) mc.level.getEntity(librarian)).getVillagerData().profession().is(VillagerProfession.FARMER));
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      var v = (Villager) mc.level.getEntity(librarian);
      var renderer = mc.getEntityRenderDispatcher().getRenderer(v);
      if (!labelText(renderer.createRenderState(v, 0)).isEmpty())
        throw new AssertionError("unscanned profession change kept previous highlights");
      service.scan();
      service.tick();
      if (service.record(librarian).snapshot() != null || labelText(renderer.createRenderState(v, 0)).size() != 2)
        throw new AssertionError("profession change did not clear old highlights");
      service.settings(original);
    });
    server.runOnServer(s -> ids.forEach(id -> setProfession((Villager) s.overworld().getEntity(id), VillagerProfession.NONE)));
    c.waitFor(mc -> ids.stream().noneMatch(id -> VerdictService.INSTANCE.record(id) != null));
    server.runOnServer(s -> ids.forEach(id -> s.overworld().getEntity(id).discard()));
    c.waitFor(mc -> ids.stream().noneMatch(id -> mc.level.getEntity(id) != null));
    server.runCommand("tp @p 0 -60 0");
    connection.waitForClientboundPackets();
    System.out.println("VV_MULTILINE_PASS real offers, languages, highest enchantment, armor, custom name, switches, reused state, submission, wall, far");
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private void reuseRenderState(net.minecraft.client.Minecraft mc, net.minecraft.world.entity.Entity entity,
      net.minecraft.client.renderer.entity.state.EntityRenderState state) {
    // Each state was created by this entity's renderer; the helper only erases the wildcard.
    ((net.minecraft.client.renderer.entity.EntityRenderer) mc.getEntityRenderDispatcher().getRenderer(entity))
        .extractRenderState(entity, state, 0);
  }

  private void assertSubmittedMarkerRows(net.minecraft.client.Minecraft mc, Villager v, List<String> expected) {
    try {
      var renderer = mc.getEntityRenderDispatcher().getRenderer(v);
      var state = renderer.createRenderState(v, 0);
      var submitted = new ArrayList<String>();
      var anchors = new ArrayList<Double>();
      var collector = (net.minecraft.client.renderer.SubmitNodeCollector) java.lang.reflect.Proxy.newProxyInstance(
          getClass().getClassLoader(), new Class<?>[] {net.minecraft.client.renderer.SubmitNodeCollector.class},
          (proxy, method, args) -> {
            if (method.getName().equals("submitNameTag")) {
              submitted.add(((net.minecraft.network.chat.Component) args[3]).getString());
              anchors.add(((net.minecraft.world.phys.Vec3) args[1]).y);
              if (!(boolean) args[4]) throw new AssertionError("marker not submitted through walls");
            }
            return null;
          });
      var submit = net.minecraft.client.renderer.entity.EntityRenderer.class.getDeclaredMethod("submitNameDisplay",
          net.minecraft.client.renderer.entity.state.EntityRenderState.class,
          com.mojang.blaze3d.vertex.PoseStack.class, net.minecraft.client.renderer.SubmitNodeCollector.class,
          net.minecraft.client.renderer.state.level.CameraRenderState.class);
      submit.setAccessible(true);
      submit.invoke(renderer, state, new com.mojang.blaze3d.vertex.PoseStack(), collector,
          new net.minecraft.client.renderer.state.level.CameraRenderState());
      var wanted = new ArrayList<String>();
      wanted.add("Alice");
      wanted.addAll(expected);
      if (!submitted.equals(wanted)) throw new AssertionError("submitted rows: " + submitted);
      for (int i = 1; i < anchors.size(); i++)
        if (Math.abs(anchors.get(i - 1) - anchors.get(i) - MarkerRenderer.LINE_HEIGHT) > 0.00001)
          throw new AssertionError("marker rows overlap or have wrong spacing");
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private void testEligibilityAndOutlines(ClientGameTestContext c, TestServerContext server,
      TestServerConnection connection, UUID source) {
    var ids = server.computeOnServer(s -> {
      var result = new ArrayList<UUID>();
      for (int i = 0; i < 3; i++) {
        var v = new Villager(EntityTypes.VILLAGER, s.overworld());
        v.setNoAi(true);
        v.setPos(i * 2, -60, 6);
        if (i == 1) setProfession(v, VillagerProfession.NITWIT);
        if (i == 2) {
          setProfession(v, VillagerProfession.LIBRARIAN);
          v.setAge(-24000);
        }
        s.overworld().addFreshEntity(v);
        result.add(v.getUUID());
      }
      return result;
    });
    c.waitFor(mc -> ids.stream().allMatch(id -> mc.level.getEntity(id) != null));
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.scan();
      service.tick();
      for (var id : ids) {
        service.manual(mc.level.getEntity(id));
        if (service.record(id) != null || service.markers.containsKey(id)
            || service.nearby.contains(id) || service.probe.context() != null)
          throw new AssertionError("ineligible villager entered records/markers/probe");
      }
    });
    UUID worker = ids.getFirst();
    server.runOnServer(s -> {
      var v = (Villager) s.overworld().getEntity(worker);
      setProfession(v, VillagerProfession.LIBRARIAN);
      v.setVillagerData(v.getVillagerData().withLevel(5));
    });
    c.waitFor(mc -> VerdictService.eligible((Villager) mc.level.getEntity(worker)));
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.scan();
      service.tick();
      var v = (Villager) mc.level.getEntity(worker);
      if (service.record(worker) == null || !service.markers.containsKey(worker))
        throw new AssertionError("new worker not scanned");
      var state = mc.getEntityRenderDispatcher().getRenderer(v).createRenderState(v, 0);
      if (!state.appearsGlowing() || state.outlineColor != service.markers.get(worker).color()
          || v.isCurrentlyGlowing()) throw new AssertionError("client outline missing or entity mutated");
      SettingsScreen.toggleMarkers();
      if (mc.getEntityRenderDispatcher().getRenderer(v).createRenderState(v, 0).appearsGlowing())
        throw new AssertionError("master switch did not disable outline");
      SettingsScreen.toggleMarkers();
      service.tick();
      service.edit(service.record(worker).withSnapshot(service.record(source).snapshot()));
    });
    server.runOnServer(s -> s.overworld().getEntity(worker).setGlowingTag(true));
    c.waitFor(mc -> mc.level.getEntity(worker).isCurrentlyGlowing());
    c.runOnClient(mc -> {
      var v = (Villager) mc.level.getEntity(worker);
      if (mc.getEntityRenderDispatcher().getRenderer(v).createRenderState(v, 0).outlineColor
          == VerdictService.INSTANCE.markers.get(worker).color())
        throw new AssertionError("vanilla outline overwritten");
    });
    server.runOnServer(s -> s.overworld().getEntity(worker).setGlowingTag(false));
    c.waitFor(mc -> !mc.level.getEntity(worker).isCurrentlyGlowing());
    c.getInput().lookAt(new net.minecraft.core.BlockPos(0, -59, 6));
    c.takeScreenshot("worker-outline-visible");
    server.runCommand("fill -1 -60 3 1 -57 3 minecraft:stone");
    connection.waitForClientboundPackets();
    c.waitTicks(3);
    c.runOnClient(mc -> {
      var v = (Villager) mc.level.getEntity(worker);
      var state = mc.getEntityRenderDispatcher().getRenderer(v).createRenderState(v, 0);
      if (mc.player.hasLineOfSight(v) || !state.appearsGlowing() || state.getData(MarkerRenderer.LINES) == null)
        throw new AssertionError("wall outline/name missing or wall did not occlude worker");
    });
    c.takeScreenshot("worker-outline-through-wall");
    server.runCommand("fill -1 -60 3 1 -57 3 minecraft:air");
    server.runOnServer(s -> setProfession((Villager) s.overworld().getEntity(worker), VillagerProfession.FARMER));
    c.waitFor(mc -> ((Villager) mc.level.getEntity(worker)).getVillagerData().profession().is(VillagerProfession.FARMER));
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.scan();
      if (service.record(worker).snapshot() != null)
        throw new AssertionError("previous profession snapshot survived");
      service.selected = worker;
      service.manual(mc.level.getEntity(worker));
      if (service.probe.context() == null) throw new AssertionError("worker manual probe missing");
    });
    server.runOnServer(s -> setProfession((Villager) s.overworld().getEntity(worker), VillagerProfession.NONE));
    c.waitFor(mc -> !VerdictService.eligible((Villager) mc.level.getEntity(worker)));
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      var v = (Villager) mc.level.getEntity(worker);
      if (mc.getEntityRenderDispatcher().getRenderer(v).createRenderState(v, 0).appearsGlowing())
        throw new AssertionError("unemployed worker retains stale outline");
      service.tick();
      if (service.record(worker) != null || service.markers.containsKey(worker)
          || service.nearby.contains(worker) || service.selected != null || service.probe.context() != null
          || service.analysis.verdicts().containsKey(worker))
        throw new AssertionError("worker loss did not clear state");
      service.save();
    });
    var catalog = c.computeOnClient(mc -> VerdictService.INSTANCE.storedWorlds());
    c.waitFor(mc -> catalog.isDone());
    catalog.join();
    var directory = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
        .resolve("villagerverdict/worlds");
    try (var store = new JsonStore(e -> { throw new AssertionError(e); })) {
      if (store.worlds(directory).join().stream().flatMap(w -> w.cachedTrades().stream())
          .anyMatch(v -> ids.contains(v.uuid())))
        throw new AssertionError("ineligible villagers persisted to disk");
    }
    server.runOnServer(s -> setProfession((Villager) s.overworld().getEntity(worker), VillagerProfession.LIBRARIAN));
    c.waitFor(mc -> VerdictService.eligible((Villager) mc.level.getEntity(worker)));
    c.runOnClient(mc -> {
      var service = VerdictService.INSTANCE;
      service.scan();
      service.tick();
      if (service.record(worker) == null || service.record(worker).snapshot() != null
          || !service.markers.containsKey(worker))
        throw new AssertionError("regained profession did not start fresh");
    });
    server.runOnServer(s -> setProfession((Villager) s.overworld().getEntity(worker), VillagerProfession.NONE));
    c.waitFor(mc -> !VerdictService.eligible((Villager) mc.level.getEntity(worker)));
    c.runOnClient(mc -> {
      VerdictService.INSTANCE.tick();
      // The next scenario exercises quarantine independently.
      VerdictService.INSTANCE.probe.reset();
    });
    server.runOnServer(s -> ids.forEach(id -> s.overworld().getEntity(id).discard()));
    c.waitFor(mc -> ids.stream().noneMatch(id -> mc.level.getEntity(id) != null));
    System.out.println("VV_WORKER_FILTER_OUTLINE_PASS eligibility, profession transitions, snapshots, master switch, vanilla glow, wall");
  }

  private void testFastHeldItemProbes(ClientGameTestContext c, TestServerContext server,
      TestServerConnection connection) {
    var original = c.computeOnClient(mc -> VerdictService.INSTANCE.settings);
    c.runOnClient(mc -> {
      var s = original;
      VerdictService.INSTANCE.settings(new Settings(s.version(), s.markers(), s.statusMarks(),
          s.summaries(), s.green(), s.yellow(), s.red(), s.radius(), s.scanTicks(), false,
          s.markerLimit(), s.textRange(), 100, s.timeoutMillis(), s.backups(),
          s.notifications(), s.autoServers(), s.acknowledged(), s.dataset(), s.excludedProfessions()));
      VerdictService.INSTANCE.probe.reset();
    });
    server.runCommand("item replace entity @p weapon.mainhand with minecraft:name_tag[minecraft:custom_name='\"Do not apply\"']");
    server.runCommand("item replace entity @p weapon.offhand with minecraft:villager_spawn_egg");
    connection.waitForClientboundPackets();
    c.waitFor(mc -> mc.player.getMainHandItem().is(Items.NAME_TAG)
        && mc.player.getOffhandItem().is(Items.VILLAGER_SPAWN_EGG));
    var ids = server.computeOnServer(s -> {
      var result = new ArrayList<UUID>();
      for (int i = 0; i < 3; i++) {
        var v = new Villager(EntityTypes.VILLAGER, s.overworld());
        v.setNoAi(true);
        v.setPos(1 + i * .5, -60, 0);
        setProfession(v, VillagerProfession.LIBRARIAN);
        var offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 5 + i),
            new ItemStack(Items.BOOK), 12, 1, 0));
        v.setOffers(offers);
        s.overworld().addFreshEntity(v);
        result.add(v.getUUID());
      }
      return result;
    });
    c.waitFor(mc -> ids.stream().allMatch(id -> mc.level.getEntity(id) != null));
    c.runOnClient(mc -> VerdictService.INSTANCE.enable(true));
    c.waitTicks(5);
    c.runOnClient(mc -> {
      if (VerdictService.INSTANCE.probe.context() != null
          || ids.stream().anyMatch(id -> VerdictService.INSTANCE.record(id) != null))
        throw new AssertionError("special items initiated automatic interaction");
    });
    server.runCommand("item replace entity @p weapon.offhand with minecraft:shield");
    connection.waitForClientboundPackets();
    c.waitFor(mc -> ids.stream().allMatch(id -> VerdictService.INSTANCE.record(id) != null
        && VerdictService.INSTANCE.record(id).snapshot() != null), 400);
    c.waitFor(mc -> mc.player.containerMenu == mc.player.inventoryMenu);
    c.runOnClient(mc -> {
      if (mc.gui.screen() != null || !mc.player.getMainHandItem().is(Items.NAME_TAG)
          || !mc.player.getOffhandItem().is(Items.SHIELD))
        throw new AssertionError("offhand probing changed screen or held items");
      VerdictService.INSTANCE.enable(false);
      VerdictService.INSTANCE.settings(original);
    });
    connection.waitForServerboundPackets();
    server.runOnServer(s -> {
      for (int i = 0; i < ids.size(); i++) {
        var v = (Villager) s.overworld().getEntity(ids.get(i));
        if (v.hasCustomName() || v.getOffers().getFirst().getUses() != 0)
          throw new AssertionError("probe used item or traded");
        setProfession(v, VillagerProfession.NONE);
      }
    });
    c.waitFor(mc -> ids.stream().noneMatch(id -> VerdictService.INSTANCE.record(id) != null));
    server.runOnServer(s -> ids.forEach(id -> s.overworld().getEntity(id).discard()));
    c.waitFor(mc -> ids.stream().noneMatch(id -> mc.level.getEntity(id) != null));
    server.runCommand("item replace entity @p weapon.mainhand with minecraft:air");
    server.runCommand("item replace entity @p weapon.offhand with minecraft:air");
    connection.waitForClientboundPackets();
    System.out.println("VV_FAST_HELD_PROBE_PASS three fresh workers without scans, offhand fallback, no item use or trades");
  }

  private Settings withDataset(Settings s, String dataset) {
    return new Settings(s.version(), s.markers(), s.statusMarks(), s.summaries(), s.green(), s.yellow(), s.red(), s.radius(), s.scanTicks(), s.autoScan(),
        s.markerLimit(), s.textRange(), s.intervalMillis(), s.timeoutMillis(),
        s.backups(), s.notifications(), s.autoServers(), s.acknowledged(), dataset, s.excludedProfessions());
  }

  /** Test-only barrier makes the transition happen while disk clearing is still queued. */
  private StoreBarrier holdWorldStore(ClientGameTestContext c) {
    var release = new CountDownLatch(1);
    var entered = new CompletableFuture<Void>();
    c.runOnClient(mc -> {
      ExecutorService worker;
      try {
        var storeField = VerdictService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        var workerField = JsonStore.class.getDeclaredField("worker");
        workerField.setAccessible(true);
        worker = (ExecutorService) workerField.get(storeField.get(VerdictService.INSTANCE));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
      worker.submit(() -> {
        entered.complete(null);
        try {
          if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("store barrier timed out");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError(e);
        }
      });
    });
    c.waitFor(mc -> entered.isDone());
    return new StoreBarrier(release);
  }

  private record StoreBarrier(CountDownLatch release) implements AutoCloseable {
    public void close() {
      release.countDown();
    }
  }

  private void openShortcutsViaSettings(ClientGameTestContext c) {
    c.clickScreenButton("vv.settings");
    c.waitForScreen(SettingsScreen.class);
    c.clickScreenButton("vv.settings.tab.tools");
    c.clickScreenButton("vv.shortcuts");
  }

  private void openDataViaSettings(ClientGameTestContext c) {
    c.clickScreenButton("vv.settings");
    c.waitForScreen(SettingsScreen.class);
    c.clickScreenButton("vv.settings.tab.tools");
    c.clickScreenButton("vv.data");
  }

  private void clickButtonContaining(ClientGameTestContext c, String needle) {
    c.runOnClient(mc -> {
      var info = new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
      for (var child : mc.gui.screen().children()) {
        if (child instanceof net.minecraft.client.gui.components.Button button
            && button.active
            && button.getMessage().getString().contains(needle)) {
          button.onPress(info);
          return;
        }
      }
      throw new AssertionError("No active button matching '" + needle + "' on " + mc.gui.screen());
    });
  }

  private void clickButtonPrefix(ClientGameTestContext c, String prefix) {
    c.runOnClient(mc -> {
      var info = new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0);
      for (var child : mc.gui.screen().children()) {
        if (child instanceof net.minecraft.client.gui.components.Button button
            && button.active
            && button.getMessage().getString().startsWith(prefix)) {
          button.onPress(info);
          return;
        }
      }
      throw new AssertionError("No active button starting with '" + prefix + "' on " + mc.gui.screen());
    });
  }

  private void waitDataScreen(ClientGameTestContext c) {
    c.waitFor(mc -> mc.gui.screen() instanceof DataScreen screen
        && screen.children().stream().anyMatch(child -> child instanceof net.minecraft.client.gui.components.Button b
            && b.active && b.getMessage().getString().equals(
                net.minecraft.network.chat.Component.translatable("vv.clearWorld").getString())));
  }

  private WorldData withIdentity(WorldData source, WorldIdentity identity) {
    return new WorldData(4, identity, source.cachedTrades(), source.targets(), source.regions());
  }
}
