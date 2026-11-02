package cn.yumis.fight.modules.industry.client;

import com.google.gson.JsonObject;
import java.util.*;

/** Display interpolation follows confirmed amounts and never changes transferable inventory. */
final class IndustrialFluidDisplay {
    static final class Scalar {
        private String identity="";private long target,at;private double from;private boolean initialized;
        double sample(long next,String type){long now=System.nanoTime();if(!initialized||!identity.equals(type)){initialized=true;identity=type;from=target=next;at=now;}else if(next!=target){from=value(now);target=next;at=now;}return value(now);}
        private double value(long now){double t=Math.clamp((now-at)/250_000_000d,0,1);return t>=1?target:from+(target-from)*t;}
    }
    private final Map<String,Scalar> values=new HashMap<>();
    long amount(JsonObject info,String key){long amount=info.has(key)?info.get(key).getAsLong():0;String identity=(info.has("line_index")?info.get("line_index").getAsString():"0")+"/"+(info.has(key+"_id")?info.get(key+"_id").getAsString():"");return Math.max(0,Math.round(values.computeIfAbsent(key,k->new Scalar()).sample(amount,identity)));}
}
