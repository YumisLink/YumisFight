package cn.yumis.fight.modules.equipment.api;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.server.*;
import java.util.*;
import cn.yumis.fight.platform.ItemData;

/** Module-owned platform registrations. */
public final class EquipmentContentTypes {
    private static final String ID=ItemData.NS;
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(ID);
    public static final DeferredBlock<FabricatorBlock> FABRICATOR=BLOCKS.registerBlock("equipment_fabricator",FabricatorBlock::new,p->p.strength(3).sound(SoundType.METAL));
    public static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,ID);
    public static final DeferredHolder<MenuType<?>,MenuType<FabricatorMenu>> FABRICATOR_MENU=MENUS.register("fabricator",()->new MenuType<>(FabricatorMenu::new,FeatureFlags.DEFAULT_FLAGS));
    public static void register(IEventBus bus){BLOCKS.register(bus);MENUS.register(bus);cn.yumis.fight.platform.ItemRegistry.ITEMS.registerSimpleBlockItem(FABRICATOR);}
}
