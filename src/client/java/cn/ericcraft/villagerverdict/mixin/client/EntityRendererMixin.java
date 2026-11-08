package cn.ericcraft.villagerverdict.mixin.client;

import cn.ericcraft.villagerverdict.client.MarkerRenderer;
import cn.ericcraft.villagerverdict.client.VerdictService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void vv$extract(Entity entity, EntityRenderState state, float delta, CallbackInfo ci) {
    state.setData(MarkerRenderer.LINES, null);
    var s = VerdictService.INSTANCE;
    var m = s.markers.get(entity.getUUID());
    if (m == null || !s.settings.markers()
        || !(entity instanceof Villager v) || !VerdictService.eligible(v)) return;
    var record = s.record(entity.getUUID());
    if (record == null || !record.profession().equals(v.getVillagerData().profession().getRegisteredName())) return;
    if (!state.appearsGlowing()) state.outlineColor = m.color();
    if (!m.lines().isEmpty()) {
      state.setData(MarkerRenderer.LINES, m.lines());
      if (state.nameTag != null)
        state.nameTagAttachment = new Vec3(0,
            state.boundingBoxHeight + .35 + m.lines().size() * MarkerRenderer.LINE_HEIGHT, 0);
    }
  }

  @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("TAIL"))
  private void vv$submitLines(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
      CameraRenderState camera, CallbackInfo ci) {
    MarkerRenderer.submit(state, pose, collector, camera);
  }
}
