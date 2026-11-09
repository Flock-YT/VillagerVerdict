package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.DisplayStatus;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;

final class MerchantReplacementButton {
    private MerchantReplacementButton() {}

    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof MerchantScreen merchant)) return;
            int container = merchant.getMenu().containerId;
            var button = Button.builder(Component.translatable("vv.replacement.view"),
                ignored -> VerdictService.INSTANCE.viewReplacements(container))
                .bounds((width - 100) / 2, Math.min(height - 22, (height + 166) / 2 + 4), 100, 20).build();
            Screens.getWidgets(screen).add(button);
            update(button, container);
            ScreenEvents.afterTick(screen).register(ignored -> update(button, container));
            ScreenEvents.remove(screen).register(ignored -> VerdictService.INSTANCE.merchantScreenRemoved(container));
        });
    }

    private static void update(Button button, int container) {
        var service = VerdictService.INSTANCE;
        var source = service.tradingVillager(container);
        var verdict = source == null ? null : service.analysis.verdicts().get(source.uuid());
        button.visible = source != null && service.displayStatus(source, verdict) == DisplayStatus.DISCARD;
        button.active = false;
        if (!button.visible) return;
        String hint;
        if (service.analysisPending() || !service.tradingOffersReceived(container)) hint = "vv.replacement.analyzing";
        else if (!service.settings.markers()) hint = "vv.replacement.markersOff";
        else {
            var selection = service.replacements(source);
            button.active = !selection.targets().isEmpty();
            hint = !button.active ? "vv.replacement.none"
                : selection.partial() ? "vv.replacement.partial" : "vv.replacement.help";
        }
        button.setTooltip(Tooltip.create(Component.translatable(hint)));
    }
}
