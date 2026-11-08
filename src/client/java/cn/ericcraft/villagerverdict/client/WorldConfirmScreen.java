package cn.ericcraft.villagerverdict.client;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;

/** Only confirmations created by this mod use a clear world background. */
public final class WorldConfirmScreen extends ConfirmScreen {
    public WorldConfirmScreen(BooleanConsumer callback, Component title, Component message) {
        super(callback, title, message);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (!WorldScreen.extractWorldBackground(minecraft)) super.extractBackground(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
