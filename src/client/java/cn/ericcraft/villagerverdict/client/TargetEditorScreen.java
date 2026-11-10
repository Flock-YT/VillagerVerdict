package cn.ericcraft.villagerverdict.client;

import cn.ericcraft.villagerverdict.model.TargetTrade;
import cn.ericcraft.villagerverdict.model.UiFormatting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

/** Beginner-friendly editor for wanted trades with searchable Chinese/English item & enchantment pickers. */
public final class TargetEditorScreen extends WorldScreen {
    private enum Mode {
        FORM,
        PICK_ITEM,
        PICK_ENCHANT
    }

    private record ItemOption(String id, String displayName, ItemStack stack, boolean popular) {}

    private record EnchantOption(String id, String displayName, int maxLevel) {}

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
    private String error = "";

    private EditBox priceBox;
    private EditBox pickerSearchBox;
    private String pickerQuery = "";
    private int pickerOffset;
    private boolean restorePickerFocus;
    private final LiveWidgets pickerWidgets = new LiveWidgets(this::addRenderableWidget, widget -> {
        if (getFocused() == widget) setFocused(null);
        removeWidget(widget);
    });
    private boolean refreshingPicker;
    private Language catalogLanguage;
    private Object catalogLevel;
    private String catalogItemId = "";
    private List<ItemOption> itemCatalog = List.of();
    private List<EnchantOption> enchantCatalog = List.of();
    private List<ItemOption> visibleItems = List.of();

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
        priceBox = null;
        normalizeEnchantment();
        if (mode == Mode.PICK_ENCHANT && !canChooseEnchantment()) mode = Mode.FORM;
        if (mode == Mode.FORM) {
            pickerWidgets.begin(true);
            pickerWidgets.end();
            initForm();
        } else refreshPicker(true);
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
                    rebuildWidgets();
                })
            .bounds(innerX + 22, 39, innerW - 22, 20)
            .build());

        if (canChooseEnchantment()) initEnchantmentControls(innerX, innerW);

        int priceY = priceY();
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
            .bounds(innerX + innerW - 144, priceY, 80, 20)
            .build());
        unlimitedBtn.setTooltip(Tooltip.create(Component.translatable("vv.targetEditor.priceHint")));

        priceBox = new EditBox(font, innerX + innerW - 60, priceY, 60, 20, priceLabel());
        priceBox.setMaxLength(12);
        priceBox.setValue(maxPriceText);
        priceBox.setHint(Component.translatable("vv.targetEditor.priceHint"));
        priceBox.setTooltip(Tooltip.create(Component.translatable("vv.targetEditor.priceHint")));
        priceBox.setResponder(v -> {
            maxPriceText = v;
            error = "";
        });
        addRenderableWidget(priceBox);

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

    private int priceY() {
        return canChooseEnchantment() ? 126 : 74;
    }

    private Component priceLabel() {
        return Component.translatable(canChooseEnchantment()
            ? "vv.targetEditor.priceLabel" : "vv.targetEditor.priceLabelPlain");
    }

    private void initEnchantmentControls(int innerX, int innerW) {
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
    }

    private void refreshPicker(boolean reattach) {
        if (refreshingPicker || mode == Mode.FORM) return;
        refreshingPicker = true;
        pickerWidgets.begin(reattach);
        ensurePickerCatalog();
        if (mode == Mode.PICK_ITEM) initItemPicker();
        else initEnchantPicker();
        pickerWidgets.end();
        refreshingPicker = false;
    }

    private void initPickerSearch(int reservedWidth, String titleKey, String hintKey) {
        int innerX = panelX() + 8;
        int innerW = panelWidth() - 16;
        int searchWidth = innerW - reservedWidth;
        pickerSearchBox = pickerWidgets.editBox("search:" + mode,
            () -> new EditBox(font, innerX, 30, searchWidth, 20, Component.translatable(titleKey)),
            innerX, 30, searchWidth);
        if (!pickerSearchBox.getValue().equals(pickerQuery)) pickerSearchBox.setValue(pickerQuery);
        pickerSearchBox.setMaxLength(128);
        pickerSearchBox.setHint(ClientSearch.hint(hintKey));
        pickerWidgets.tooltip(pickerSearchBox, ClientSearch.hint(hintKey));
        pickerSearchBox.setResponder(v -> {
            pickerQuery = v;
            pickerOffset = 0;
            refreshPicker(false);
        });
        if (restorePickerFocus) {
            setFocused(pickerSearchBox);
            restorePickerFocus = false;
        }
    }

    private void initItemPicker() {
        int innerX = panelX() + 8;
        int innerW = panelWidth() - 16;
        initPickerSearch(64, "vv.targetEditor.pickItemTitle", "vv.targetEditor.searchItemHint");
        pickerWidgets.button("back", Component.translatable("vv.targetEditor.backToForm"),
            innerX + innerW - 60, 30, 60, () -> {
                mode = Mode.FORM;
                rebuildWidgets();
            });
        int quickCount = width >= 460 ? 6 : 4;
        int qw = (innerW - (quickCount - 1) * 4) / quickCount;
        for (int i = 0; i < quickCount; i++) {
            String qid = QUICK_PICK_ITEMS.get(i);
            String label = ClientDisplayText.itemDisplayName(qid);
            pickerWidgets.button("quick:" + qid, Component.literal(font.plainSubstrByWidth(label, qw - 8)),
                innerX + i * (qw + 4), 54, qw, () -> selectItem(qid)).setHeight(18);
        }
        List<ItemOption> options = filteredItems();
        visibleItems = options;
        int listTop = 78;
        int rows = Math.max(1, (height - listTop - 34) / 22);
        pickerOffset = Math.clamp(pickerOffset, 0, Math.max(0, options.size() - rows));
        for (int i = pickerOffset; i < Math.min(options.size(), pickerOffset + rows); i++) {
            ItemOption opt = options.get(i);
            int rowY = listTop + (i - pickerOffset) * 22;
            String label = opt.displayName();
            if (width >= 520) label += "  (" + opt.id() + ")";
            pickerWidgets.button("item:" + opt.id(), Component.literal(font.plainSubstrByWidth(label, innerW - 34)),
                innerX + 22, rowY, innerW - 22, () -> selectItem(opt.id()));
        }
        if (options.isEmpty() && pickerQuery.contains(":")) {
            String raw = pickerQuery.trim();
            pickerWidgets.button("raw-item", Component.translatable("vv.targetEditor.useRawId", raw),
                innerX, listTop, innerW, () -> selectItem(raw));
        }
    }

    private void initEnchantPicker() {
        int innerX = panelX() + 8;
        int innerW = panelWidth() - 16;
        initPickerSearch(122, "vv.targetEditor.pickEnchantTitle", "vv.targetEditor.searchEnchantHint");
        pickerWidgets.button("clear", Component.translatable("vv.clear"),
            innerX + innerW - 118, 30, 56, () -> {
                enchantmentId = "";
                minLevel = 1;
                mode = Mode.FORM;
                rebuildWidgets();
            });
        pickerWidgets.button("back", Component.translatable("vv.targetEditor.backToForm"),
            innerX + innerW - 58, 30, 58, () -> {
                mode = Mode.FORM;
                rebuildWidgets();
            });
        List<EnchantOption> options = filteredEnchantments();
        int listTop = 56;
        int rows = Math.max(1, (height - listTop - 34) / 22);
        pickerOffset = Math.clamp(pickerOffset, 0, Math.max(0, options.size() - rows));
        for (int i = pickerOffset; i < Math.min(options.size(), pickerOffset + rows); i++) {
            EnchantOption opt = options.get(i);
            int rowY = listTop + (i - pickerOffset) * 22;
            String label = opt.displayName() + " · "
                + Component.translatable("vv.targetEditor.maxLevelHint", opt.maxLevel()).getString();
            pickerWidgets.button("enchant:" + opt.id(), Component.literal(font.plainSubstrByWidth(label, innerW - 12)),
                innerX, rowY, innerW, () -> {
                    enchantmentId = opt.id();
                    minLevel = Math.clamp(minLevel, 1, opt.maxLevel());
                    mode = Mode.FORM;
                    rebuildWidgets();
                });
        }
    }

    private void selectItem(String id) {
        itemId = id;
        mode = Mode.FORM;
        error = "";
        rebuildWidgets();
    }

    private void ensurePickerCatalog() {
        boolean reload = catalogLanguage != Language.getInstance() || catalogLevel != minecraft.level;
        if (reload) {
            catalogLanguage = Language.getInstance();
            catalogLevel = minecraft.level;
            var items = new ArrayList<ItemOption>();
            for (var holder : BuiltInRegistries.ITEM.listElements().toList()) {
                if (holder.value() == Items.AIR) continue;
                String id = holder.getRegisteredName();
                ItemStack stack = holder.value().getDefaultInstance();
                items.add(new ItemOption(id, stack.getHoverName().getString(), stack, POPULAR_ITEMS.contains(id)));
            }
            items.sort(Comparator.comparing((ItemOption o) -> !o.popular())
                .thenComparingInt(o -> {
                    int index = POPULAR_ITEMS.indexOf(o.id());
                    return index < 0 ? Integer.MAX_VALUE : index;
                }).thenComparing(ItemOption::displayName));
            itemCatalog = List.copyOf(items);
        }
        if (reload || !itemId.equals(catalogItemId)) {
            catalogItemId = itemId;
            enchantCatalog = supportedEnchantments().map(holder -> new EnchantOption(holder.getRegisteredName(),
                    holder.value().description().getString(), Math.max(1, holder.value().getMaxLevel())))
                .sorted(Comparator.comparing(EnchantOption::displayName)).toList();
        }
    }

    private List<ItemOption> filteredItems() {
        return itemCatalog.stream().filter(o -> ClientSearch.contains(o.displayName(), pickerQuery)
            || ClientSearch.contains(o.id(), pickerQuery)).toList();
    }

    private List<EnchantOption> filteredEnchantments() {
        return enchantCatalog.stream().filter(o -> ClientSearch.contains(o.displayName(), pickerQuery)
            || ClientSearch.contains(o.id(), pickerQuery)).toList();
    }

    private Stream<Holder.Reference<Enchantment>> supportedEnchantments() {
        var mc = Minecraft.getInstance();
        ItemStack chosen = ClientDisplayText.itemStack(itemId);
        if (mc.level == null || chosen.isEmpty()) return Stream.empty();
        return mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
            .filter(holder -> chosen.is(Items.ENCHANTED_BOOK) || holder.value().isSupportedItem(chosen));
    }

    private boolean canChooseEnchantment() {
        return supportedEnchantments().findAny().isPresent();
    }

    private void normalizeEnchantment() {
        enchantmentId = enchantmentId.trim();
        var selected = supportedEnchantments()
            .filter(holder -> holder.getRegisteredName().equals(enchantmentId)).findFirst();
        if (selected.isEmpty()) {
            enchantmentId = "";
            minLevel = 1;
        } else {
            minLevel = Math.clamp(minLevel, 1, Math.max(1, selected.get().value().getMaxLevel()));
        }
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
        normalizeEnchantment();
        try {
            if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("item");
            String trimmedPrice = maxPriceText == null || maxPriceText.isBlank() ? "0" : maxPriceText.trim();
            double price = Double.parseDouble(trimmedPrice);
            if (!Double.isFinite(price) || price < 0) throw new IllegalArgumentException("price >= 0");
            int level = enchantmentId.isBlank() ? 0 : Math.max(1, minLevel);
            var target = new TargetTrade(
                targetId, itemId.trim(), enchantmentId.trim(), level, price);
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
            if (canChooseEnchantment()) {
                g.text(font, Component.translatable("vv.targetEditor.enchantLabel"), innerX, 63, 0xFF9FB8D8);
                g.text(font, Component.translatable("vv.targetEditor.levelLabel"), innerX, 106, 0xFFE6EDF7);
            }
            g.text(font, priceLabel(), innerX, priceY() + 6, 0xFFE6EDF7);

            TargetTrade previewRule = new TargetTrade(
                targetId,
                itemId,
                enchantmentId,
                enchantmentId.isBlank() ? 0 : minLevel,
                parseMaxPriceOrDefault());
            String previewText = Component.translatable(
                "vv.targetEditor.preview", ClientDisplayText.targetSummary(previewRule)).getString();
            int py = priceY() + 30;
            for (var line : font.split(Component.literal(previewText), innerW)) {
                if (py < 24 + panelH - 10) {
                    g.text(font, line, innerX, py, 0xFF55DD88);
                    py += 10;
                }
            }
            for (var line : font.split(Component.translatable("vv.targetEditor.queryHelp"), innerW)) {
                if (py + 9 < 24 + panelH) {
                    g.text(font, line, innerX, py, 0xFF8CA4C4);
                    py += 10;
                }
            }
        } else if (mode == Mode.PICK_ITEM) {
            int innerX = px + 8;
            List<ItemOption> options = visibleItems;
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
    public void tick() {
        refreshPicker(false);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horiz, double vert) {
        if (mode != Mode.FORM) {
            pickerOffset = Math.max(0, pickerOffset - (int) Math.signum(vert));
            refreshPicker(false);
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
                refreshPicker(false);
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
