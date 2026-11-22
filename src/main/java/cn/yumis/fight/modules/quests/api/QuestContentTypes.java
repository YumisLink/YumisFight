package cn.yumis.fight.modules.quests.api;

import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import net.neoforged.bus.api.IEventBus;

/** Quest-owned item registration; the chant page belongs to the awakening quest, not to the NPC shell. */
public final class QuestContentTypes {
    private QuestContentTypes(){}
    public static void register(IEventBus bus){
        ItemRegistry.ALL.put(AwakeningQuest.CHANT_PAGE,ItemRegistry.ITEMS.registerSimpleItem(AwakeningQuest.CHANT_PAGE));
    }
}
