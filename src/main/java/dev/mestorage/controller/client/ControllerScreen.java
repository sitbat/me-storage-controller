package dev.mestorage.controller.client;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Snapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Compact left navigation, real remote cell slots, and a read-only right hand inventory browser. */
public final class ControllerScreen extends AbstractContainerScreen<ControllerMenu> {
    private static final int TEXT = 0xffe3e8ed;
    private static final int MUTED = 0xffa7b2c0;
    private static final int ACCENT = 0xff7ad3b6;
    private EditBox deviceSearch;
    private EditBox contentSearch;
    private boolean sortByAmount = true;
    private long searchDue;
    private int rightWidth;
    private Button backButton;
    private Button locateButton;
    private Button devicePrevious;
    private Button deviceNext;
    private Button contentPrevious;
    private Button contentNext;
    private Button cellsPrevious;
    private Button cellsNext;
    private final Map<String, Snapshot.DeviceInfo> knownDevices = new HashMap<>();

    public ControllerScreen(ControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 232;
    }

    private static Component tr(String key, Object... arguments) {
        return Component.translatable("gui.me_storage_controller." + key, arguments);
    }

    @Override
    protected void init() {
        imageWidth = Math.min(width - 8, 520);
        rightWidth = imageWidth - 212;
        String oldDevices = deviceSearch == null ? "" : deviceSearch.getValue();
        String oldContents = contentSearch == null ? "" : contentSearch.getValue();
        super.init();
        backButton = button(139, 5, 23, 17, Component.literal("<"), b -> {
            Snapshot s = menu.getSnapshot();
            request(s.selectedCell() >= 0 ? s.selectedDevice() : "", -1, s.devicePage(), 0);
        });
        backButton.setTooltip(Tooltip.create(tr("back")));
        locateButton = button(165, 5, 30, 17, tr("locate"), b -> {
            Snapshot s = menu.getSnapshot();
            var d = knownDevices.get(s.selectedDevice());
            if (d != null) DeviceHighlight.show(d.dimension(), d.pos());
        });
        deviceSearch = new EditBox(font, leftPos + 8, topPos + 27, 186, 16, tr("device_search"));
        deviceSearch.setMaxLength(64);
        deviceSearch.setHint(tr("device_search"));
        deviceSearch.setValue(oldDevices);
        deviceSearch.setResponder(value -> searchDue = System.currentTimeMillis() + 300);
        addRenderableWidget(deviceSearch);
        contentSearch = new EditBox(font, leftPos + 204, topPos + 75, Math.max(40, rightWidth - 35), 16,
                tr("content_search"));
        contentSearch.setMaxLength(64);
        contentSearch.setHint(tr("content_search"));
        contentSearch.setValue(oldContents);
        contentSearch.setResponder(value -> searchDue = System.currentTimeMillis() + 300);
        addRenderableWidget(contentSearch);
        Button sort = button(imageWidth - 39, 75, 31, 16, Component.literal("#"), b -> {
            sortByAmount = !sortByAmount;
            b.setMessage(Component.literal(sortByAmount ? "#" : "A"));
            b.setTooltip(Tooltip.create(tr(sortByAmount ? "sort_amount" : "sort_name")));
            Snapshot s = menu.getSnapshot();
            request(s.selectedDevice(), s.selectedCell(), s.devicePage(), 0);
        });
        sort.setMessage(Component.literal(sortByAmount ? "#" : "A"));
        sort.setTooltip(Tooltip.create(tr(sortByAmount ? "sort_amount" : "sort_name")));
        devicePrevious = button(8, 104, 19, 11, Component.literal("<"), b -> {
            Snapshot s = menu.getSnapshot();
            request(s.selectedDevice(), s.selectedCell(), s.devicePage() - 1, s.contentPage());
        });
        deviceNext = button(176, 104, 19, 11, Component.literal(">"), b -> {
            Snapshot s = menu.getSnapshot();
            request(s.selectedDevice(), s.selectedCell(), s.devicePage() + 1, s.contentPage());
        });
        contentPrevious = button(204, 212, 21, 14, Component.literal("<"), b -> {
            Snapshot s = menu.getSnapshot();
            request(s.selectedDevice(), s.selectedCell(), s.devicePage(), s.contentPage() - 1);
        });
        contentNext = button(imageWidth - 29, 212, 21, 14, Component.literal(">"), b -> {
            Snapshot s = menu.getSnapshot();
            request(s.selectedDevice(), s.selectedCell(), s.devicePage(), s.contentPage() + 1);
        });
        cellsPrevious = button(177, 151, 18, 16, Component.literal("<"), b -> {
            Snapshot s = menu.getSnapshot();
            request(s.selectedDevice(), cellOffset(s) - 10, s.devicePage(), 0);
        });
        cellsNext = button(177, 170, 18, 16, Component.literal(">"), b -> {
            Snapshot s = menu.getSnapshot();
            request(s.selectedDevice(), cellOffset(s) + 10, s.devicePage(), 0);
        });
        cellsPrevious.setTooltip(Tooltip.create(tr("cells_previous")));
        cellsNext.setTooltip(Tooltip.create(tr("cells_next")));
        updateButtons();
    }

