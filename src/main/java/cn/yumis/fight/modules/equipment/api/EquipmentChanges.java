package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.platform.ItemData;
import com.google.gson.*;
import cn.yumis.fight.modules.inventory.api.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static cn.yumis.fight.modules.equipment.api.EquipmentJson.JSON;

/** Personal processing rules. Random results are produced once by the owning client. */
public final class EquipmentChanges {
    public record Result(ItemStack equipment,ItemStack currency,List<ItemStack> outputs){}
    private EquipmentChanges(){}
    public static Result process(ItemStack source,ItemStack input,int socket,Random random){
        var gear=EquipmentItems.gear(source);if(gear==null||input.isEmpty())throw new IllegalArgumentException("请选择装备和通货");String id=ItemData.id(input);var currency=input.copy();currency.shrink(1);var outputs=new ArrayList<ItemStack>();
        if(gear.corrupted&&!id.equals("affix_gem"))throw new IllegalArgumentException("腐化装备仅允许继续镶嵌宝石");
        if(id.equals("set_extraction_stone")){
            if(gear.set.isEmpty())throw new IllegalArgumentException("该装备没有套装");outputs.add(EquipmentItems.payload("set_stone",Map.of("set",gear.set,"level",gear.level,"group",gear.slot.armor()?"armor":"jewelry"),"套装石 · "+EquipmentDefinitions.current().sets.get(gear.set).name()));return new Result(ItemStack.EMPTY,currency,outputs);
        }
        if(id.equals("set_stone")){
            var payload=JsonParser.parseString(EquipmentItems.payload(input)).getAsJsonObject();boolean armor=payload.get("group").getAsString().equals("armor");
            if(gear.level<payload.get("level").getAsInt()||armor&&!gear.slot.armor()||!armor&&!gear.slot.jewelry())throw new IllegalArgumentException("套装石部位或来源等级不符");
            String set=payload.get("set").getAsString();var definition=EquipmentDefinitions.current().sets.get(set);if(definition==null||!definition.group().equals(payload.get("group").getAsString()))throw new IllegalArgumentException("套装定义或分类不符");gear.set=set;gear.revision++;
        }else if(id.equals("affix_gem"))gear=EquipmentRules.socket(gear,JSON.fromJson(EquipmentItems.payload(input),Gem.class),socket>=0?socket:gear.gems.size()<gear.sockets?gear.gems.size():0);
        else if(id.equals("affix_extraction_stone")){var gem=GemExtraction.create(gear);outputs.add(EquipmentItems.payload("affix_gem",gem,gem.name));return new Result(ItemStack.EMPTY,currency,outputs);}
        else gear=EquipmentRules.process(gear,id,random);
        var updated=source.copy();EquipmentItems.write(updated,gear);return new Result(updated,currency,outputs);
    }
    public static void prepareCraft(net.minecraft.world.entity.player.Player player,cn.yumis.fight.modules.character.api.Progress progress,boolean allowed,cn.yumis.fight.modules.professions.api.Profession profession,JsonObject j,Random random){
        if(!j.has("rulesRevision")||j.get("rulesRevision").getAsLong()!=EquipmentDefinitions.current().revision)throw new IllegalArgumentException("装备规则已更新，请刷新后重试");
        if(!(player.containerMenu instanceof FabricatorMenu))throw new IllegalArgumentException("请使用装备制作器");
        if(!allowed)throw new IllegalArgumentException("没有制作权限");
        Slot slot=Slot.valueOf(j.get("slot").getAsString());int level=j.get("level").getAsInt();if(level>progress.level)throw new IllegalArgumentException("制作等级超过角色等级");
        Weapon weapon=Weapon.valueOf(j.get("weapon").getAsString());ArmorType armor=j.has("armorType")?ArmorType.valueOf(j.get("armorType").getAsString()):ArmorType.UNTYPED;JewelryType jewelry=j.has("jewelryType")?JewelryType.valueOf(j.get("jewelryType").getAsString()):JewelryType.UNTYPED;
        if(slot.armor()&&(armor==ArmorType.UNTYPED||jewelry!=JewelryType.UNTYPED)||slot.jewelry()&&(jewelry==JewelryType.UNTYPED||armor!=ArmorType.UNTYPED)||!slot.armor()&&!slot.jewelry()&&(armor!=ArmorType.UNTYPED||jewelry!=JewelryType.UNTYPED))throw new IllegalArgumentException("请选择有效装备类型");
        var before=new ArrayList<ItemStack>();var after=new ArrayList<ItemStack>();for(int i=0;i<36;i++){before.add(player.getInventory().getItem(i).copy());after.add(before.get(i).copy());}
        for(var need:MaterialCatalog.current().recipe(slot,level)){int left=need.count();for(int i=0;i<36&&left>0;i++){var stack=after.get(i);if(!stack.isEmpty()&&(need.regional()?EquipmentItems.material(stack).equals(need.id()):BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(need.id()))){int count=Math.min(left,stack.getCount());stack.shrink(count);left-=count;}}if(left>0)throw new IllegalArgumentException("材料不足："+need.id());}
        var edits=new JsonArray();for(int i=0;i<36;i++)if(!ItemStack.matches(before.get(i),after.get(i)))edits.add(InventoryEdits.change(player,"inventory",i,before.get(i),after.get(i)));j.add("inventoryChanges",edits);j.addProperty("menu",player.containerMenu.containerId);
        var outputs=new JsonArray();outputs.add(InventoryEdits.encode(player,EquipmentItems.gear(EquipmentRules.create(slot,weapon,Rarity.COMMON,level,armor,jewelry,random))));j.add("outputs",outputs);
        var value=EquipmentJson.copy(profession,cn.yumis.fight.modules.professions.api.Profession.class);j.add("beforeProfession",JSON.toJsonTree(value));value.award(60,false);value.grantTitle("craftsman");j.add("profession",JSON.toJsonTree(value));
    }
    public static void prepareCurrency(net.minecraft.world.entity.player.Player player,JsonObject j,Random random){
        if(!j.has("rulesRevision")||j.get("rulesRevision").getAsLong()!=EquipmentDefinitions.current().revision)throw new IllegalArgumentException("装备规则已更新，请刷新后重试");
        int index=j.get("slot").getAsInt();if(j.get("menu").getAsInt()!=player.containerMenu.containerId)throw new IllegalArgumentException("加工界面已变化");
        var slot=player.containerMenu.getSlot(index);if(!SlotAccess.isWritable(slot,player))throw new IllegalArgumentException("该栏位不能加工装备");var source=slot.getItem();var gear=EquipmentItems.gear(source);var cursor=player.containerMenu.getCarried();
        if(gear==null||!gear.instance.toString().equals(j.get("equipment").getAsString())||gear.revision!=j.get("revision").getAsInt()||!InventoryEdits.encode(player,cursor).equals(j.get("cursor")))throw new IllegalArgumentException("装备或通货已变化");
        var value=EquipmentChanges.process(source,cursor,j.has("socket")?j.get("socket").getAsInt():-1,random);var edits=new JsonArray();edits.add(InventoryEdits.change(player,"menu",index,source,value.equipment()));edits.add(InventoryEdits.change(player,"cursor",0,cursor,value.currency()));j.add("inventoryChanges",edits);var outputs=new JsonArray();for(var item:value.outputs())outputs.add(InventoryEdits.encode(player,item));j.add("outputs",outputs);
    }
}
