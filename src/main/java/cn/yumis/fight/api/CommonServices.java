package cn.yumis.fight.api;
import cn.yumis.fight.rules.Models.Stats;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
/** Embedded public services: no companion mod is required. */
public final class CommonServices {
    private static final Map<UUID,AttributeSources> ATTRIBUTES=new ConcurrentHashMap<>();
    private CommonServices(){
    }
    public static AttributeSources attributes(UUID player){
        return ATTRIBUTES.computeIfAbsent(player,id->new AttributeSources());
    }
    public static void clear(UUID player){
        ATTRIBUTES.remove(player);
    }
    public static void apply(UUID player,Stats stats){
        var source=ATTRIBUTES.get(player);
        if(source!=null){
            var next=source.apply(stats.values);
            stats.values.clear();
            stats.values.putAll(next);
        }
    }
}
