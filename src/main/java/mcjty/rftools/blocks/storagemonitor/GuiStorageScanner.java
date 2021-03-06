package mcjty.rftools.blocks.storagemonitor;

import mcjty.lib.base.StyleConfig;
import mcjty.lib.container.GenericGuiContainer;
import mcjty.lib.container.GhostOutputSlot;
import mcjty.lib.entity.GenericEnergyStorageTileEntity;
import mcjty.lib.gui.Window;
import mcjty.lib.gui.events.BlockRenderEvent;
import mcjty.lib.gui.events.DefaultSelectionEvent;
import mcjty.lib.gui.layout.HorizontalAlignment;
import mcjty.lib.gui.layout.HorizontalLayout;
import mcjty.lib.gui.layout.PositionalLayout;
import mcjty.lib.gui.layout.VerticalLayout;
import mcjty.lib.gui.widgets.*;
import mcjty.lib.gui.widgets.Button;
import mcjty.lib.gui.widgets.Label;
import mcjty.lib.gui.widgets.Panel;
import mcjty.lib.gui.widgets.TextField;
import mcjty.lib.network.Argument;
import mcjty.lib.network.clientinfo.PacketGetInfoFromServer;
import mcjty.lib.tools.ItemStackTools;
import mcjty.lib.tools.MinecraftTools;
import mcjty.lib.varia.BlockPosTools;
import mcjty.lib.varia.Logging;
import mcjty.rftools.RFTools;
import mcjty.rftools.craftinggrid.GuiCraftingGrid;
import mcjty.rftools.craftinggrid.PacketRequestGridSync;
import mcjty.rftools.network.RFToolsMessages;
import net.minecraft.block.Block;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;


public class GuiStorageScanner extends GenericGuiContainer<StorageScannerTileEntity> {
    private static final int STORAGE_MONITOR_WIDTH = 256;
    private static final int STORAGE_MONITOR_HEIGHT = 244;

    private static final ResourceLocation iconLocation = new ResourceLocation(RFTools.MODID, "textures/gui/storagescanner.png");
    private static final ResourceLocation guielements = new ResourceLocation(RFTools.MODID, "textures/gui/guielements.png");

    private WidgetList storageList;
    private WidgetList itemList;
    private ToggleButton openViewButton;
    private EnergyBar energyBar;
    private Button topButton;
    private Button upButton;
    private Button downButton;
    private Button bottomButton;
    private Button removeButton;
    private TextField searchField;
    private ImageChoiceLabel exportToStarred;
    private Panel storagePanel;
    private Panel itemPanel;

    private GuiCraftingGrid craftingGrid;

    private long prevTime = -1;

    private int listDirty = 0;

    // From server: all the positions with inventories
    public static List<InventoriesInfoPacketClient.InventoryInfo> fromServer_inventories = new ArrayList<>();
    // From server: all the positions with inventories matching the search
    public static Set<BlockPos> fromServer_foundInventories = new HashSet<>();
    // From server: the contents of an inventory
    public static List<ItemStack> fromServer_inventory = new ArrayList<>();

