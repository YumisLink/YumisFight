package cn.yumis.fight.verification;
import cn.yumis.fight.bootstrap.ApplicationJson;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Observes packets only in explicitly enabled verification clients. */
public final class FeedbackProbe {
    private static final Map<String,Integer> damage=new HashMap<>(),direction=new HashMap<>();
    public static String weapon="";
    public static double attack;
    public static JsonObject lastReport;
    private FeedbackProbe(){}
    public static void damage(LivingEntity entity){if(Boolean.getBoolean("ydnf.verify")&&entity.level().isClientSide())damage.merge(entity.getStringUUID(),1,Integer::sum);}
    public static void direction(LivingEntity entity){if(Boolean.getBoolean("ydnf.verify")&&entity.level().isClientSide())direction.merge(entity.getStringUUID(),1,Integer::sum);}
    public static JsonObject snapshot(){
        JsonObject data=new JsonObject();data.add("damage",cn.yumis.fight.bootstrap.ApplicationJson.JSON.toJsonTree(damage));data.add("direction",cn.yumis.fight.bootstrap.ApplicationJson.JSON.toJsonTree(direction));data.addProperty("weapon",weapon);data.addProperty("attack",attack);if(lastReport!=null)data.add("lastReport",lastReport.deepCopy());return data;
    }
    public static void reset(){damage.clear();direction.clear();weapon="";attack=0;lastReport=null;}
}
