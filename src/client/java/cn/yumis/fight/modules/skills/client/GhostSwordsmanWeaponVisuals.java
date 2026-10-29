package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.equipment.api.EquipmentVisualItems;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** The nine approved 3D meshes, shared by the owner and observers through the cast seed. */
public final class GhostSwordsmanWeaponVisuals {
    public static final List<EquipmentVisualItems.WeaponModel> WEAPONS=EquipmentVisualItems.weaponModels();
    public record Inclination(float degrees,float azimuth){}
    public static List<Inclination> inclinations(long seed){
        var random=new SplittableRandom(seed^0x2E8F51A7L);List<Inclination> result=new ArrayList<>(24);
        for(int i=0;i<24;i++)result.add(new Inclination((float)random.nextDouble(-30,30),(float)random.nextDouble(360)));
        return List.copyOf(result);
    }
    public static List<EquipmentVisualItems.WeaponModel> chooseModels(long seed){
        var pool=new ArrayList<>(WEAPONS);
        var random=new SplittableRandom(seed^0x137BEA49L);
        for(int i=pool.size()-1;i>0;i--)Collections.swap(pool,i,random.nextInt(i+1));
        List<EquipmentVisualItems.WeaponModel> result=new ArrayList<>(24);
        for(int i=0;i<24;i++)result.add(pool.get(i%pool.size()));
        return List.copyOf(result);
    }
    public static ItemStack[] choose(long seed){
        var models=chooseModels(seed);ItemStack[] result=new ItemStack[24];
        for(int i=0;i<result.length;i++)result[i]=EquipmentVisualItems.weaponModel(models.get(i).id());
        return result;
    }
    private GhostSwordsmanWeaponVisuals(){
    }
}
