package cn.yumis.fight.modules.combat.api;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import java.util.*;
/** Owner-side equipment-triggered states, with explicit source, lifetime and stack limits. */
public final class EquipmentBuffs {
    public static final class State {public String source="";public int stacks;public long expires;}
    private EquipmentBuffs(){}
    private static String source(String key,EquipmentEffects.Snapshot effects){return switch(key){case "golden"->effects.goldenSource;case "imprint"->effects.imprintSource;case "wall"->effects.wallSource;default->effects.heroSource;};}
    public static void combat(Runtime runtime,long now){var wall=runtime.equipmentBuffs.get("wall");if(wall!=null&&wall.expires>now&&wall.source.equals(runtime.equipment.wallSource))wall.expires=Math.max(wall.expires,now+15000);}
    public static void wall(Runtime runtime,long now){gain(runtime,"wall",runtime.equipment.wallSource,1000,15000,now);}
    public static boolean prune(Runtime runtime,EquipmentEffects.Snapshot effects,long now,boolean dead){return runtime.equipmentBuffs.entrySet().removeIf(entry->{String source=source(entry.getKey(),effects);return dead||source.isEmpty()||!source.equals(entry.getValue().source)||entry.getValue().expires<=now;});}
    public static void gain(Runtime runtime,String key,String source,int maximum,long duration,long now){
        if(source.isEmpty())return;State state=runtime.equipmentBuffs.computeIfAbsent(key,k->new State());if(!state.source.equals(source)||state.expires<=now)state.stacks=0;state.source=source;state.stacks=Math.min(maximum,state.stacks+1);state.expires=now+duration;
    }
    public static void imprint(Runtime runtime,EquipmentEffects.Snapshot effects,long now){gain(runtime,"imprint",effects.imprintSource,18,20000,now);}
    public static void killed(Runtime runtime,EquipmentEffects.Snapshot effects,String elitePower,long now){gain(runtime,"golden",effects.goldenSource,255,20000,now);if(!effects.heroSource.isEmpty()&&!elitePower.isEmpty())gain(runtime,"elite/"+elitePower,effects.heroSource,1,(long)(effects.heroDuration*1000),now);}
    public static void apply(Stats stats,Runtime runtime,long now){
        for(var entry:runtime.equipmentBuffs.entrySet()){State b=entry.getValue();String source=source(entry.getKey(),runtime.equipment);if(b.expires<=now||b.stacks<=0||source.isEmpty()||!source.equals(b.source))continue;switch(entry.getKey()){
            case "wall"->stats.mut(Stat.PHYSICAL_DEFENSE,.01*b.stacks);
            case "golden"->stats.extra(Stat.DROP_RATE,b.stacks);
            case "imprint"->stats.modifier("final").mut+=.01*b.stacks;
            case "elite/strong"->stats.mut(Stat.HEALTH,.5);
            case "elite/rage"->stats.modifier("final").mut+=.3;
            case "elite/armor"->{stats.mut(Stat.PHYSICAL_DEFENSE,.5);stats.mut(Stat.MAGICAL_DEFENSE,.5);}
            case "elite/elemental"->{for(Stat s:List.of(Stat.LIGHT_RESIST,Stat.FIRE_RESIST,Stat.ICE_RESIST,Stat.DARK_RESIST))stats.extra(s,30);}
            default->{}
        }}
    }
}
