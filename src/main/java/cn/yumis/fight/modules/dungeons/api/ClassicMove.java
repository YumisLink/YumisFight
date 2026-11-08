package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.dungeons.content.*;
import java.util.*;

/** Dungeon content parameters. Animation, warning, contact pulses and recovery use one timeline. */
public record ClassicMove(ClassicEnemyKind actor,String clip,String title,long duration,long cooldown,
                          String shape,double range,double width,double coefficient,boolean magic,
                          int element,String effect,int stunTicks,double[] contacts) {
    public ClassicMove {
        contacts=contacts.clone();
        if(duration<=0||cooldown<duration||range<0||width<=0||coefficient<0||contacts.length==0)
            throw new IllegalArgumentException("Invalid classic dungeon move: "+clip);
        for(double phase:contacts)if(phase<0||phase>1)throw new IllegalArgumentException("Invalid animation contact phase");
    }
    @Override public double[] contacts(){return contacts.clone();}
    public String id(){return actor.id+"/"+clip;}
    public boolean ranged(){return Set.of("DART","BOMB","WAVE").contains(shape);}
    public boolean targetsGround(){return Set.of("ice","thunder","mark","ore_burst","roots").contains(clip);}
    /** Projectile travel/target selection is distinct from a bomb's impact radius. */
    public double engageRange(){return shape.equals("NONE")||shape.equals("BOMB")||targetsGround()?16:range;}
    public long contact(int index){return Math.round(contacts[index]*duration);}
    public long windup(){return Math.round(duration*.30);}
    private static final Map<ClassicEnemyKind,List<ClassicMove>> ACTORS=new EnumMap<>(ClassicEnemyKind.class);
    private static final Map<String,ClassicMove> IDS=new HashMap<>();
    static {
        for(var set:List.of(MoonlightTavern.moves(),Hamelin.moves(),PursuitAnnihilation.moves(),SeaTrain.moves(),
                           TimeGate.moves(),MysteriousAwareness.moves(),Grandine.moves(),WitchForest.moves())){
            for(var entry:set.entrySet()){
                if(ACTORS.put(entry.getKey(),List.copyOf(entry.getValue()))!=null)throw new IllegalStateException("Duplicate classic enemy content");
                for(var move:entry.getValue())if(IDS.put(move.id(),move)!=null)throw new IllegalStateException("Duplicate classic move identity");
            }
        }
    }
    public static List<ClassicMove> forActor(ClassicEnemyKind kind){return ACTORS.getOrDefault(kind,List.of());}
    public static ClassicMove find(String id){return IDS.get(id);}
    public static ClassicMove find(ClassicEnemyKind actor,String clip){return actor==null?null:find(actor.id+"/"+clip);}
}
