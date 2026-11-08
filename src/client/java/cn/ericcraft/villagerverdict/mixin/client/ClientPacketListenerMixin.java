package cn.ericcraft.villagerverdict.mixin.client;

import cn.ericcraft.villagerverdict.client.VerdictService;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
  @Inject(
      method = "handleOpenScreen",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
              shift = At.Shift.AFTER))
  private void vv$open(ClientboundOpenScreenPacket p, CallbackInfo ci) {
    VerdictService.INSTANCE.open(p.getContainerId(), p.getType() == MenuType.MERCHANT);
  }

  @Inject(method = "handleMerchantOffers", at = @At("TAIL"))
  private void vv$offers(ClientboundMerchantOffersPacket p, CallbackInfo ci) {
    VerdictService.INSTANCE.offers(p);
  }

  @Inject(method = "handleContainerContent", at = @At("TAIL"))
  private void vv$content(ClientboundContainerSetContentPacket p, CallbackInfo ci) {
    VerdictService.INSTANCE.content(p.containerId());
  }

  @Inject(method = "handleContainerClose", at = @At("TAIL"))
  private void vv$close(ClientboundContainerClosePacket p, CallbackInfo ci) {
    VerdictService.INSTANCE.closed(p.getContainerId());
  }
}
