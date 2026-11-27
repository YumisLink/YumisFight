package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.combat.api.ProjectileSpawn;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Eye aura/gears and Asura career mythic runtimes (2-step basic brands, homing ice blade, triple fire blasts). */
final class AsuraClient {
    private static final double HOMING_ICE_SPEED = 8.0;
    private static final double HOMING_ICE_WIDTH = 3.0;
    private static final double HOMING_ICE_HEIGHT = 2.0;
    private static final double HOMING_ICE_DETECT_RANGE = 22.0;
    private static final long FIRE_CHAIN_STEP_MS = 180L;

    private static final class Gear {SkillCast cast;Vec3 position;double distance,range;long next,started,lastVisual;int sequence;final Set<UUID> hit=new HashSet<>();}
    private static final class HomingIce {
        SkillCast cast;
        String visualId;
        Vec3 origin,position,direction;
        long started,expiresAt,nextTurnAt,nextStepAt,turnMillis;
        int visualSequence,turnIndex;
        final Set<UUID> hit=new HashSet<>();
    }
    private static final class FireBlastChain {
        SkillCast cast;
        Vec3 origin,direction,lastPos;
        double blastRadius,range;
        int totalBlasts,fired;
        long nextBlastAt;
    }

    private final List<Gear> gears=new ArrayList<>();
    private final List<HomingIce> homingBlades=new ArrayList<>();
    private final List<FireBlastChain> fireChains=new ArrayList<>();
    private String aura="";private int pulses,basicStep;

    void clear(){
        for(var gear:gears){visual(gear,"CANCEL");TrainingCombat.release(gear.cast.id,gear.cast.eyeGear);}
        for(var blade:homingBlades){homingIceVisual(blade,"CANCEL",false);TrainingCombat.release(blade.cast.id,blade.visualId);}
        for(var chain:fireChains){TrainingCombat.release(chain.cast.id,"fire_chain");}
        gears.clear();homingBlades.clear();fireChains.clear();aura="";pulses=0;basicStep=0;
    }

    boolean recordBasicAttack(int every){
        if(++basicStep>=Math.max(1,every)){
            basicStep=0;
            return true;
        }
        return false;
    }

    void startHomingIce(SkillCast source){
        long now=ClientUi.now();
        long turnMs=Math.max(200L,TowerBattleCard.ASURA_ICE_HOMING.millis("turn"));
        long durationMs=Math.max(turnMs,TowerBattleCard.ASURA_ICE_HOMING.millis("duration"));
        Vec3 startPos=source.origin.add(0,.15,0);
        Vec3 initialDir=aimAtNearestEnemy(startPos,horizontal(source.direction));
        var blade=new HomingIce();
        blade.cast=derived(source);
        blade.cast.controlSeconds=Math.max(0,source.parameters.get("controlSeconds",1.5));
        blade.cast.hitIndex=64;
        blade.visualId=UUID.nameUUIDFromBytes((source.id+"/homing_ice").getBytes(StandardCharsets.UTF_8)).toString();
        blade.origin=blade.position=startPos;
        blade.direction=initialDir;
        blade.started=now;
        blade.expiresAt=now+durationMs;
        blade.turnMillis=turnMs;
        blade.nextTurnAt=now+turnMs;
        blade.nextStepAt=now;
        homingBlades.add(blade);
        TrainingCombat.hold(source.id,blade.visualId,now+durationMs+1000L);
        homingIceVisual(blade,"START",false);
        homingIceVisual(blade,"RELEASE",true);
    }

    void startFireBlasts(SkillCast source,SkillBehaviors.Context context){
        long now=ClientUi.now();
        var chain=new FireBlastChain();
        chain.cast=derived(source);
        chain.origin=chain.lastPos=source.origin;
        chain.direction=horizontal(source.direction);
        chain.blastRadius=Math.max(.5,source.parameters.get("blastRadius",2.5));
        chain.range=Math.max(3d,source.parameters.get("range",10d));
        chain.totalBlasts=Math.max(1,(int)Math.round(source.parameters.get("slashes",TowerBattleCard.ASURA_FIRE_TRIPLE.value("blasts"))));
        chain.nextBlastAt=now;
        fireChains.add(chain);
        TrainingCombat.hold(source.id,"fire_chain",now+chain.totalBlasts*FIRE_CHAIN_STEP_MS+1200L);
        advanceFireChain(chain,context,now);
        if(chain.fired>=chain.totalBlasts){
            TrainingCombat.release(chain.cast.id,"fire_chain");
            fireChains.remove(chain);
        }
    }

