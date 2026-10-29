package cn.yumis.fight.modules.combat.api.client;

import com.google.gson.JsonObject;
import java.util.*;

/** Local analysis rounds consume confirmed outcomes; this state never participates in combat authority. */
public final class DamageAnalysis {
    public static final String LOCAL_SOURCE="_analysisSource";
    public enum Kind { COMBAT, TRAINING }
    public record Source(String id,String title) {
        public Source {Objects.requireNonNull(id);Objects.requireNonNull(title);}
    }
    public static final Source BASIC=new Source("yumis_dungeon_and_fight:basic_attack","普通攻击");
    public static final Source OTHER=new Source("yumis_dungeon_and_fight:other_damage","其他伤害");
    public record Ticket(long round,Source source,Kind kind,long occurredAt,long delayMillis){}
    public record Row(Source source,double damage,long hits){}
    public record View(boolean recording,String reason,Kind kind,double seconds,double total,List<Row> rows){}
    private record Effect(Source source,long expires){}
    private static final Map<String,Row> ROWS=new LinkedHashMap<>();
    private static final Map<String,Effect> EFFECTS=new LinkedHashMap<>();
    private static long round,started,stopped;
    private static boolean recording,initialized;
    private static String session="",dimension="",instance="",reason="尚未开始";
    private static Kind kind;
    private static final List<long[]> INTERVALS=new ArrayList<>();
    private DamageAnalysis(){}
    private static long now(){return net.minecraft.util.Util.getMillis();}
    public static boolean recording(){return recording;}
    public static void toggle(long combatUntil){
        if(recording){stop("手动停止");return;}
        round++;ROWS.clear();INTERVALS.clear();kind=null;started=now();stopped=0;recording=true;reason="等待交战";
        if(combatUntil>started){kind=Kind.COMBAT;INTERVALS.add(new long[]{started,combatUntil});}
    }
    public static void stop(String why){if(recording){recording=false;stopped=now();reason=why;}}
    public static void clear(){round++;ROWS.clear();INTERVALS.clear();EFFECTS.clear();recording=false;started=stopped=0;kind=null;reason="尚未开始";initialized=false;}
    /** Resource/profile confirmations are not scene changes. Life generations are intentionally excluded. */
    public static boolean lifecycle(String nextSession,String nextDimension,String nextInstance,boolean dead){
        boolean changed=initialized&&(!session.equals(nextSession)||!dimension.equals(nextDimension)||!instance.equals(nextInstance));
        if(changed)clear();
        session=nextSession;dimension=nextDimension;instance=nextInstance;initialized=true;
        if(dead)stop("死亡，已停止");
        return changed;
    }
    public static Ticket submit(Source source,Kind eventKind,long occurredAt,cn.yumis.fight.modules.combat.api.CombatSettings settings){return recording?new Ticket(round,source,eventKind,occurredAt,settings.delayMillis()):null;}
    public static void combat(Ticket ticket){
        if(ticket==null||ticket.round!=round)return;
        if(kind==null)kind=ticket.kind;if(kind!=ticket.kind){stop("实战与沙袋切换，已停止");return;}
        long start=Math.max(started,ticket.occurredAt),end=ticket.occurredAt+ticket.delayMillis;if(end<=start)return;
        INTERVALS.add(new long[]{start,end});INTERVALS.sort(Comparator.comparingLong(span->span[0]));
        for(int i=1;i<INTERVALS.size();)if(INTERVALS.get(i-1)[1]>=INTERVALS.get(i)[0]){INTERVALS.get(i-1)[1]=Math.max(INTERVALS.get(i-1)[1],INTERVALS.remove(i)[1]);}else i++;
    }
    public static boolean current(Ticket ticket){return ticket!=null&&ticket.round==round;}
    public static void accepted(Ticket ticket,double damage){
        if(ticket==null||ticket.round!=round||!Double.isFinite(damage)||damage<=0)return;
        if(kind==null)kind=ticket.kind;
        if(kind!=ticket.kind){stop("实战与沙袋切换，已停止");return;}
        combat(ticket);
        Row old=ROWS.get(ticket.source.id);
        ROWS.put(ticket.source.id,new Row(ticket.source,(old==null?0:old.damage)+damage,(old==null?0:old.hits)+1));
    }
    public static void training(Source source,double damage,cn.yumis.fight.modules.combat.api.CombatSettings settings){accepted(submit(source,Kind.TRAINING,now(),settings),damage);}
    public static View view(){
        var rows=new ArrayList<>(ROWS.values());rows.sort(Comparator.comparingDouble(Row::damage).reversed().thenComparing(row->row.source.id));
        long until=recording?now():stopped;double seconds=INTERVALS.stream().mapToLong(span->Math.max(0,Math.min(until,span[1])-span[0])).sum()/1000d;
        String status=recording?(INTERVALS.isEmpty()?"等待交战":INTERVALS.getLast()[1]<=now()?"脱战暂停":"记录中"):reason;
        return new View(recording,status,kind,seconds,rows.stream().mapToDouble(Row::damage).sum(),List.copyOf(rows));
    }
    public static void attach(JsonObject extras,Source source){JsonObject value=new JsonObject();value.addProperty("id",source.id);value.addProperty("title",source.title);extras.add(LOCAL_SOURCE,value);}
    public static Source source(JsonObject extras){
        if(extras==null||!extras.has(LOCAL_SOURCE))return OTHER;
        var value=extras.getAsJsonObject(LOCAL_SOURCE);return new Source(value.get("id").getAsString(),value.get("title").getAsString());
    }
    public static void rememberEffect(String id,Source source,long duration){
        EFFECTS.values().removeIf(effect->effect.expires<now());EFFECTS.put(id,new Effect(source,now()+duration));
        while(EFFECTS.size()>4096)EFFECTS.remove(EFFECTS.keySet().iterator().next());
    }
    public static Source effect(String id){var value=EFFECTS.get(id);return value!=null&&value.expires>=now()?value.source:OTHER;}
}
