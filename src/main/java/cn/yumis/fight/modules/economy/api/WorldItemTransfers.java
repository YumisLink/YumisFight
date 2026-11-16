package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.modules.inventory.api.PersistentContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.ToIntFunction;

/** Public, unattended transfers between a persistent container and public world items. */
public final class WorldItemTransfers {
    public record Emission(ItemStack stack,Vec3 position,Vec3 velocity) {
        public Emission {stack=stack.copy();}
    }
    public interface Port {
        int collect(ItemEntity source,PersistentContainer target,ToIntFunction<ItemStack> receive);
        void spill(PersistentContainer source,String operation,List<Emission> emissions,Runnable debit,boolean retiring);
    }
    private static Port port;
    private WorldItemTransfers(){}
    public static void install(Port value){port=value;}
    public static void clear(){port=null;}
    public static boolean ready(){return port!=null;}
    public static int collect(ItemEntity source,PersistentContainer target,ToIntFunction<ItemStack> receive){
        if(port==null)return 0;return port.collect(source,target,receive);
    }
    public static void spill(PersistentContainer source,String operation,List<Emission> emissions,Runnable debit,boolean retiring){
        if(port==null)throw new IllegalStateException("世界物品接收尚未就绪");
        port.spill(source,operation,List.copyOf(emissions),debit,retiring);
    }
}
