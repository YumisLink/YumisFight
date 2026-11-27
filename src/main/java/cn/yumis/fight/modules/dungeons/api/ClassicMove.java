package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.dungeons.content.*;
import java.util.*;

/** Dungeon content parameters. Animation, warning, contact pulses and recovery use one timeline. */
public record ClassicMove(ClassicEnemyKind actor,String clip,String title,long duration,long cooldown,
                          String shape,double range,double width,double coefficient,boolean magic,
                          int element,String effect,int stunTicks,double[] contacts) {
    public enum PullMode { NONE, INHALE, CAGE }

    private static final Set<String> GROUND_CLIPS = Set.of("ice","thunder","mark","ore_burst","roots");
    private static final Set<String> ARC_LEAP_CLIPS = Set.of("jump","approach_hop","transform_pounce");
    private static final Set<String> RANGED_SHAPES = Set.of("DART","BOMB","WAVE");

    public ClassicMove {
        contacts=contacts.clone();
        if(duration<=0||cooldown<duration||range<0||width<=0||coefficient<0||contacts.length==0)
            throw new IllegalArgumentException("Invalid classic dungeon move: "+clip);
        for(double phase:contacts)if(phase<0||phase>1)throw new IllegalArgumentException("Invalid animation contact phase");
    }
    @Override public double[] contacts(){return contacts.clone();}
    public String id(){return actor.id+"/"+clip;}
    public boolean ranged(){return RANGED_SHAPES.contains(shape);}
    public boolean targetsGround(){return GROUND_CLIPS.contains(clip);}
    public boolean isArcLeap(){return "DASH".equals(shape)&&ARC_LEAP_CLIPS.contains(clip);}
    public boolean isPlayerLocked(){return "mark".equals(clip);}
    public boolean isSelfDestruct(){return "explode".equals(clip);}
    public boolean isBurrow(){return "burrow".equals(effect);}
    public PullMode pullMode(){
        if("tree_cage".equals(clip))return PullMode.CAGE;
        if("inhale".equals(clip))return PullMode.INHALE;
        return PullMode.NONE;
    }
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
    private static ClassicMove applyBalance(ClassicMove base){
        if(base==null)return null;
        var data=EnemyBalanceRules.current().classicMove(base.id());
        if(data==null)return base;
        return new ClassicMove(base.actor,base.clip,data.title(),data.duration(),data.cooldown(),data.shape(),
            data.range(),data.width(),data.coefficient(),data.magic(),data.element(),data.effect(),data.stunTicks(),data.contacts());
    }
    public static List<ClassicMove> forActor(ClassicEnemyKind kind){
        var base=ACTORS.getOrDefault(kind,List.of());
        if(base.isEmpty())return base;
        var out=new ArrayList<ClassicMove>(base.size());
        for(var m:base)out.add(applyBalance(m));
        return List.copyOf(out);
    }
    public static ClassicMove find(String id){return applyBalance(IDS.get(id));}
    public static ClassicMove find(ClassicEnemyKind actor,String clip){return actor==null?null:find(actor.id+"/"+clip);}
}
