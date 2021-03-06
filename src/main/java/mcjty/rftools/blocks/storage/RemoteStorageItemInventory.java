package mcjty.rftools.blocks.storage;

import mcjty.lib.compat.CompatInventory;
import mcjty.lib.tools.ItemStackList;
import mcjty.lib.tools.ItemStackTools;
import mcjty.rftools.craftinggrid.CraftingGrid;
import mcjty.rftools.craftinggrid.CraftingGridProvider;
import mcjty.rftools.craftinggrid.InventoriesItemSource;
import mcjty.rftools.craftinggrid.StorageCraftingTools;
import mcjty.rftools.jei.JEIRecipeAcceptor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nonnull;
import java.util.List;

public class RemoteStorageItemInventory implements CompatInventory, CraftingGridProvider, JEIRecipeAcceptor {
    private ItemStackList stacks = ItemStackList.create(RemoteStorageItemContainer.MAXSIZE_STORAGE);
    private final EntityPlayer entityPlayer;
    private CraftingGrid craftingGrid = new CraftingGrid();

    public RemoteStorageItemInventory(EntityPlayer player) {
        this.entityPlayer = player;
        NBTTagCompound tagCompound = entityPlayer.getHeldItem(EnumHand.MAIN_HAND).getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
            entityPlayer.getHeldItem(EnumHand.MAIN_HAND).setTagCompound(tagCompound);
        }
        craftingGrid.readFromNBT(tagCompound.getCompoundTag("grid"));
    }

    private RemoteStorageTileEntity getRemoteStorage() {
        int id = getStorageID();
        if (id == -1) {
            return null;
        }
        return RemoteStorageIdRegistry.getRemoteStorage(entityPlayer.getEntityWorld(), id);
    }

    private int getStorageID() {
        ItemStack heldItem = entityPlayer.getHeldItem(EnumHand.MAIN_HAND);
        if (ItemStackTools.isEmpty(heldItem) || heldItem.getTagCompound() == null) {
            return -1;
        }
        return heldItem.getTagCompound().getInteger("id");
    }


    @Override
    public void storeRecipe(int index) {
        getCraftingGrid().storeRecipe(index);
    }

    @Override
    public void setRecipe(int index, ItemStack[] stacks) {
        craftingGrid.setRecipe(index, stacks);
        markDirty();
    }

    @Override
    public CraftingGrid getCraftingGrid() {
        return craftingGrid;
    }

    @Override
    public void markInventoryDirty() {
        markDirty();
    }

    @Override
    @Nonnull
    public int[] craft(EntityPlayerMP player, int n, boolean test) {
        InventoriesItemSource itemSource = new InventoriesItemSource()
                .add(player.inventory, 0).add(this, 0);
        if (test) {
            return StorageCraftingTools.testCraftItems(player, n, craftingGrid.getActiveRecipe(), itemSource);
        } else {
            StorageCraftingTools.craftItems(player, n, craftingGrid.getActiveRecipe(), itemSource);
            return new int[0];
        }
    }

    @Override
    public void setGridContents(List<ItemStack> stacks) {
        for (int i = 0 ; i < stacks.size() ; i++) {
            craftingGrid.getCraftingGridInventory().setInventorySlotContents(i, stacks.get(i));
        }
        markDirty();
    }

    private boolean isServer() {
        return !entityPlayer.getEntityWorld().isRemote;
    }

    private ItemStackList getStacks() {
        if (isServer()) {
            RemoteStorageTileEntity storage = getRemoteStorage();
            if (storage == null) {
                return ItemStackList.create(0);
            }
            int si = storage.findRemoteIndex(getStorageID());
            if (si == -1) {
                return ItemStackList.create(0);
            }
            return storage.getRemoteStacks(si);
        } else {
            int maxSize = entityPlayer.getHeldItem(EnumHand.MAIN_HAND).getTagCompound().getInteger("maxSize");
            if (maxSize != stacks.size()) {
                stacks = ItemStackList.create(maxSize);
            }
            return stacks;
        }
    }

    @Override
    public int getSizeInventory() {
        if (isServer()) {
            RemoteStorageTileEntity storage = getRemoteStorage();
            if (storage == null) {
                return 0;
            }
            int si = storage.findRemoteIndex(getStorageID());
            if (si == -1) {
                return 0;
            }
            int maxStacks = storage.getMaxStacks(si);
            entityPlayer.getHeldItem(EnumHand.MAIN_HAND).getTagCompound().setInteger("maxSize", maxStacks);
            return maxStacks;
        } else {
            return entityPlayer.getHeldItem(EnumHand.MAIN_HAND).getTagCompound().getInteger("maxSize");
        }
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        if (isServer()) {
            RemoteStorageTileEntity storage = getRemoteStorage();
            if (storage == null) {
                return ItemStackTools.getEmptyStack();
            }
            int si = storage.findRemoteIndex(getStorageID());
            if (si == -1) {
                return ItemStackTools.getEmptyStack();
            }
            return storage.getRemoteSlot(si, index);
        } else {
            return stacks.get(index);
        }
    }

    @Override
    public ItemStack decrStackSize(int index, int amount) {
        if (isServer()) {
            RemoteStorageTileEntity storage = getRemoteStorage();
            if (storage == null) {
                return ItemStackTools.getEmptyStack();
            }
            int si = storage.findRemoteIndex(getStorageID());
            if (si == -1) {
                return ItemStackTools.getEmptyStack();
            }
            return storage.decrStackSizeRemote(si, index, amount);
        } else {
            if (index >= stacks.size()) {
                return ItemStackTools.getEmptyStack();
            }
            if (ItemStackTools.isValid(stacks.get(index))) {
                markDirty();
                if (ItemStackTools.getStackSize(stacks.get(index)) <= amount) {
                    ItemStack old = stacks.get(index);
                    stacks.set(index, ItemStackTools.getEmptyStack());
                    return old;
                }
                ItemStack its = stacks.get(index).splitStack(amount);
                if (ItemStackTools.isEmpty(stacks.get(index))) {
                    stacks.set(index, ItemStackTools.getEmptyStack());
                }
                return its;
            }
        }
        return ItemStackTools.getEmptyStack();
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        if (isServer()) {
            RemoteStorageTileEntity storage = getRemoteStorage();
            if (storage == null) {
                return;
            }
            int si = storage.findRemoteIndex(getStorageID());
            if (si == -1) {
                return;
            }
            storage.updateRemoteSlot(si, getInventoryStackLimit(), index, stack);
        } else {
            if (index >= stacks.size()) {
                return;
            }
            stacks.set(index, stack);
            if (ItemStackTools.isValid(stack) && ItemStackTools.getStackSize(stack) > getInventoryStackLimit()) {
                ItemStackTools.setStackSize(stack, getInventoryStackLimit());
            }
            markDirty();
        }
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        RemoteStorageTileEntity storage = getRemoteStorage();
        if (storage != null) {
            storage.markDirty();
        }
        NBTTagCompound tagCompound = entityPlayer.getHeldItem(EnumHand.MAIN_HAND).getTagCompound();
        tagCompound.setTag("grid", craftingGrid.writeToNBT());
    }

    @Override
    public boolean isUsable(EntityPlayer player) {
        return true;
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        ItemStackList s = getStacks();
        if (index >= s.size()) {
            return false;
        }
        if (isServer()) {
            RemoteStorageTileEntity storage = getRemoteStorage();
            if (storage == null) {
                return false;
            }
            int si = storage.findRemoteIndex(getStorageID());
            if (si == -1) {
                return false;
            }
            if (index >= storage.getMaxStacks(si)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        ItemStack stack = getStackInSlot(index);
        setInventorySlotContents(index, ItemStackTools.getEmptyStack());
        return stack;
    }

    @Override
    public void openInventory(EntityPlayer player) {

    }

    @Override
    public void closeInventory(EntityPlayer player) {

    }

    @Override
    public int getField(int id) {
        return 0;
    }

    @Override
    public void setField(int id, int value) {

    }

    @Override
    public int getFieldCount() {
        return 0;
    }

    @Override
    public void clear() {

    }

    @Override
    public String getName() {
        return "remote inventory";
    }

    @Override
    public boolean hasCustomName() {
        return false;
    }

    @Override
    public ITextComponent getDisplayName() {
        return null;
    }
}
