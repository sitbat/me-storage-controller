package dev.mestorage.controller.storage;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Uses AE's registered container strategies, including addon key types, on the real carried stack. */
public final class ContainerTransfers {
    private static final Logger LOGGER=LoggerFactory.getLogger(ContainerTransfers.class);
    public enum Result { UNHANDLED, UNCHANGED, CHANGED }
    private ContainerTransfers() {}

    public static Result handle(AbstractContainerMenu menu,Player player,MEStorage storage,IEnergySource energy,
                                IActionSource source,@Nullable AEKey clickedKey,int button,boolean shift) {
        // Like an AE terminal, ordinary right-click empties a container independently of the hovered entry.
        if(button==1 && !shift && ContainerItemStrategies.getContainedStack(menu.getCarried())!=null)
            return empty(menu,player,storage,energy,source);
        if(clickedKey==null || clickedKey instanceof AEItemKey) return Result.UNHANDLED;
        if(button!=0 || !ContainerItemStrategies.isKeySupported(clickedKey)) return Result.UNCHANGED;
        return fill(menu,player,storage,energy,source,clickedKey,shift);
    }

    private static Result fill(AbstractContainerMenu menu,Player player,MEStorage storage,IEnergySource energy,
                               IActionSource source,AEKey key,boolean toBackpack) {
        boolean borrowedBucket=false;
        // AE's empty-hand convenience applies to bucketable fluids. Other registered resources use their own containers.
        if(menu.getCarried().isEmpty() && key instanceof AEFluidKey fluid && fluid.getFluid().getBucket()!=Items.AIR) {
            if(StorageHelper.poweredExtraction(energy,storage,AEItemKey.of(Items.BUCKET),1,source)==1) {
                menu.setCarried(new ItemStack(Items.BUCKET));borrowedBucket=true;
            }
        }
        var context=ContainerItemStrategies.findCarriedContextForKey(key,player,menu);
        long moved=0;
        if(context!=null) {
            long available=StorageHelper.poweredExtraction(energy,storage,key,Long.MAX_VALUE,source,Actionable.SIMULATE);
            if(available>0) {
                long accepted=context.insert(key,available,Actionable.SIMULATE);
                if(accepted>0 && accepted<=available) {
                    long extracted=StorageHelper.poweredExtraction(energy,storage,key,accepted,source);
                    if(extracted>0) {
                        moved=context.insert(key,extracted,Actionable.MODULATE);
                        if(moved<extracted) {
                            // Compensation is not a second user transfer and must not charge energy again.
                            long returned=storage.insert(key,extracted-moved,Actionable.MODULATE,source);
                            if(returned!=extracted-moved) LOGGER.error("Container fill violated its simulation: {} {} could not be returned to its original scope",extracted-moved-returned,key);
                        }
                        if(moved>0) context.playFillSound(player,key);
                    }
                }
            }
        }
        if(borrowedBucket && menu.getCarried().is(Items.BUCKET)) {
            long restored=storage.insert(AEItemKey.of(Items.BUCKET),1,Actionable.MODULATE,source);
            if(restored==1) menu.setCarried(ItemStack.EMPTY);
            // A scope that refuses reinsertion leaves the real bucket on the cursor; it is never discarded.
        }
        if(moved>0 && toBackpack) {
            // A stacked-container strategy may already have placed its filled result in the backpack.
            // Only move the cursor if it now actually contains the selected resource, not remaining empty buckets.
            var contents=ContainerItemStrategies.getContainedStack(menu.getCarried(),key.getType());
            if(contents!=null && contents.what().equals(key) && contents.amount()>0) moveCarriedToBackpack(menu,player);
        }
        return moved>0 || borrowedBucket ? Result.CHANGED : Result.UNCHANGED;
    }

    private static Result empty(AbstractContainerMenu menu,Player player,MEStorage storage,IEnergySource energy,IActionSource source) {
        var context=ContainerItemStrategies.findCarriedContext(null,player,menu);
        if(context==null) return Result.UNCHANGED;
        var contents=context.getExtractableContent();
        if(contents==null || contents.amount()<=0) return Result.UNCHANGED;
        var key=contents.what();
        long accepted=StorageHelper.poweredInsert(energy,storage,key,contents.amount(),source,Actionable.SIMULATE);
        if(accepted<=0) return Result.UNCHANGED;
        // A bucket cannot provide a partial bucket even when the destination has some space or power.
        long drainable=context.extract(key,accepted,Actionable.SIMULATE);
        if(drainable<=0 || drainable>accepted) return Result.UNCHANGED;
        long extracted=context.extract(key,drainable,Actionable.MODULATE);
        if(extracted<=0) return Result.UNCHANGED;
        long inserted=StorageHelper.poweredInsert(energy,storage,key,extracted,source);
        if(inserted<extracted) {
            long returned=context.insert(key,extracted-inserted,Actionable.MODULATE);
            if(returned!=extracted-inserted) LOGGER.error("Container emptying violated its simulation: {} {} could not be returned to its container",extracted-inserted-returned,key);
        }
        if(inserted>0) context.playEmptySound(player,key);
        return Result.CHANGED;
    }

    private static void moveCarriedToBackpack(AbstractContainerMenu menu,Player player) {
        var inventory=player.getInventory();var remainder=menu.getCarried().copy();
        for(int pass=0;pass<2 && !remainder.isEmpty();pass++) for(int slot=0;slot<inventory.items.size() && !remainder.isEmpty();slot++) {
            var stack=inventory.getItem(slot);
            if(pass==0 ? stack.isEmpty() || !ItemStack.isSameItemSameTags(stack,remainder) : !stack.isEmpty()) continue;
            int max=Math.min(remainder.getMaxStackSize(),inventory.getMaxStackSize());
            int moved=Math.min(remainder.getCount(),Math.max(0,max-stack.getCount()));
            if(moved>0) {
                var merged=remainder.copy();merged.setCount(stack.getCount()+moved);
                inventory.setItem(slot,merged);remainder.shrink(moved);
            }
        }
        menu.setCarried(remainder);inventory.setChanged();
    }
}
