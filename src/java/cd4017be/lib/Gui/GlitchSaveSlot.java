package cd4017be.lib.Gui;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.SlotItemHandler;

/**
 * Special Slot type for use with inventories that have over-ranged stack sizes that don't fit in 8-bit 
 * and/or where vanilla inventory slot interaction would cause bad glitches (requires special hard coded handling in containers).
 * @author CD4017BE
 */
public class GlitchSaveSlot extends SlotItemHandler implements ISpecialSlot {

	public final int index;
	public final boolean clientInteract;

	public GlitchSaveSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition) {
		this(itemHandler, index, xPosition, yPosition, true);
	}

	public GlitchSaveSlot(IItemHandler itemHandler, int index, int xPosition, int yPosition, boolean client) {
		super(itemHandler, index, xPosition, yPosition);
		this.index = index;
		this.clientInteract = client;
	}

	// prevent vanilla from synchronizing with low stack size resolution and other unwanted things like other mods's inventory sorting mechanisms messing up everything. //

	@Override
	public boolean isItemValid(ItemStack stack) {
		return false;
	}

	@Override
	public boolean canTakeStack(EntityPlayer playerIn) {
		return false;
	}

	@Override
	public ItemStack decrStackSize(int amount) {
		return ItemStack.EMPTY;
	}

	@Override
	public void putStack(ItemStack stack) {}

	// new container system

	@Override
	public int getSlot() {
		return index;
	}

	@Override
	public boolean insertHereOnly(ItemStack stack) {
		return false;
	}

	@Override
	public ItemStack onClick(int b, ClickType ct, EntityPlayer player, AdvancedContainer container) {
		ItemStack item = getStack();
		if (ct == ClickType.CLONE) {
			ISpecialSlot.quickSelect(player, item);
			return ItemStack.EMPTY;
		} else if (ct != ClickType.PICKUP && ct != ClickType.QUICK_MOVE)
			return ItemStack.EMPTY;
		if (!clientInteract) {
			if (player.world.isRemote)
				return ItemStack.EMPTY;
			container.hardInvUpdate();
		}
		boolean boost = ct == ClickType.QUICK_MOVE;
		ItemStack curItem = player.inventory.getItemStack();
		if (curItem.getCount() > 0 && (item.isEmpty() || ItemHandlerHelper.canItemStacksStack(item, curItem))) {
			if (boost) {
				ItemStack rem = insertItem(ItemHandlerHelper.copyStackWithSize(curItem, 65536), true);
				int n = 65536 - rem.getCount(), n1 = 0;
				if (n <= 0) return ItemStack.EMPTY;
				if (b == 0) {
					if (n < curItem.getCount()) curItem.shrink(n1 = n);
					else {
						n1 = curItem.getCount();
						player.inventory.setItemStack(ItemStack.EMPTY);
					}
				}
				if (n1 < n)
					n1 += ISpecialSlot.getFromPlayerInv(ItemHandlerHelper.copyStackWithSize(curItem, n - n1), player.inventory);
				insertItem(ItemHandlerHelper.copyStackWithSize(curItem, n1), false);
			} else {
				int n = b == 0 ? curItem.getCount() : 1;
				ItemStack rem = insertItem(ItemHandlerHelper.copyStackWithSize(curItem, n), false);
				curItem.shrink(n - rem.getCount());
				if (curItem.getCount() <= 0) player.inventory.setItemStack(ItemStack.EMPTY);
			}
		} else if (item.getCount() > 0) {
			int n = boost ? (b == 0 ? item.getMaxStackSize() : 65536) : (b == 0 ? 1 : 8);
			if ((item = extractItem(n, true)).getCount() == 0) return ItemStack.EMPTY;
			int rem = ISpecialSlot.putInPlayerInv(item.copy(), player.inventory);
			extractItem(item.getCount() - rem, false);
		}
		return ItemStack.EMPTY;
	}

}
