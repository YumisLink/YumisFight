package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Cosmetic registered items only: no equipment UUID, affix roll, economy value or transferable instance. */
public final class EquipmentVisualItems {
    /** One entry per existing OBJ mesh; common level aliases must not overweight the five base models. */
    public record WeaponModel(String id,String item,String appearance,String itemModel){}
    private static final List<WeaponModel> WEAPON_MODELS=List.of(
        new WeaponModel("common_katana","katana","katana_50_base","item/expansion/katana_50_base"),
        new WeaponModel("common_lightsaber","lightsaber","lightsaber_50_base","item/expansion/lightsaber_50_base"),
        new WeaponModel("common_greatsword","greatsword","greatsword_50_base","item/expansion/greatsword_50_base"),
        new WeaponModel("common_shortsword","shortsword","shortsword_50_base","item/expansion/shortsword_50_base"),
        new WeaponModel("common_blunt","blunt","blunt_50_base","item/expansion/blunt_50_base"),
        new WeaponModel("skyspine","lightsaber","skyspine","item/named/skyspine"),
        new WeaponModel("bylle","greatsword","bylle","item/named/bylle"),
        new WeaponModel("willy_staff","staff","willy_staff","item/named/willy_staff"),
        new WeaponModel("niwu","club","niwu","item/named/niwu")
    );
    private EquipmentVisualItems(){}
    public static List<WeaponModel> weaponModels(){return WEAPON_MODELS;}
    public static ItemStack weaponModel(String id){
        WeaponModel model=WEAPON_MODELS.stream().filter(candidate->candidate.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("未开放的3D武器外观："+id));
        ItemStack stack=ItemRegistry.item(model.item(),1);ItemData.string(stack,ItemData.NS+"_appearance",model.appearance());return stack;
    }
}
