package cn.yumis.fight.modules.equipment.platform;

import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;

import com.google.gson.*;

import net.minecraft.world.item.*;

import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.registries.*;
import java.util.*;
import java.io.*;
import static cn.yumis.fight.platform.ItemRegistry.*;
import static cn.yumis.fight.platform.ItemData.NS;
import static cn.yumis.fight.modules.equipment.api.EquipmentItems.*;

/** Equipment and crafting-material registration owned by equipment. */
public final class EquipmentContent {
    private EquipmentContent(){}
    static {
        for(Weapon w:Weapon.values()){
            String id=w.name().toLowerCase(Locale.ROOT);
            ALL.put(id,ITEMS.registerItem(id,WeaponItem::new,p->p.stacksTo(1)));
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
        ALL.put("crafting_material",ITEMS.registerSimpleItem("crafting_material"));
        try(InputStream in=EquipmentContent.class.getResourceAsStream("/assets/"+NS+"/equipment_catalog.json")){
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
    }    public static void register(){ /* class initialization registers content */ }
}
