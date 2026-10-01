package dev.mestorage.controller;

import dev.mestorage.controller.block.ControllerBlock;
import dev.mestorage.controller.block.ControllerBlockEntity;
import dev.mestorage.controller.config.ControllerConfig;
import dev.mestorage.controller.folder.FolderService;
import appeng.api.networking.GridServices;
import dev.mestorage.controller.menu.ControllerMenu;
import dev.mestorage.controller.network.Network;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(MEStorageController.ID)
public final class MEStorageController {
    public static final String ID = "me_storage_controller";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ID);
    public static final RegistryObject<Block> CONTROLLER = BLOCKS.register("controller", ControllerBlock::new);
    public static final RegistryObject<Item> CONTROLLER_ITEM = ITEMS.register("controller", () -> new BlockItem(CONTROLLER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<ControllerBlockEntity>> ENTITY = ENTITIES.register("controller", () -> BlockEntityType.Builder.of(ControllerBlockEntity::new, CONTROLLER.get()).build(null));
    public static final RegistryObject<MenuType<ControllerMenu>> MENU = MENUS.register("controller", () -> IForgeMenuType.create(ControllerMenu::new));

    public MEStorageController() {
        GridServices.register(FolderService.class,FolderService.class);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ControllerConfig.SPEC, ControllerConfig.FILE_NAME);
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        if (!FMLEnvironment.production) registerTestFixtures(bus);
        bus.addListener(this::creativeTab);
        Network.register();
    }

    private static void registerTestFixtures(IEventBus bus) {
        try {
            Class.forName("dev.mestorage.controller.test.EnergyBypassGameTests")
                    .getMethod("registerFixtures", IEventBus.class).invoke(null, bus);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not register development energy fixtures", failure);
        }
    }

    private void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) event.accept(CONTROLLER_ITEM);
    }
}
