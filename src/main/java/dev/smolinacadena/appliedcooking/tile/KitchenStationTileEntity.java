package dev.smolinacadena.appliedcooking.tile;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import dev.smolinacadena.appliedcooking.ModBlocks;
import dev.smolinacadena.appliedcooking.api.cookingforblockheads.capability.KitchenItemProvider;
import dev.smolinacadena.appliedcooking.block.KitchenStationBlock;
import dev.smolinacadena.appliedcooking.item.KitchenStationBlockItem;
import net.blay09.mods.cookingforblockheads.api.capability.CapabilityKitchenItemProvider;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nullable;

public class KitchenStationTileEntity extends TileEntity implements ITickable {

    private static final long UNSET_GRID_KEY = -1L;

    private final KitchenItemProvider itemProvider = new KitchenItemProvider(this);
    private long gridKey = UNSET_GRID_KEY;

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityKitchenItemProvider.CAPABILITY || super.hasCapability(capability, facing);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityKitchenItemProvider.CAPABILITY) {
            return (T) itemProvider;
        }
        return super.getCapability(capability, facing);
    }

    @Nullable
    public IActionHost getActionHost() {
        if (gridKey != UNSET_GRID_KEY && world != null) {
            ILocatable locatable = AEApi.instance().registries().locatable().getLocatableBy(gridKey);
            if (locatable instanceof IActionHost) {
                return (IActionHost) locatable;
            }
        }
        return null;
    }

    @Nullable
    public IMEMonitor<IAEItemStack> getNetworkStorage() {
        IActionHost actionHost = getActionHost();
        if (actionHost == null) {
            return null;
        }

        IGridNode node = actionHost.getActionableNode();
        if (node == null) {
            return null;
        }

        IGrid grid = node.getGrid();
        if (grid == null) {
            return null;
        }

        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) {
            return null;
        }

        return storageGrid.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
    }

    public void applyDataFromItemToBlockEntity(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey(KitchenStationBlockItem.TAG_GRID_KEY)) {
            gridKey = stack.getTagCompound().getLong(KitchenStationBlockItem.TAG_GRID_KEY);
            setConnected(getActionHost() != null);
        }
        markDirty();
    }

    public void applyDataFromBlockEntityToItem(ItemStack stack) {
        if (gridKey != UNSET_GRID_KEY) {
            if (!stack.hasTagCompound()) {
                stack.setTagCompound(new NBTTagCompound());
            }
            stack.getTagCompound().setLong(KitchenStationBlockItem.TAG_GRID_KEY, gridKey);
        }
    }

    /**
     * Forge's default implementation returns {@code oldState != newState} (a reference
     * comparison) for non-vanilla tile entities, so any same-block state change discards this
     * tile entity and silently drops {@link #gridKey}. {@link #setConnected} changes the block
     * state on the same block, which therefore wiped the linked key right after placement.
     * Keep the tile entity unless the block itself actually changes.
     */
    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    public void setConnected(boolean connected) {
        if (world == null) {
            return;
        }

        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() == ModBlocks.KITCHEN_STATION && state.getValue(KitchenStationBlock.CONNECTED) != connected) {
            world.setBlockState(pos, state.withProperty(KitchenStationBlock.CONNECTED, connected), 3);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey(KitchenStationBlockItem.TAG_GRID_KEY)) {
            gridKey = compound.getLong(KitchenStationBlockItem.TAG_GRID_KEY);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (gridKey != UNSET_GRID_KEY) {
            compound.setLong(KitchenStationBlockItem.TAG_GRID_KEY, gridKey);
        }
        return compound;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) {
            return;
        }
        setConnected(getActionHost() != null);
    }
}