    public GuiStorageScanner(StorageScannerTileEntity storageScannerTileEntity, StorageScannerContainer storageScannerContainer) {
        super(RFTools.instance, RFToolsMessages.INSTANCE, storageScannerTileEntity, storageScannerContainer, RFTools.GUI_MANUAL_MAIN, "stomon");
        GenericEnergyStorageTileEntity.setCurrentRF(storageScannerTileEntity.getEnergyStored(EnumFacing.DOWN));

        craftingGrid = new GuiCraftingGrid();

        xSize = STORAGE_MONITOR_WIDTH;
        ySize = STORAGE_MONITOR_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();

        int maxEnergyStored = tileEntity.getMaxEnergyStored(EnumFacing.DOWN);
        energyBar = new EnergyBar(mc, this).setFilledRectThickness(1).setVertical().setDesiredWidth(10).setDesiredHeight(50).setMaxValue(maxEnergyStored).setShowText(false);
        energyBar.setValue(GenericEnergyStorageTileEntity.getCurrentRF());

        openViewButton = new ToggleButton(mc, this).setCheckMarker(false).setText("V")
                .setTooltips("Toggle wide storage list");
        openViewButton.setPressed(tileEntity.isOpenWideView());
        openViewButton.addButtonEvent(widget -> toggleView());
        upButton = new Button(mc, this).setText("U").setTooltips("Move inventory up")
                .addButtonEvent(widget -> moveUp());
        topButton = new Button(mc, this).setText("T").setTooltips("Move inventory to the top")
                .addButtonEvent(widget -> moveTop());
        downButton = new Button(mc, this).setText("D").setTooltips("Move inventory down")
                .addButtonEvent(widget -> moveDown());
        bottomButton = new Button(mc, this).setText("B").setTooltips("Move inventory to the bottom")
                .addButtonEvent(widget -> moveBottom());
        removeButton = new Button(mc, this).setText("R").setTooltips("Remove inventory from list")
                .addButtonEvent(widget -> removeFromList());

        Panel energyPanel = new Panel(mc, this).setLayout(new VerticalLayout().setVerticalMargin(0).setSpacing(1))
                .setDesiredWidth(10);
        energyPanel
                .addChild(openViewButton)
                .addChild(energyBar)
                .addChild(topButton)
                .addChild(upButton)
                .addChild(downButton)
                .addChild(bottomButton)
                .addChild(new Label(mc, this).setText(" "))
                .addChild(removeButton);

        exportToStarred = new ImageChoiceLabel(mc, this)
                .setLayoutHint(new PositionalLayout.PositionalHint(12, 223, 13, 13))
                .addChoiceEvent((parent, newChoice) -> changeExportMode());
        exportToStarred.addChoice("No", "Export to current container", guielements, 131, 19);
        exportToStarred.addChoice("Yes", "Export to first routable container", guielements, 115, 19);

        storagePanel = makeStoragePanel(energyPanel);
        itemPanel = makeItemPanel();

        Button scanButton = new Button(mc, this)
                .setText("Scan")
                .setDesiredWidth(50)
                .setDesiredHeight(14)
                .addButtonEvent(parent -> RFToolsMessages.INSTANCE.sendToServer(new PacketGetInfoFromServer(RFTools.MODID,
                        new InventoriesInfoPacketServer(tileEntity.getDimension(), tileEntity.getStorageScannerPos(), true))))
                .setTooltips("Start/stop a scan of", "all storage units", "in radius");
        ScrollableLabel radiusLabel = new ScrollableLabel(mc, this)
                .addValueEvent((parent, newValue) -> changeRadius(newValue))
                .setRealMinimum(1)
                .setRealMaximum(20);
        radiusLabel.setRealValue(tileEntity.getRadius());

        searchField = new TextField(mc, this).addTextEvent((parent, newText) -> {
            storageList.clearHilightedRows();
            fromServer_foundInventories.clear();
            startSearch(newText);
        });
        Panel searchPanel = new Panel(mc, this)
                .setLayoutHint(new PositionalLayout.PositionalHint(8, 142, 256-11, 18))
                .setLayout(new HorizontalLayout()).setDesiredHeight(18)
                .addChild(new Label(mc, this).setText("Search:"))
                .addChild(searchField);

        Slider radiusSlider = new Slider(mc, this)
                .setHorizontal()
                .setTooltips("Radius of scan")
                .setMinimumKnobSize(12)
                .setDesiredHeight(14)
                .setScrollable(radiusLabel);
        Panel scanPanel = new Panel(mc, this)
                .setLayoutHint(new PositionalLayout.PositionalHint(8, 162, 74, 54))
                .setFilledRectThickness(-2)
                .setFilledBackground(StyleConfig.colorListBackground)
                .setLayout(new VerticalLayout().setVerticalMargin(6).setSpacing(1))
                .addChild(scanButton)
                .addChild(radiusSlider)
                .addChild(radiusLabel);

        if (tileEntity.isDummy()) {
            scanButton.setEnabled(false);
            radiusLabel.setVisible(false);
            radiusSlider.setVisible(false);
        }

        Widget toplevel = new Panel(mc, this).setBackground(iconLocation).setLayout(new PositionalLayout())
                .addChild(storagePanel)
                .addChild(itemPanel)
                .addChild(searchPanel)
                .addChild(scanPanel)
                .addChild(exportToStarred);
        toplevel.setBounds(new Rectangle(guiLeft, guiTop, xSize, ySize));

        window = new Window(this, toplevel);

        Keyboard.enableRepeatEvents(true);

        fromServer_foundInventories.clear();
        fromServer_inventory.clear();

        if (tileEntity.isDummy()) {
            fromServer_inventories.clear();
        } else {
            tileEntity.requestRfFromServer(RFTools.MODID);
        }

        BlockPos pos = tileEntity.getCraftingGridContainerPos();
        craftingGrid.initGui(modBase, network, mc, this, pos, tileEntity.getCraftingGridProvider(), guiLeft, guiTop, xSize, ySize);
        network.sendToServer(new PacketRequestGridSync(pos));

        if (StorageScannerConfiguration.hilightStarredOnGuiOpen) {
            storageList.setSelected(0);
        }
    }

