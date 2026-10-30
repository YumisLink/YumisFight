package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.combat.api.ProjectileSpawn;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.*;
import java.util.*;

/** Eye aura and gears use the accepted eye snapshot, never the normal attack multiplier. */
final class AsuraClient {
    private static final class Gear {SkillCast cast;Vec3 position;double distance,range;long next,started,lastVisual;int sequence;final Set<UUID> hit=new HashSet<>();}
    private final List<Gear> gears=new ArrayList<>();
    private String aura="";private int pulses;
    void clear(){for(var gear:gears){visual(gear,"CANCEL");TrainingCombat.release(gear.cast.id,gear.cast.eyeGear);}gears.clear();aura="";pulses=0;}
    boolean basic(String cycle,JsonObject content,Map<String,SkillCast> sources){
        if(!content.has("eyeCast"))return false;var source=sources.get(content.get("eyeCast").getAsString());if(source==null)return true;
        var player=Minecraft.getInstance().player;var gear=new Gear();gear.cast=derived(source);gear.cast.eyeGear=cycle;gear.cast.hitIndex=AsuraRules.GEAR_INDEX;
        var launch=content.getAsJsonObject("eyeProjectile");gear.cast.direction=ProjectileSpawn.vector(launch.getAsJsonArray("direction"));gear.position=gear.cast.origin=gear.cast.center=ProjectileSpawn.vector(launch.getAsJsonArray("origin"));gear.range=launch.get("range").getAsDouble();gear.started=launch.get("at").getAsLong();gear.next=ClientUi.now()-Math.clamp(SkillsClient.current().serverNow()-gear.started,0,4500);
        gears.add(gear);TrainingCombat.hold(source.id,cycle,ClientUi.now()+4500);visual(gear,"START");return true;
    }
    void tick(SkillBehaviors.Context context,SkillCast source,long eyeUntil){
        long now=ClientUi.now();var player=Minecraft.getInstance().player;
        if(source!=null&&eyeUntil>0){
            if(!aura.equals(source.id)){aura=source.id;pulses=0;}
            while(pulses<20&&now-source.start>=(pulses+1)*1000L){
                SkillCast pulse=derived(source);pulse.dot=true;pulse.hitIndex=pulses++;pulse.center=player.position();
                for(var target:SkillController.INSTANCE.targets(player.getBoundingBox().inflate(AsuraRules.AURA_RADIUS,4,AsuraRules.AURA_RADIUS)))
                    if(target.position().subtract(player.position()).horizontalDistanceSqr()<=AsuraRules.AURA_RADIUS*AsuraRules.AURA_RADIUS&&Math.abs(target.getY()-player.getY())<=4&&SkillController.INSTANCE.sight(player.getEyePosition(),target.getBoundingBox().getCenter()))context.hit(pulse,target,source.parameters.get("damage",0)*AsuraRules.AURA_RATIO);
            }
        }
        for(var gear:new ArrayList<>(gears)){
            while(gear.distance<gear.range&&now>=gear.next){
                Vec3 from=gear.position,to=from.add(gear.cast.direction.scale(Math.min(AsuraRules.GEAR_SPEED*.05,gear.range-gear.distance)));gear.next+=50;
                if(!SkillController.INSTANCE.sight(from,to)){gear.distance=gear.range;break;}
                gear.position=to;gear.distance+=from.distanceTo(to);gear.cast.center=to;
                for(var target:SkillController.INSTANCE.targets(new AABB(from,to).inflate(.75))){
                    if(gear.hit.contains(target.getUUID())||!SkillController.INSTANCE.sight(from,target.getBoundingBox().getCenter()))continue;
                    if(context.hit(gear.cast,target,gear.cast.parameters.get("damage",0)*AsuraRules.GEAR_RATIO))gear.hit.add(target.getUUID());
                }
            }
            if(gear.distance>=gear.range||now-gear.cast.start>30000){visual(gear,"CANCEL");TrainingCombat.release(gear.cast.id,gear.cast.eyeGear);gears.remove(gear);}
            else if(now-gear.lastVisual>=150){visual(gear,"TRAIL");gear.lastVisual=now;}
        }
    }
    private static SkillCast derived(SkillCast source){var c=new SkillCast();c.id=source.id;c.skill=source.skill;c.parameters=source.parameters;c.stats=source.stats;c.equipment=source.equipment;c.start=source.start;c.origin=source.origin;c.direction=source.direction;c.dimension=source.dimension;return c;}
    private static void visual(Gear gear,String phase){
        var state=SkillsClient.current();var player=Minecraft.getInstance().player;if(player==null)return;
        String id=UUID.nameUUIDFromBytes((gear.cast.eyeGear+"/eye").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        JsonObject j=new JsonObject();j.addProperty("caster",player.getStringUUID());j.addProperty("session",state.session());j.addProperty("generation",state.own().generation);j.addProperty("id",id);j.addProperty("kind",AsuraRules.GEAR_VISUAL);j.addProperty("phase",phase);j.addProperty("sequence",gear.sequence++);j.addProperty("startedAt",gear.started);j.addProperty("at",state.serverNow());j.addProperty("duration",4000);j.addProperty("seed",id.hashCode());j.addProperty("cosmeticOnly",true);j.addProperty("color",CareerRules.EYE_COLOR);
        j.addProperty("x",gear.cast.origin.x);j.addProperty("y",gear.cast.origin.y);j.addProperty("z",gear.cast.origin.z);j.addProperty("px",gear.position.x);j.addProperty("py",gear.position.y);j.addProperty("pz",gear.position.z);j.addProperty("dx",gear.cast.direction.x);j.addProperty("dy",gear.cast.direction.y);j.addProperty("dz",gear.cast.direction.z);
        if(phase.equals("START")){var motion=SkillController.motion(gear.range,gear.range/AsuraRules.GEAR_SPEED*1000,false);motion.addProperty("at",gear.started);j.add("motion",motion);}Effects.receive(j);state.send("visual",j);
    }
}
