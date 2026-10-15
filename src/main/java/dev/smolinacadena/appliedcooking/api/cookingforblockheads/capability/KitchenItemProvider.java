package dev.smolinacadena.appliedcooking.api.cookingforblockheads.capability;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.helpers.MachineSource;
import dev.smolinacadena.appliedcooking.tile.KitchenStationTileEntity;
import net.blay09.mods.cookingforblockheads.api.SourceItem;
import net.blay09.mods.cookingforblockheads.api.capability.IKitchenItemProvider;
import net.blay09.mods.cookingforblockheads.api.capability.IngredientPredicate;
import net.blay09.mods.cookingforblockheads.registry.CookingRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Supplies ingredients to Cooking for Blockheads from the Applied Energistics 2 network the Kitchen Station is
 * linked to.
 * <p>
 * Two ways to satisfy a wanted ingredient, tried in order:
 * <ul>
 *     <li><b>Item</b>, the network holds the matching item directly (extract/insert as normal).</li>
 *     <li><b>Fluid</b>, water, milk, and any other fluid whose filled bucket a recipe accepts are drained straight
 *     from network storage. A recipe that wants a water bucket (or a modded water/milk item registered with CFB)
 *     is satisfied by draining one bucket (1000mB) of the matching fluid, mirroring CFB's own Sink and Milk Jar.</li>
 * </ul>
 * The item path is always tried first, so a real stored item is preferred over one synthesized from fluid.
 */
public class KitchenItemProvider implements IKitchenItemProvider {

    /** Fluid drained per synthesized item, matching CFB's Sink and Milk Jar (one bucket). */
    private static final int FLUID_PER_ITEM = 1000;

    /**
     * Slot marker for a source backed by a fluid in the ME network rather than a stored item. CFB only treats a
     * negative slot as "virtual / do not consume" and consumes any non-negative one, so a positive marker lets the
     * fluid source be consumed while {@link #returnItemStack} can still tell the two apart.
     */
    private static final int FLUID_SLOT = 1;

    private final KitchenStationTileEntity tileEntity;
    private final Map<String, Integer> usedStackSizes = new HashMap<>();
    private final Map<String, Integer> usedFluidAmounts = new HashMap<>();

    private IItemList<IAEFluidStack> cachedFluidList;
    private long cachedFluidTick = Long.MIN_VALUE;

    public KitchenItemProvider(KitchenStationTileEntity tileEntity) {
        this.tileEntity = tileEntity;
    }

    @Nullable
    public IMEMonitor<IAEItemStack> getNetworkStorage() {
        return tileEntity.getNetworkStorage();
    }

    @Nullable
    public IMEMonitor<IAEFluidStack> getFluidStorage() {
        return tileEntity.getFluidStorage();
    }

    private static String keyOf(ItemStack stack) {
        return String.valueOf(stack.getItem().getRegistryName()) + "@" + stack.getMetadata();
    }

    private static IItemStorageChannel itemChannel() {
        return AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
    }

    private static IFluidStorageChannel fluidChannel() {
        return AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
    }

