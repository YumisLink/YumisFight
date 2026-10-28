package cn.yumis.fight.modules.inventory.api;

import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.function.*;
import net.minecraft.world.item.Item;
/** inventory application entry. Dependencies are explicit ports, not the global runtime. */
public final class InventoryItems {
    private InventoryItems(){}
    public static void consume(ServerPlayer p,Item item,int count){
        int total=0;
        for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(item))total+=p.getInventory().getItem(i).getCount();
        if(total<count)throw new IllegalArgumentException("材料不足");
        for(int i=0;i<36&&count>0;i++){
            var stack=p.getInventory().getItem(i);
            if(stack.is(item)){
                int take=Math.min(count,stack.getCount());
                stack.shrink(take);
                count-=take;
            }
        }
    }
}
