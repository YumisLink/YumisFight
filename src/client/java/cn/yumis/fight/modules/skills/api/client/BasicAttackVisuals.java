package cn.yumis.fight.modules.skills.api.client;

import cn.yumis.fight.modules.skills.client.SkillVisualRegistry;
import com.google.gson.JsonObject;
import java.util.*;

/** Weapon-specific cosmetic effects for basic attacks. */
public final class BasicAttackVisuals {
    @FunctionalInterface public interface Style {void decorate(JsonObject visual,int slash,double interval);}
    private record Entry(boolean wholeCycle,boolean multiSlash,Style style){}
    private static final Map<String,Entry> STYLES=new LinkedHashMap<>();
    private BasicAttackVisuals(){}
    public static void register(String weapon,boolean wholeCycle,Style style){register(weapon,wholeCycle,wholeCycle,style);}
    public static void register(String weapon,boolean wholeCycle,boolean multiSlash,Style style){if(STYLES.putIfAbsent(weapon,new Entry(wholeCycle,multiSlash,Objects.requireNonNull(style)))!=null)throw new IllegalArgumentException("Duplicate basic attack visual: "+weapon);}
    public static boolean wholeCycle(String weapon){return wholeCycle(weapon,1);}
    public static boolean wholeCycle(String weapon,int slashes){SkillVisualRegistry.handler("ATTACK");return wholeCycleRegistered(weapon,slashes);}
    static boolean wholeCycleRegistered(String weapon,int slashes){var entry=STYLES.get(weapon);return entry!=null&&entry.wholeCycle()&&(slashes<=1||entry.multiSlash());}
    public static long slashDuration(double interval,int slashes,boolean sweep){
        double slot=interval/Math.max(1,slashes);
        return sweep?Math.clamp(Math.round(slot*.38),320L,440L):Math.clamp(Math.round(210+slot*.42),260L,360L);
    }
    public static void apply(String weapon,JsonObject visual,int slash,double interval){
        // Initialize the registered presentation content before the first attack.
        SkillVisualRegistry.handler("ATTACK");
        var entry=STYLES.get(weapon);
        if(entry!=null)entry.style().decorate(visual,slash,interval);
    }
}
