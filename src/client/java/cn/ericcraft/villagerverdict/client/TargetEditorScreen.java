package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.TargetTrade;
import cn.ericcraft.villagerverdict.model.UiFormatting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Beginner-friendly editor for wanted trades with searchable Chinese/English item & enchantment pickers. */
public final class TargetEditorScreen extends WorldScreen {
    private enum Mode {
        FORM,
        PICK_ITEM,
        PICK_ENCHANT
    }

    private record ItemOption(String id, String displayName, ItemStack stack, boolean popular) {}

    private record EnchantOption(String id, String displayName, int maxLevel, boolean supported) {}

    private static final List<String> POPULAR_ITEMS = List.of(
        "minecraft:enchanted_book",
        "minecraft:emerald",
        "minecraft:diamond_chestplate",
        "minecraft:diamond_pickaxe",
        "minecraft:bookshelf",
        "minecraft:arrow",
        "minecraft:diamond_sword",
        "minecraft:quartz_block",
        "minecraft:glass",
        "minecraft:golden_carrot",
        "minecraft:name_tag",
        "minecraft:lantern"
    );

    private static final List<String> QUICK_PICK_ITEMS = List.of(
        "minecraft:enchanted_book",
        "minecraft:emerald",
        "minecraft:diamond_chestplate",
        "minecraft:diamond_pickaxe",
        "minecraft:bookshelf",
        "minecraft:arrow"
    );

    private final Screen parent;
    private final String targetId;
    private final boolean editing;
    private final Consumer<TargetTrade> onSave;
    private final Runnable onDelete;

    private Mode mode = Mode.FORM;
    private String itemId;
    private String enchantmentId;
    private int minLevel;
    private String maxPriceText;
    private int backups;
    private String error = "";

    private EditBox priceBox;
    private EditBox pickerSearchBox;
    private String pickerQuery = "";
    private int pickerOffset;
    private boolean restorePickerFocus;
    private int pickerCursor;

    private record IconRow(ItemStack stack, int x, int y) {}

    private final List<IconRow> pickerIcons = new ArrayList<>();

    public TargetEditorScreen(
            Screen parent,
            TargetTrade existing,
            Consumer<TargetTrade> onSave,
            Runnable onDelete) {
        super(Component.translatable(
            existing == null ? "vv.targetEditor.titleNew" : "vv.targetEditor.titleEdit"));
        this.parent = parent;
        this.editing = existing != null;
        this.targetId = existing == null ? UUID.randomUUID().toString() : existing.id();
        this.itemId = existing == null ? "minecraft:enchanted_book" : existing.item();
        this.enchantmentId = existing == null ? "" : existing.enchantment();
        this.minLevel = existing == null ? 1 : Math.max(1, existing.minLevel());
        this.maxPriceText = existing == null || existing.maxUnitPrice() == 0
            ? "0"
            : UiFormatting.formatDecimal(existing.maxUnitPrice());
        this.backups = existing == null ? 1 : Math.max(1, existing.backups());
        this.onSave = onSave;
        this.onDelete = onDelete;
    }

    private int panelX() {
        return Math.max(12, width / 2 - 190);
    }

    private int panelWidth() {
        return Math.min(380, width - 24);
    }

    @Override
    protected void init() {
        if (mode == Mode.FORM) initForm();
        else if (mode == Mode.PICK_ITEM) initItemPicker();
        else initEnchantPicker();
    }

