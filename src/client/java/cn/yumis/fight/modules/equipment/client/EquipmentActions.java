package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.professions.api.Profession;
import cn.yumis.fight.modules.inventory.api.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;

/** Equipment generation, costs and eligibility run only on the owner's client. */
public final class EquipmentActions {
    private static final Random RANDOM=new Random();private static final Gson JSON=new Gson();
    private static Supplier<Progress> progress;private static Supplier<JsonObject> profile;private static Function<String,Profession> profession;
    private static final Map<String,String> initializing=new HashMap<>();
    private EquipmentActions(){}
    public static void clear(){initializing.clear();}
    public static void install(CommandRouter<Void,JsonObject> router,Supplier<Progress> p,Supplier<JsonObject> view,Function<String,Profession> professions){progress=p;profile=view;profession=professions;router.register("equipment","craft",(ignored,j)->craft(j));router.register("equipment","cubeCraft",(ignored,j)->EquipmentChanges.prepareCube(Minecraft.getInstance().player,profession.apply("craftsman"),j));router.register("equipment","currency",(ignored,j)->currency(j));router.register("equipment","initializeEquipment",(ignored,j)->initialize(j));}
    private static void craft(JsonObject j){EquipmentChanges.prepareCraft(Minecraft.getInstance().player,progress.get(),profile.get().get("craftAllowed").getAsBoolean(),profession.apply("craftsman"),j,RANDOM);}
    private static void currency(JsonObject j){EquipmentChanges.prepareCurrency(Minecraft.getInstance().player,j,RANDOM);}
    private static void initialize(JsonObject j){
        var player=Minecraft.getInstance().player;String area=j.get("area").getAsString();int index=j.get("slot").getAsInt();var source=InventoryEdits.source(player,area,index);ItemStack generated;
        var gear=EquipmentItems.gear(source);
        if(gear!=null&&gear.creativeTemplate)generated=CreativeEquipment.materialize(source,RANDOM);
        else if(!source.isEmpty()&&ItemData.own(source)&&EquipmentItems.GEAR_IDS.contains(ItemData.id(source))&&EquipmentItems.serialized(source).isEmpty()){
            String id=ItemData.id(source);Slot slot=Arrays.stream(Slot.values()).filter(s->s.item().equals(id)).findFirst().orElse(Slot.WEAPON);Weapon weapon=Arrays.stream(Weapon.values()).filter(w->w.name().equalsIgnoreCase(id)).findFirst().orElse(Weapon.KATANA);
            if(slot==Slot.WEAPON&&!Weapon.standard().contains(weapon)||progress.get().level<slot.minimum())throw new IllegalArgumentException("该装备尚不能初始化");generated=source.copy();EquipmentItems.write(generated,EquipmentTemplates.createStandard(slot,weapon,Rarity.COMMON,progress.get().level,RANDOM));
        }else{j.add("inventoryChanges",new JsonArray());return;}
        var edits=new JsonArray();edits.add(InventoryEdits.change(player,area,index,source,generated));j.add("inventoryChanges",edits);
    }
    public static void tick(){
        var player=Minecraft.getInstance().player;if(player==null||!ClientUi.current().ready()){initializing.clear();return;}
        for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);String key="inventory/"+i;var view=EquipmentItems.view(stack);boolean needed=view!=null&&view.creativeTemplate()||!stack.isEmpty()&&ItemData.own(stack)&&EquipmentItems.GEAR_IDS.contains(ItemData.id(stack))&&EquipmentItems.serialized(stack).isEmpty();
            if(!needed){initializing.remove(key);continue;}String source=InventoryEdits.encode(player,stack)+"/"+progress.get().level+"/"+EquipmentDefinitions.current().revision;if(source.equals(initializing.get(key)))continue;initializing.put(key,source);var j=ClientUi.current().operation("initializeEquipment");j.addProperty("area","inventory");j.addProperty("slot",i);ClientUi.current().send("initializeEquipment",j);
        }
    }
}
