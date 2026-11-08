package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.platform.ItemRegistry;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Existing chest pools, evaluated once by the owner before consuming the chest. */
public final class ChestRewards {
    private ChestRewards(){}
    public static ItemStack waystoneChest(int tier){var definition=ChestDefinitions.current().waystone(tier);var stack=ItemRegistry.item("treasure_chest",1);var data=new JsonObject();data.addProperty("id",UUID.randomUUID().toString());data.addProperty("definition","waystone");data.addProperty("tier",tier);data.addProperty("level",80);cn.yumis.fight.platform.ItemData.string(stack,"treasure_chest",data.toString());stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal(definition.name().replace("%s",Integer.toString(tier))));return stack;}
    public static List<ItemStack> generate(JsonObject data,Stats stats,Random random){
        int tier=data.get("tier").getAsInt(),level=data.get("level").getAsInt();String definition=data.get("definition").getAsString();var result=new ArrayList<ItemStack>();
        if(definition.equals("waystone")){
            var rule=ChestDefinitions.current().waystone(tier);double luck=rule.inheritDropRate()?stats.get(Stat.DROP_RATE):0;
            for(int i=0,n=random.nextInt(rule.currencyMinimum(),rule.currencyMaximum()+1);i<n;i++)result.add(ItemRegistry.item(CurrencyRules.rollGuaranteed(luck,random).id(),1));for(var entry:rule.fixed())result.add(item(entry));for(int i=0;i<rule.specialRolls();i++){var entry=ChestDefinitions.draw(rule.special(),random);if(entry!=null)result.add(item(entry));}return result;
        }
        if(tier<1||tier>10||level<1||level>80)throw new IllegalArgumentException("宝箱数据无效");var rule=ChestDefinitions.current().require(definition);double luck=tier*200d+stats.get(Stat.DROP_RATE);
        for(int i=0,n=rule.equipmentBase()+tier*rule.equipmentPerTier();i<n;i++){var rarity=LootOdds.rarity(cn.yumis.fight.modules.dungeons.api.Difficulty.NORMAL,true,false,luck,random);if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;var eligible=new ArrayList<Slot>();for(var slot:Slot.values())if(slot.minimum()<=level)eligible.add(slot);var gear=EquipmentTemplates.createStandard(eligible.get(random.nextInt(eligible.size())),Weapon.standard().get(random.nextInt(Weapon.standard().size())),rarity,level,random);gear.source="treasure_chest/"+data.get("id").getAsString();result.add(EquipmentItems.gear(gear));}
        for(int i=0,n=rule.currencyBase()+tier*rule.currencyPerTier();i<n;i++)result.add(ItemRegistry.item(CurrencyRules.rollGuaranteed(luck,random).id(),1));for(var entry:rule.fixed())result.add(item(entry));for(int i=0;i<rule.specialRolls();i++){var entry=ChestDefinitions.draw(rule.special(),random);if(entry!=null)result.add(item(entry));}return result;
    }
    public static ItemStack item(ChestDefinitions.Entry entry){if(!entry.item().contains(":"))return ItemRegistry.item(entry.item(),entry.count());var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(entry.item()));if(item==null||item==net.minecraft.world.item.Items.AIR)throw new IllegalArgumentException("未知奖励物品");return new ItemStack(item,entry.count());}
}