    private void initForm() {
        int px = panelX();
        int pw = panelWidth();
        int innerX = px + 10;
        int innerW = pw - 20;

        String itemText = ClientDisplayText.itemDisplayName(itemId)
            + " (" + Component.translatable("vv.targetEditor.changeItem").getString() + ")";
        addRenderableWidget(Button.builder(
                Component.literal(font.plainSubstrByWidth(itemText, innerW - 32)),
                ignored -> {
                    syncPriceBox();
                    mode = Mode.PICK_ITEM;
                    pickerQuery = "";
                    pickerOffset = 0;
                    restorePickerFocus = true;
                    pickerCursor = 0;
                    rebuildWidgets();
                })
            .bounds(innerX + 22, 39, innerW - 22, 20)
            .build());

        int clearW = 54;
        String enchText = enchantmentId.isBlank()
            ? Component.translatable("vv.targetEditor.anyEnchant").getString()
            : ClientDisplayText.enchantmentDisplayName(enchantmentId)
                + " (" + Component.translatable("vv.targetEditor.changeEnchant").getString() + ")";
        addRenderableWidget(Button.builder(
                Component.literal(font.plainSubstrByWidth(enchText, innerW - clearW - 12)),
                ignored -> {
                    syncPriceBox();
                    mode = Mode.PICK_ENCHANT;
                    pickerQuery = "";
                    pickerOffset = 0;
                    restorePickerFocus = true;
                    pickerCursor = 0;
                    rebuildWidgets();
                })
            .bounds(innerX, 74, innerW - clearW - 4, 20)
            .build());

        var clearEnch = addRenderableWidget(Button.builder(
                Component.translatable("vv.clear"),
                ignored -> {
                    syncPriceBox();
                    enchantmentId = "";
                    minLevel = 1;
                    rebuildWidgets();
                })
            .bounds(innerX + innerW - clearW, 74, clearW, 20)
            .build());
        clearEnch.active = !enchantmentId.isBlank();

        int maxLevel = Math.max(1, ClientDisplayText.enchantmentMaxLevel(enchantmentId));
        int ctrlX = innerX + innerW - 116;
        var minusLvl = addRenderableWidget(Button.builder(
                Component.literal("-"),
                ignored -> {
                    syncPriceBox();
                    minLevel = Math.max(1, minLevel - 1);
                    rebuildWidgets();
                })
            .bounds(ctrlX, 100, 24, 20)
            .build());
        var lvlDisp = addRenderableWidget(Button.builder(
                Component.literal(enchantmentId.isBlank() ? "-" : "Lv." + minLevel),
                ignored -> {})
            .bounds(ctrlX + 28, 100, 60, 20)
            .build());
        lvlDisp.active = false;
        var plusLvl = addRenderableWidget(Button.builder(
                Component.literal("+"),
                ignored -> {
                    syncPriceBox();
                    minLevel = Math.min(maxLevel, minLevel + 1);
                    rebuildWidgets();
                })
            .bounds(ctrlX + 92, 100, 24, 20)
            .build());
        minusLvl.active = !enchantmentId.isBlank() && minLevel > 1;
        plusLvl.active = !enchantmentId.isBlank() && minLevel < maxLevel;

        boolean unlimited = parseMaxPriceOrDefault() == 0.0;
        var unlimitedBtn = addRenderableWidget(Button.builder(
                Component.literal((unlimited ? "● " : "")
                    + Component.translatable(
                        unlimited ? "vv.targetEditor.priceUnlimited" : "vv.targetEditor.priceLimited").getString())
                    .withColor(unlimited ? 0xFF55DD88 : 0xFFE6EDF7),
                ignored -> {
                    syncPriceBox();
                    maxPriceText = unlimited ? "16" : "0";
                    rebuildWidgets();
                })
            .bounds(innerX + innerW - 144, 126, 80, 20)
            .build());
        unlimitedBtn.setTooltip(Tooltip.create(Component.translatable("vv.targetEditor.priceHint")));

        priceBox = new EditBox(font, innerX + innerW - 60, 126, 60, 20,
            Component.translatable("vv.targetEditor.priceLabel"));
        priceBox.setMaxLength(12);
        priceBox.setValue(maxPriceText);
        priceBox.setHint(Component.translatable("vv.targetEditor.priceHint"));
        priceBox.setResponder(v -> {
            maxPriceText = v;
            error = "";
        });
        addRenderableWidget(priceBox);

        var minusKeep = addRenderableWidget(Button.builder(
                Component.literal("-"),
                ignored -> {
                    syncPriceBox();
                    backups = Math.max(1, backups - 1);
                    rebuildWidgets();
                })
            .bounds(ctrlX, 152, 24, 20)
            .build());
        minusKeep.active = backups > 1;
        var keepDisp = addRenderableWidget(Button.builder(
                Component.translatable("vv.targetEditor.keepCount", backups),
                ignored -> {})
            .bounds(ctrlX + 28, 152, 60, 20)
            .build());
        keepDisp.active = false;
        addRenderableWidget(Button.builder(
                Component.literal("+"),
                ignored -> {
                    syncPriceBox();
                    backups = Math.min(16, backups + 1);
                    rebuildWidgets();
                })
            .bounds(ctrlX + 92, 152, 24, 20)
            .build());

        int footerY = height - 26;
        addRenderableWidget(Button.builder(Component.translatable("vv.save"), ignored -> saveAndClose())
            .bounds(px, footerY, 84, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> onClose())
            .bounds(px + 90, footerY, 80, 20)
            .build());
        if (editing && onDelete != null) {
            addRenderableWidget(Button.builder(Component.translatable("vv.delete"), ignored -> {
                    onDelete.run();
                    onClose();
                })
                .bounds(px + pw - 80, footerY, 80, 20)
                .build());
        }
    }

