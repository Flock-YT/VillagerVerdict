package cn.ericcraft.villagerverdict.mixin.client;

import cn.ericcraft.villagerverdict.client.VerdictService;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void vv$extract(Entity entity, EntityRenderState state, float delta, CallbackInfo ci) {
    var s = VerdictService.INSTANCE;
    var m = s.markers.get(entity.getUUID());
    if (m == null || !s.settings.markers()) return;
    if (s.settings.outlines()
        && !state.appearsGlowing()
        && (m.visible() || s.settings.throughWalls())) state.outlineColor = m.color();
    if (m.summary()
        && (s.settings.statusMarks() || s.settings.summaries())
        && (m.visible() || s.settings.throughWalls())) {
      var text =
          s.settings.summaries()
              ? m.text().copy()
              : net.minecraft.network.chat.Component.literal(m.symbol());
      text.withColor(m.color());
      state.nameTag =
          state.nameTag == null ? text : state.nameTag.copy().append(" · ").append(text);
      state.nameTagAttachment = new Vec3(0, state.boundingBoxHeight + .35, 0);
    }
  }
}
