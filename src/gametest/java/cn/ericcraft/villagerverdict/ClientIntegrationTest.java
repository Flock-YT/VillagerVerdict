package cn.ericcraft.villagerverdict;

import cn.ericcraft.villagerverdict.client.*;
import java.util.*;
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
      c.waitFor(mc -> VerdictService.INSTANCE.ready());
      server.runCommand("tp @p 0 -60 0");
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
            if (service.markers.size() > 128 || !service.markers.containsKey(id))
              throw new AssertionError("marker cap or selection priority failed");
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
}