    private void initItemPicker() {
        int px = panelX();
        int pw = panelWidth();
        int innerX = px + 8;
        int innerW = pw - 16;

        pickerSearchBox = new EditBox(font, innerX, 30, innerW - 64, 20,
            Component.translatable("vv.targetEditor.pickItemTitle"));
        pickerSearchBox.setMaxLength(128);
        pickerSearchBox.setValue(pickerQuery);
        pickerSearchBox.setHint(Component.translatable("vv.targetEditor.searchItemHint"));
        pickerSearchBox.setResponder(v -> {
            pickerQuery = v;
            pickerOffset = 0;
            restorePickerFocus = true;
            pickerCursor = pickerSearchBox.getCursorPosition();
            rebuildWidgets();
        });
        addRenderableWidget(pickerSearchBox);
        if (restorePickerFocus) {
            setFocused(pickerSearchBox);
            pickerSearchBox.setCursorPosition(Math.min(pickerCursor, pickerQuery.length()));
            restorePickerFocus = false;
        }

        addRenderableWidget(Button.builder(
                Component.translatable("vv.targetEditor.backToForm"),
                ignored -> {
                    mode = Mode.FORM;
                    rebuildWidgets();
                })
            .bounds(innerX + innerW - 60, 30, 60, 20)
            .build());

        int quickCount = width >= 460 ? 6 : 4;
        int qw = (innerW - (quickCount - 1) * 4) / quickCount;
        for (int i = 0; i < quickCount; i++) {
            String qid = QUICK_PICK_ITEMS.get(i);
            String label = ClientDisplayText.itemDisplayName(qid);
            addRenderableWidget(Button.builder(
                    Component.literal(font.plainSubstrByWidth(label, qw - 8)),
                    ignored -> selectItem(qid))
                .bounds(innerX + i * (qw + 4), 54, qw, 18)
                .build());
        }

        List<ItemOption> options = filteredItems();
        int listTop = 78;
        int rows = Math.max(1, (height - listTop - 34) / 22);
        pickerOffset = Math.clamp(pickerOffset, 0, Math.max(0, options.size() - rows));
        for (int i = pickerOffset; i < Math.min(options.size(), pickerOffset + rows); i++) {
            ItemOption opt = options.get(i);
            int rowY = listTop + (i - pickerOffset) * 22;
            String label = opt.displayName();
            if (width >= 520) label += "  (" + opt.id() + ")";
            addRenderableWidget(Button.builder(
                    Component.literal(font.plainSubstrByWidth(label, innerW - 34)),
                    ignored -> selectItem(opt.id()))
                .bounds(innerX + 22, rowY, innerW - 22, 20)
                .build());
        }
        if (options.isEmpty() && pickerQuery.contains(":")) {
            String raw = pickerQuery.trim();
            addRenderableWidget(Button.builder(
                    Component.translatable("vv.targetEditor.useRawId", raw),
                    ignored -> selectItem(raw))
                .bounds(innerX, listTop, innerW, 20)
                .build());
        }
    }

