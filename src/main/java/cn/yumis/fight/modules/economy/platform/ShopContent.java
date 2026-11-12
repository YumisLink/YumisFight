package cn.yumis.fight.modules.economy.platform;

import cn.yumis.fight.modules.economy.api.ShopMenu;
import cn.yumis.fight.platform.ItemData;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class ShopContent {
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,ItemData.NS);
    public static final DeferredHolder<MenuType<?>,MenuType<ShopMenu>> SHOP=MENUS.register("npc_shop",()->IMenuTypeExtension.create(ShopMenu::new));
    private ShopContent(){}
    public static void register(IEventBus bus){MENUS.register(bus);ShopMenu.type(SHOP);}
}
