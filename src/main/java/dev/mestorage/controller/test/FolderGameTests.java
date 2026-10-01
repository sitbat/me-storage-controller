package dev.mestorage.controller.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import dev.mestorage.controller.MEStorageController;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.folder.FolderBook;
import dev.mestorage.controller.folder.FolderService;
import dev.mestorage.controller.folder.FolderSavedData;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Network;
import dev.mestorage.controller.network.FolderTransfer;
import dev.mestorage.controller.storage.StorageScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Shared folder metadata against real AE grids, physical cells and independent player menus. */
@GameTestHolder(MEStorageController.ID)
@PrefixGameTestTemplate(false)
public final class FolderGameTests {
    private static final BlockPos LEFT = new BlockPos(2, 2, 2);
    private static final BlockPos RIGHT = new BlockPos(5, 2, 2);
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey DIAMOND = AEItemKey.of(Items.DIAMOND);
    private static final AEFluidKey WATER = AEFluidKey.of(Fluids.WATER);
    private record Fixture(ControllerBlockEntity left, ControllerBlockEntity right,
                           DriveBlockEntity first, DriveBlockEntity second, DriveBlockEntity outside,
                           Player alice, Player bob, ControllerMenu menuA, ControllerMenu menuB) {
        FolderService service() { return left.getMainNode().getGrid().getService(FolderService.class); }
        FolderService otherService() { return right.getMainNode().getGrid().getService(FolderService.class); }
        List<DriveBlockEntity> drives() { return List.of(first, second, outside); }
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sharedNestedFolderCrudNeverMovesPhysicalStorage(GameTestHelper h) {
        var f = fixture(h);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            populate(f);
            var physical = physicalCells(f);
            check(h, !f.alice.getUUID().equals(f.bob.getUUID()), "Sharing fixture requires distinct real player identities");
            check(h, f.service() == f.otherService(), "Both physical controllers must use the same grid folder service");
            var parent = create(h, f, null, "矿物仓库"); unchanged(h, f, physical, "create parent");
            var child = create(h, f, parent, "子分类"); unchanged(h, f, physical, "create child");
            var member = new FolderBook.MemberRef(deviceId(f, f.first), 0);
            apply(h, f.service(), edit(FolderBook.Op.ASSIGN, child, null, "", List.of(member), List.of()));
            unchanged(h, f, physical, "assign a slot");
            check(h, f.otherService().members(parent).contains(member), "Other controller must immediately see descendant membership");
            apply(h, f.service(), edit(FolderBook.Op.RENAME, child, null, "重命名", List.of(), List.of()));
            unchanged(h, f, physical, "rename");
            apply(h, f.otherService(), edit(FolderBook.Op.MOVE, child, null, "", List.of(), List.of()));
            unchanged(h, f, physical, "move child to root");
            check(h, !f.service().members(parent).contains(member), "Moving a child must update ancestor membership");
            apply(h, f.service(), edit(FolderBook.Op.MOVE, child, parent, "", List.of(), List.of()));
            unchanged(h, f, physical, "move child back");
            apply(h, f.otherService(), edit(FolderBook.Op.DELETE, child, null, "", List.of(), List.of()));
            unchanged(h, f, physical, "delete child");
            check(h, f.service().members(parent).contains(member), "Deleting a child must promote its members to its parent");
            apply(h, f.service(), edit(FolderBook.Op.DELETE, parent, null, "", List.of(), List.of()));
            unchanged(h, f, physical, "delete parent");
            check(h, f.service().view().folders().stream().noneMatch(folder -> folder.id().equals(parent) || folder.id().equals(child)), "Deleted folders remained visible");
            f.menuA.broadcastChanges(); f.menuB.broadcastChanges();
            check(h, f.menuA.getFolderView().equals(f.menuB.getFolderView()), "Independent player menus must see the same shared folder revision and tree");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void staleEditsAndParentCyclesAreRejectedWithoutPhysicalChanges(GameTestHelper h) {
        var f = fixture(h);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            populate(f); var physical = physicalCells(f);
            var parent = create(h, f, null, "Parent");
            var child = create(h, f, parent, "Child");
            long stale = f.otherService().view().revision();
            apply(h, f.service(), edit(FolderBook.Op.RENAME, child, null, "Alice wins", List.of(), List.of()));
            var before = f.service().view();
            var result = f.otherService().apply(stale, edit(FolderBook.Op.RENAME, child, null, "Bob stale", List.of(), List.of()));
            check(h, !result.error().isEmpty() && f.service().view().equals(before), "Stale concurrent edit must not overwrite a newer edit");
            reject(h, f.service(), edit(FolderBook.Op.MOVE, parent, child, "", List.of(), List.of()));
            reject(h, f.service(), edit(FolderBook.Op.MOVE, parent, parent, "", List.of(), List.of()));
            reject(h, f.service(), edit(FolderBook.Op.ASSIGN, child, null, "", List.of(), List.of(parent)));
            reject(h, f.service(), edit(FolderBook.Op.MOVE, child, UUID.randomUUID(), "", List.of(), List.of()));
            unchanged(h, f, physical, "invalid and stale metadata edits");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void nestedFolderTransfersDeduplicateAndCellMembershipFollowsSlot(GameTestHelper h) {
        var f = fixture(h); var ids = new UUID[2];
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            populate(f); ids[0] = create(h, f, null, "Aggregate"); ids[1] = create(h, f, ids[0], "Slots");
            apply(h, f.service(), edit(FolderBook.Op.ASSIGN, ids[0], null, "", List.of(new FolderBook.MemberRef(deviceId(f, f.first), -1)), List.of()));
            apply(h, f.service(), edit(FolderBook.Op.ASSIGN, ids[1], null, "", List.of(
                    new FolderBook.MemberRef(deviceId(f, f.first), 0), new FolderBook.MemberRef(deviceId(f, f.second), 1)), List.of()));
            check(h, f.service().members(ids[0]).size() == 2, "Ancestor whole-drive membership must subsume duplicate descendant slots");
            select(f.menuA, ids[0], 1);
            check(h, shown(f.menuA, IRON) == 1000 && shown(f.menuA, WATER) == 12000 && shown(f.menuA, DIAMOND) == 0,
                    "Parent aggregation must count each physical storage once and exclude nonmembers");
            check(h, f.menuA.getSnapshot().capacity().totalBytes() == 64 * 1024 + 2 * 1024,
                    "Overlapping whole-device and child-slot capacity was double-counted");
            content(f.menuA, IRON, 0, false);
            check(h, f.menuA.getCarried().getCount() == 64 && amount(f.first, 0, IRON) == 936 && amount(f.second, 0, IRON) == 2000,
                    "Folder extraction crossed into a nonmember iron cell");
            content(f.menuA, null, 0, false);
            check(h, amount(f.first, 0, IRON) == 1000 && f.menuA.getCarried().isEmpty(), "Folder item round trip lost contents");
            select(f.menuA, ids[1], 2);
        }).thenIdle(1).thenExecute(() -> {
            f.menuA.broadcastChanges();
            check(h, f.menuA.getSnapshot().selectedDevice().equals("folder:" + ids[1])
                    && f.menuA.getSnapshot().revision() == 2 && shown(f.menuA, WATER) == 7000,
                    "Child folder selection must be acknowledged before its bucket transfer: selected="
                            + f.menuA.getSnapshot().selectedDevice() + ", revision=" + f.menuA.getSnapshot().revision()
                            + ", water=" + shown(f.menuA, WATER));
            f.menuA.setCarried(new ItemStack(Items.BUCKET)); content(f.menuA, WATER, 0, false);
            check(h, f.menuA.getCarried().is(Items.WATER_BUCKET) && amount(f.second, 1, WATER) == 6000
                    && amount(f.first, 1, WATER) == 5000 && amount(f.outside, 1, WATER) == 11000,
                    "Child folder bucket extraction must use only its member fluid slot: cursor=" + f.menuA.getCarried()
                            + ", first=" + amount(f.first, 1, WATER) + ", second=" + amount(f.second, 1, WATER)
                            + ", outside=" + amount(f.outside, 1, WATER));
            content(f.menuA, null, 1, false);
            check(h, f.menuA.getCarried().is(Items.BUCKET) && amount(f.second, 1, WATER) == 7000, "Folder fluid round trip failed");
            f.menuA.setCarried(ItemStack.EMPTY);
            var old = f.first.getInternalInventory().getStackInSlot(0);
            f.first.getInternalInventory().setItemDirect(0, ItemStack.EMPTY);
            f.first.getInternalInventory().setItemDirect(2, old);
            f.first.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
            insert(f.first, 0, DIAMOND, 21);
        }).thenIdle(5).thenExecute(() -> {
            select(f.menuA, ids[1], 3);
            check(h, shown(f.menuA, IRON) == 0 && shown(f.menuA, DIAMOND) == 21,
                    "Slot membership must follow newly inserted cell, not the old cell moved elsewhere");
            content(f.menuA, DIAMOND, 0, false);
            check(h, f.menuA.getCarried().getCount() == 21 && amount(f.first, 2, IRON) == 1000 && amount(f.outside, 0, DIAMOND) == 99,
                    "Replacement slot transfer must not touch old cell or same-key external storage");
            content(f.menuA, null, 0, false);
            check(h, amount(f.first, 0, DIAMOND) == 21 && f.menuA.getCarried().isEmpty(), "New member cell round trip failed");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void menuPermissionsAndOldFolderRevisionCannotMutateStorage(GameTestHelper h) {
        var f = fixture(h);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            populate(f); var folder = create(h, f, null, "Protected");
            apply(h, f.service(), edit(FolderBook.Op.ASSIGN, folder, null, "", List.of(new FolderBook.MemberRef(deviceId(f, f.first), 0)), List.of()));
            select(f.menuA, folder, 1); f.menuB.broadcastChanges();
            var before = f.service().view(); var physical = physicalCells(f);
            var rename = edit(FolderBook.Op.RENAME, folder, null, "Denied", List.of(), List.of());
            f.alice.getAbilities().mayBuild = false;
            f.menuA.handleFolderEdit(new Network.FolderEditAction(f.menuA.containerId, 1, before.revision(), rename));
            content(f.menuA, IRON, 0, false);
            check(h, f.service().view().equals(before) && f.menuA.getCarried().isEmpty(), "Build restriction must reject shared edits and folder transfers");
            f.alice.getAbilities().mayBuild = true;
            var guard = new FolderMemberInteractionGuard(f.alice, f.first.getBlockPos()); MinecraftForge.EVENT_BUS.register(guard);
            try { content(f.menuA, IRON, 0, false); }
            finally { MinecraftForge.EVENT_BUS.unregister(guard); }
            check(h, f.menuA.getCarried().isEmpty(), "Folder scope must respect actual member device protection");
            var snapshot = f.menuA.getSnapshot();
            var oldAction = new Network.ContentAction(f.menuA.containerId, snapshot.revision(), snapshot.selectedDevice(), -1, IRON, 0, false, before.revision());
            f.menuB.handleFolderEdit(new Network.FolderEditAction(f.menuB.containerId, 2, before.revision(),
                    edit(FolderBook.Op.UNASSIGN, null, null, "", List.of(new FolderBook.MemberRef(deviceId(f, f.first), 0)), List.of())));
            check(h, f.service().view().revision() > before.revision() && f.service().members(folder).isEmpty(), "Other player's edit must change shared membership");
            f.menuA.handleContentAction(oldAction);
            check(h, f.menuA.getCarried().isEmpty(), "Old folder revision must not access a former member");
            f.menuA.handleFolderEdit(new Network.FolderEditAction(f.menuA.containerId, 3, before.revision(), rename));
            check(h, f.service().view().folders().stream().noneMatch(entry -> entry.name().equals("Denied")), "Stale player edit overwrote current shared folder");
            unchanged(h, f, physical, "protected/stale folder operations");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void realGridSplitAndMergeKeepIndependentEditsAndDeletedTombstones(GameTestHelper h) {
        var f = fixture(h); var ids = new UUID[4]; var physical = new ArrayList<CompoundTag>();
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            populate(f); physical.addAll(physicalCells(f));
            ids[0] = create(h, f, null, "Shared"); ids[1] = create(h, f, ids[0], "Delete later");
            apply(h, f.service(), edit(FolderBook.Op.ASSIGN, ids[0], null, "", List.of(new FolderBook.MemberRef(deviceId(f, f.outside), 0)), List.of()));
            h.setBlock(LEFT.east(2), Blocks.AIR);
        }).thenWaitUntil(() -> check(h, f.left.getMainNode().isActive() && f.right.getMainNode().isActive()
                && f.left.getMainNode().getGrid() != f.right.getMainNode().getGrid(), "Physical cable removal must split two powered real grids"))
        .thenExecute(() -> {
            check(h, f.service() != f.otherService() && f.service().view().folders().size() == 2 && f.otherService().view().folders().size() == 2,
                    "Grid split must copy metadata into independently editable services");
            apply(h, f.service(), edit(FolderBook.Op.DELETE, ids[1], null, "", List.of(), List.of()));
            apply(h, f.otherService(), edit(FolderBook.Op.RENAME, ids[1], null, "Offline branch rename", List.of(), List.of()));
            apply(h, f.otherService(), edit(FolderBook.Op.RENAME, ids[0], null, "Right newer", List.of(), List.of()));
            ids[2] = create(h, f, null, "Same name");
            ids[3] = applyCreate(h, f.otherService(), null, "Same name");
            check(h, f.otherService().view().folders().stream().anyMatch(folder -> folder.id().equals(ids[1])), "Split edits leaked into the other grid before reconnect");
            select(f.menuA, ids[0], 1); f.menuA.setCarried(new ItemStack(Items.DIAMOND, 4));
            content(f.menuA, null, 0, false);
            check(h, f.menuA.getCarried().getCount() == 4 && amount(f.outside, 0, DIAMOND) == 99,
                    "Offline member must not allow insertion or fall back to all visible storage");
            f.menuA.setCarried(ItemStack.EMPTY);
            PartHelper.setPart(h.getLevel(), h.absolutePos(LEFT.east(2)), null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        }).thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            var folders = f.service().view().folders();
            check(h, folders.stream().noneMatch(folder -> folder.id().equals(ids[1])), "Stale split book resurrected a deleted folder after merge");
            check(h, folders.stream().anyMatch(folder -> folder.id().equals(ids[0]) && folder.name().equals("Right newer")), "Latest independently stamped rename was lost");
            check(h, folders.stream().anyMatch(folder -> folder.id().equals(ids[2])) && folders.stream().anyMatch(folder -> folder.id().equals(ids[3])), "Same-name folders with distinct identities must both survive merge");
            unchanged(h, f, physical, "split/merge metadata synchronization");
        }).thenSucceed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void folderSavedDataAndLargeAtomicWireDocumentsRoundTrip(GameTestHelper h) {
        var f = fixture(h);
        h.startSequence().thenWaitUntil(() -> ready(h, f)).thenExecute(() -> {
            var parent = create(h, f, null, "中文仓库"); var child = create(h, f, parent, "Child");
            apply(h, f.service(), edit(FolderBook.Op.ASSIGN, child, null, "", List.of(new FolderBook.MemberRef(deviceId(f, f.first), 0)), List.of()));
            var data = FolderSavedData.get(h.getLevel().getServer());
            try {
                var output = new java.io.ByteArrayOutputStream(); NbtIo.writeCompressed(data.save(new CompoundTag()), output);
                var loaded = FolderSavedData.load(NbtIo.readCompressed(new java.io.ByteArrayInputStream(output.toByteArray())));
                check(h, loaded.read(f.service().bookId()).view().equals(f.service().view()), "Actual compressed SavedData must retain hierarchy, slot membership and revision");
                check(h, loaded.nextRevision() > f.service().view().revision(), "Reloaded server revision clock must not reuse stale edit revisions");
            } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
            check(h, containsBookId(f.left.saveWithFullMetadata(), f.service().bookId()), "Actual controller node NBT must persist the shared book reference");
            var members = new ArrayList<FolderBook.MemberRef>();
            for (int slot = 0; slot < 2000; slot++) members.add(new FolderBook.MemberRef("block:minecraft:overworld:123456,100,123456:expanded_drive", slot));
            var view = new FolderBook.View(17, List.of(new FolderBook.Folder(UUID.randomUUID(), null, "多选元件", members)));
            var cursor = new FolderTransfer.Cursor(FolderTransfer.encodeView(view));
            var assembler = new FolderTransfer.Assembler(FolderTransfer.MAX_VIEW_BYTES); byte[] completed = null; int frames = 0;
            var chunks = new ArrayList<FolderTransfer.Frame>();
            while (!cursor.finished()) {
                var frame = cursor.next(); chunks.add(frame); frames++;
                check(h, frame.payload().length <= FolderTransfer.FRAME_BYTES, "Large folder packet exceeded bounded frame size");
                completed = assembler.accept(frame);
                check(h, cursor.finished() || completed == null, "Partial document must not replace the complete folder tree");
            }
            check(h, frames > 1 && FolderTransfer.decodeView(completed).equals(view), "Large shared folder document lost members while framing");
            boolean rejected = false;
            try { new FolderTransfer.Assembler(FolderTransfer.MAX_VIEW_BYTES).accept(chunks.get(1)); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(h, rejected, "Out-of-order folder frames must not produce a partial shared tree");
            var edit = edit(FolderBook.Op.ASSIGN, parent, null, "", members, List.of(child));
            check(h, FolderTransfer.decodeEdit(FolderTransfer.encodeEdit(edit)).equals(edit), "Large multi-selection edit lost members or nested-folder identities");
            verifySavedDataVersionSharing(h);
            verifyAccumulatedEditFramesFailExplicitly(h,f);
        }).thenSucceed();
    }

    private static void verifyAccumulatedEditFramesFailExplicitly(GameTestHelper h,Fixture f) {
        var before=f.service().view();var physical=physicalCells(f);
        var member=new FolderBook.MemberRef(deviceId(f,f.first),0);
        // This is a valid edit with repeated selection entries, deliberately large enough
        // to model normally paced client frames delivered together after network delay.
        var edit=edit(FolderBook.Op.CREATE,null,null,"Queued selection",java.util.Collections.nCopies(6000,member),List.of());
        var cursor=new FolderTransfer.Cursor(FolderTransfer.encodeEdit(edit));
        long request=99001;
        for(int i=0;i<8;i++){
            check(h,!cursor.finished(),"Rate-limit fixture must have at least nine real encoded frames");
            f.menuA.handleFolderEditFrame(new Network.FolderEditFrameMessage(f.menuA.containerId,request,before.revision(),cursor.next()));
            check(h,f.service().view().equals(before)&&f.menuA.getFolderError().isEmpty(),"An incomplete accepted edit frame changed folders or prematurely failed");
        }
        check(h,!cursor.finished(),"Eight frames must leave the edit incomplete");
        f.menuA.handleFolderEditFrame(new Network.FolderEditFrameMessage(f.menuA.containerId,request,before.revision(),cursor.next()));
        check(h,f.menuA.getFolderError().equals("gui.me_storage_controller.folder_error.limit"),"Ninth accumulated frame must explicitly finish the request with limit feedback");
        while(!cursor.finished())f.menuA.handleFolderEditFrame(new Network.FolderEditFrameMessage(f.menuA.containerId,request,before.revision(),cursor.next()));
        check(h,f.service().view().equals(before),"Rejected edit tail must never perform folder CRUD");
        check(h,f.menuA.getFolderError().equals("gui.me_storage_controller.folder_error.limit"),"Tail frames must not replace the terminal limit response");
        unchanged(h,f,physical,"rate-limited edit");
    }

    private static void verifySavedDataVersionSharing(GameTestHelper h) {
        var original = new FolderBook();
        var created = original.apply(0, edit(FolderBook.Op.CREATE, null, null, "Original", List.of(), List.of()), 1);
        check(h, created.success(), "Version-sharing fixture could not create initial book");
        var revisionOnly = original.save(); revisionOnly.putLong("revision", 2);
        var branch = FolderBook.load(revisionOnly);
        var data = new FolderSavedData(); var left = UUID.randomUUID(); var right = UUID.randomUUID();
        data.put(left, original); data.put(right, branch);
        var packed = data.save(new CompoundTag());
        check(h, packed.getList("versions", 10).size() == 1 && packed.getList("heads", 10).size() == 2,
                "Identical folder records at different grid-view revisions must share one persisted version");
        var renamed = branch.apply(2, edit(FolderBook.Op.RENAME, created.createdId(), null, "Changed branch", List.of(), List.of()), 3);
        check(h, renamed.success(), "Version-sharing fixture rename failed"); data.put(right, branch);
        check(h, data.read(left).view().folders().get(0).name().equals("Original"), "Editing one persisted branch mutated another shared version");
        var loaded = FolderSavedData.load(data.save(new CompoundTag()));
        check(h, loaded.read(left).view().equals(original.view()) && loaded.read(right).view().equals(branch.view()),
                "Deduplicated SavedData reload mixed branch contents or independent head revisions");
        var legacy = new CompoundTag(); var oldBooks = new net.minecraft.nbt.ListTag();
        var oldEntry = original.save(); oldEntry.putUUID("bookId", left); oldBooks.add(oldEntry); legacy.put("books", oldBooks); legacy.putLong("clock", 1);
        check(h, FolderSavedData.load(legacy).read(left).view().equals(original.view()), "Legacy full-book save format must still load without losing folder state");
    }

    private static Fixture fixture(GameTestHelper h) {
        h.setBlock(LEFT.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(RIGHT.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        h.setBlock(LEFT, MEStorageController.CONTROLLER.get());
        h.setBlock(RIGHT, MEStorageController.CONTROLLER.get());
        for (var pos : List.of(LEFT.east(), LEFT.east(2)))
            PartHelper.setPart(h.getLevel(), h.absolutePos(pos), null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var first = drive(h, LEFT.west(), Direction.WEST);
        var second = drive(h, LEFT.north(), Direction.NORTH);
        var outside = drive(h, RIGHT.east(), Direction.EAST);
        for (var d : List.of(first, second, outside)) {
            d.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_64K.stack());
            d.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack());
        }
        var left = (ControllerBlockEntity) h.getBlockEntity(LEFT);
        var right = (ControllerBlockEntity) h.getBlockEntity(RIGHT);
        var alice = h.makeMockSurvivalPlayer(); var bob = h.makeMockSurvivalPlayer();
        var a = h.absolutePos(LEFT); var b = h.absolutePos(RIGHT);
        alice.setPos(a.getX() + .5, a.getY(), a.getZ() + .5);
        bob.setPos(b.getX() + .5, b.getY(), b.getZ() + .5);
        var menuA = new ControllerMenu(51, alice.getInventory(), left);
        var menuB = new ControllerMenu(52, bob.getInventory(), right);
        alice.containerMenu = menuA; bob.containerMenu = menuB;
        return new Fixture(left, right, first, second, outside, alice, bob, menuA, menuB);
    }
    private static DriveBlockEntity drive(GameTestHelper h, BlockPos pos, Direction facing) {
        var state = AEBlocks.DRIVE.block().defaultBlockState();
        h.setBlock(pos, IOrientationStrategy.get(state).setFacing(state, facing));
        return (DriveBlockEntity) h.getBlockEntity(pos);
    }
    private static void ready(GameTestHelper h, Fixture f) {
        check(h, f.left.getMainNode().isActive() && f.right.getMainNode().isActive()
                && f.left.getMainNode().getGrid() == f.right.getMainNode().getGrid()
                && f.drives().stream().allMatch(d -> d.getMainNode().isActive()), "Shared folder fixture must boot as one actual AE grid");
    }
    private static void populate(Fixture f) {
        insert(f.first, 0, IRON, 1000); insert(f.first, 1, WATER, 5000);
        insert(f.second, 0, IRON, 2000); insert(f.second, 1, WATER, 7000);
        insert(f.outside, 0, DIAMOND, 99); insert(f.outside, 1, WATER, 11000);
    }
    private static void insert(DriveBlockEntity drive, int slot, AEKey key, long amount) {
        drive.getCellInventory(slot).insert(key, amount, Actionable.MODULATE, IActionSource.empty());
    }
    private static String deviceId(Fixture f, DriveBlockEntity drive) {
        return StorageScanner.discover(f.left.getMainNode().getGrid()).stream().filter(d -> d.owner() == drive).findFirst().orElseThrow().id();
    }
    private static FolderBook.Edit edit(FolderBook.Op op, UUID id, UUID parent, String name,
                                        List<FolderBook.MemberRef> members, List<UUID> folders) {
        return new FolderBook.Edit(op, id, parent, name, members, folders);
    }
    private static UUID create(GameTestHelper h, Fixture f, UUID parent, String name) {
        return applyCreate(h, f.service(), parent, name);
    }
    private static UUID applyCreate(GameTestHelper h, FolderService service, UUID parent, String name) {
        var result = service.apply(service.view().revision(), edit(FolderBook.Op.CREATE, null, parent, name, List.of(), List.of()));
        check(h, result.error().isEmpty() && result.createdId() != null, "Shared folder creation failed: " + result.error());
        return result.createdId();
    }
    private static void apply(GameTestHelper h, FolderService service, FolderBook.Edit edit) {
        var result = service.apply(service.view().revision(), edit);
        check(h, result.error().isEmpty(), "Valid shared folder edit failed: " + edit.op() + ": " + result.error());
    }
    private static void reject(GameTestHelper h, FolderService service, FolderBook.Edit edit) {
        var before = service.view(); var result = service.apply(before.revision(), edit);
        check(h, !result.error().isEmpty() && service.view().equals(before), "Invalid folder edit changed shared metadata: " + edit.op());
    }
    private static List<CompoundTag> physicalCells(Fixture f) {
        var result = new ArrayList<CompoundTag>();
        for (var drive : f.drives()) for (int slot = 0; slot < 10; slot++)
            result.add(drive.getInternalInventory().getStackInSlot(slot).save(new CompoundTag()));
        return result;
    }
    private static void unchanged(GameTestHelper h, Fixture f, List<CompoundTag> before, String action) {
        check(h, physicalCells(f).equals(before), "Folder " + action + " changed a physical storage cell or its complete content NBT");
    }
    private static long amount(DriveBlockEntity drive, int slot, AEKey key) {
        return StorageScanner.contents(drive.getCellInventory(slot)).get(key);
    }
    private static long shown(ControllerMenu menu, AEKey key) {
        return menu.getSnapshot().contents().stream().filter(content -> content.key().equals(key)).mapToLong(content -> content.amount()).sum();
    }
    private static void select(ControllerMenu menu, UUID folder, long revision) {
        menu.handleRequest(new Network.Request(menu.containerId, "folder:" + folder, -1, 0, 0, "", "", true, List.of(), List.of(), revision));
        menu.broadcastChanges();
    }
    private static void content(ControllerMenu menu, AEKey key, int button, boolean shift) {
        var snapshot = menu.getSnapshot();
        menu.handleContentAction(new Network.ContentAction(menu.containerId, snapshot.revision(), snapshot.selectedDevice(),
                snapshot.selectedCell(), key, button, shift, menu.getFolderView().revision()));
    }
    private static boolean containsBookId(net.minecraft.nbt.Tag tag, UUID id) {
        if (tag instanceof CompoundTag compound) {
            if (compound.hasUUID(FolderService.NODE_TAG) && compound.getUUID(FolderService.NODE_TAG).equals(id)) return true;
            for (var key : compound.getAllKeys()) if (containsBookId(compound.get(key), id)) return true;
        } else if (tag instanceof net.minecraft.nbt.ListTag list) {
            for (var child : list) if (containsBookId(child, id)) return true;
        }
        return false;
    }
    private static final class FolderMemberInteractionGuard {
        private final Player player; private final BlockPos pos;
        FolderMemberInteractionGuard(Player player, BlockPos pos) { this.player = player; this.pos = pos; }
        @SubscribeEvent public void denyFolderMemberInteraction(PlayerInteractEvent.RightClickBlock event) {
            if (event.getEntity() == player && event.getPos().equals(pos)) event.setCanceled(true);
        }
    }
    private static void check(GameTestHelper h, boolean condition, String message) { h.assertTrue(condition, message); }
}
