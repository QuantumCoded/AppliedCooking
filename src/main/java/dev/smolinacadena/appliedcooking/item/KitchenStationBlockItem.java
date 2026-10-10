package dev.smolinacadena.appliedcooking.item;

import appeng.api.features.INetworkEncodable;
import dev.smolinacadena.appliedcooking.AppliedCooking;
import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class KitchenStationBlockItem extends ItemBlock implements INetworkEncodable {

    public static final String TAG_GRID_KEY = "gridKey";

    public KitchenStationBlockItem(Block block) {
        super(block);
        setMaxStackSize(1);
        setCreativeTab(AppliedCooking.CREATIVE_TAB);
    }

    @Override
    public String getEncryptionKey(ItemStack item) {
        NBTTagCompound tag = item.getTagCompound();
        if (tag != null && tag.hasKey(TAG_GRID_KEY)) {
            return String.valueOf(tag.getLong(TAG_GRID_KEY));
        }
        return "";
    }

    @Override
    public void setEncryptionKey(ItemStack item, String encKey, String name) {
        NBTTagCompound tag = item.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            item.setTagCompound(tag);
        }
        tag.setLong(TAG_GRID_KEY, Long.parseLong(encKey));
    }
}