    boolean basic(String cycle,JsonObject content,Map<String,SkillCast> sources){
        if(!content.has("eyeCast"))return false;var source=sources.get(content.get("eyeCast").getAsString());if(source==null)return true;
        var gear=new Gear();gear.cast=derived(source);gear.cast.eyeGear=cycle;gear.cast.hitIndex=AsuraRules.GEAR_INDEX;
        var launch=content.getAsJsonObject("eyeProjectile");gear.cast.direction=ProjectileSpawn.vector(launch.getAsJsonArray("direction"));gear.position=gear.cast.origin=gear.cast.center=ProjectileSpawn.vector(launch.getAsJsonArray("origin"));gear.range=launch.get("range").getAsDouble();gear.started=launch.get("at").getAsLong();gear.next=ClientUi.now()-Math.clamp(SkillsClient.current().serverNow()-gear.started,0,4500);
        gears.add(gear);TrainingCombat.hold(source.id,cycle,ClientUi.now()+travelMillis(gear)+1000);visual(gear,"START");return true;
    }

    void tick(SkillBehaviors.Context context,SkillCast source,long eyeUntil){
        long now=ClientUi.now();var player=Minecraft.getInstance().player;
        if(source!=null&&eyeUntil>0){
            if(!aura.equals(source.id)){aura=source.id;pulses=0;}
            int auraCap=(int)Math.max(1,source.parameters.get("auraPulses",20));while(pulses<auraCap&&now-source.start>=(pulses+1)*1000L){
                SkillCast pulse=derived(source);pulse.dot=true;pulse.hitIndex=pulses++;pulse.center=player.position();
                double auraRadius=Math.max(.5,source.parameters.get("auraRadius",AsuraRules.AURA_RADIUS));
                for(var target:SkillController.INSTANCE.targets(player.getBoundingBox().inflate(auraRadius,4,auraRadius)))
                    if(target.position().subtract(player.position()).horizontalDistanceSqr()<=auraRadius*auraRadius&&Math.abs(target.getY()-player.getY())<=4&&SkillController.INSTANCE.sight(player.getEyePosition(),target.getBoundingBox().getCenter()))context.hit(pulse,target,source.parameters.get("damage",0)*source.parameters.get("auraRatio",AsuraRules.AURA_RATIO));
            }
        }
        for(var gear:new ArrayList<>(gears)){
            while(gear.distance<gear.range&&now>=gear.next){
                double gearSpeed=Math.max(1,gear.cast.parameters.get("gearSpeed",AsuraRules.GEAR_SPEED));Vec3 from=gear.position,to=from.add(gear.cast.direction.scale(Math.min(gearSpeed*.05,gear.range-gear.distance)));gear.next+=50;
                if(!SkillController.INSTANCE.sight(from,to)){gear.distance=gear.range;break;}
                gear.position=to;gear.distance+=from.distanceTo(to);gear.cast.center=to;
                for(var target:SkillController.INSTANCE.targets(new AABB(from,to).inflate(.75))){
                    if(gear.hit.contains(target.getUUID())||!SkillController.INSTANCE.sight(from,target.getBoundingBox().getCenter()))continue;
                    if(context.hit(gear.cast,target,gear.cast.parameters.get("damage",0)*gear.cast.parameters.get("gearRatio",AsuraRules.GEAR_RATIO)))gear.hit.add(target.getUUID());
                }
            }
            double gearSpeed=Math.max(1,gear.cast.parameters.get("gearSpeed",AsuraRules.GEAR_SPEED));long gearLife=(long)(Math.max(30,2*gear.cast.parameters.get("gearRange",AsuraRules.GEAR_RANGE)/gearSpeed)*1000);if(gear.distance>=gear.range||now-gear.cast.start>gearLife){visual(gear,"CANCEL");TrainingCombat.release(gear.cast.id,gear.cast.eyeGear);gears.remove(gear);}
            else if(now-gear.lastVisual>=150){visual(gear,"TRAIL");gear.lastVisual=now;}
        }
        for(var blade:new ArrayList<>(homingBlades))advanceHomingIce(blade,context,now);
        for(var chain:new ArrayList<>(fireChains)){
            advanceFireChain(chain,context,now);
            if(chain.fired>=chain.totalBlasts){TrainingCombat.release(chain.cast.id,"fire_chain");fireChains.remove(chain);}
        }
    }