    @Override
    public void resetSimulation() {
        for (Map.Entry<String, Integer> entry : usedStackSizes.entrySet()) {
            entry.setValue(0);
        }
        for (Map.Entry<String, Integer> entry : usedFluidAmounts.entrySet()) {
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
        if (amount <= 0) {
            return;
        }

        // A real stored item is always preferred over one synthesized from fluid.
        if (useStoredItem(itemStack, amount, simulate)) {
            return;
        }

        useFluidItem(itemStack, amount, simulate);
    }

    /**
     * Extract {@code amount} of {@code itemStack} from the network's item storage.
     *
     * @return true if the network held any of it (so the caller should not fall back to fluids).
     */
    private boolean useStoredItem(ItemStack itemStack, int amount, boolean simulate) {
        IMEMonitor<IAEItemStack> storage = getNetworkStorage();
        IActionHost actionHost = getActionHost();
        if (storage == null || actionHost == null) {
            return false;
        }

        String key = keyOf(itemStack);
        int used = simulate ? getSimulatedUseCount(key) : 0;
        if (itemStack.getCount() - used < amount) {
            return false;
        }

        IAEItemStack request = itemChannel().createStack(itemStack);
        if (request == null) {
            return false;
        }
        request.setStackSize(amount);

        IAEItemStack extracted = storage.extractItems(request, simulate ? Actionable.SIMULATE : Actionable.MODULATE, new MachineSource(actionHost));
        if (extracted == null || extracted.getStackSize() <= 0) {
            return false;
        }

        if (simulate) {
            usedStackSizes.put(key, used + (int) extracted.getStackSize());
        }
        return true;
    }

    /**
     * Drain the fluid that backs {@code itemStack} from the network's fluid storage. Which fluid a given item is
     * backed by is decided by {@link #fluidForItem(ItemStack)}, so a water bucket is served from water, a milk
     * bucket from milk, and any other fluid from its own bucket item.
     */
    private void useFluidItem(ItemStack itemStack, int amount, boolean simulate) {
        Fluid fluid = fluidForItem(itemStack);
        if (fluid == null) {
            return;
        }

        IMEMonitor<IAEFluidStack> storage = getFluidStorage();
        IActionHost actionHost = getActionHost();
        if (storage == null || actionHost == null) {
            return;
        }

        int needed = amount * FLUID_PER_ITEM;
        // Simulated use is tracked here rather than in AE2, so guard against overdrawing across a scan.
        if (simulate && getFluidAvailable(fluid) < needed) {
            return;
        }

        IAEFluidStack request = fluidChannel().createStack(new FluidStack(fluid, needed));
        if (request == null) {
            return;
        }

        IAEFluidStack extracted = storage.extractItems(request, simulate ? Actionable.SIMULATE : Actionable.MODULATE, new MachineSource(actionHost));
        if (extracted == null || extracted.getStackSize() <= 0) {
            return;
        }

        if (simulate) {
            usedFluidAmounts.merge(fluid.getName(), (int) extracted.getStackSize(), Integer::sum);
        } else {
            cachedFluidList = null;
        }
    }

    @Override
    public ItemStack returnItemStack(ItemStack itemStack) {
        return itemStack;
    }

    @Override
    public ItemStack returnItemStack(ItemStack itemStack, SourceItem sourceItem) {
        // A container handed back for a fluid-sourced item, such as the empty bucket left behind by a water bucket
        // we synthesized from network fluid, must not be stored: that bucket never existed, so inserting it would
        // mint one from nothing. CFB's own Sink and Milk Jar swallow it the same way.
        if (sourceItem != null && sourceItem.getSourceSlot() == FLUID_SLOT) {
            return ItemStack.EMPTY;
        }

        IMEMonitor<IAEItemStack> storage = getNetworkStorage();
        IActionHost actionHost = getActionHost();
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

        // Nothing stored, so try to satisfy the ingredient from network fluid instead.
        SourceItem water = findFluidSource(predicate, CookingRegistry.getWaterItems(), FluidRegistry.WATER);
        if (water != null) {
            return water;
        }

        SourceItem milk = findFluidSource(predicate, CookingRegistry.getMilkItems(), FluidRegistry.getFluid("milk"));
        if (milk != null) {
            return milk;
        }

        return findGeneralFluidSource(predicate);
    }

    /**
     * Water/milk fast-path: if the network holds at least a bucket of {@code fluid} and any of {@code candidates}
     * (the items CFB registers for that fluid, e.g. the water bucket or a modded water item) satisfies the
     * ingredient, return a token that drains a bucket and yields that item.
     */
    @Nullable
    private SourceItem findFluidSource(IngredientPredicate predicate, NonNullList<ItemStack> candidates, @Nullable Fluid fluid) {
        if (fluid == null) {
            return null;
        }

        int available = getFluidAvailable(fluid);
        if (available < FLUID_PER_ITEM) {
            return null;
        }
        int units = available / FLUID_PER_ITEM;

        for (ItemStack candidate : candidates) {
            if (candidate.isEmpty()) {
                continue;
            }
            if (predicate.test(candidate, units)) {
                return new SourceItem(this, FLUID_SLOT, candidate.copy());
            }
        }
        return null;
    }

    /**
     * Fluid-agnostic fallback: for every fluid stored in the network, build its filled bucket and ask whether the
     * ingredient accepts it. Covers lava and modded fluids whose bucket a recipe wants.
     */
    @Nullable
    private SourceItem findGeneralFluidSource(IngredientPredicate predicate) {
        IItemList<IAEFluidStack> fluids = fluidList();
        if (fluids == null) {
            return null;
        }

        for (IAEFluidStack aeStack : fluids) {
            if (aeStack == null || aeStack.getStackSize() < FLUID_PER_ITEM) {
                continue;
            }
            Fluid fluid = aeStack.getFluid();
            if (fluid == null) {
                continue;
            }

            ItemStack bucket = FluidUtil.getFilledBucket(new FluidStack(fluid, FLUID_PER_ITEM));
            if (bucket.isEmpty()) {
                continue;
            }

            int available = getFluidAvailable(fluid);
            if (available >= FLUID_PER_ITEM && predicate.test(bucket, available / FLUID_PER_ITEM)) {
                return new SourceItem(this, FLUID_SLOT, bucket);
            }
        }
        return null;
    }

    /**
     * The fluid backing {@code stack}: water for CFB's water items, milk for its milk items, otherwise any network
     * fluid whose filled bucket is that item.
     */
    @Nullable
    private Fluid fluidForItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        if (matchesAny(CookingRegistry.getWaterItems(), stack)) {
            return FluidRegistry.WATER;
        }

        Fluid milk = FluidRegistry.getFluid("milk");
        if (milk != null && matchesAny(CookingRegistry.getMilkItems(), stack)) {
            return milk;
        }

        IItemList<IAEFluidStack> fluids = fluidList();
        if (fluids != null) {
            for (IAEFluidStack aeStack : fluids) {
                Fluid fluid = aeStack != null ? aeStack.getFluid() : null;
                if (fluid == null) {
                    continue;
                }
                ItemStack bucket = FluidUtil.getFilledBucket(new FluidStack(fluid, FLUID_PER_ITEM));
                if (!bucket.isEmpty() && ItemStack.areItemsEqual(bucket, stack)) {
                    return fluid;
                }
            }
        }
        return null;
    }

