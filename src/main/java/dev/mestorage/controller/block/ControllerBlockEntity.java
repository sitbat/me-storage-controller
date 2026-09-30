package dev.mestorage.controller.block;

import appeng.api.networking.*;
import appeng.api.util.AECableType;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.menu.ControllerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ControllerBlockEntity extends BlockEntity implements IInWorldGridNodeHost, MenuProvider {
    private IManagedGridNode mainNode = newNode();
    private boolean initialized;
    private boolean stopped;
    private CompoundTag savedNode;
    private IManagedGridNode newNode() { return GridHelper.createManagedNode(this, new IGridNodeListener<ControllerBlockEntity>() {
        @Override public void onSaveChanges(ControllerBlockEntity owner, IGridNode node) { owner.setChanged(); }
    }).setInWorldNode(true).setTagName("node").setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(2)
      .setVisualRepresentation(MEStorageController.CONTROLLER_ITEM.get()); }

    public ControllerBlockEntity(BlockPos pos, BlockState state) { super(MEStorageController.ENTITY.get(), pos, state); }
    public IManagedGridNode getMainNode() { return mainNode; }
    public static void tick(Level level, BlockPos pos, BlockState state, ControllerBlockEntity be) {
        if (!be.initialized && !be.stopped && !be.isRemoved()) { be.mainNode.create(level, pos); be.initialized=true; }
    }
    @Override public IGridNode getGridNode(Direction side) { return mainNode.getNode(); }
    @Override public AECableType getCableConnectionType(Direction side) { return AECableType.SMART; }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); mainNode.saveToNBT(tag); }
    @Override public void load(CompoundTag tag) { super.load(tag); mainNode.loadFromNBT(tag); }
    private void stopNode() {
        if(stopped) return;
        savedNode=new CompoundTag(); mainNode.saveToNBT(savedNode); mainNode.destroy(); stopped=true;
    }
    @Override public void setRemoved() { stopNode(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { stopNode(); super.onChunkUnloaded(); }
    @Override public void clearRemoved() {
        super.clearRemoved();
        if(stopped) { mainNode=newNode(); if(savedNode!=null) mainNode.loadFromNBT(savedNode); initialized=false; stopped=false; }
    }
    public boolean canUse(Player player) {
        return level != null && !isRemoved() && player.level() == level && level.getBlockEntity(worldPosition) == this
            && player.distanceToSqr(worldPosition.getX()+.5, worldPosition.getY()+.5, worldPosition.getZ()+.5) <= 64
            && level.mayInteract(player, worldPosition);
    }
    @Override public Component getDisplayName() { return Component.translatable("block.me_storage_controller.controller"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new ControllerMenu(id, inventory, this); }
}