    private Button button(int x, int y, int w, int h, Component text, Button.OnPress action) {
        return addRenderableWidget(Button.builder(text, action).bounds(leftPos + x, topPos + y, w, h).build());
    }

    private void request(String device, int cell, int devicePage, int contentPage) {
        searchDue = 0;
        menu.request(device, cell, Math.max(0, devicePage), Math.max(0, contentPage),
                deviceSearch.getValue(), contentSearch.getValue(), sortByAmount);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        deviceSearch.tick();
        contentSearch.tick();
        if (searchDue != 0 && System.currentTimeMillis() >= searchDue) {
            Snapshot s = menu.getSnapshot();
            request(s.selectedDevice(), s.selectedCell(), 0, 0);
        }
        updateButtons();
    }

    private void updateButtons() {
        Snapshot s = menu.getSnapshot();
        for (var d : s.devices()) knownDevices.put(d.id(), d);
        backButton.active = !s.selectedDevice().isEmpty();
        var selected = knownDevices.get(s.selectedDevice());
        locateButton.active = selected != null && !selected.dimension().toString().equals("me_storage_controller:unknown");
        devicePrevious.active = s.devicePage() > 0;
        deviceNext.active = s.devicePage() + 1 < s.devicePages();
        contentPrevious.active = s.contentPage() > 0;
        contentNext.active = s.contentPage() + 1 < s.contentPages();
        cellsPrevious.visible = cellsNext.visible = s.cellSlots() > 10;
        cellsPrevious.active = cellOffset(s) > 0;
        cellsNext.active = cellOffset(s) + 10 < s.cellSlots();
    }