    private void advanceHomingIce(HomingIce blade,SkillBehaviors.Context context,long now){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null||now>=blade.expiresAt){
            homingIceVisual(blade,"CANCEL",false);
            TrainingCombat.release(blade.cast.id,blade.visualId);
            homingBlades.remove(blade);
            return;
        }
        TrainingCombat.touch(blade.cast.id);
        int budget=20;
        while(now>=blade.nextStepAt&&blade.nextStepAt<blade.expiresAt&&budget-->0){
            long stepAt=blade.nextStepAt;
            if(stepAt>=blade.nextTurnAt){
                blade.nextTurnAt+=blade.turnMillis;
                blade.hit.clear();
                blade.turnIndex++;
                blade.cast.hitIndex=64+blade.turnIndex;
                blade.direction=aimAtNearestEnemy(blade.position,blade.direction.scale(-1));
                homingIceVisual(blade,"SEGMENT",true);
            }
            Vec3 from=blade.position;
            Vec3 candidate=from.add(blade.direction.scale(HOMING_ICE_SPEED*.05));
            Vec3 next=from;
            BlockHitResult floor=mc.level.clip(new ClipContext(candidate.add(0,1.2,0),candidate.add(0,-1.2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            if(floor.getType()!=HitResult.Type.MISS&&Math.abs(floor.getLocation().y-from.y)<=1.05){
                Vec3 grounded=new Vec3(candidate.x,floor.getLocation().y+.1,candidate.z);
                if(SkillController.INSTANCE.sight(from.add(0,.2,0),grounded.add(0,.2,0)))next=grounded;
            }
            blade.position=next;
            blade.cast.center=next;
            double damage=blade.cast.parameters.get("damage",2);
            for(var target:SkillController.INSTANCE.targets(new AABB(from,next).inflate(HOMING_ICE_WIDTH*.5,HOMING_ICE_HEIGHT*.5,HOMING_ICE_WIDTH*.5))){
                if(blade.hit.contains(target.getUUID())||!SkillController.INSTANCE.sight(from.add(0,.3,0),target.getBoundingBox().getCenter()))continue;
                if(context.hit(blade.cast,target,damage))blade.hit.add(target.getUUID());
            }
            blade.nextStepAt+=50;
        }
    }

    private void advanceFireChain(FireBlastChain chain,SkillBehaviors.Context context,long now){
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.player==null)return;
        TrainingCombat.touch(chain.cast.id);
        while(chain.fired<chain.totalBlasts&&now>=chain.nextBlastAt){
            double firstDist=Math.min(2.5,chain.range*.3);
            double span=Math.max(0,chain.range-1.0-firstDist);
            double dist=chain.totalBlasts<=1?firstDist:firstDist+span*chain.fired/(chain.totalBlasts-1);
            Vec3 desired=chain.origin.add(chain.direction.scale(dist));
            Vec3 fromEye=chain.lastPos.add(0,.8,0);
            Vec3 toEye=new Vec3(desired.x,chain.lastPos.y+.8,desired.z);
            var wall=mc.level.clip(new ClipContext(fromEye,toEye,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            Vec3 clamped=wall.getType()!=HitResult.Type.MISS?wall.getLocation().subtract(chain.direction.scale(.3)):new Vec3(desired.x,chain.lastPos.y,desired.z);
            var floor=mc.level.clip(new ClipContext(new Vec3(clamped.x,chain.lastPos.y+2.0,clamped.z),new Vec3(clamped.x,chain.lastPos.y-3.0,clamped.z),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            Vec3 groundPos=floor.getType()!=HitResult.Type.MISS?new Vec3(clamped.x,floor.getLocation().y,clamped.z):new Vec3(clamped.x,chain.lastPos.y,clamped.z);
            Vec3 blastCenter=groundPos.add(0,.10,0);
            chain.lastPos=groundPos;
            chain.cast.hitIndex=chain.fired;
            chain.cast.center=blastCenter;
            fireBlastVisual(chain,blastCenter,chain.fired);
            double radius=chain.blastRadius;
            double damage=chain.cast.parameters.get("damage",0);
            for(var target:SkillController.INSTANCE.targets(new AABB(blastCenter,blastCenter).inflate(radius,Math.max(4,radius),radius))){
                Vec3 tc=target.getBoundingBox().getCenter();
                double reach=radius+target.getBbWidth()*.5;
                double dx=tc.x-blastCenter.x,dz=tc.z-blastCenter.z;
                if(dx*dx+dz*dz<=reach*reach&&Math.abs(tc.y-blastCenter.y)<=Math.max(4,radius)&&SkillController.INSTANCE.sight(blastCenter.add(0,.8,0),tc)){
                    context.hit(chain.cast,target,damage);
                }
            }
            chain.fired++;
            chain.nextBlastAt+=FIRE_CHAIN_STEP_MS;
        }
    }

    private Vec3 aimAtNearestEnemy(Vec3 from,Vec3 fallback){
        LivingEntity best=null;
        double bestDistSq=HOMING_ICE_DETECT_RANGE*HOMING_ICE_DETECT_RANGE;
        for(var target:SkillController.INSTANCE.targets(new AABB(from,from).inflate(HOMING_ICE_DETECT_RANGE,6,HOMING_ICE_DETECT_RANGE))){
            Vec3 delta=target.position().subtract(from);
            double d2=delta.horizontalDistanceSqr();
            if(d2>=.12&&d2<bestDistSq&&SkillController.INSTANCE.sight(from.add(0,.6,0),target.getBoundingBox().getCenter())){
                bestDistSq=d2;best=target;
            }
        }
        if(best==null)return horizontal(fallback);
        return horizontal(best.position().subtract(from));
    }

    private static Vec3 horizontal(Vec3 v){
        if(v==null)return new Vec3(0,0,1);
        Vec3 flat=new Vec3(v.x,0,v.z);
        return flat.lengthSqr()<1e-6?new Vec3(0,0,1):flat.normalize();
    }

    private static SkillCast derived(SkillCast source){var c=new SkillCast();c.id=source.id;c.skill=source.skill;c.parameters=source.parameters;c.stats=source.stats;c.equipment=source.equipment;c.start=source.start;c.origin=source.origin;c.direction=source.direction;c.dimension=source.dimension;c.session=source.session;c.generation=source.generation;c.job=source.job;c.instance=source.instance;return c;}
    private static long travelMillis(Gear gear){
        double speed=Math.max(1,gear.cast.parameters.get("gearSpeed",AsuraRules.GEAR_SPEED));
        return Math.max(1,Math.round(gear.range/speed*1000));
    }

    private static void homingIceVisual(HomingIce blade,String phase,boolean withMotion){
        var state=SkillsClient.current();var player=Minecraft.getInstance().player;if(player==null)return;
        long now=ClientUi.now(),serverNow=state.serverNow();
        long legMs=Math.max(100L,Math.min(blade.turnMillis,blade.expiresAt-now));
        JsonObject j=new JsonObject();
        j.addProperty("caster",player.getStringUUID());j.addProperty("session",state.session());j.addProperty("generation",state.own().generation);
        j.addProperty("id",blade.visualId);j.addProperty("kind",CareerSkills.ICE);j.addProperty("phase",phase);
        j.addProperty("sequence",blade.visualSequence++);j.addProperty("startedAt",serverNow-(now-blade.started));j.addProperty("at",serverNow);
        j.addProperty("duration",(blade.expiresAt-blade.started)+500L);j.addProperty("seed",blade.visualId.hashCode());j.addProperty("cosmeticOnly",true);
        j.addProperty("color",CareerClient.INSTANCE.visualColor(blade.cast.stats,0x75d8f5));
        j.addProperty("x",blade.origin.x);j.addProperty("y",blade.origin.y);j.addProperty("z",blade.origin.z);
        j.addProperty("px",blade.position.x);j.addProperty("py",blade.position.y);j.addProperty("pz",blade.position.z);
        j.addProperty("dx",blade.direction.x);j.addProperty("dy",0d);j.addProperty("dz",blade.direction.z);
        if(withMotion){
            var motion=SkillController.motion(HOMING_ICE_SPEED*(legMs/1000d),legMs,true);
            motion.addProperty("at",serverNow);
            j.add("motion",motion);
        }
        Effects.receive(j);state.send("visual",j);
    }

    private static void fireBlastVisual(FireBlastChain chain,Vec3 center,int index){
        var state=SkillsClient.current();var player=Minecraft.getInstance().player;if(player==null)return;
        long serverNow=state.serverNow();
        String id=UUID.nameUUIDFromBytes((chain.cast.id+"/fire_blast/"+index).getBytes(StandardCharsets.UTF_8)).toString();
        double scale=Math.clamp(chain.blastRadius/2.5d,.5,16d);
        int color=CareerClient.INSTANCE.visualColor(chain.cast.stats,0xff923e);
        String[] phases={"START","RELEASE","FINISH"};
        for(int seq=0;seq<phases.length;seq++){
            JsonObject j=new JsonObject();
            j.addProperty("caster",player.getStringUUID());j.addProperty("session",state.session());j.addProperty("generation",state.own().generation);
            j.addProperty("id",id);j.addProperty("kind",CareerSkills.FIRE);j.addProperty("phase",phases[seq]);
            j.addProperty("sequence",seq);j.addProperty("startedAt",serverNow);j.addProperty("at",serverNow);
            j.addProperty("duration",1200L);j.addProperty("seed",id.hashCode());j.addProperty("cosmeticOnly",true);
            j.addProperty("color",color);j.addProperty("horizontalScale",scale);
            j.addProperty("x",center.x);j.addProperty("y",center.y);j.addProperty("z",center.z);
            j.addProperty("px",center.x);j.addProperty("py",center.y);j.addProperty("pz",center.z);
            j.addProperty("dx",chain.direction.x);j.addProperty("dy",0d);j.addProperty("dz",chain.direction.z);
            Effects.receive(j);state.send("visual",j);
        }
    }

    private static void visual(Gear gear,String phase){
        var state=SkillsClient.current();var player=Minecraft.getInstance().player;if(player==null)return;
        String id=UUID.nameUUIDFromBytes((gear.cast.eyeGear+"/eye").getBytes(StandardCharsets.UTF_8)).toString();
        JsonObject j=new JsonObject();j.addProperty("caster",player.getStringUUID());j.addProperty("session",state.session());j.addProperty("generation",state.own().generation);j.addProperty("id",id);j.addProperty("kind",AsuraRules.GEAR_VISUAL);j.addProperty("phase",phase);j.addProperty("sequence",gear.sequence++);j.addProperty("startedAt",gear.started);j.addProperty("at",state.serverNow());j.addProperty("duration",travelMillis(gear)+500);j.addProperty("seed",id.hashCode());j.addProperty("cosmeticOnly",true);j.addProperty("color",CareerRules.EYE_COLOR);
        j.addProperty("x",gear.cast.origin.x);j.addProperty("y",gear.cast.origin.y);j.addProperty("z",gear.cast.origin.z);j.addProperty("px",gear.position.x);j.addProperty("py",gear.position.y);j.addProperty("pz",gear.position.z);j.addProperty("dx",gear.cast.direction.x);j.addProperty("dy",gear.cast.direction.y);j.addProperty("dz",gear.cast.direction.z);
        if(phase.equals("START")){double gearSpeed=Math.max(1,gear.cast.parameters.get("gearSpeed",AsuraRules.GEAR_SPEED));var motion=SkillController.motion(gear.range,gear.range/gearSpeed*1000,false);motion.addProperty("at",gear.started);j.add("motion",motion);}Effects.receive(j);state.send("visual",j);
    }
}
