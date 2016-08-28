/*------------------------------------------------------------------------------
 Copyright (c) CovertJaguar, 2011-2016
 http://railcraft.info

 This code is the property of CovertJaguar
 and may only be used with explicit written
 permission unless otherwise specified on the
 license page at http://railcraft.info/wiki/info:license.
 -----------------------------------------------------------------------------*/
package mods.railcraft.common.blocks.tracks.kits.variants;

import com.google.common.base.Predicate;
import mods.railcraft.api.tracks.ITrackKitEmitter;
import mods.railcraft.common.blocks.tracks.kits.TrackKits;
import mods.railcraft.common.carts.CartConstants;
import mods.railcraft.common.plugins.forge.PowerPlugin;
import mods.railcraft.common.util.misc.Game;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityMinecartCommandBlock;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

public class TrackKitDetector extends TrackKitRailcraft implements ITrackKitEmitter {
    private byte delay;

    @Override
    public TrackKits getTrackKitContainer() {
        return TrackKits.DETECTOR;
    }

    @Override
    public int getRenderState() {
        return delay > 0 ? 1 : 0;
    }

    @Override
    public void update() {
        if (Game.isClient(theWorldAsserted())) {
            return;
        }
        if (delay > 0) {
            delay--;
            if (delay == 0) {
                notifyNeighbors();
            }
        }
    }

    @Override
    public void onMinecartPass(EntityMinecart cart) {
        setTrackPowering();
    }

    protected void notifyNeighbors() {
        World world = theWorldAsserted();
        world.notifyNeighborsOfStateChange(getPos(), getTile().getBlockType());
        world.notifyNeighborsOfStateChange(getPos().down(), getTile().getBlockType());
        sendUpdateToClient();
    }

    protected void setTrackPowering() {
        boolean notify = delay == 0;
        delay = CartConstants.DETECTED_POWER_OUTPUT_FADE;
        if (notify) {
            notifyNeighbors();
        }
    }

    @Override
    public int getPowerOutput() {
        return delay > 0 ? PowerPlugin.FULL_POWER : PowerPlugin.NO_POWER;
    }

    public boolean isEmittingPower() {
        return getPowerOutput() > 0;
    }

    @Override
    public int getComparatorInputOverride() {
        if (isEmittingPower()) {
            World world = theWorldAsserted();
            List<EntityMinecart> carts = findMinecarts(world, getPos(), EntityMinecart.class);
            if (!carts.isEmpty() && carts.get(0).getComparatorLevel() > -1) return carts.get(0).getComparatorLevel();
            List<EntityMinecartCommandBlock> commandCarts = findMinecarts(world, getPos(), EntityMinecartCommandBlock.class);

            if (!commandCarts.isEmpty()) {
                return commandCarts.get(0).getCommandBlockLogic().getSuccessCount();
            }

            List<EntityMinecart> chestCarts = findMinecarts(world, getPos(), EntityMinecart.class, EntitySelectors.HAS_INVENTORY);

            if (!chestCarts.isEmpty()) {
                return Container.calcRedstoneFromInventory((IInventory) chestCarts.get(0));
            }
        }
        return 0;
    }

    @SafeVarargs
    private final <T extends EntityMinecart> List<T> findMinecarts(World worldIn, BlockPos pos, Class<T> clazz, Predicate<Entity>... filter) {
        AxisAlignedBB axisalignedbb = getDetectionBox(pos);
        return filter.length != 1 ? worldIn.getEntitiesWithinAABB(clazz, axisalignedbb) : worldIn.getEntitiesWithinAABB(clazz, axisalignedbb, filter[0]);
    }

    private AxisAlignedBB getDetectionBox(BlockPos pos) {
        float f = 0.2F;
        return new AxisAlignedBB(pos.getX() + f, pos.getY(), pos.getZ() + f, pos.getX() + 1 - f, pos.getY() + 1 - f, pos.getZ() + 1 - f);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound) {
        super.writeToNBT(nbttagcompound);
        nbttagcompound.setByte("delay", delay);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound) {
        super.readFromNBT(nbttagcompound);
        delay = nbttagcompound.getByte("delay");
    }

    @Override
    public void writePacketData(DataOutputStream data) throws IOException {
        super.writePacketData(data);
        data.writeByte(delay);
    }

    @Override
    public void readPacketData(DataInputStream data) throws IOException {
        super.readPacketData(data);
        byte delayData = data.readByte();
        if ((delay == 0) != (delayData == 0)) {
            delay = delayData;
            markBlockNeedsUpdate();
        }
    }
}
