package dev.smolinacadena.appliedcooking;

import dev.smolinacadena.appliedcooking.proxy.CommonProxy;
import dev.smolinacadena.appliedcooking.tile.KitchenStationTileEntity;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

// The version deliberately has no default here: FML resolves it from
// version.properties, which the build expands from the Gradle `version` (and
// therefore from the git tag in CI). Setting it here too would pin it to a
// stale literal and make FML ignore version.properties.
@Mod(modid = AppliedCooking.MOD_ID, name = AppliedCooking.NAME,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = "required-after:appliedenergistics2;required-after:cookingforblockheads")
public class AppliedCooking {

    public static final String MOD_ID = "appliedcooking";
    public static final String NAME = "Applied Cooking";

    @Mod.Instance(MOD_ID)
    public static AppliedCooking instance;

    @SidedProxy(clientSide = "dev.smolinacadena.appliedcooking.proxy.ClientProxy",
            serverSide = "dev.smolinacadena.appliedcooking.proxy.CommonProxy")
    public static CommonProxy proxy;

    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(MOD_ID) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(ModBlocks.KITCHEN_STATION);
        }
    };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GameRegistry.registerTileEntity(KitchenStationTileEntity.class, new ResourceLocation(MOD_ID, "kitchen_station"));
        proxy.preInit(event);
    }
}
