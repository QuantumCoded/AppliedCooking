package dev.smolinacadena.appliedcooking;

import dev.smolinacadena.appliedcooking.block.KitchenStationBlock;
import net.minecraft.block.Block;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = AppliedCooking.MOD_ID)
public class ModBlocks {

    public static final KitchenStationBlock KITCHEN_STATION = new KitchenStationBlock();

    private ModBlocks() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(KITCHEN_STATION.setRegistryName(AppliedCooking.MOD_ID, "kitchen_station"));
    }
}
