package cn.yumis.fight.game;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Rules.Rarity;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;
import com.google.gson.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.registries.*;
import java.util.*;
import java.io.*;
public final class GameItems {
    public static final String NS="yumis_dungeon_and_fight";
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(NS);
    public static final Map<String,DeferredItem<Item>> ALL=new LinkedHashMap<>();
    public static final Map<String,String> NAMES=new HashMap<>();
    public static final Set<String> GEAR_IDS=new HashSet<>();
    static {
        for(Weapon w:Weapon.values()){
            String id=w.name().toLowerCase(Locale.ROOT);
            ALL.put(id,ITEMS.registerItem(id,Item::new,p->p.stacksTo(1)));
            GEAR_IDS.add(id);
        }
        for(Slot s:Slot.values())if(s!=Slot.WEAPON){
            String id=s.item();
            ALL.put(id,ITEMS.registerItem(id,s.armor()?Item::new:CurioGearItem::new,p->{
                p.stacksTo(1);if(s.armor())p.equippable(s==Slot.CHEST?EquipmentSlot.CHEST:s==Slot.HEAD?EquipmentSlot.HEAD:s==Slot.LEGS?EquipmentSlot.LEGS:EquipmentSlot.FEET);return p;
            }
            ));
            GEAR_IDS.add(id);
        }
        for(String id:List.of("transmutation_stone","augmentation_stone","regal_stone","exalted_stone","divine_stone","crafting_material","set_stone","affix_gem","set_extraction_stone","affix_extraction_stone","socket_stone","epic_upgrade_stone","mythic_upgrade_stone"))ALL.put(id,ITEMS.registerSimpleItem(id));
        try(InputStream in=GameItems.class.getResourceAsStream("/assets/"+NS+"/equipment_catalog.json")){
            if(in!=null){
                JsonElement e=JsonParser.parseReader(new InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8));
                JsonArray entries=e.isJsonArray()?e.getAsJsonArray():e.getAsJsonObject().getAsJsonArray("entries");
                if(entries!=null)for(JsonElement x:entries){
                    JsonObject o=x.getAsJsonObject();
                    NAMES.put(o.get("id").getAsString(),o.get("name").getAsString());
                }
            }
        }
        catch(Exception ex){
            throw new ExceptionInInitializerError(ex);
        }
    }
    private GameItems(){
    }
    public static ItemStack item(String id,int count){
        return new ItemStack(ALL.get(id).get(),count);
    }
    public static String string(ItemStack stack,String key){
        return stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag().getStringOr(key,"");
    }
    public static void string(ItemStack stack,String key,String value){
        CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putString(key,value));
    }
    public static Gear gear(ItemStack stack){
        String s=string(stack,NS);
        if(s.isEmpty())return null;
        try{
            return JSON.fromJson(s,Gear.class);
        }
        catch(RuntimeException e){
            return null;
        }
    }
    public static String appearance(Gear g){
        String kind=g.slot==Slot.WEAPON?g.weapon.name().toLowerCase(Locale.ROOT):g.slot.name().toLowerCase(Locale.ROOT);
        int band=g.level<20?10:g.level<30?20:g.level<40?30:g.level<50?40:50;
        String style=g.rarity==Rarity.EPIC?"epic":g.rarity==Rarity.MYTHIC?"mythic":"base";
        return kind+"_"+band+"_"+style;
    }
    public static String name(Gear g){
        return NAMES.getOrDefault(appearance(g),g.slot==Slot.WEAPON?g.weapon.title():g.slot.title());
    }
    public static void write(ItemStack stack,Gear g){
        string(stack,NS,JSON.toJson(g));
        stack.set(DataComponents.ITEM_NAME,ModText.of(name(g)).withStyle(s->s.withColor(g.rarity.color()).withItalic(false)));
    }
    public static ItemStack gear(Gear g){
        ItemStack stack=item(g.slot==Slot.WEAPON?g.weapon.name().toLowerCase(Locale.ROOT):g.slot.item(),1);
        write(stack,g);
        return stack;
    }
    public static ItemStack material(String id,int count){
        Catalog.Material m=Catalog.CURRENT.materials.get(id);
        if(m==null)throw new IllegalArgumentException("未知材料");
        ItemStack s=item("crafting_material",count);
        string(s,NS+"_material",id);
        s.set(DataComponents.ITEM_NAME,ModText.of(m.name()));
        return s;
    }
    public static String material(ItemStack stack){
        return string(stack,NS+"_material");
    }
    public static ItemStack payload(String item,Object value,String title){
        ItemStack s=item(item,1);
        string(s,NS+"_payload",JSON.toJson(value));
        s.set(DataComponents.ITEM_NAME,ModText.of(title));
        return s;
    }
    public static String payload(ItemStack stack){
        return string(stack,NS+"_payload");
    }
    public static boolean own(ItemStack stack){
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(NS);
    }
    public static String id(ItemStack stack){
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }
}
