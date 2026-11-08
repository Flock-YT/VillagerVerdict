package cn.ericcraft.villagerverdict.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class MarkerRenderer {
    public static final RenderStateDataKey<List<Component>> LINES = RenderStateDataKey.create(() -> "Villager Verdict lines");
    public static final double LINE_HEIGHT = 11 * 0.025;

    private MarkerRenderer() {}

    public static void submit(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
        CameraRenderState camera) {
        var lines = state.getData(LINES);
        if (lines == null) return;
        for (int i = 0; i < lines.size(); i++) {
            var anchor = new Vec3(0, state.boundingBoxHeight + .35 + (lines.size() - 1 - i) * LINE_HEIGHT, 0);
            collector.submitNameTag(pose, anchor, 0, lines.get(i), true, state.lightCoords, camera);
        }
    }
}
