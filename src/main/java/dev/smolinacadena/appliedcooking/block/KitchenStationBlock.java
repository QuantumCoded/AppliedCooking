package dev.smolinacadena.appliedcooking.block;

import dev.smolinacadena.appliedcooking.AppliedCooking;
import dev.smolinacadena.appliedcooking.ModItems;
import dev.smolinacadena.appliedcooking.tile.KitchenStationTileEntity;
import net.blay09.mods.cookingforblockheads.block.BlockKitchen;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.List;

public class KitchenStationBlock extends BlockKitchen {

    public static final PropertyBool CONNECTED = PropertyBool.create("connected");

    private static final AxisAlignedBB SHAPE_NORTH = new AxisAlignedBB(3 / 16.0, -1 / 16.0, 6 / 16.0, 13 / 16.0, 7.5 / 16.0, 13 / 16.0);
    private static final AxisAlignedBB SHAPE_SOUTH = new AxisAlignedBB(3 / 16.0, -1 / 16.0, 3 / 16.0, 13 / 16.0, 7.5 / 16.0, 10 / 16.0);
    private static final AxisAlignedBB SHAPE_EAST = new AxisAlignedBB(3 / 16.0, -1 / 16.0, 3 / 16.0, 10 / 16.0, 7.5 / 16.0, 13 / 16.0);
    private static final AxisAlignedBB SHAPE_WEST = new AxisAlignedBB(6 / 16.0, -1 / 16.0, 3 / 16.0, 13 / 16.0, 7.5 / 16.0, 13 / 16.0);

    public KitchenStationBlock() {
        super(Material.IRON);
        setTranslationKey(AppliedCooking.MOD_ID + ":kitchen_station");
        setCreativeTab(AppliedCooking.CREATIVE_TAB);
        setHardness(2.5f);
        setSoundType(SoundType.METAL);
        setHarvestLevel("pickaxe", 1);
        setDefaultState(getDefaultState().withProperty(CONNECTED, false));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, CONNECTED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        EnumFacing facing = EnumFacing.byIndex(meta & 7);
        if (facing.getAxis() == EnumFacing.Axis.Y) {
            facing = EnumFacing.NORTH;
        }
        return getDefaultState().withProperty(FACING, facing).withProperty(CONNECTED, (meta & 8) != 0);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        int meta = state.getValue(FACING).getIndex();
        if (state.getValue(CONNECTED)) {
            meta |= 8;
        }
        return meta;
    }

    @Override
    @SuppressWarnings("deprecation")
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case NORTH:
                return SHAPE_NORTH;
            case WEST:
                return SHAPE_WEST;
            case EAST:
                return SHAPE_EAST;
            case SOUTH:
            default:
                return SHAPE_SOUTH;
        }
    }

    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new KitchenStationTileEntity();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);

        if (!world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity instanceof KitchenStationTileEntity) {
                ((KitchenStationTileEntity) tileEntity).applyDataFromItemToBlockEntity(stack);
            }
        }
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        drops.add(createDrop(world.getTileEntity(pos)));
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state, @Nullable TileEntity te, ItemStack stack) {
        player.addStat(StatList.getBlockStats(this));
        player.addExhaustion(0.005F);

        if (world.isRemote || world.restoringBlockSnapshots) {
            return;
        }

        // The block has already been removed by the time this is called, so the tile entity
        // is taken from the passed-in parameter rather than looked up from the world.
        NonNullList<ItemStack> drops = NonNullList.create();
        drops.add(createDrop(te));

        float chance = ForgeEventFactory.fireBlockHarvesting(drops, world, pos, state, 0, 1.0f, false, player);
        for (ItemStack drop : drops) {
            if (!drop.isEmpty() && world.rand.nextFloat() <= chance) {
                spawnAsEntity(world, pos, drop);
            }
        }
    }

    private ItemStack createDrop(@Nullable TileEntity tileEntity) {
        ItemStack stack = new ItemStack(ModItems.KITCHEN_STATION);
        if (tileEntity instanceof KitchenStationTileEntity) {
            ((KitchenStationTileEntity) tileEntity).applyDataFromBlockEntityToItem(stack);
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag advanced) {
        super.addInformation(stack, world, tooltip, advanced);
        tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.appliedcooking:kitchen_station.description"));
    }
}
