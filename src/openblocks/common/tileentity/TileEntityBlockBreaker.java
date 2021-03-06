package openblocks.common.tileentity;

import java.util.ArrayList;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeDirection;
import net.minecraftforge.common.ForgeHooks;
import openmods.GenericInventory;
import openmods.IInventoryProvider;
import openmods.api.INeighbourAwareTile;
import openmods.include.IExtendable;
import openmods.include.IncludeInterface;
import openmods.sync.ISyncableObject;
import openmods.sync.SyncableBoolean;
import openmods.tileentity.SyncedTileEntity;
import openmods.utils.BlockUtils;
import openmods.utils.InventoryUtils;
import openmods.utils.OpenModsFakePlayer;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class TileEntityBlockBreaker extends SyncedTileEntity implements INeighbourAwareTile, IExtendable, IInventoryProvider {

	public enum Slots {
		buffer
	}

	private boolean _redstoneSignal;
	private int _redstoneAnimTimer;
	private SyncableBoolean activated;

	private final GenericInventory inventory = new GenericInventory("blockbreaker", true, 1) {
		@Override
		public boolean isItemValidForSlot(int i, ItemStack itemstack) {
			return false;
		}
	};

	@Override
	protected void createSyncedFields() {
		activated = new SyncableBoolean(false);
	}

	@SideOnly(Side.CLIENT)
	public boolean isActivated() {
		return activated.getValue();
	}

	@Override
	public void updateEntity() {
		super.updateEntity();
		if (!worldObj.isRemote) {
			if (activated.getValue() && _redstoneAnimTimer > 0) _redstoneAnimTimer--;
			if (activated.getValue() && _redstoneAnimTimer <= 0) {
				activated.setValue(false);
			}

			sync();
		}
	}

	public void setRedstoneSignal(boolean redstoneSignal) {
		if (redstoneSignal != _redstoneSignal) {
			_redstoneSignal = redstoneSignal;
			if (_redstoneSignal) {
				if (!worldObj.isRemote) {
					_redstoneAnimTimer = 5;
					activated.setValue(true);
					sync();
				}
				breakBlock();
			}
		}
	}

	private void breakBlock() {
		if (worldObj.isRemote) return;

		ForgeDirection direction = getRotation();
		int x = xCoord + direction.offsetX, y = yCoord + direction.offsetY, z = zCoord
				+ direction.offsetZ;

		if (worldObj.blockExists(x, y, z)) {
			int blockId = worldObj.getBlockId(x, y, z);
			Block block = Block.blocksList[blockId];
			if (block != null) {
				int metadata = worldObj.getBlockMetadata(x, y, z);
				if (block != Block.bedrock
						&& block.getBlockHardness(worldObj, z, y, z) > -1.0F) {
					EntityPlayer fakePlayer = OpenModsFakePlayer.getPlayerForWorld(worldObj);
					fakePlayer.inventory.currentItem = 0;
					fakePlayer.inventory.setInventorySlotContents(0, new ItemStack(Item.pickaxeDiamond));
					if (ForgeHooks.canHarvestBlock(block, fakePlayer, metadata)) {
						ArrayList<ItemStack> items = block.getBlockDropped(worldObj, x, y, z, metadata, 0);
						if (items != null) {
							ForgeDirection back = direction.getOpposite();
							ejectAt(worldObj,
									xCoord + back.offsetX,
									yCoord + back.offsetY,
									zCoord + back.offsetZ,
									back, items);
						}
					}
					fakePlayer.setDead();
					worldObj.playAuxSFX(2001, x, y, z, blockId + (metadata << 12));
					worldObj.setBlockToAir(x, y, z);
				}
			}
			worldObj.playSoundEffect(xCoord + 0.5D, yCoord + 0.5D, zCoord + 0.5D, "tile.piston.in", 0.5F, worldObj.rand.nextFloat() * 0.15F + 0.6F);
		}
	}

	public void ejectAt(World world, int x, int y, int z, ForgeDirection direction, ArrayList<ItemStack> itemStacks) {

		TileEntity targetInventory = getTileInDirection(direction);
		for (ItemStack stack : itemStacks) {
			// if there's any stack in our buffer slot, eject it. Why is it
			// there?
			ItemStack currentStack = inventory.getStackInSlot(Slots.buffer);
			if (currentStack != null) {
				BlockUtils.ejectItemInDirection(world, x, y, z, direction, currentStack);
			}

			// clear the buffer slot
			inventory.setInventorySlotContents(Slots.buffer.ordinal(), stack);

			// push the item out into a pipe or inventory
			InventoryUtils.moveItemInto(inventory, Slots.buffer.ordinal(), targetInventory, -1, 64, direction, true);
			// if there's anything left for whatever reason (maybe no inventory)
			ItemStack buffer = inventory.getStackInSlot(Slots.buffer);
			if (buffer != null) {
				// eject it
				BlockUtils.ejectItemInDirection(world, x, y, z, direction, buffer);
				inventory.setInventorySlotContents(Slots.buffer.ordinal(), null);
			}
		}
	}

	@Override
	public void onNeighbourChanged(int blockId) {
		if (!worldObj.isRemote) {
			setRedstoneSignal(worldObj.isBlockIndirectlyGettingPowered(xCoord, yCoord, zCoord));
		}
	}

	@Override
	public void onSynced(Set<ISyncableObject> changes) {
		if (changes.contains(activated)) {
			worldObj.markBlockForRenderUpdate(xCoord, yCoord, zCoord);
		}
	}

	@Override
	@IncludeInterface
	public IInventory getInventory() {
		return inventory;
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
		inventory.writeToNBT(tag);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		inventory.readFromNBT(tag);
	}

}
