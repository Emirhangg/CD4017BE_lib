package cd4017be.lib.block;

import cd4017be.lib.property.PropertyBlockMimic;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author cd4017be
 *
 */
public abstract class BlockCoveredPipe extends BlockPipe {

	public static BlockCoveredPipe create(String id, Material m, SoundType sound, Class<? extends TileEntity> tile, int states) {
		return new BlockCoveredPipe(id, m, sound, CON_PROPS.length + 1, tile) {
			@Override
			protected PropertyInteger createBaseState() {
				return states > 1 ? PropertyInteger.create("type", 0, states - 1) : null;
			}
		};
	}

	public static final byte NEVER = -1, BY_BOUNDING_BOX = 0, BY_CONNECTION = 1, ALWAYS = 2;

	protected byte solidMode;

	/**
	 * @param mode = {@link #NEVER}, {@link #BY_BOUNDING_BOX}, {@link #BY_CONNECTION} or {@link #ALWAYS}
	 * @return this
	 */
	public BlockCoveredPipe setSolid(byte mode) {
		solidMode = mode;
		return this;
	}

	protected BlockCoveredPipe(String id, Material m, SoundType sound, int mods, Class<? extends TileEntity> tile) {
		super(id, m, sound, mods, tile);
		setMultilayer();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public String moduleVariant(int i) {
		return i < CON_PROPS.length ? CON_PROPS[i] : PropertyBlockMimic.instance.getName();
	}

	@Override
	@SideOnly(Side.CLIENT)
	public Class<?> moduleType(int i) {
		return i < CON_PROPS.length ? Byte.class : IBlockState.class;
	}

	@Override
	protected BlockStateContainer createBlockState() {
		baseState = createBaseState();
		return new ExtendedBlockState(this, baseState == null ? new IProperty[0] : new IProperty[] {baseState}, new IUnlistedProperty[] {moduleRef, PropertyBlockMimic.instance});
	}

	@Override
	public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
		IExtendedBlockState eState = (IExtendedBlockState)super.getExtendedState(state, world, pos);
		IModularTile tile = eState.getValue(moduleRef);
		if (tile == null) return eState;
		IBlockState cover = tile.getModuleState(CON_PROPS.length);
		if (cover == null) return eState;
		return eState.withProperty(PropertyBlockMimic.instance, cover.getBlock().getExtendedState(cover.getActualState(world, pos), world, pos));
	}

	@Override
	public BlockPipe setSize(double size) {
		size /= 2.0;
		double min = 0.5 - size, max = 0.5 + size;
		boundingBox = new AxisAlignedBB[] {
			new AxisAlignedBB(min, min, min, max, max, max),
			new AxisAlignedBB(min, 0.0, min, max, min, max),
			new AxisAlignedBB(min, max, min, max, 1.0, max),
			new AxisAlignedBB(min, min, 0.0, max, max, min),
			new AxisAlignedBB(min, min, max, max, max, 1.0),
			new AxisAlignedBB(0.0, min, min, min, max, max),
			new AxisAlignedBB(max, min, min, 1.0, max, max),
			FULL_BLOCK_AABB,
		};
		return this;
	}

	protected IBlockState getCover(IBlockAccess world, BlockPos pos) {
		if (world.getBlockState(pos).getBlock() != this) return null;
		TileEntity te = world.getTileEntity(pos);
		if (te instanceof IModularTile) return ((IModularTile)te).getModuleState(6);
		else return null;
	}

	@Override
	public boolean isSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
		byte mode = solidMode;
		if (mode == ALWAYS) return true;
		if (mode == BY_BOUNDING_BOX) return super.isSideSolid(state, world, pos, side);
		TileEntity te = world.getTileEntity(pos);
		if (te instanceof IModularTile) {
			IModularTile mt = (IModularTile)te;
			if (mode == BY_CONNECTION && mt.isModulePresent(side.ordinal())) return true;
			IBlockState cover = mt.getModuleState(6);
			return cover != null && cover.isSideSolid(world, pos, side);
		} else return false;
	}

	@Override
	public float getBlockHardness(IBlockState state, World world, BlockPos pos) {
		IBlockState cover = getCover(world, pos);
		if (cover == null) return blockHardness;
		float h = cover.getBlockHardness(world, pos);
		return h < 0 ? h : h + blockHardness;
	}

	@Override
	public float getExplosionResistance(World world, BlockPos pos, Entity exploder, Explosion explosion) {
		float r = blockResistance / 5.0F;
		IBlockState cover = getCover(world, pos);
		if (cover != null) r += cover.getBlock().getExplosionResistance(world, pos, exploder, explosion);
		return r;
	}

	@Override
	public boolean canEntityDestroy(IBlockState state, IBlockAccess world, BlockPos pos, Entity entity) {
		IBlockState cover = getCover(world, pos);
		return cover == null || cover.getBlock().canEntityDestroy(cover, world, pos, entity);
	}

	@SuppressWarnings("deprecation")
	@Override
	public int getLightValue(IBlockState state, IBlockAccess world, BlockPos pos) {
		IBlockState cover = getCover(world, pos);
		if (cover == null) return lightValue;
		//using getLightValue(World, BlockPos) would end in infinite recursion because ... Minecraft.
		return Math.max(lightValue, cover.getLightValue());
	}

	@Override
	public int getLightOpacity(IBlockState state, IBlockAccess world, BlockPos pos) {
		IBlockState cover = getCover(world, pos);
		if (cover == null) return lightOpacity;
		return Math.max(lightOpacity, cover.getLightOpacity(world, pos));
	}

	@Override
	public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
		if (super.doesSideBlockRendering(state, world, pos, face)) return true;
		IBlockState cover = getCover(world, pos);
		return cover != null && cover.doesSideBlockRendering(world, pos, face);
	}

	@Override
	public boolean canBeConnectedTo(IBlockAccess world, BlockPos pos, EnumFacing facing) {
		IBlockState cover = getCover(world, pos);
		return cover != null;
	}

	@Override
	public SoundType getSoundType(IBlockState state, World world, BlockPos pos, Entity entity) {
		IBlockState cover = getCover(world, pos);
		return cover == null ? blockSoundType : cover.getBlock().getSoundType(cover, world, pos, entity);
	}

	@Override
	public boolean canSustainLeaves(IBlockState state, IBlockAccess world, BlockPos pos) {
		IBlockState cover = getCover(world, pos);
		return cover != null && cover.getBlock().canSustainLeaves(cover, world, pos);
	}

	@Override
	public boolean canSustainPlant(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing direction, IPlantable plantable) {
		IBlockState cover = getCover(world, pos);
		return cover != null && cover.getBlock().canSustainPlant(cover, world, pos, direction, plantable);
	}

	@Override
	public boolean isBurning(IBlockAccess world, BlockPos pos) {
		IBlockState cover = getCover(world, pos);
		return cover != null && cover.getBlock().isBurning(world, pos);
	}

	@Override
	public boolean isFireSource(World world, BlockPos pos, EnumFacing side) {
		IBlockState cover = getCover(world, pos);
		return cover != null && cover.getBlock().isFireSource(world, pos, side);
	}

}
