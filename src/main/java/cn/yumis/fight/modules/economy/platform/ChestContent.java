package cn.yumis.fight.modules.economy.platform;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.modules.economy.api.TreasureChestItem;
/** Physical reward items; specific chest definitions remain data driven. */
public final class ChestContent {
    public static void register(){ItemRegistry.ALL.put("treasure_chest",ItemRegistry.ITEMS.registerItem("treasure_chest",TreasureChestItem::new));ItemRegistry.ALL.put("abyss_invitation",ItemRegistry.ITEMS.registerSimpleItem("abyss_invitation"));}
    private ChestContent(){}
}
