package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.equipment.api.MageBlood;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.equipment.api.Rarity;

import net.minecraft.core.registries.Registries;

import net.minecraft.world.item.*;
import net.neoforged.neoforge.registries.*;
import java.util.*;
public final class CreativeCatalog {
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,ItemData.NS);
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> EPIC=TABS.register("epic_equipment",()->CreativeModeTab.builder().title(ModText.of("史诗装备")).icon(()->EquipmentItems.gear(EquipmentTemplates.creative(Rarity.EPIC).getFirst())).displayItems((p,o)->EquipmentTemplates.creative(Rarity.EPIC).forEach(g->o.accept(EquipmentItems.gear(g)))).build());
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> MYTHIC=TABS.register("mythic_equipment",()->CreativeModeTab.builder().title(ModText.of("神话装备")).icon(()->EquipmentItems.gear(MageBlood.create(new Random(0)))).displayItems((p,o)->EquipmentTemplates.creative(Rarity.MYTHIC).forEach(g->o.accept(EquipmentItems.gear(g)))).build());
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("equipment",()->CreativeModeTab.builder().title(ModText.of("地下城与战斗")).icon(()->ItemRegistry.item("katana",1)).displayItems((parameters,output)->{
        for(var gear:EquipmentTemplates.creative())output.accept(EquipmentItems.gear(gear));for(var material:MaterialCatalog.current().materials.values())output.accept(EquipmentItems.material(material.id(),1));for(var entry:ItemRegistry.ALL.entrySet())if(!EquipmentItems.GEAR_IDS.contains(entry.getKey())&&!Set.of("crafting_material","set_stone","affix_gem").contains(entry.getKey()))output.accept(entry.getValue().get());output.accept(FightBlock());
    }
    ).build());
    private static ItemStack FightBlock(){
        return new ItemStack(cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.FABRICATOR.get());
    }
    private CreativeCatalog(){
    }
}
