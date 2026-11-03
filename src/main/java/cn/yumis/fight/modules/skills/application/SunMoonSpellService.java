package cn.yumis.fight.modules.skills.application;

import cn.yumis.fight.modules.skills.api.*;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Sun/moon area choreography owns its contacts; MageSkillService supplies the shared receipts. */
final class SunMoonSpellService {
    private final MageSkillService mage;
    private final List<Area> areas=new ArrayList<>();
    private static final class Area {
        MageSkillService.Source source;String visual;Vec3 point;UUID target;long targetGeneration,start,next;int index,sequence;
    }
    private SunMoonSpellService(MageSkillService mage){this.mage=mage;}
    static void install(MageSkillService mage){var service=new SunMoonSpellService(mage);for(String id:SunMoonSkills.ALL)if(!id.equals(SunMoonSkills.CYCLE)){mage.spell(id,service::release);mage.reduction(id,p->{service.clear(p.getUUID());mage.cancelContent(p,SunMoonSkills.ALL);});}mage.contentLifecycle(service::tick,service::clear);}
    private Runnable release(MageSkillService.Source source){
        var p=mage.owner(source);String id=source.skill.id;
        if(id.equals(SunMoonSkills.MOON))return ()->mage.startProjectile(source);
        if(id.equals(SunMoonSkills.REFLUX))return ()->{mage.visual(source,UUID.randomUUID().toString(),"START",p.position(),Vec3.ZERO,800,0,null);mage.finished(source);};
        var aim=SkillTargeting.ground(p,source.parameters.get("range",16),e->MageSkillService.eligible(p,e));
        var area=new Area();area.source=source;area.visual=UUID.randomUUID().toString();area.point=aim.point();area.start=System.currentTimeMillis();
        if(id.equals(SunMoonSkills.PARADOX)&&aim.target()!=null){area.target=aim.target().getUUID();area.targetGeneration=mage.targetGeneration(aim.target());}
        long delay=id.equals(SunMoonSkills.SUN)?350:id.equals(SunMoonSkills.CORONA)?800:id.equals(SunMoonSkills.PARADOX)?600:id.equals(SunMoonSkills.STARFALL)?250:0;
        area.next=area.start+delay;
        JsonObject motion=null;
        if(area.target!=null){motion=new JsonObject();motion.addProperty("target",area.target.toString());motion.addProperty("targetEntity",aim.target().getId());motion.addProperty("targetFeet",true);motion.addProperty("range",0);motion.addProperty("velocity",0);motion.addProperty("millis",600);}
        JsonObject trajectory=motion;return ()->{areas.add(area);mage.visual(source,area.visual,"START",area.point,source.origin.subtract(area.point),id.equals(SunMoonSkills.STARFALL)?2600:2000,0,trajectory);};
    }
    private void tick(long now){
        for(var area:new ArrayList<>(areas)){
            var source=area.source;var p=mage.owner(source);if(!mage.valid(source)||p==null){areas.remove(area);continue;}
            if(area.target!=null){var entity=p.level().getEntity(area.target);if(entity instanceof LivingEntity target&&mage.targetAlive(target)&&mage.targetGeneration(target)==area.targetGeneration)area.point=target.position();else area.target=null;}
            if(now<area.next)continue;
            if(!p.level().hasChunkAt(BlockPos.containing(area.point))){areas.remove(area);mage.finished(source);continue;}
            String id=source.skill.id;double damage=source.parameters.get("damage",0),radius=source.parameters.get("radius",3);
            if(id.equals(SunMoonSkills.STARFALL)){
                while(area.index<8&&now>=area.next){hit(area,area.point,radius,damage/8,2);area.index++;area.next+=250;mage.visual(source,area.visual,"SEGMENT",area.point,Vec3.ZERO,2600,++area.sequence,null);}
                if(area.index<8)continue;
            }else if(id.equals(SunMoonSkills.PARADOX)){hit(area,area.point,radius,damage/2,2);hit(area,area.point,radius,damage/2,1);}
            else hit(area,id.equals(SunMoonSkills.CORONA)?area.point.add(0,2,0):area.point,radius,damage,1);
            Vec3 point=id.equals(SunMoonSkills.CORONA)?area.point.add(0,2,0):area.point;
            mage.visual(source,area.visual,"FINISH",point,Vec3.ZERO,id.equals(SunMoonSkills.STARFALL)?2600:2000,++area.sequence,null);areas.remove(area);mage.finished(source);
        }
    }
    private void hit(Area area,Vec3 center,double radius,double coefficient,int element){
        var source=area.source;var p=mage.owner(source);
        for(var target:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),e->MageSkillService.eligible(p,e))){
            Vec3 nearest=MageSkillService.closest(target,center);
            if(nearest.distanceToSqr(center)>radius*radius)continue;
            var obstruction=p.level().clip(new ClipContext(center.add(0,.08,0),target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(obstruction.getType()==HitResult.Type.MISS)mage.issue(source,target,center,null,false,coefficient,element);
        }
    }
    private void clear(UUID owner){for(var area:new ArrayList<>(areas))if(area.source.owner.equals(owner)){mage.visual(area.source,area.visual,"CANCEL",area.point,Vec3.ZERO,2000,++area.sequence,null);area.source.cancelled=true;areas.remove(area);}}
}