    private int getStoragePanelWidth() {
        return openViewButton.isPressed() ? 130 : 50;
    }

    private Panel makeItemPanel() {
        itemList = new WidgetList(mc, this).setPropagateEventsToChildren(true)
            .setInvisibleSelection(true);
        Slider itemListSlider = new Slider(mc, this).setDesiredWidth(10).setVertical().setScrollable(itemList);
        return new Panel(mc, this)
                .setLayout(new HorizontalLayout().setSpacing(1).setHorizontalMargin(1))
                .setLayoutHint(new PositionalLayout.PositionalHint(getStoragePanelWidth() + 6, 4, 256-getStoragePanelWidth()-12, 86+54))
                .addChild(itemList).addChild(itemListSlider);
    }

    private Panel makeStoragePanel(Panel energyPanel) {
        storageList = new WidgetList(mc, this).addSelectionEvent(new DefaultSelectionEvent() {
            @Override
            public void select(Widget parent, int index) {
                getInventoryOnServer();
            }

            @Override
            public void doubleClick(Widget parent, int index) {
                hilightSelectedContainer(index);
            }
        }).setPropagateEventsToChildren(true);

        Slider storageListSlider = new Slider(mc, this).setDesiredWidth(10).setVertical().setScrollable(storageList);

        return new Panel(mc, this).setLayout(new HorizontalLayout().setSpacing(1).setHorizontalMargin(1))
                .setLayoutHint(new PositionalLayout.PositionalHint(3, 4, getStoragePanelWidth(), 86+54))
                .setDesiredHeight(86+54)
                .addChild(energyPanel)
                .addChild(storageList).addChild(storageListSlider);
    }

