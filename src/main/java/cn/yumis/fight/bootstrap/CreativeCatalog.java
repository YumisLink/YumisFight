package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
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
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> EPIC=TABS.register("epic_equipment",()->CreativeModeTab.builder().title(ModText.of("AYD-史诗装备")).icon(()->EquipmentItems.gear(EquipmentTemplates.creative(Rarity.EPIC).getFirst())).displayItems((p,o)->EquipmentTemplates.creative(Rarity.EPIC).forEach(g->o.accept(EquipmentItems.gear(g)))).build());
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> MYTHIC=TABS.register("mythic_equipment",()->CreativeModeTab.builder().withTabsBefore(EPIC.getId()).withTabsAfter(net.minecraft.resources.Identifier.fromNamespaceAndPath(ItemData.NS,"industry")).title(ModText.of("AYD-神话装备")).icon(()->EquipmentItems.gear(MageBlood.create(new Random(0)))).displayItems((p,o)->EquipmentTemplates.creative(Rarity.MYTHIC).forEach(g->o.accept(EquipmentItems.gear(g)))).build());
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> CURRENCY=TABS.register("currency",()->CreativeModeTab.builder().withTabsBefore(MYTHIC.getId()).title(ModText.of("AYD-通货")).icon(()->ItemRegistry.item("chaos_stone",1)).displayItems((p,o)->{for(var currency:CurrencyRules.ALL)if(!Set.of("set_stone","affix_gem").contains(currency.id()))o.accept(ItemRegistry.item(currency.id(),1));o.accept(ItemRegistry.item(CurrencyRules.COLORLESS_CRYSTAL.id(),1));}).build());
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("equipment",()->CreativeModeTab.builder().withTabsBefore(CURRENCY.getId()).title(ModText.of("AYD-其他")).icon(()->ItemRegistry.item("katana",1)).displayItems((parameters,output)->{
        for(var gear:EquipmentTemplates.creative())output.accept(EquipmentItems.gear(gear));for(var material:MaterialCatalog.current().materials.values())output.accept(EquipmentItems.material(material.id(),1));for(var entry:ItemRegistry.ALL.entrySet())if(!EquipmentItems.GEAR_IDS.contains(entry.getKey())&&CurrencyRules.find(entry.getKey())==null&&!entry.getKey().equals(CurrencyRules.COLORLESS_CRYSTAL.id())&&!Set.of("crafting_material","treasure_chest","loot_jar").contains(entry.getKey()))output.accept(entry.getValue().get());
        for(String definition:cn.yumis.fight.modules.economy.api.JarDefinitions.current().jars.keySet())output.accept(cn.yumis.fight.modules.economy.api.LootJarItem.create(definition,1));output.accept(FightBlock());
    }
    ).build());
    private static ItemStack FightBlock(){
        return new ItemStack(cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.FABRICATOR.get());
    }
    private CreativeCatalog(){
    }
}
