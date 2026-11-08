package cn.ericcraft.villagerverdict.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Feedback belongs to explicit shortcuts, never to background service operations. */
final class ShortcutActions {
    private ShortcutActions() {}

    static void show(Component message) {
        Minecraft.getInstance().gui.hud.setOverlayMessage(message, false);
    }

    static void scan() {
        var service = VerdictService.INSTANCE;
        show(service.scan()
            ? Component.translatable("vv.feedback.scan", service.nearby.size())
            : Component.translatable("vv.feedback.scanUnavailable"));
    }

    static void toggleMarkers() {
        SettingsScreen.toggleMarkers();
        show(Component.translatable(VerdictService.INSTANCE.settings.markers()
            ? "vv.feedback.markersOn" : "vv.feedback.markersOff"));
    }

    static void navigateNext() {
        var service = VerdictService.INSTANCE;
        service.navigateNext();
        var target = service.record(service.selected);
        show(target == null ? Component.translatable("vv.feedback.noTarget")
            : Component.translatable("vv.feedback.located", (int) target.x(), (int) target.y(),
                (int) target.z(), (int) Math.sqrt(service.distanceSquared(target))));
    }

    static void toggleProbe() {
        SettingsScreen.toggleProbe(ShortcutActions::show);
    }
}
