package dev.smolinacadena.appliedcooking;

import dev.smolinacadena.appliedcooking.item.KitchenStationBlockItem;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = AppliedCooking.MOD_ID)
public class ModItems {

    public static final KitchenStationBlockItem KITCHEN_STATION = new KitchenStationBlockItem(ModBlocks.KITCHEN_STATION);

    private ModItems() {
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(KITCHEN_STATION.setRegistryName(AppliedCooking.MOD_ID, "kitchen_station"));
    }
}