    private void initEnchantPicker() {
        int px = panelX();
        int pw = panelWidth();
        int innerX = px + 8;
        int innerW = pw - 16;

        pickerSearchBox = new EditBox(font, innerX, 30, innerW - 122, 20,
            Component.translatable("vv.targetEditor.pickEnchantTitle"));
        pickerSearchBox.setMaxLength(128);
        pickerSearchBox.setValue(pickerQuery);
        pickerSearchBox.setHint(Component.translatable("vv.targetEditor.searchEnchantHint"));
        pickerSearchBox.setResponder(v -> {
            pickerQuery = v;
            pickerOffset = 0;
            restorePickerFocus = true;
            pickerCursor = pickerSearchBox.getCursorPosition();
            rebuildWidgets();
        });
        addRenderableWidget(pickerSearchBox);
        if (restorePickerFocus) {
            setFocused(pickerSearchBox);
            pickerSearchBox.setCursorPosition(Math.min(pickerCursor, pickerQuery.length()));
            restorePickerFocus = false;
        }

        addRenderableWidget(Button.builder(
                Component.translatable("vv.clear"),
                ignored -> {
                    enchantmentId = "";
                    minLevel = 1;
                    mode = Mode.FORM;
                    rebuildWidgets();
                })
            .bounds(innerX + innerW - 118, 30, 56, 20)
            .build());

        addRenderableWidget(Button.builder(
                Component.translatable("vv.targetEditor.backToForm"),
                ignored -> {
                    mode = Mode.FORM;
                    rebuildWidgets();
                })
            .bounds(innerX + innerW - 58, 30, 58, 20)
            .build());

        List<EnchantOption> options = filteredEnchantments();
        int listTop = 56;
        int rows = Math.max(1, (height - listTop - 34) / 22);
        pickerOffset = Math.clamp(pickerOffset, 0, Math.max(0, options.size() - rows));
        for (int i = pickerOffset; i < Math.min(options.size(), pickerOffset + rows); i++) {
            EnchantOption opt = options.get(i);
            int rowY = listTop + (i - pickerOffset) * 22;
            String label = opt.displayName() + " · "
                + Component.translatable("vv.targetEditor.maxLevelHint", opt.maxLevel()).getString();
            addRenderableWidget(Button.builder(
                    Component.literal(font.plainSubstrByWidth(label, innerW - 12)),
                    ignored -> {
                        enchantmentId = opt.id();
                        minLevel = Math.clamp(minLevel, 1, opt.maxLevel());
                        mode = Mode.FORM;
                        rebuildWidgets();
                    })
                .bounds(innerX, rowY, innerW, 20)
                .build());
        }
        if (options.isEmpty() && pickerQuery.contains(":")) {
            String raw = pickerQuery.trim();
            addRenderableWidget(Button.builder(
                    Component.translatable("vv.targetEditor.useRawId", raw),
                    ignored -> {
                        enchantmentId = raw;
                        minLevel = Math.max(1, minLevel);
                        mode = Mode.FORM;
                        rebuildWidgets();
                    })
                .bounds(innerX, listTop, innerW, 20)
                .build());
        }
    }

    private void selectItem(String id) {
        itemId = id;
        mode = Mode.FORM;
        error = "";
        rebuildWidgets();
    }

    private List<ItemOption> filteredItems() {
        String q = pickerQuery.trim().toLowerCase(Locale.ROOT);
        var list = new ArrayList<ItemOption>();
        for (var holder : BuiltInRegistries.ITEM.listElements().toList()) {
            var item = holder.value();
            if (item == Items.AIR) continue;
            String id = holder.getRegisteredName();
            ItemStack stack = item.getDefaultInstance();
            String name = stack.getHoverName().getString();
            if (q.isEmpty()
                    || name.toLowerCase(Locale.ROOT).contains(q)
                    || id.toLowerCase(Locale.ROOT).contains(q)) {
                int popIndex = POPULAR_ITEMS.indexOf(id);
                list.add(new ItemOption(id, name, stack, popIndex >= 0));
            }
        }
        list.sort(Comparator
            .comparing((ItemOption o) -> !o.popular())
            .thenComparingInt(o -> {
                int idx = POPULAR_ITEMS.indexOf(o.id());
                return idx < 0 ? Integer.MAX_VALUE : idx;
            })
            .thenComparing(ItemOption::displayName));
        return list;
    }

