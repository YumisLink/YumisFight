package cn.yumis.fight.modules.equipment.api;

import static cn.yumis.fight.modules.equipment.api.EquipmentJson.JSON;

import com.google.gson.*;
import net.minecraft.core.component.DataComponents;

import net.minecraft.world.item.*;

import net.neoforged.neoforge.registries.*;
import java.util.*;
import java.io.*;
import static cn.yumis.fight.platform.ItemData.*;
import static cn.yumis.fight.platform.ItemRegistry.item;
import cn.yumis.fight.modules.presentation.api.ModText;
/** Equipment serialization, identity and material payloads. */
public final class EquipmentItems {
    public static final Map<String,String> NAMES=new HashMap<>();
    public static final Set<String> GEAR_IDS=new HashSet<>();
    private EquipmentItems(){}
    public static Gear gear(ItemStack stack){
        String s=string(stack,NS);
        if(s.isEmpty())return null;
        try{
            return cn.yumis.fight.modules.equipment.api.EquipmentResolution.read(s);
        }
        catch(RuntimeException e){
            return null;
        }
    }
    public static ItemStack gear(Gear g){
        ItemStack stack=item(g.slot==Slot.WEAPON?g.weapon.name().toLowerCase(Locale.ROOT):g.slot.item(),1);
        write(stack,g);
        return stack;
    }
    public static String name(Gear g){
        if(!g.savedName.isEmpty())return g.savedName;
        if(MageBlood.is(g))return MageBlood.NAME;
        String base=NAMES.getOrDefault(EquipmentTemplates.appearance(g),g.slot==Slot.WEAPON?g.weapon.title():g.slot.title());
        if(g.slot.armor()&&EquipmentTemplates.armor(g)!=ArmorType.UNTYPED)return EquipmentTemplates.armor(g).title()+g.slot.title();
        if(g.slot.jewelry()&&EquipmentTemplates.jewelry(g)!=JewelryType.UNTYPED)return EquipmentTemplates.jewelry(g).title()+"之"+base;
        return base;
    }
    public static void write(ItemStack stack,Gear g){
        if(g.savedName.isEmpty())g.savedName=name(g);
        string(stack,NS,JSON.toJson(g));
        stack.set(DataComponents.ITEM_NAME,ModText.of(name(g)).withStyle(s->s.withColor(g.rarity.color()).withItalic(false)));
    }
    public static ItemStack material(String id,int count){
        MaterialCatalog.Material m=MaterialCatalog.current().materials.get(id);
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
        string(s,NS+"_payload_modules_v1",JSON.toJson(value));
        s.set(DataComponents.ITEM_NAME,ModText.of(title));
        return s;
    }
    public static String payload(ItemStack stack){
        return string(stack,NS+"_payload_modules_v1");
    }
}
