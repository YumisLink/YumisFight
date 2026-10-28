package cn.yumis.fight.platform;
import java.util.*;
import java.util.function.BiPredicate;
public final class Permissions {
    private static final Map<String,BiPredicate<UUID,String>> PROVIDERS=new LinkedHashMap<>();
    private Permissions(){
    }
    public static synchronized void register(String id,BiPredicate<UUID,String> provider){
        PROVIDERS.put(id,Objects.requireNonNull(provider));
    }
    public static synchronized void remove(String id){
        PROVIDERS.remove(id);
    }
    public static synchronized boolean allowed(UUID player,String action){
        return PROVIDERS.values().stream().allMatch(p->p.test(player,action));
    }
}
