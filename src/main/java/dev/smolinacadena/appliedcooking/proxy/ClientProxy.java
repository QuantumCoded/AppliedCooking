package dev.smolinacadena.appliedcooking.proxy;

import dev.smolinacadena.appliedcooking.AppliedCooking;
import dev.smolinacadena.appliedcooking.ModItems;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        ModelLoader.setCustomModelResourceLocation(ModItems.KITCHEN_STATION, 0,
                new ModelResourceLocation(AppliedCooking.MOD_ID + ":kitchen_station", "inventory"));
    }
}
