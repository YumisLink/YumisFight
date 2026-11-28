package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.platform.ItemRegistry;
import com.google.gson.JsonObject;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Existing chest pools, evaluated once by the owner before consuming the chest. */
public final class ChestRewards {
    public static final int MAX_EQUIPMENT = 24;
    public static final int MAX_MULTIPLIER = 4096;

    public record Outcome(List<ItemStack> items, long gold) {}

    private ChestRewards(){}

    public static ItemStack waystoneChest(int tier){var definition=ChestDefinitions.current().waystone(tier);var stack=ItemRegistry.item("treasure_chest",1);var data=new JsonObject();data.addProperty("id",UUID.randomUUID().toString());data.addProperty("definition","waystone");data.addProperty("tier",tier);data.addProperty("level",80);cn.yumis.fight.platform.ItemData.string(stack,"treasure_chest",data.toString());stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal(definition.name().replace("%s",Integer.toString(tier))));return stack;}

    public static List<ItemStack> generate(JsonObject data,Stats stats,Random random){
        return open(data,stats,random).items();
    }

    public static Outcome open(JsonObject data,Stats stats,Random random){
        int tier=data.get("tier").getAsInt(),level=data.get("level").getAsInt();String definition=data.get("definition").getAsString();var result=new ArrayList<ItemStack>();
        String source=data.has("id")&&data.get("id").isJsonPrimitive()?"treasure_chest/"+data.get("id").getAsString():"";
        if(definition.equals("waystone")){
            var rule=ChestDefinitions.current().waystone(tier);double luck=rule.inheritDropRate()?stats.get(Stat.DROP_RATE):0;
            for(int i=0,n=random.nextInt(rule.currencyMinimum(),rule.currencyMaximum()+1);i<n;i++)addStacked(result,ItemRegistry.item(CurrencyRules.rollGuaranteed(luck,random).id(),1));
            for(var entry:rule.fixed())addStacked(result,item(entry,random,source));
            for(int i=0;i<rule.specialRolls();i++){var entry=ChestDefinitions.draw(rule.special(),random);if(entry!=null)addStacked(result,item(entry,random,source));}
            return new Outcome(List.copyOf(result),0L);
        }
        if(tier<1||tier>10||level<1||level>80)throw new IllegalArgumentException("宝箱数据无效");
        int multiplier=data.has("multiplier")&&data.get("multiplier").isJsonPrimitive()?Math.clamp(data.get("multiplier").getAsInt(),1,MAX_MULTIPLIER):1;
        String theme=data.has("theme")&&data.get("theme").isJsonPrimitive()?data.get("theme").getAsString():"";
        var rule=ChestDefinitions.current().require(definition);
        double luck=tier*200d+stats.get(Stat.DROP_RATE);

        int baseEquip=rule.equipmentBase()+tier*rule.equipmentPerTier();
        int extraEquip=multiplier>1?Math.min(8,(31-Integer.numberOfLeadingZeros(multiplier))*2):0;
        int equipCount=baseEquip==0?0:Math.min(MAX_EQUIPMENT,baseEquip+extraEquip);
        for(int i=0;i<equipCount;i++){
            var rarity=LootOdds.rarity(Difficulty.NORMAL,true,false,luck,random);
            if(rarity.ordinal()>Rarity.LEGENDARY.ordinal())rarity=Rarity.LEGENDARY;
            var eligible=new ArrayList<Slot>();for(var slot:Slot.values())if(slot.minimum()<=level)eligible.add(slot);
            var gear=EquipmentTemplates.createStandard(eligible.get(random.nextInt(eligible.size())),Weapon.standard().get(random.nextInt(Weapon.standard().size())),rarity,level,random);
            gear.source=source;
            result.add(EquipmentItems.gear(gear));
        }

        int baseCurrency=rule.currencyBase()+tier*rule.currencyPerTier();
        int totalCurrency=Math.multiplyExact(baseCurrency,multiplier);
        if(totalCurrency>0){
            int rolls=Math.min(totalCurrency,24);
            int perRoll=totalCurrency/rolls,remainder=totalCurrency%rolls;
            for(int i=0;i<rolls;i++){
                int count=perRoll+(i<remainder?1:0);
                addCounted(result,ItemRegistry.item(CurrencyRules.rollGuaranteed(luck,random).id(),1),count);
            }
        }

        if(definition.equals("treasure")&&!theme.isBlank()){
            try{
                var materials=DungeonCatalog.current().dungeon(theme).materials();
                if(!materials.isEmpty()){
                    int totalMaterials=Math.multiplyExact(2+tier,multiplier);
                    int perType=totalMaterials/materials.size(),remainder=totalMaterials%materials.size();
                    for(int i=0;i<materials.size();i++){
                        int count=perType+(i<remainder?1:0);
                        if(count>0)addCounted(result,EquipmentItems.material(materials.get(i),1),count);
                    }
                }
            }catch(IllegalArgumentException ignored){}
        }

        for(var entry:rule.fixed()){
            if(entry.template()!=null)result.add(item(entry,random,source));
            else addCounted(result,item(new ChestDefinitions.Entry(entry.item(),1,entry.weight(),null),random,source),Math.multiplyExact(entry.count(),multiplier));
        }
        for(int i=0;i<rule.specialRolls();i++){
            var entry=ChestDefinitions.draw(rule.special(),random);
            if(entry==null)continue;
            if(entry.template()!=null)result.add(item(entry,random,source));
            else addCounted(result,item(new ChestDefinitions.Entry(entry.item(),1,entry.weight(),null),random,source),Math.multiplyExact(entry.count(),multiplier));
        }

        long gold=0L;
        if(definition.equals("treasure")){
            long baseGold=CurrencyRules.killGold(level,false,true,Difficulty.WARRIOR,0.5+0.25*tier);
            gold=Math.multiplyExact(baseGold,(long)multiplier);
        }
        return new Outcome(List.copyOf(result),gold);
    }

    private static void addCounted(List<ItemStack> out,ItemStack prototype,int total){
        if(prototype.isEmpty()||total<=0)return;
        int max=Math.max(1,prototype.getMaxStackSize());
        int remaining=total;
        for(var existing:out){
            if(remaining<=0)return;
            if(ItemStack.isSameItemSameComponents(existing,prototype)&&existing.getCount()<max){
                int move=Math.min(remaining,max-existing.getCount());
                existing.grow(move);
                remaining-=move;
            }
        }
        while(remaining>0){
            int chunk=Math.min(remaining,max);
            out.add(prototype.copyWithCount(chunk));
            remaining-=chunk;
        }
    }

    private static void addStacked(List<ItemStack> out,ItemStack stack){
        if(!stack.isEmpty())addCounted(out,stack.copyWithCount(1),stack.getCount());
    }

    public static ItemStack item(ChestDefinitions.Entry entry,Random random,String source){
        if(entry.template()!=null){
            var gear=NamedEquipment.create(entry.template(),random);
            gear.source=source;
            return EquipmentItems.gear(gear);
        }
        if(!entry.item().contains(":"))return ItemRegistry.item(entry.item(),entry.count());
        var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(entry.item()));
        if(item==null||item==net.minecraft.world.item.Items.AIR)throw new IllegalArgumentException("未知奖励物品");
        return new ItemStack(item,entry.count());
    }
}
