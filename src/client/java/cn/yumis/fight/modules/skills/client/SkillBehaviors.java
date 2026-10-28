package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.SkillPresentation;
import cn.yumis.fight.modules.skills.api.SkillCast;
import cn.yumis.fight.modules.skills.api.SkillSchedule;
import cn.yumis.fight.modules.skills.api.SkillDefinition;
import cn.yumis.fight.modules.skills.api.SkillCatalog;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.attributes.api.Stats;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** SkillDefinition behavior registry. Combat provides geometry and lifecycle ports; it does not select skill content. */
public final class SkillBehaviors {
    public interface Context {
        LocalPlayer player();
        void buff(String name,long expires);
        void absorption(String name,double value);
        void visual(SkillCast cast,String phase);
        void slash(SkillCast cast,double length,double width,double height,double coefficient,double knockback,boolean fan);
        void wave(Vec3 origin,Vec3 direction,double range,double width,double height,Stats stats,SkillDefinition skill,double coefficient,boolean passive,boolean ground);
        void area(SkillCast cast,double radius,double coefficient,boolean inward);
        void passive(SkillCast cast);
        boolean hit(SkillCast cast,net.minecraft.world.entity.LivingEntity target,double coefficient);
        void cancel(boolean normal);
    }
    @FunctionalInterface public interface Behavior {void tick(Context context,SkillCast cast,long now,long elapsed,long stage,double speed);}
    @FunctionalInterface public interface Starter {void start(Context context,SkillCast cast,long now);}
    private static final Map<String,Starter> STARTERS=new LinkedHashMap<>();
    public static void registerStart(String id,Starter starter){if(STARTERS.putIfAbsent(id,Objects.requireNonNull(starter))!=null)throw new IllegalArgumentException("Duplicate skill start: "+id);}
    public static void start(Context context,SkillCast cast,long now){var starter=STARTERS.get(cast.skill.id);if(starter!=null)starter.start(context,cast,now);}
    private static final Map<String,Behavior> BEHAVIORS=new LinkedHashMap<>();
    static {
        registerStart(SkillCatalog.OVERDRIVE.id,(x,c,now)->{x.buff("yumis_dungeon_and_fight:overdrive",now+30000);x.absorption("overdrivePower",.15*Math.pow(1.12,c.parameters.rank()-1));});
        register(List.of(SkillCatalog.GHOST),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                if(c.release||elapsed>=3000){
                    x.visual(c,"RELEASE");
                    double charge=1+Math.min(1,elapsed/1000d);
                    x.slash(c,c.parameters.get("range",3),3,2,c.parameters.get("damage",1.6)*charge+(elapsed>=1000?c.parameters.get("soulBonus",0):0),.4,false);
                    x.cancel(true);
                }
            
        });
        register(List.of(SkillCatalog.TRIPLE,SkillCatalog.DRAGON),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                double duration=c.skill==SkillCatalog.TRIPLE?.2:.25,length=c.skill==SkillCatalog.TRIPLE?2.5:4;
                x.buff("movementProtection",now+500);
                if(stage<=duration*1000||c.moved<length){
                    if(now-c.visualTrail>=100){
                        x.visual(c,"TRAIL");
                        c.visualTrail=now;
                    }
                    Vec3 before=p.position();
                    double desired=length*Math.min(1,stage/(duration*1000));double move=Math.max(0,desired-c.moved);p.move(MoverType.SELF,c.direction.scale(move));c.moved+=move;
                    x.slash(c,1.5,2,2,c.parameters.get("damage",.8),.4,false);
                    if(move>0&&p.position().distanceToSqr(before)<.00001&&stage>50){c.stageStart=now-(long)(duration*1000/speed);c.moved=length;}
                }
                if(stage>=duration*1000&&c.hit.size()>=0&&c.slashes<c.segment){
                    c.slashes=c.segment;
                    if(c.stats.weaponType.equals("SHORTSWORD")&&c.skill==SkillCatalog.TRIPLE)x.passive(c);
                }
                if(now>c.window||c.segment>=c.parameters.get("slashes",3)&&stage>duration*1000)x.cancel(true);
            
        });
        register(List.of(SkillCatalog.WAVE),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                x.wave(p.position().add(0,.15,0),c.direction,c.parameters.get("range",10),2,1.5,c.stats,c.skill,c.parameters.get("damage",2),false,true);
                x.cancel(true);
            
        });
        register(List.of(SkillCatalog.OVERDRIVE),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                if(elapsed>=150)x.cancel(true);
            
        });
        register(List.of(SkillCatalog.RISING),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                if(now-c.visualTrail>=100){
                    x.visual(c,"TRAIL");
                    c.visualTrail=now;
                }
                x.buff("movementProtection",now+500);
                if(!c.finisher){double desired=4*Math.min(1,elapsed/300d);double move=Math.max(0,desired-c.moved);p.move(MoverType.SELF,c.direction.scale(move));c.moved+=move;}
                if(elapsed<300){
                    x.slash(c,1.5,2,2,c.parameters.get("damage",1.6),.5,false);
                }
                else if(!c.finisher){
                    c.finisher=true;
                    x.visual(c,"RELEASE");
                    c.hit.clear();
                    x.slash(c,2,2,3,c.parameters.get("finisher",2.4),.6,false);
                }
                if(elapsed>=700)x.cancel(true);
            
        });
        register(List.of(SkillCatalog.DRAW),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                if(elapsed>=400&&!c.finisher){
                    c.finisher=true;
                    x.visual(c,"RELEASE");
                    x.slash(c,c.parameters.get("range",5),10,3,c.parameters.get("damage",6.5),.4,true);
                    if(c.stats.weaponType.equals("SHORTSWORD"))x.passive(c);
                }
                if(elapsed>=800)x.cancel(true);
            
        });
        register(List.of(SkillCatalog.DANCE),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                SkillSchedule schedule=SkillSchedule.dance(c.parameters);
                while(c.slashes<schedule.due(elapsed)){
                    x.visual(c,"HIT");
                    c.hit.clear();
                    x.slash(c,c.parameters.get("range",4),3,2,c.parameters.get("damage",.7),.04,false);
                    if(c.stats.weaponType.equals("SHORTSWORD"))x.passive(c);
                    c.slashes++;
                }
                if(elapsed>=schedule.durationMillis()){
                    x.wave(p.position().add(0,.8,0),c.direction,8,3,2,c.stats,c.skill,c.parameters.get("finisher",5),false,false);
                    x.cancel(true);
                }
            
        });
        register(List.of(SkillCatalog.STORM),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                p.setPos(c.origin);
                p.setDeltaMovement(Vec3.ZERO);
                int total=(int)c.parameters.get("slashes",24);
                double duration=c.parameters.get("duration",8)*1000;
                int due=Math.min(total,Math.max(0,(int)((elapsed-duration*.125)/(duration*.75/total))+1));
                while(c.slashes<due){
                    c.hit.clear();
                    x.area(c,c.parameters.get("range",5),c.parameters.get("damage",1),true);
                    c.slashes++;
                }
                if(elapsed>=c.parameters.get("duration",8)*900&&!c.finisher){
                    c.finisher=true;
                    x.visual(c,"FINISH");
                    c.hit.clear();
                    x.area(c,c.parameters.get("range",5),c.parameters.get("finisher",8),true);
                }
                if(elapsed>=c.parameters.get("duration",8)*1000)x.cancel(true);
            
        });
    }
    static {CareerSkillBehaviors.install();}
    private SkillBehaviors(){}
    private static void register(List<SkillDefinition> skills,Behavior behavior){for(SkillDefinition skill:skills)register(skill.id,behavior);}
    public static void register(String id,Behavior behavior){if(BEHAVIORS.putIfAbsent(id,Objects.requireNonNull(behavior))!=null)throw new IllegalArgumentException("Duplicate skill behavior: "+id);}
    public static void advance(Context context,SkillCast cast,long now){
        var behavior=BEHAVIORS.get(cast.skill.id);
        if(behavior==null){context.cancel(true);return;}
        double speed=SkillPresentation.fixedActionTime(cast.skill)?1:cast.speed;
        behavior.tick(context,cast,now,(long)((now-cast.start)*speed),(long)((now-cast.stageStart)*speed),speed);
    }
}
