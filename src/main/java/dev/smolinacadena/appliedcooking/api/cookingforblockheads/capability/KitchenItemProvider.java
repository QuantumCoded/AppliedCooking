package dev.smolinacadena.appliedcooking.api.cookingforblockheads.capability;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.helpers.MachineSource;
import dev.smolinacadena.appliedcooking.tile.KitchenStationTileEntity;
import net.blay09.mods.cookingforblockheads.api.SourceItem;
import net.blay09.mods.cookingforblockheads.api.capability.IKitchenItemProvider;
import net.blay09.mods.cookingforblockheads.api.capability.IngredientPredicate;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KitchenItemProvider implements IKitchenItemProvider {

    private final KitchenStationTileEntity tileEntity;
    private final Map<String, Integer> usedStackSizes = new HashMap<>();

    public KitchenItemProvider(KitchenStationTileEntity tileEntity) {
        this.tileEntity = tileEntity;
    }

    @Nullable
    public IMEMonitor<IAEItemStack> getNetworkStorage() {
        return tileEntity.getNetworkStorage();
    }

    private static String keyOf(ItemStack stack) {
        return String.valueOf(stack.getItem().getRegistryName()) + "@" + stack.getMetadata();
    }

    private static IItemStorageChannel itemChannel() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    @Override
    public void resetSimulation() {
        for (Map.Entry<String, Integer> entry : usedStackSizes.entrySet()) {
            entry.setValue(0);
        }
    }

    @Override
    public int getSimulatedUseCount(int slot) {
        return 0;
    }

    public int getSimulatedUseCount(String key) {
        Integer count = usedStackSizes.get(key);
        return count != null ? count : 0;
    }

    @Override
    public ItemStack useItemStack(int slot, int amount, boolean simulate, List<IKitchenItemProvider> inventories, boolean requireBucket) {
        return ItemStack.EMPTY;
    }

    public void useItemStack(ItemStack itemStack, int amount, boolean simulate, List<IKitchenItemProvider> inventories, boolean requireBucket) {
        IMEMonitor<IAEItemStack> storage = getNetworkStorage();
        IActionHost actionHost = tileEntity.getActionHost();
        if (storage == null || actionHost == null || amount <= 0) {
            return;
        }

        String key = keyOf(itemStack);
        int used = simulate ? getSimulatedUseCount(key) : 0;
        if (itemStack.getCount() - used < amount) {
            return;
        }

        IAEItemStack request = itemChannel().createStack(itemStack);
        if (request == null) {
            return;
        }
        request.setStackSize(amount);

        IAEItemStack extracted = storage.extractItems(request, simulate ? Actionable.SIMULATE : Actionable.MODULATE, new MachineSource(actionHost));
        if (simulate && extracted != null && extracted.getStackSize() > 0) {
            usedStackSizes.put(key, used + (int) extracted.getStackSize());
        }
    }

    @Override
    public ItemStack returnItemStack(ItemStack itemStack) {
        return itemStack;
    }

    @Override
    public ItemStack returnItemStack(ItemStack itemStack, SourceItem sourceItem) {
        IMEMonitor<IAEItemStack> storage = getNetworkStorage();
        IActionHost actionHost = tileEntity.getActionHost();
        if (storage == null || actionHost == null) {
            return itemStack;
        }

        IAEItemStack input = itemChannel().createStack(itemStack);
        if (input == null) {
            return itemStack;
        }

        IAEItemStack remainder = storage.injectItems(input, Actionable.MODULATE, new MachineSource(actionHost));
        if (remainder != null && remainder.getStackSize() > 0) {
            ItemStack remainderStack = remainder.createItemStack();
            remainderStack.setCount((int) Math.min(Integer.MAX_VALUE, remainder.getStackSize()));
            return remainderStack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getSlots() {
        IMEMonitor<IAEItemStack> storage = getNetworkStorage();
        if (storage == null) {
            return 0;
        }
        return storage.getAvailableItems(itemChannel().createList()).size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public SourceItem findSource(IngredientPredicate predicate, int maxAmount, List<IKitchenItemProvider> inventories, boolean requireBucket, boolean simulate) {
        IMEMonitor<IAEItemStack> storage = getNetworkStorage();
        if (storage == null) {
            return null;
        }

        IItemList<IAEItemStack> items = storage.getAvailableItems(itemChannel().createList());
        for (IAEItemStack aeStack : items) {
            if (aeStack == null || aeStack.getStackSize() <= 0) {
                continue;
            }

            ItemStack itemStack = aeStack.createItemStack();
            if (itemStack.isEmpty()) {
                continue;
            }

            String key = keyOf(itemStack);
            int available = (int) Math.min(Integer.MAX_VALUE, aeStack.getStackSize()) - getSimulatedUseCount(key);
            if (available > 0 && predicate.test(itemStack, available)) {
                ItemStack sourceStack = itemStack.copy();
                sourceStack.setCount((int) Math.min(Integer.MAX_VALUE, aeStack.getStackSize()));
                return new SourceItem(this, 0, sourceStack);
            }
        }
        return null;
    }

    @Nullable
    @Override
    public SourceItem findSourceAndMarkAsUsed(IngredientPredicate predicate, int maxAmount, List<IKitchenItemProvider> inventories, boolean requireBucket, boolean simulate) {
        SourceItem sourceItem = findSource(predicate, maxAmount, inventories, requireBucket, simulate);
        if (sourceItem != null) {
            useItemStack(sourceItem.getSourceStack(), Math.min(sourceItem.getSourceStack().getCount(), maxAmount), simulate, inventories, requireBucket);
        }
        return sourceItem;
    }

    @Override
    public void consumeSourceItem(SourceItem sourceItem, int maxAmount, List<IKitchenItemProvider> inventories, boolean requireContainer) {
        if (sourceItem.getSourceSlot() < 0) {
            return;
        }
        useItemStack(sourceItem.getSourceStack(), maxAmount, false, inventories, requireContainer);
    }

    @Override
    public void markAsUsed(SourceItem sourceItem, int maxAmount, List<IKitchenItemProvider> inventories, boolean requireBucket) {
        useItemStack(sourceItem.getSourceStack(), Math.min(sourceItem.getSourceStack().getCount(), maxAmount), true, inventories, requireBucket);
    }
}
