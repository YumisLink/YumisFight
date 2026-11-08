package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.*;
import java.util.*;

/** Sun and moon contacts execute on the owner alongside their existing visual choreography. */
final class SunMoonClient {
    private static final class Area {SkillCast cast;String visual;Vec3 point;LivingEntity target;long generation,next;int index,sequence;}
    private static final List<Area> areas=new ArrayList<>();
    private SunMoonClient(){}
    static void install(){for(String id:SunMoonSkills.ALL)if(!id.equals(SunMoonSkills.CYCLE))MageClient.register(id,SunMoonClient::start);}
    private static void start(SkillCast c){
        var p=Minecraft.getInstance().player;String id=c.skill.id;
        if(id.equals(SunMoonSkills.MOON)){MageClient.INSTANCE.projectile(c);return;}
        if(id.equals(SunMoonSkills.REFLUX)){MageClient.INSTANCE.visual(c,UUID.randomUUID().toString(),"START",p.position(),Vec3.ZERO,800,0,null);MageClient.INSTANCE.finished(c);return;}
        var aim=SkillTargeting.ground(p,c.parameters.get("range",16),e->e.isAlive()&&(e instanceof net.minecraft.world.entity.monster.Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon));
        var a=new Area();a.cast=c;a.visual=UUID.randomUUID().toString();a.point=aim.point();
        if(id.equals(SunMoonSkills.PARADOX)&&aim.target()!=null){var target=SkillsClient.current().context(aim.target());if(target!=null){a.target=aim.target();a.generation=target.life.generation;}}
        long delay=id.equals(SunMoonSkills.SUN)?350:id.equals(SunMoonSkills.CORONA)?800:id.equals(SunMoonSkills.PARADOX)?600:id.equals(SunMoonSkills.STARFALL)?250:0;a.next=ClientUi.now()+delay;
        JsonObject motion=null;if(a.target!=null){motion=new JsonObject();motion.addProperty("target",a.target.getStringUUID());motion.addProperty("targetEntity",a.target.getId());motion.addProperty("targetFeet",true);motion.addProperty("range",0);motion.addProperty("velocity",0);motion.addProperty("millis",600);}
        areas.add(a);MageClient.INSTANCE.visual(c,a.visual,"START",a.point,p.getEyePosition().subtract(a.point),id.equals(SunMoonSkills.STARFALL)?2600:2000,0,motion);
    }
    static void tick(){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;long now=ClientUi.now();
        for(var a:new ArrayList<>(areas)){
            var c=a.cast;if(!MageClient.INSTANCE.valid(c)){areas.remove(a);continue;}
            if(a.target!=null){var target=SkillsClient.current().context(a.target);if(a.target.isAlive()&&target!=null&&!target.life.dead&&target.life.generation==a.generation)a.point=a.target.position();else a.target=null;}
            if(now<a.next)continue;if(!mc.level.hasChunkAt(BlockPos.containing(a.point))){areas.remove(a);MageClient.INSTANCE.finished(c);continue;}
            String id=c.skill.id;double damage=c.parameters.get("damage",0),radius=c.parameters.get("radius",3);
            if(id.equals(SunMoonSkills.STARFALL)){while(a.index<8&&now>=a.next){hit(a,a.point,radius,damage/8,2);a.index++;a.next+=250;MageClient.INSTANCE.visual(c,a.visual,"SEGMENT",a.point,Vec3.ZERO,2600,++a.sequence,null);}if(a.index<8)continue;}
            else if(id.equals(SunMoonSkills.PARADOX)){hit(a,a.point,radius,damage/2,2);hit(a,a.point,radius,damage/2,1);}
            else hit(a,id.equals(SunMoonSkills.CORONA)?a.point.add(0,2,0):a.point,radius,damage,1);
            Vec3 point=id.equals(SunMoonSkills.CORONA)?a.point.add(0,2,0):a.point;MageClient.INSTANCE.visual(c,a.visual,"FINISH",point,Vec3.ZERO,id.equals(SunMoonSkills.STARFALL)?2600:2000,++a.sequence,null);areas.remove(a);MageClient.INSTANCE.finished(c);
        }
    }
    private static void hit(Area a,Vec3 center,double radius,double coefficient,int element){
        for(var target:SkillController.INSTANCE.targets(new AABB(center,center).inflate(radius))){var b=target.getBoundingBox();Vec3 near=new Vec3(Math.clamp(center.x,b.minX,b.maxX),Math.clamp(center.y,b.minY,b.maxY),Math.clamp(center.z,b.minZ,b.maxZ));if(near.distanceToSqr(center)<=radius*radius&&SkillController.INSTANCE.sight(center.add(0,.08,0),target.getBoundingBox().getCenter()))MageClient.INSTANCE.issue(a.cast,target,coefficient,element,"","");}
    }
    static void cancel(String id){areas.removeIf(a->a.cast.id.equals(id));}
    static void clear(){areas.clear();}
}