    private void toggleView() {
        storagePanel.setLayoutHint(new PositionalLayout.PositionalHint(3, 4, getStoragePanelWidth(), 86+54));
        itemPanel.setLayoutHint(new PositionalLayout.PositionalHint(getStoragePanelWidth() + 6, 4, 256-getStoragePanelWidth()-12, 86+54));
        // Force layout dirty:
        window.getToplevel().setBounds(window.getToplevel().getBounds());
        listDirty = 0;
        requestListsIfNeeded();
        sendServerCommand(RFToolsMessages.INSTANCE, tileEntity.getDimension(), StorageScannerTileEntity.CMD_SETVIEW,
                new Argument("b", openViewButton.isPressed()));
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws IOException {
        super.mouseClicked(x, y, button);
        craftingGrid.getWindow().mouseClicked(x, y, button);
        if (button == 1) {
            Slot slot = getSlotAtPosition(x, y);
            if (slot instanceof GhostOutputSlot) {
                sendServerCommand(RFToolsMessages.INSTANCE, StorageScannerTileEntity.CMD_CLEARGRID);
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        craftingGrid.getWindow().handleMouseInput();
    }

    @Override
    protected void mouseReleased(int x, int y, int state) {
        super.mouseReleased(x, y, state);
        craftingGrid.getWindow().mouseMovedOrUp(x, y, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        craftingGrid.getWindow().keyTyped(typedChar, keyCode);
    }


    private void moveUp() {
        sendServerCommand(RFToolsMessages.INSTANCE, tileEntity.getDimension(), StorageScannerTileEntity.CMD_UP, new Argument("index", storageList.getSelected()-1));
        storageList.setSelected(storageList.getSelected()-1);
        listDirty = 0;
    }

    private void moveTop() {
        sendServerCommand(RFToolsMessages.INSTANCE, tileEntity.getDimension(), StorageScannerTileEntity.CMD_TOP, new Argument("index", storageList.getSelected()-1));
        storageList.setSelected(1);
        listDirty = 0;
    }

    private void moveDown() {
        sendServerCommand(RFToolsMessages.INSTANCE, tileEntity.getDimension(), StorageScannerTileEntity.CMD_DOWN, new Argument("index", storageList.getSelected()-1));
        storageList.setSelected(storageList.getSelected()+1);
        listDirty = 0;
    }

    private void moveBottom() {
        sendServerCommand(RFToolsMessages.INSTANCE, tileEntity.getDimension(), StorageScannerTileEntity.CMD_BOTTOM, new Argument("index", storageList.getSelected()-1));
        storageList.setSelected(storageList.getChildCount()-1);
        listDirty = 0;
    }

    private void removeFromList() {
        sendServerCommand(RFToolsMessages.INSTANCE, tileEntity.getDimension(), StorageScannerTileEntity.CMD_REMOVE, new Argument("index", storageList.getSelected()-1));
        listDirty = 0;
    }

    private void changeExportMode() {
        sendServerCommand(RFToolsMessages.INSTANCE, tileEntity.getDimension(), StorageScannerTileEntity.CMD_TOGGLEEXPORT);
    }

    private void hilightSelectedContainer(int index) {
        if (index == -1) {
            return;
        }
        if (index == 0) {
            // Starred
            return;
        }
        InventoriesInfoPacketClient.InventoryInfo c = fromServer_inventories.get(index-1);
        if (c != null) {
            RFTools.instance.clientInfo.hilightBlock(c.getPos(), System.currentTimeMillis() + 1000 * StorageScannerConfiguration.hilightTime);
            Logging.message(MinecraftTools.getPlayer(mc), "The inventory is now highlighted");
            MinecraftTools.getPlayer(mc).closeScreen();
        }
    }

    private void changeRadius(int r) {
        sendServerCommand(RFToolsMessages.INSTANCE, tileEntity.getDimension(), StorageScannerTileEntity.CMD_SETRADIUS, new Argument("r", r));
    }

    private void startSearch(String text) {
        if (!text.isEmpty()) {
            RFToolsMessages.INSTANCE.sendToServer(new PacketGetInfoFromServer(RFTools.MODID,
                    new SearchItemsInfoPacketServer(tileEntity.getDimension(), tileEntity.getStorageScannerPos(), text)));
        }
    }

    private void getInventoryOnServer() {
        BlockPos c = getSelectedContainerPos();
        if (c != null) {
            RFToolsMessages.INSTANCE.sendToServer(new PacketGetInfoFromServer(RFTools.MODID,
                    new GetContentsInfoPacketServer(tileEntity.getDimension(), tileEntity.getStorageScannerPos(), c)));
        }
    }

    private BlockPos getSelectedContainerPos() {
        int selected = storageList.getSelected();
        if (selected != -1) {
            if (selected == 0) {
                return new BlockPos(-1, -1, -1);
            }
            selected--;
            if (selected < fromServer_inventories.size()) {
                InventoriesInfoPacketClient.InventoryInfo info = fromServer_inventories.get(selected);
                if (info == null) {
                    return null;
                } else {
                    return info.getPos();
                }
            }
        }
        return null;
    }

    private void requestListsIfNeeded() {
        listDirty--;
        if (listDirty <= 0) {
            RFToolsMessages.INSTANCE.sendToServer(new PacketGetInfoFromServer(RFTools.MODID,
                    new InventoriesInfoPacketServer(tileEntity.getDimension(), tileEntity.getStorageScannerPos(), false)));
            getInventoryOnServer();
            listDirty = 20;
        }
    }

    private void updateContentsList() {
        itemList.removeChildren();

        Pair<Panel,Integer> currentPos = MutablePair.of(null, 0);
        int numcolumns = openViewButton.isPressed() ? 5 : 9;
        int spacing = 3;

//        Collections.sort(fromServer_inventory, (o1, o2) -> o1.stackSize == o2.stackSize ? 0 : o1.stackSize < o2.stackSize ? -1 : 1);
        Collections.sort(fromServer_inventory, (o1, o2) -> o1.getDisplayName().compareTo(o2.getDisplayName()));

        String filterText = searchField.getText().toLowerCase();

        for (ItemStack item : fromServer_inventory) {
            String displayName = item.getDisplayName();
            if (filterText.isEmpty() || displayName.toLowerCase().contains(filterText)) {
                currentPos = addItemToList(item, itemList, currentPos, numcolumns, spacing);
            }
        }
    }

    private Pair<Panel,Integer> addItemToList(ItemStack item, WidgetList itemList, Pair<Panel,Integer> currentPos, int numcolumns, int spacing) {
        Panel panel = currentPos.getKey();
        if (panel == null || currentPos.getValue() >= numcolumns) {
            panel = new Panel(mc, this).setLayout(new HorizontalLayout().setSpacing(spacing).setHorizontalMargin(1))
                    .setDesiredHeight(12).setUserObject(new Integer(-1)).setDesiredHeight(16);
            currentPos = MutablePair.of(panel, 0);
            itemList.addChild(panel);
        }
        BlockRender blockRender = new BlockRender(mc, this)
                .setRenderItem(item)
                .setUserObject(1)       // Mark as a special stack in the renderer (for tooltip)
                .setOffsetX(-1)
                .setOffsetY(-1)
                .setHilightOnHover(true);
        blockRender.addSelectionEvent(new BlockRenderEvent() {
            @Override
            public void select(Widget widget) {
                BlockRender br = (BlockRender) widget;
                Object item = br.getRenderItem();
                if (item != null) {
                    boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
                    requestItem((ItemStack) item, shift ? 1 : -1);
                }
            }

            @Override
            public void doubleClick(Widget widget) {
            }
        });
        panel.addChild(blockRender);
        currentPos.setValue(currentPos.getValue() + 1);
        return currentPos;
    }

    private void requestItem(ItemStack stack, int amount) {
        BlockPos selectedContainerPos = getSelectedContainerPos();
        if (selectedContainerPos == null) {
            return;
        }
        network.sendToServer(new PacketRequestItem(tileEntity.getDimension(), tileEntity.getStorageScannerPos(), selectedContainerPos, stack, amount));
        getInventoryOnServer();
    }

    private void changeRoutable(BlockPos c) {
        sendServerCommand(RFToolsMessages.INSTANCE, tileEntity.getDimension(), StorageScannerTileEntity.CMD_TOGGLEROUTABLE,
                new Argument("pos", c));
        listDirty = 0;
    }

    private void updateStorageList() {
        storageList.removeChildren();
        addStorageLine(null, "All routable", false);
        for (InventoriesInfoPacketClient.InventoryInfo c : fromServer_inventories) {
            String displayName = c.getName();
            boolean routable = c.isRoutable();
            addStorageLine(c, displayName, routable);
        }


        storageList.clearHilightedRows();
        int i = 0;
        for (InventoriesInfoPacketClient.InventoryInfo c : fromServer_inventories) {
            if (fromServer_foundInventories.contains(c.getPos())) {
                storageList.addHilightedRow(i+1);
            }
            i++;
        }
    }

    private void addStorageLine(InventoriesInfoPacketClient.InventoryInfo c, String displayName, boolean routable) {
        Panel panel;
        if (c == null) {
            panel = new Panel(mc, this).setLayout(new HorizontalLayout().setSpacing(8).setHorizontalMargin(5));
            panel.addChild(new ImageLabel(mc, this).setImage(guielements, 115, 19).setDesiredWidth(13).setDesiredHeight(13));
        } else {
            HorizontalLayout layout = new HorizontalLayout();
            if (!openViewButton.isPressed()) {
                layout.setHorizontalMargin(2);
            }
            panel = new Panel(mc, this).setLayout(layout);
            panel.addChild(new BlockRender(mc, this).setRenderItem(c.getBlock()));
        }
        if (openViewButton.isPressed()) {
            AbstractWidget label;
            label = new Label(mc, this).setColor(StyleConfig.colorTextInListNormal)
                    .setText(displayName)
                    .setDynamic(true)
                    .setHorizontalAlignment(HorizontalAlignment.ALIGH_LEFT)
                    .setDesiredWidth(58);
            if (c == null) {
                label.setTooltips(TextFormatting.GREEN + "All routable inventories")
                        .setDesiredWidth(74);
            } else {
                label.setTooltips(TextFormatting.GREEN + "Block at: " + TextFormatting.WHITE + BlockPosTools.toString(c.getPos()),
                        TextFormatting.GREEN + "Name: " + TextFormatting.WHITE + displayName,
                        "(doubleclick to highlight)");
            }
            panel.addChild(label);
            if (c != null) {
                ImageChoiceLabel choiceLabel = new ImageChoiceLabel(mc, this)
                        .addChoiceEvent((parent, newChoice) -> changeRoutable(c.getPos())).setDesiredWidth(13);
                choiceLabel.addChoice("No", "Not routable", guielements, 131, 19);
                choiceLabel.addChoice("Yes", "Routable", guielements, 115, 19);
                choiceLabel.setCurrentChoice(routable ? 1 : 0);
                panel.addChild(choiceLabel);
            }
        }
        storageList.addChild(panel);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float v, int i, int i2) {
        updateStorageList();
        updateContentsList();
        requestListsIfNeeded();

        int selected = storageList.getSelected();
        removeButton.setEnabled(selected != -1);
        if (selected <= 0 || storageList.getChildCount() <= 2) {
            upButton.setEnabled(false);
            downButton.setEnabled(false);
            topButton.setEnabled(false);
            bottomButton.setEnabled(false);
        } else if (selected == 1) {
            topButton.setEnabled(false);
            upButton.setEnabled(false);
            downButton.setEnabled(true);
            bottomButton.setEnabled(true);
        } else if (selected == storageList.getChildCount()-1) {
            topButton.setEnabled(true);
            upButton.setEnabled(true);
            downButton.setEnabled(false);
            bottomButton.setEnabled(false);
        } else {
            topButton.setEnabled(true);
            upButton.setEnabled(true);
            downButton.setEnabled(true);
            bottomButton.setEnabled(true);
        }

        if (!tileEntity.isDummy()) {
            tileEntity.requestRfFromServer(RFTools.MODID);
            int currentRF = GenericEnergyStorageTileEntity.getCurrentRF();

            energyBar.setValue(currentRF);
            exportToStarred.setCurrentChoice(tileEntity.isExportToCurrent() ? 0 : 1);
        } else {
            if (System.currentTimeMillis() - lastTime > 300) {
                lastTime = System.currentTimeMillis();
                RFToolsMessages.INSTANCE.sendToServer(new PacketGetInfoFromServer(RFTools.MODID, new ScannerInfoPacketServer(tileEntity.getDimension(),
                        tileEntity.getPos())));
            }
            energyBar.setValue(ScannerInfoPacketClient.rfReceived);
            exportToStarred.setCurrentChoice(ScannerInfoPacketClient.exportToCurrentReceived ? 0 : 1);
        }

        drawWindow();
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int i1, int i2) {
        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        List<String> tooltips = craftingGrid.getWindow().getTooltips();
        if (tooltips != null) {
            drawHoveringText(tooltips, window.getTooltipItems(), x - guiLeft, y - guiTop, mc.fontRenderer);
        }

        super.drawGuiContainerForegroundLayer(i1, i2);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);

        int x = Mouse.getEventX() * width / mc.displayWidth;
        int y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        Widget widget = window.getToplevel().getWidgetAtPosition(x, y);
        if (widget instanceof BlockRender) {
            BlockRender blockRender = (BlockRender) widget;
            Object renderItem = blockRender.getRenderItem();
            ItemStack itemStack;
            if (renderItem instanceof ItemStack) {
                itemStack = (ItemStack) renderItem;
            } else if (renderItem instanceof Block) {
                itemStack = new ItemStack((Block) renderItem);
            } else if (renderItem instanceof Item) {
                itemStack = new ItemStack((Item) renderItem);
            } else {
                itemStack = ItemStackTools.getEmptyStack();
            }
            if (ItemStackTools.isValid(itemStack)) {
                boolean custom = blockRender.getUserObject() instanceof Integer;
                customRenderToolTip(itemStack, mouseX, mouseY, custom);
            }
        }
    }

    private void customRenderToolTip(ItemStack stack, int x, int y, boolean custom) {
        List<String> list;
        if (stack.getItem() == null) {
            // Protection for bad itemstacks
            list = new ArrayList<>();
        } else {
            list = stack.getTooltip(MinecraftTools.getPlayer(this.mc), this.mc.gameSettings.advancedItemTooltips);
        }

        for (int i = 0; i < list.size(); ++i) {
            if (i == 0) {
                list.set(i, stack.getRarity().rarityColor + list.get(i));
            } else {
                list.set(i, TextFormatting.GRAY + list.get(i));
            }
        }

        if (custom) {
            List<String> newlist = new ArrayList<>();
            newlist.add(TextFormatting.GREEN + "Click: "+ TextFormatting.WHITE + "full stack");
            newlist.add(TextFormatting.GREEN + "Shift + click: "+ TextFormatting.WHITE + "single item");
            newlist.add("");
            newlist.addAll(list);
            list = newlist;
        }

        FontRenderer font = null;
        if (stack.getItem() != null) {
            font = stack.getItem().getFontRenderer(stack);
        }
        this.drawHoveringText(list, x, y, (font == null ? fontRenderer : font));
    }

    private static long lastTime = 0;

    @Override
    protected void drawWindow() {
        super.drawWindow();
        craftingGrid.draw();
    }

}