    private List<EnchantOption> filteredEnchantments() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        String q = pickerQuery.trim().toLowerCase(Locale.ROOT);
        ItemStack chosen = ClientDisplayText.itemStack(itemId);
        boolean checkSupport = !chosen.isEmpty() && !chosen.is(Items.ENCHANTED_BOOK);
        var list = new ArrayList<EnchantOption>();
        mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(holder -> {
            String id = holder.getRegisteredName();
            String name = holder.value().description().getString();
            if (q.isEmpty()
                    || name.toLowerCase(Locale.ROOT).contains(q)
                    || id.toLowerCase(Locale.ROOT).contains(q)) {
                boolean supported = !checkSupport || holder.value().isSupportedItem(chosen);
                list.add(new EnchantOption(id, name, Math.max(1, holder.value().getMaxLevel()), supported));
            }
        });
        list.sort(Comparator
            .comparing((EnchantOption o) -> !o.supported())
            .thenComparing(EnchantOption::displayName));
        return list;
    }

    private void syncPriceBox() {
        if (priceBox != null) maxPriceText = priceBox.getValue();
    }

    private double parseMaxPriceOrDefault() {
        try {
            String raw = maxPriceText == null ? "0" : maxPriceText.trim();
            if (raw.isEmpty()) return 0.0;
            double v = Double.parseDouble(raw);
            return Double.isFinite(v) && v >= 0 ? v : 0.0;
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private void saveAndClose() {
        syncPriceBox();
        try {
            if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("item");
            String trimmedPrice = maxPriceText == null || maxPriceText.isBlank() ? "0" : maxPriceText.trim();
            double price = Double.parseDouble(trimmedPrice);
            if (!Double.isFinite(price) || price < 0) throw new IllegalArgumentException("price >= 0");
            int level = enchantmentId.isBlank() ? 0 : Math.max(1, minLevel);
            var target = new TargetTrade(
                targetId, itemId.trim(), enchantmentId.trim(), level, price, backups);
            onSave.accept(target);
            onClose();
        } catch (Exception e) {
            error = Component.translatable("vv.invalid").getString() + ": " + e.getMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Component screenTitle = switch (mode) {
            case FORM -> title;
            case PICK_ITEM -> Component.translatable("vv.targetEditor.pickItemTitle");
            case PICK_ENCHANT -> Component.translatable("vv.targetEditor.pickEnchantTitle");
        };
        g.centeredText(font, screenTitle, width / 2, 10, 0xFFFFFFFF);
        int px = panelX();
        int pw = panelWidth();
        int panelH = Math.max(176, height - 56);
        ClientDisplayText.drawPanel(g, px, 24, pw, panelH);

        pickerIcons.clear();
        if (mode == Mode.FORM) {
            int innerX = px + 10;
            int innerW = pw - 20;
            g.text(font, Component.translatable("vv.targetEditor.itemLabel"), innerX, 28, 0xFF9FB8D8);
            ItemStack preview = ClientDisplayText.itemStack(itemId);
            if (!preview.isEmpty()) {
                g.item(preview, innerX, 41);
            }
            g.text(font, Component.translatable("vv.targetEditor.enchantLabel"), innerX, 63, 0xFF9FB8D8);
            g.text(font, Component.translatable("vv.targetEditor.levelLabel"), innerX, 106, 0xFFE6EDF7);
            g.text(font, Component.translatable("vv.targetEditor.priceLabel"), innerX, 132, 0xFFE6EDF7);
            g.text(font, Component.translatable("vv.targetEditor.keepLabel"), innerX, 158, 0xFFE6EDF7);

            TargetTrade previewRule = new TargetTrade(
                targetId,
                itemId,
                enchantmentId,
                enchantmentId.isBlank() ? 0 : minLevel,
                parseMaxPriceOrDefault(),
                backups);
            String previewText = Component.translatable(
                "vv.targetEditor.preview", ClientDisplayText.targetSummary(previewRule)).getString();
            int py = 178;
            for (var line : font.split(Component.literal(previewText), innerW)) {
                if (py < 24 + panelH - 10) {
                    g.text(font, line, innerX, py, 0xFF55DD88);
                    py += 10;
                }
            }
            if (py + 10 < 24 + panelH) {
                g.text(font, font.plainSubstrByWidth(
                    Component.translatable("vv.targetEditor.keepTip").getString(), innerW),
                    innerX, py, 0xFF8CA4C4);
            }
        } else if (mode == Mode.PICK_ITEM) {
            int innerX = px + 8;
            List<ItemOption> options = filteredItems();
            int listTop = 78;
            int rows = Math.max(1, (height - listTop - 34) / 22);
            for (int i = pickerOffset; i < Math.min(options.size(), pickerOffset + rows); i++) {
                int rowY = listTop + (i - pickerOffset) * 22;
                pickerIcons.add(new IconRow(options.get(i).stack(), innerX + 2, rowY + 2));
            }
        }

        super.extractRenderState(g, mx, my, delta);
        for (var icon : pickerIcons) {
            g.item(icon.stack(), icon.x(), icon.y());
        }
        if (!error.isEmpty()) {
            g.text(font, font.plainSubstrByWidth(error, pw), px, height - 38, 0xFFFF6677);
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horiz, double vert) {
        if (mode != Mode.FORM) {
            pickerOffset = Math.max(0, pickerOffset - (int) Math.signum(vert));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(x, y, horiz, vert);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (mode != Mode.FORM) {
            if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
                mode = Mode.FORM;
                rebuildWidgets();
                return true;
            }
            if (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN
                    || e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEUP) {
                pickerOffset = Math.max(0,
                    pickerOffset + (e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_PAGEDOWN ? 5 : -5));
                rebuildWidgets();
                return true;
            }
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
