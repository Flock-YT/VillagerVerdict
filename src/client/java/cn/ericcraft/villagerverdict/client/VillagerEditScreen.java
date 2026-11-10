package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.UiFormatting;
import cn.ericcraft.villagerverdict.model.VillagerRecord;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Beginner-friendly editor for a villager's keep mode, note, and search tags. */
public final class VillagerEditScreen extends WorldScreen {
    private static final VillagerRecord.Manual[] OPTIONS = {
        VillagerRecord.Manual.AUTO,
        VillagerRecord.Manual.KEEP
    };

    private final Screen parent;
    private final VillagerRecord villager;
    private final Consumer<VillagerRecord> onSave;
    private VillagerRecord.Manual selectedManual;
    private String noteText;
    private String tagsText;
    private EditBox noteBox;
    private EditBox tagsBox;

    public VillagerEditScreen(
            Screen parent, VillagerRecord villager, Consumer<VillagerRecord> onSave) {
        super(Component.translatable("vv.editVillager.title"));
        this.parent = parent;
        this.villager = villager;
        this.onSave = onSave;
        this.selectedManual = UiFormatting.normalizeManualForUi(villager.manual());
        this.noteText = villager.note();
        this.tagsText = String.join(", ", villager.tags());
    }

    private int panelX() {
        return Math.max(12, width / 2 - 175);
    }

    private int panelWidth() {
        return Math.min(350, width - 24);
    }

    @Override
    protected void init() {
        int px = panelX();
        int pw = panelWidth();
        int innerW = pw - 20;
        int btnW = (innerW - 4) / 2;
        int startX = px + 10;

        for (int i = 0; i < OPTIONS.length; i++) {
            VillagerRecord.Manual mode = OPTIONS[i];
            boolean active = mode == selectedManual;
            String label = (active ? "● " : "")
                + Component.translatable("vv.manual." + mode.name()).getString();
            var btn = Button.builder(
                    Component.literal(label).withColor(active ? 0xFF55DD88 : 0xFFE6EDF7),
                    ignored -> {
                        syncDraftFromBoxes();
                        selectedManual = mode;
                        rebuildWidgets();
                    })
                .bounds(startX + i * (btnW + 4), 56, btnW, 20)
                .build();
            btn.setTooltip(Tooltip.create(Component.translatable("vv.manual." + mode.name() + ".desc")));
            addRenderableWidget(btn);
        }

        noteBox = new EditBox(font, startX, 118, innerW, 18, Component.translatable("vv.note"));
        noteBox.setMaxLength(256);
        noteBox.setValue(noteText);
        noteBox.setHint(Component.translatable("vv.editVillager.noteHint"));
        noteBox.setResponder(v -> noteText = v);
        addRenderableWidget(noteBox);

        tagsBox = new EditBox(font, startX, 158, innerW, 18, Component.translatable("vv.tags"));
        tagsBox.setMaxLength(512);
        tagsBox.setValue(tagsText);
        tagsBox.setHint(Component.translatable("vv.editVillager.tagsHint"));
        tagsBox.setResponder(v -> tagsText = v);
        tagsBox.setTooltip(Tooltip.create(Component.translatable("vv.editVillager.tagsTip")));
        addRenderableWidget(tagsBox);

        int footerY = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("vv.save"), ignored -> saveAndClose())
            .bounds(px, footerY, 96, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(px + 102, footerY, 84, 20)
            .build());
    }

    private void syncDraftFromBoxes() {
        if (noteBox != null) noteText = noteBox.getValue();
        if (tagsBox != null) tagsText = tagsBox.getValue();
    }

    private void saveAndClose() {
        syncDraftFromBoxes();
        Set<String> tags = new LinkedHashSet<>();
        Arrays.stream(tagsText.split("[,，]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(tags::add);
        onSave.accept(villager.withManual(selectedManual, noteText.trim(), Set.copyOf(tags)));
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);
        int px = panelX();
        int pw = panelWidth();
        int panelH = Math.max(176, height - 58);
        ClientDisplayText.drawPanel(g, px, 26, pw, panelH);

        int x = px + 10;
        int innerW = pw - 20;
        String prof = villager.baby()
            ? Component.translatable("vv.baby").getString()
            : Component.translatable(
                "entity.minecraft.villager."
                    + villager.profession().substring(villager.profession().indexOf(':') + 1))
                .getString();
        String subtitle = prof + " · Lv." + villager.level()
            + " · (" + (int) villager.x() + ", " + (int) villager.y() + ", " + (int) villager.z() + ")";
        g.text(font, font.plainSubstrByWidth(subtitle, innerW), x, 32, 0xFFE6EDF7);

        g.text(font, Component.translatable("vv.editVillager.statusLabel"), x, 45, 0xFF9FB8D8);

        int descY = 80;
        for (var line : font.split(
                Component.translatable("vv.manual." + selectedManual.name() + ".desc"), innerW)) {
            if (descY <= 98) {
                g.text(font, line, x, descY, 0xFFBBD0E8);
                descY += 10;
            }
        }

        g.text(font, Component.translatable("vv.note"), x, 106, 0xFF9FB8D8);
        g.text(font, Component.translatable("vv.tags"), x, 146, 0xFF9FB8D8);
        int tipY = 180;
        for (var line : font.split(Component.translatable("vv.editVillager.tagsTip"), innerW)) {
            if (tipY + 9 < 26 + panelH) {
                g.text(font, line, x, tipY, 0xFF8CA4C4);
                tipY += 10;
            }
        }
        for (var line : font.split(Component.translatable("vv.editVillager.scopeNote"), innerW)) {
            if (tipY + 9 < 26 + panelH) {
                g.text(font, line, x, tipY, 0xFF8CA4C4);
                tipY += 10;
            }
        }

        super.extractRenderState(g, mx, my, delta);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
