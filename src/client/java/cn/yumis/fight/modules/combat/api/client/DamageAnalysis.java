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
    public record Ticket(long round,Source source,Kind kind){}
    public record Row(Source source,double damage,long hits){}
    public record View(boolean recording,String reason,Kind kind,double seconds,double total,List<Row> rows){}
    private record Effect(Source source,long expires){}
    private static final Map<String,Row> ROWS=new LinkedHashMap<>();
    private static final Map<String,Effect> EFFECTS=new LinkedHashMap<>();
    private static long round,started,stopped;
    private static boolean recording,initialized;
    private static String session="",dimension="",instance="",reason="尚未开始";
    private static Kind kind;
    private DamageAnalysis(){}
    private static long now(){return net.minecraft.util.Util.getMillis();}
    public static boolean recording(){return recording;}
    public static void toggle(){
        if(recording){stop("手动停止");return;}
        round++;ROWS.clear();kind=null;started=now();stopped=0;recording=true;reason="记录中";
    }
    public static void stop(String why){if(recording){recording=false;stopped=now();reason=why;}}
    public static void clear(){round++;ROWS.clear();EFFECTS.clear();recording=false;started=stopped=0;kind=null;reason="尚未开始";initialized=false;}
    /** Resource/profile confirmations are not scene changes. Life generations are intentionally excluded. */
    public static boolean lifecycle(String nextSession,String nextDimension,String nextInstance,boolean dead){
        boolean changed=initialized&&(!session.equals(nextSession)||!dimension.equals(nextDimension)||!instance.equals(nextInstance));
        if(changed)clear();
        session=nextSession;dimension=nextDimension;instance=nextInstance;initialized=true;
        if(dead)stop("死亡，已停止");
        return changed;
    }
    public static Ticket submit(Source source,Kind eventKind){return recording?new Ticket(round,source,eventKind):null;}
    public static boolean current(Ticket ticket){return ticket!=null&&ticket.round==round;}
    public static void accepted(Ticket ticket,double damage){
        if(ticket==null||ticket.round!=round||!Double.isFinite(damage)||damage<=0)return;
        if(kind==null)kind=ticket.kind;
        if(kind!=ticket.kind){stop("实战与沙袋切换，已停止");return;}
        Row old=ROWS.get(ticket.source.id);
        ROWS.put(ticket.source.id,new Row(ticket.source,(old==null?0:old.damage)+damage,(old==null?0:old.hits)+1));
    }
    public static void training(Source source,double damage){accepted(submit(source,Kind.TRAINING),damage);}
    public static View view(){
        var rows=new ArrayList<>(ROWS.values());rows.sort(Comparator.comparingDouble(Row::damage).reversed().thenComparing(row->row.source.id));
        double seconds=started==0?0:Math.max(0,((recording?now():stopped)-started)/1000d);
        return new View(recording,reason,kind,seconds,rows.stream().mapToDouble(Row::damage).sum(),List.copyOf(rows));
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
