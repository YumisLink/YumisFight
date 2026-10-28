package cn.yumis.fight.modules.combat.api;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import java.util.*;
/** Committed equipment-triggered states. Uploads cannot manufacture or erase these states. */
public final class EquipmentBuffs {
    public static final class State {public String source="";public int stacks;public long expires;}
    private EquipmentBuffs(){}
    public static boolean prune(Runtime runtime,EquipmentEffects.Snapshot effects,long now,boolean dead){return runtime.equipmentBuffs.entrySet().removeIf(entry->{String source=entry.getKey().equals("golden")?effects.goldenSource:entry.getKey().equals("imprint")?effects.imprintSource:effects.heroSource;return dead||source.isEmpty()||!source.equals(entry.getValue().source)||entry.getValue().expires<=now;});}
    public static void gain(Runtime runtime,String key,String source,int maximum,long duration,long now){
        if(source.isEmpty())return;State state=runtime.equipmentBuffs.computeIfAbsent(key,k->new State());if(!state.source.equals(source)||state.expires<=now)state.stacks=0;state.source=source;state.stacks=Math.min(maximum,state.stacks+1);state.expires=now+duration;
    }
    public static void imprint(Runtime runtime,EquipmentEffects.Snapshot effects,long now){gain(runtime,"imprint",effects.imprintSource,18,20000,now);}
    public static void killed(Runtime runtime,EquipmentEffects.Snapshot effects,String elitePower,long now){gain(runtime,"golden",effects.goldenSource,255,20000,now);if(!effects.heroSource.isEmpty()&&!elitePower.isEmpty())gain(runtime,"elite/"+elitePower,effects.heroSource,1,(long)(effects.heroDuration*1000),now);}
    public static void apply(Stats stats,Runtime runtime,long now){
        for(var entry:runtime.equipmentBuffs.entrySet()){State b=entry.getValue();String source=entry.getKey().equals("golden")?runtime.equipment.goldenSource:entry.getKey().equals("imprint")?runtime.equipment.imprintSource:runtime.equipment.heroSource;if(b.expires<=now||b.stacks<=0||source.isEmpty()||!source.equals(b.source))continue;switch(entry.getKey()){
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