    private static boolean matchesAny(NonNullList<ItemStack> candidates, ItemStack stack) {
        for (ItemStack candidate : candidates) {
            if (candidate.isEmpty() || candidate.getItem() != stack.getItem()) {
                continue;
            }
            int meta = candidate.getMetadata();
            if (meta == stack.getMetadata() || meta == OreDictionary.WILDCARD_VALUE) {
                return true;
            }
        }
        return false;
    }

    /** The network's fluid list, rebuilt at most once per game tick and reused across a kitchen scan. */
    @Nullable
    private IItemList<IAEFluidStack> fluidList() {
        IMEMonitor<IAEFluidStack> storage = getFluidStorage();
        if (storage == null) {
            cachedFluidList = null;
            return null;
        }

        long now = tileEntity.getWorld() != null ? tileEntity.getWorld().getTotalWorldTime() : 0L;
        if (cachedFluidList == null || cachedFluidTick != now) {
            cachedFluidList = storage.getAvailableItems(fluidChannel().createList());
            cachedFluidTick = now;
        }
        return cachedFluidList;
    }

    /** How much of {@code fluid} the network can still spare, after any use already simulated this scan. */
    private int getFluidAvailable(Fluid fluid) {
        IItemList<IAEFluidStack> fluids = fluidList();
        if (fluids == null) {
            return 0;
        }

        for (IAEFluidStack aeStack : fluids) {
            if (aeStack == null || aeStack.getFluid() != fluid) {
                continue;
            }

            Integer used = usedFluidAmounts.get(fluid.getName());
            long remaining = aeStack.getStackSize() - (used != null ? used : 0);
            return remaining > 0 ? (int) Math.min(Integer.MAX_VALUE, remaining) : 0;
        }
        return 0;
    }

    @Nullable
    private IActionHost getActionHost() {
        return tileEntity.getActionHost();
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
