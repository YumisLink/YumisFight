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
        void path(SkillCast cast,Vec3 from,Vec3 to,double width,double height,double coefficient,double knockback);
        void wave(Vec3 origin,Vec3 direction,double range,double width,double height,Stats stats,SkillDefinition skill,double coefficient,boolean passive,boolean ground);
        void area(SkillCast cast,double radius,double coefficient,boolean inward);
        void passive(SkillCast cast);
        boolean hit(SkillCast cast,net.minecraft.world.entity.LivingEntity target,double coefficient);
        void cancel(boolean normal);
    }
    @FunctionalInterface public interface Behavior {void tick(Context context,SkillCast cast,long now,long elapsed,long stage,double speed);}
    @FunctionalInterface public interface Starter {void start(Context context,SkillCast cast,long now);}
    private static final Map<String,java.util.function.Supplier<Vec3>> PREPARATIONS=new LinkedHashMap<>();
    public static Vec3 prepare(SkillDefinition skill){var preparation=PREPARATIONS.get(skill.id);return preparation==null?null:preparation.get();}
    public static void finish(SkillCast cast){if(cast!=null&&cast.gravityLease){var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null){player.setNoGravity(cast.previousGravity);player.fallDistance=0;}cast.gravityLease=false;}}
    private static final Map<String,Starter> STARTERS=new LinkedHashMap<>();
    public static void registerStart(String id,Starter starter){if(STARTERS.putIfAbsent(id,Objects.requireNonNull(starter))!=null)throw new IllegalArgumentException("Duplicate skill start: "+id);}
    public static void start(Context context,SkillCast cast,long now){var starter=STARTERS.get(cast.skill.id);if(starter!=null)starter.start(context,cast,now);}
    private static final Map<String,Behavior> BEHAVIORS=new LinkedHashMap<>();
    static {
        PREPARATIONS.put(SkillCatalog.BLINK.id,SkillBehaviors::blink);
        registerStart(SkillCatalog.BLINK.id,(x,c,now)->{x.player().setPos(c.center);x.player().fallDistance=0;});
        registerStart(SkillCatalog.STORM.id,(x,c,now)->{c.previousGravity=x.player().isNoGravity();c.gravityLease=true;x.player().setNoGravity(true);x.player().fallDistance=0;});
        registerStart(SkillCatalog.OVERDRIVE.id,(x,c,now)->{x.buff("yumis_dungeon_and_fight:overdrive",now+30000);x.absorption("overdrivePower",.15*Math.pow(1.12,c.parameters.rank()-1));});
        register(List.of(SkillCatalog.GHOST),(x,c,now,elapsed,stage,speed)->{
            var p=x.player();
                if(c.release||elapsed>=3000){
                    x.visual(c,"RELEASE");
                    double charge=1+Math.min(1,elapsed/1000d);
                    double coefficient=c.parameters.get("damage",1.6)*charge+(elapsed>=1000?c.parameters.get("soulBonus",0):0);if(elapsed>=1000)coefficient*=c.parameters.get("ghostFieldMultiplier",1);
                    x.slash(c,c.parameters.get("range",3),3,2,coefficient,.4,false);
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
            c.sweepDegrees=360;
            if(elapsed>=400&&!c.release){c.release=true;c.hitIndex=0;x.slash(c,c.parameters.get("range",5),5,3,c.parameters.get("damage",1),.4,true);x.visual(c,"RELEASE");if(c.stats.weaponType.equals("SHORTSWORD"))x.passive(c);}
            boolean light=Set.of("LIGHTSABER","KATANA").contains(c.stats.weaponType)&&SkillLearning.effective(c.stats,cn.yumis.fight.modules.skills.api.client.SkillsClient.current().progress(),SkillCatalog.MASTERY)>0;
            if(elapsed>=550&&light&&!c.finisher){
                c.finisher=true;c.hit.clear();c.hitIndex=1;c.elementOverride=0;c.passiveDamage=true;c.suppressEquipment=true;Stats original=c.stats;c.stats=original.copy();c.stats.modifiers.put("crit",0d);
                x.slash(c,c.parameters.get("range",5),5,3,c.parameters.get("damage",1)*.2,0,true);x.visual(c,"HIT");c.stats=original;c.elementOverride=-2;c.passiveDamage=false;c.suppressEquipment=false;
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
    static {CareerSkillBehaviors.install();OracleClient.install();}
    private SkillBehaviors(){}
    private static void register(List<SkillDefinition> skills,Behavior behavior){for(SkillDefinition skill:skills)register(skill.id,behavior);}
    public static void register(String id,Behavior behavior){if(BEHAVIORS.putIfAbsent(id,Objects.requireNonNull(behavior))!=null)throw new IllegalArgumentException("Duplicate skill behavior: "+id);}
    public static void advance(Context context,SkillCast cast,long now){
        var behavior=BEHAVIORS.get(cast.skill.id);
        if(behavior==null){context.cancel(true);return;}
        double speed=SkillPresentation.fixedActionTime(cast.skill)?1:cast.speed;
        behavior.tick(context,cast,now,(long)((now-cast.start)*speed),(long)((now-cast.stageStart)*speed),speed);
    }
    private static Vec3 blink(){
        var p=net.minecraft.client.Minecraft.getInstance().player;
        Vec3 origin=p.position(),direction=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize();if(direction.lengthSqr()<.001)throw new IllegalArgumentException("没有有效前进方向");
        double limit=5;
        for(double height: new double[]{.1,p.getBbHeight()*.5,p.getEyeHeight()}){Vec3 start=origin.add(0,height,0);var obstruction=p.level().clip(new net.minecraft.world.level.ClipContext(start,start.add(direction.scale(5)),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));if(obstruction.getType()!=net.minecraft.world.phys.HitResult.Type.MISS)limit=Math.min(limit,Math.max(0,start.distanceTo(obstruction.getLocation())-p.getBbWidth()*.5-.02));}
        for(double distance=limit;distance>=.25;distance-=.25){Vec3 desired=origin.add(direction.scale(distance));var floor=p.level().clip(new net.minecraft.world.level.ClipContext(desired.add(0,1,0),desired.add(0,-4.99,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));if(floor.getType()==net.minecraft.world.phys.HitResult.Type.MISS)continue;Vec3 point=floor.getLocation().add(0,.001,0);net.minecraft.core.BlockPos pos=net.minecraft.core.BlockPos.containing(point);
            if(origin.distanceToSqr(point)>25||!p.level().hasChunkAt(pos)||!p.level().getWorldBorder().isWithinBounds(pos))continue;net.minecraft.world.phys.AABB box=p.getBoundingBox().move(point.subtract(origin));
            if(p.level().noCollision(p,box)&&!p.level().containsAnyLiquid(box)&&p.level().getBlockState(net.minecraft.core.BlockPos.containing(point.add(0,-.02,0))).blocksMotion())return point;
        }
        throw new IllegalArgumentException("前方5格内没有可落地位置");
    }
}
