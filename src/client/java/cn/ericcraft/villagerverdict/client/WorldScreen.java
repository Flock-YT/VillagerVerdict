package cn.ericcraft.villagerverdict.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Mod screens leave the world clear without changing the player's menu settings. */
public abstract class WorldScreen extends Screen {
    protected WorldScreen(Component title) {
        super(title);
    }

    static boolean extractWorldBackground(Minecraft minecraft) {
        if (minecraft.level == null) return false;
        minecraft.gui.hud.extractDeferredSubtitles();
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!extractWorldBackground(minecraft)) super.extractBackground(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