    private static int cellOffset(Snapshot s) { return Math.max(0, s.selectedCell()) / 10 * 10; }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        Snapshot s = menu.getSnapshot();
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xff10161e);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xff566270);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xff252f3c);
        g.fill(x + 199, y + 5, x + 200, y + imageHeight - 5, 0xff637280);
        clippedText(g, title, x + 8, y + 10, 127, TEXT);
        for (int i = 0; i < s.devices().size() && i < 3; i++) {
            Snapshot.DeviceInfo d = s.devices().get(i);
            int rowY = y + 46 + i * 19;
            boolean selected = d.id().equals(s.selectedDevice());
            g.fill(x + 8, rowY, x + 195, rowY + 18, selected ? 0xff415565 : 0xff19232e);
            g.fill(x + 10, rowY + 3, x + 12, rowY + 15, d.active() ? ACCENT : 0xffcc845b);
            clippedText(g, d.name(), x + 16, rowY + 1, 175, TEXT);
            clippedText(g, d.dimension().toString().equals("me_storage_controller:unknown") ? tr("unknown_location")
                            : Component.literal(d.pos().getX() + ", " + d.pos().getY() + ", " + d.pos().getZ()),
                    x + 16, rowY + 10, 175, MUTED);
        }
        centered(g, tr("page", s.devicePage() + 1, Math.max(1, s.devicePages())), x + 100, y + 105, MUTED);
        for (int i = 0; i < 10; i++) {
            boolean enabled = !s.selectedDevice().isEmpty() && cellOffset(s) + i < s.cellSlots();
            slotBackground(g, x + 8 + 18 * i, y + 117, i < s.editableSlots());
            int cellX = x + 8 + 18 * i;
            g.fill(cellX, y + 136, cellX + 16, y + 147,
                    s.selectedCell() == cellOffset(s) + i ? 0xff416d63 : enabled ? 0xff445362 : 0xff1d2631);
            centered(g, Component.literal(Integer.toString(cellOffset(s) + i + 1)), cellX + 8, y + 137,
                    enabled ? TEXT : 0xff697482);
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) slotBackground(g, x + 8 + col * 18, y + 151 + row * 18, true);
        }
        for (int col = 0; col < 9; col++) slotBackground(g, x + 8 + col * 18, y + 209, true);
        renderDetails(g, s);
    }

    private void renderDetails(GuiGraphics g, Snapshot s) {
        int x = leftPos + 204, y = topPos;
        clippedText(g, s.title(), x, y + 9, rightWidth, ACCENT);
        var cap = s.capacity();
        long used = cap.usedBytes(), total = cap.totalBytes();
        Component mainCapacity = tr("bytes", number(used), number(total));
        if (total < 0 && cap.totalSlots() >= 0) {
            used = cap.occupiedSlots(); total = cap.totalSlots();
            mainCapacity = tr("slots", number(used), number(total));
        } else if (total < 0 && cap.fluidCapacity() >= 0) {
            used = cap.fluidAmount(); total = cap.fluidCapacity();
            mainCapacity = tr("fluid", number(used), number(total));
        }
        clippedText(g, mainCapacity, x, y + 25, rightWidth, TEXT);
        g.fill(x, y + 39, x + rightWidth, y + 47, 0xff131c25);
        if (used >= 0 && total > 0) {
            double ratio = Math.min(1, (double) used / total);
            g.fill(x + 1, y + 40, x + 1 + (int) ((rightWidth - 2) * ratio), y + 46,
                    ratio >= 0.95 ? 0xffd58365 : ACCENT);
        }
        Component secondaryCapacity = cap.usedTypes() >= 0 ? tr("types", number(cap.usedTypes()), number(cap.totalTypes()))
                : cap.totalSlots() >= 0 && cap.fluidCapacity() >= 0 ? tr("fluid", number(cap.fluidAmount()), number(cap.fluidCapacity()))
                : tr("contents", number(s.contentCount()));
        clippedText(g, secondaryCapacity, x, y + 51, rightWidth, MUTED);
        Component note = !s.error().isEmpty() ? Component.translatable(s.error())
                : !s.online() ? tr("offline") : cap.unknownCells() > 0 ? tr("unknown_cells", cap.unknownCells())
                : tr("contents", number(s.contentCount()));
        clippedText(g, note, x, y + 63, rightWidth, s.online() ? MUTED : 0xffeda482);
        for (int i = 0; i < s.contents().size() && i < 6; i++) {
            Snapshot.Content content = s.contents().get(i);
            int rowY = y + 95 + i * 19;
            g.fill(x, rowY, x + rightWidth, rowY + 18, i % 2 == 0 ? 0xff19232e : 0xff202b37);
            drawKey(g, content.key(), x + 1, rowY + 1);
            clippedText(g, displayName(content.key()), x + 21, rowY + 1, rightWidth - 23, TEXT);
            clippedText(g, Component.literal(exactAmount(content)), x + 21, rowY + 10, rightWidth - 23, ACCENT);
        }
        if (s.contents().isEmpty()) clippedText(g, tr("no_contents"), x + 3, y + 101, rightWidth - 6, MUTED);
        centered(g, tr("page", s.contentPage() + 1, Math.max(1, s.contentPages())), x + rightWidth / 2, y + 215, MUTED);
    }

    private static String number(long n) {
        return n < 0 ? tr("unknown").getString() : NumberFormat.getIntegerInstance().format(n);
    }

    /** Avoid floating-point conversion, so very large cells retain every stored unit. */
    static String exactAmount(Snapshot.Content content) {
        AEKey key = content.key();
        if (key instanceof AEFluidKey) return number(content.amount()) + " mB";
        int unit = Math.max(1, key.getAmountPerUnit());
        String symbol = key.getUnitSymbol();
        String suffix = symbol == null || symbol.isBlank() ? "" : " " + symbol;
        if (unit == 1) return number(content.amount()) + suffix;
        try {
            return BigDecimal.valueOf(content.amount()).divide(BigDecimal.valueOf(unit)).stripTrailingZeros().toPlainString() + suffix;
        } catch (ArithmeticException nonTerminating) {
            return number(content.amount()) + "/" + number(unit) + suffix;
        }
    }

    private Component displayName(AEKey key) {
        try { return AEKeyRendering.getDisplayName(key); }
        catch (RuntimeException unsupported) { return key.getDisplayName(); }
    }

    private void drawKey(GuiGraphics g, AEKey key, int x, int y) {
        try { AEKeyRendering.drawInGui(minecraft, g, x, y, key); }
        catch (RuntimeException unsupported) { g.drawString(font, "?", x + 4, y + 4, MUTED, false); }
    }

    private void clippedText(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        String value = text.getString();
        if (font.width(value) > maxWidth) value = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("…"))) + "…";
        g.drawString(font, value, x, y, color, false);
    }

    private void centered(GuiGraphics g, Component text, int x, int y, int color) {
        g.drawString(font, text, x - font.width(text) / 2, y, color, false);
    }

    private static void slotBackground(GuiGraphics g, int x, int y, boolean enabled) {
        g.fill(x - 1, y - 1, x + 17, y + 17, enabled ? 0xff111923 : 0xff26303b);
        g.fill(x, y, x + 17, y + 17, enabled ? 0xff82909b : 0xff35404b);
        g.fill(x, y, x + 16, y + 16, enabled ? 0xff4c5864 : 0xff26303b);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) { }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        Snapshot s = menu.getSnapshot();
        int x = mouseX - leftPos, y = mouseY - topPos;
        List<Component> tooltip = new ArrayList<>();
        if (x >= 8 && x < 195 && y >= 46 && y < 103) {
            int index = (y - 46) / 19;
            if (index < s.devices().size()) {
                var d = s.devices().get(index);
                tooltip.add(d.name());
                tooltip.add(d.dimension().toString().equals("me_storage_controller:unknown") ? tr("unknown_location")
                        : Component.literal(d.dimension() + " · " + d.pos().getX() + ", " + d.pos().getY() + ", " + d.pos().getZ()));
                tooltip.add(Component.translatable("gui.me_storage_controller.kind." + d.kind()));
            }
        } else if (x >= 8 && x < 188 && y >= 136 && y < 147 && cellOffset(s) + (x - 8) / 18 < s.cellSlots()) {
            tooltip.add(tr("cell_details", cellOffset(s) + (x - 8) / 18 + 1));
        } else if (x >= 204 && x < imageWidth - 8 && y >= 95 && y < 209) {
            int index = (y - 95) / 19;
            if (index < s.contents().size()) {
                var content = s.contents().get(index);
                try { tooltip.addAll(AEKeyRendering.getTooltip(content.key())); }
                catch (RuntimeException unsupported) { tooltip.add(displayName(content.key())); }
                tooltip.add(Component.literal(exactAmount(content)));
                tooltip.add(Component.literal(content.key().getId().toString()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
        } else if (x >= 204 && x < imageWidth - 8 && y >= 24 && y < 72) {
            var cap = s.capacity();
            tooltip.add(tr("bytes", number(cap.usedBytes()), number(cap.totalBytes())));
            tooltip.add(tr("types", number(cap.usedTypes()), number(cap.totalTypes())));
            if (cap.totalSlots() >= 0) tooltip.add(tr("slots", number(cap.occupiedSlots()), number(cap.totalSlots())));
            if (cap.fluidCapacity() >= 0) tooltip.add(tr("fluid", number(cap.fluidAmount()), number(cap.fluidCapacity())));
            if (cap.totalSlots() >= 0 || cap.fluidCapacity() >= 0) tooltip.add(tr("external_capacity"));
            if (cap.unknownCells() > 0) tooltip.add(tr("unknown_cells", cap.unknownCells()));
            if (!s.error().isEmpty()) tooltip.add(Component.translatable(s.error()));
        } else if (x >= 204 && x < imageWidth - 8 && y >= 5 && y < 22) tooltip.add(s.title());
        if (!tooltip.isEmpty()) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) mouseX - leftPos, y = (int) mouseY - topPos;
        Snapshot s = menu.getSnapshot();
        if (button == 0 && x >= 8 && x < 195 && y >= 46 && y < 103) {
            int index = (y - 46) / 19;
            if (index < s.devices().size()) {
                request(s.devices().get(index).id(), -1, s.devicePage(), 0);
                return true;
            }
        }
        if (button == 0 && x >= 8 && x < 188 && y >= 136 && y < 147 && !s.selectedDevice().isEmpty()) {
            int cell = cellOffset(s) + (x - 8) / 18;
            if (cell < s.cellSlots()) {
                request(s.selectedDevice(), cell, s.devicePage(), 0);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != 256 && (deviceSearch.isFocused() || contentSearch.isFocused())) {
            (deviceSearch.isFocused() ? deviceSearch : contentSearch).keyPressed(key, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
