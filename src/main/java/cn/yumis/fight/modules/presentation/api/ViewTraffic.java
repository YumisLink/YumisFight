package cn.yumis.fight.modules.presentation.api;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Application payload measurements, before platform compression. */
public final class ViewTraffic {
    private static final Map<String,long[]> COUNTERS=new LinkedHashMap<>();
    private ViewTraffic(){}
    public static synchronized void record(String direction,String action,String json){long[] value=COUNTERS.computeIfAbsent(direction+"/"+action,k->new long[2]);value[0]++;value[1]+=json.getBytes(StandardCharsets.UTF_8).length;}
    public static synchronized Map<String,List<Long>> metrics(){Map<String,List<Long>> copy=new LinkedHashMap<>();COUNTERS.forEach((k,v)->copy.put(k,List.of(v[0],v[1])));return Map.copyOf(copy);}
    public static synchronized void clear(){COUNTERS.clear();}
}
