package cn.yumis.fight.game;
import cn.yumis.fight.rules.*;
import static cn.yumis.fight.rules.Rules.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.*;
import java.util.*;
public final class CreativeCatalog {
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,GameItems.NS);
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("equipment",()->CreativeModeTab.builder().title(Component.literal("地下城与战斗")).icon(()->GameItems.item("katana",1)).displayItems((parameters,output)->{
        for(Weapon weapon:Weapon.values())output.accept(GameItems.gear(EquipmentRules.create(Slot.WEAPON,weapon,cn.yumis.fight.rules.Rules.Rarity.COMMON,1,new Random(0))));for(Slot slot:Slot.values())if(slot!=Slot.WEAPON)output.accept(GameItems.gear(EquipmentRules.create(slot,Weapon.KATANA,cn.yumis.fight.rules.Rules.Rarity.COMMON,slot.minimum(),new Random(0))));for(var material:Catalog.CURRENT.materials.values())output.accept(GameItems.material(material.id(),1));for(var entry:GameItems.ALL.entrySet())if(!GameItems.GEAR_IDS.contains(entry.getKey())&&!Set.of("crafting_material","set_stone","affix_gem").contains(entry.getKey()))output.accept(entry.getValue().get());output.accept(FightBlock());
    }
    ).build());
    private static ItemStack FightBlock(){
        return new ItemStack(cn.yumis.fight.FightMod.FABRICATOR.get());
    }
    private CreativeCatalog(){
    }
}
