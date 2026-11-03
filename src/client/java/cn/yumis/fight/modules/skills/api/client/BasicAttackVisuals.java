package cn.yumis.fight.modules.skills.api.client;

import cn.yumis.fight.modules.skills.client.SkillVisualRegistry;
import com.google.gson.JsonObject;
import java.util.*;

/** Content-owned cosmetic styles. Applying a style replaces only the vanilla hand swing. */
public final class BasicAttackVisuals {
    @FunctionalInterface public interface Style {void decorate(JsonObject visual,int slash,double interval);}
    private static final Map<String,Style> STYLES=new LinkedHashMap<>();
    private BasicAttackVisuals(){}
    public static void register(String weapon,Style style){if(STYLES.putIfAbsent(weapon,Objects.requireNonNull(style))!=null)throw new IllegalArgumentException("Duplicate basic attack visual: "+weapon);}
    public static boolean apply(String weapon,JsonObject visual,int slash,double interval){
        // Initialize the registered presentation content before the first attack.
        SkillVisualRegistry.handler("ATTACK");
        var style=STYLES.get(weapon);if(style==null)return false;
        style.decorate(visual,slash,interval);return true;
    }
}
