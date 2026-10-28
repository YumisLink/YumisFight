package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;

import cn.yumis.fight.modules.equipment.api.EquipmentRules;

import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Supplier;
/** Render-only sword pool, using the reference's seeded selection order. */
public final class GhostSwordsmanWeaponVisuals {
    public static final List<Supplier<ItemStack>> WEAPONS=new ArrayList<>();
    static {
        for(Weapon type:Weapon.values())for(int level:new int[]{
            10,20,30,40,50
        }
        )for(Rarity rarity:List.of(Rarity.COMMON,Rarity.EPIC,Rarity.MYTHIC))WEAPONS.add(()->EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,type,rarity,level,new Random(0))));
    }
    public static ItemStack[] choose(long seed){
        var pool=new ArrayList<>(WEAPONS);
        var random=new SplittableRandom(seed^0x137BEA49L);
        for(int i=pool.size()-1;i>0;i--)Collections.swap(pool,i,random.nextInt(i+1));
        ItemStack[] result=new ItemStack[24];
        for(int i=0;i<24;i++)result[i]=pool.get(i%pool.size()).get().copy();
        return result;
    }
    private GhostSwordsmanWeaponVisuals(){
    }
}
