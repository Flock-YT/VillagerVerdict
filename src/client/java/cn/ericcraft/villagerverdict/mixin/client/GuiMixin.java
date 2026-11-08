package cn.ericcraft.villagerverdict.mixin.client;

import cn.ericcraft.villagerverdict.client.VerdictService;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {
  @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
  private void vv$screen(Screen screen, CallbackInfo ci) {
    if (VerdictService.INSTANCE.screen(screen)) ci.cancel();
  }
}
