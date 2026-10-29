package cn.yumis.fight.modules.dungeons.api;
import cn.yumis.fight.modules.combat.api.CombatTiming;
import java.util.*;

/** Canonical damage stays independent of the observer's synchronized level. */
public final class DamageMeter {
    public Map<String,Entry> players=new LinkedHashMap<>();
    public long frozenAt;
    public static final class Entry {
        public String name="";
        public Map<Integer,Double> damage=new TreeMap<>();
        public List<long[]> combat=new ArrayList<>();
        public void event(long time,long delayMillis){
            combat.add(new long[]{time,time+delayMillis});
            combat.sort(Comparator.comparingLong(a->a[0]));
            List<long[]> merged=new ArrayList<>();
            for(long[] span:combat){if(merged.isEmpty()||merged.getLast()[1]<span[0])merged.add(span.clone());else merged.getLast()[1]=Math.max(merged.getLast()[1],span[1]);}
            combat=merged;
        }
        public long elapsed(long now){long total=0;for(long[] span:combat)total+=Math.max(0,Math.min(now,span[1])-span[0]);return total;}
        public double canonical(){return damage.values().stream().mapToDouble(Double::doubleValue).sum();}
        public double displayed(int viewerLevel,boolean sync){double value=0;for(var bucket:damage.entrySet())value+=bucket.getValue()*(sync?(Math.max(bucket.getKey(),viewerLevel)+1d)/(bucket.getKey()+1d):1);return value;}
    }
}
