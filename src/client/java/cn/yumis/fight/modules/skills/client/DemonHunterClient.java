package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.*;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** The owner executes its fel movement, contacts and fury; packets only publish results. */
public final class DemonHunterClient {
    private static final class Action {
        SkillCast cast;Vec3 from,to,center,previous;DemonHunterChoreography.Scene dance;String session,job,instance;long generation;
        LivingEntity target;long targetGeneration,at,last,comboAt,moveAt=-1;double duration,arc,travelled;int index,stage=-1,sequence;boolean previousGravity;
        final Set<UUID> struck=new HashSet<>();
    }
    private record Landing(Vec3 point,double arc){}
    private static Action primary,aura;
    private static Landing aimed;
    private DemonHunterClient(){}
    private static SkillsClient state(){return SkillsClient.current();}
    private static LocalPlayer player(){return Minecraft.getInstance().player;}
    private static DemonHunterState resource(){return CareerClient.INSTANCE.view.demonHunter;}
    public static void registerResource(){PrimaryResource.register(cn.yumis.fight.modules.character.api.JobCatalog.DEMON_HUNTER,()->new PrimaryResource.View("怒气",resource().fury,DemonHunterRules.MAX_FURY,DemonHunterRules.COLOR,List.of("攻击命中及眼棱引导产生怒气，技能按各自说明消耗怒气。","脱战后每秒减少10点；怒气上限100，不受魔力与魔力恢复属性影响。")));}
    public static void install(){for(String id:DemonHunterSkills.ALL)if(!id.equals(DemonHunterSkills.SIGHT))MageClient.register(id,DemonHunterClient::start);}
    public static boolean basicAllowed(Stats stats){return !stats.weaponType.equals("WARGLAIVE")||player().getOffhandItem().isEmpty();}
    public static boolean active(){return primary!=null;}
    public static boolean blocksMovement(){return primary!=null&&!primary.cast.skill.id.equals(DemonHunterSkills.CHAOS);}
    public static boolean invulnerable(){return primary!=null&&(primary.cast.skill.id.equals(DemonHunterSkills.DANCE)||primary.cast.skill.id.equals(DemonHunterSkills.META)||primary.target!=null);}
    public static boolean steadfast(){return primary!=null&&primary.cast.skill.id.equals(DemonHunterSkills.HUNT)&&primary.target==null;}
    public static long beamAge(){return primary!=null&&primary.cast.skill.id.equals(DemonHunterSkills.BEAM)?ClientUi.now()-primary.at:-1;}
    public static double careerDamageTaken(double normal){return beamAge()>=0?.20:normal;}
    public static double danceAge(UUID id){return primary!=null&&primary.cast.skill.id.equals(DemonHunterSkills.DANCE)&&primary.cast.id.equals(id.toString())?ClientUi.now()-primary.at:-1;}
    public static double huntComboAge(UUID id){return primary!=null&&primary.target!=null&&primary.cast.id.equals(id.toString())?ClientUi.now()-primary.comboAt:-1;}
    public static boolean charging(UUID id){return primary!=null&&primary.cast.skill.id.equals(DemonHunterSkills.HUNT)&&primary.target==null&&primary.cast.id.equals(id.toString());}
    public static boolean sight(){return player()!=null&&state().ready()&&!state().own().dead&&DemonHunterRules.sight(state().stats(),state().progress());}
    public static boolean beforeRequest(SkillDefinition skill){if(primary==null)return true;if(!primary.cast.skill.id.equals(DemonHunterSkills.BEAM)||skill.id.equals(DemonHunterSkills.BEAM))return false;cancelChannel();return true;}
    public static boolean cancelChannel(){if(beamAge()<0)return false;finish(primary,true);return true;}
    public static void prepare(SkillDefinition skill){
        if(!DemonHunterSkills.contains(skill.id))return;
        if(resource().fury<DemonHunterRules.cost(skill.id))throw new IllegalArgumentException("怒气不足");
        if(skill.id.equals(DemonHunterSkills.META))aimed=landing();
    }
    private static Vec3 safe(Vec3 from,Vec3 to){return DemonHunterChoreography.safePosition(player(),from,to);}
    private static boolean floor(Vec3 point){var p=player();return p.level().clip(new ClipContext(point.add(0,.04,0),point.add(0,-.12,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS;}
    private static Landing landing(){
        var p=player();Vec3 aim=SkillTargeting.ground(p,16,DemonHunterClient::eligible).point();var ground=p.level().clip(new ClipContext(aim.add(0,.1,0),aim.add(0,-16,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(ground.getType()==HitResult.Type.MISS)throw new IllegalArgumentException("没有可安全落地的位置");Vec3 to=ground.getLocation().add(0,.01,0),from=p.position();var box=p.getBoundingBox().move(to.subtract(from));
        if(from.distanceToSqr(to)>256||!p.level().noCollision(p,box)||p.level().containsAnyLiquid(box)||!p.level().getWorldBorder().isWithinBounds(BlockPos.containing(to)))throw new IllegalArgumentException("落点空间不足或超出16格");
        for(double height:new double[]{3,2,1,0}){boolean valid=true;for(int i=1;i<=80;i++){double u=i/80d;Vec3 point=from.lerp(to,u).add(0,4*height*u*(1-u),0);if(!p.level().hasChunkAt(BlockPos.containing(point))||!p.level().noCollision(p,p.getBoundingBox().move(point.subtract(from)))){valid=false;break;}}if(valid)return new Landing(to,height);}
        throw new IllegalArgumentException("无法安全跃向该落点");
    }
    private static boolean eligible(LivingEntity e){return e.isAlive()&&(e instanceof net.minecraft.world.entity.monster.Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)&&(!(e instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag b)||!b.timed()||b.ownedBy(player())&&!b.finished());}
    private static void start(SkillCast c){
        if(c.skill.id.equals(DemonHunterSkills.GLAIVE)){MageClient.INSTANCE.projectile(c);return;}
        var p=player();var a=new Action();a.cast=c;a.session=state().session();a.job=state().progress().jobId();a.instance=state().own().instance;a.generation=state().own().generation;a.at=a.last=ClientUi.now();a.from=a.to=a.center=a.previous=p.position();a.previousGravity=p.isNoGravity();
        String id=c.skill.id;a.duration=c.parameters.get("duration",1)*1000/(id.equals(DemonHunterSkills.AURA)||id.equals(DemonHunterSkills.BEAM)||id.equals(DemonHunterSkills.HUNT)?1:c.speed);
        c.direction=p.getLookAngle().normalize();
        if(id.equals(DemonHunterSkills.BEAM)){a.duration=DemonHunterChoreography.BEAM_DURATION;a.to=safe(a.from,a.from.add(0,1,0));}
        if(id.equals(DemonHunterSkills.RUSH))a.to=a.from.add(c.direction.scale(12));
        if(id.equals(DemonHunterSkills.HUNT))a.duration=DemonHunterChoreography.HUNT_DURATION;
        if(id.equals(DemonHunterSkills.META)){a.to=aimed.point();a.arc=aimed.arc();aimed=null;}
        if(id.equals(DemonHunterSkills.DANCE))prepareDance(a);
        if(id.equals(DemonHunterSkills.AURA)){if(aura!=null)finish(aura,false);aura=a;resource().auraCast=c.id;resource().auraUntil=state().serverNow()+6000;}
        else{primary=a;if(!id.equals(DemonHunterSkills.CHAOS)){p.stopFallFlying();p.setNoGravity(true);p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;}if(a.dance!=null)position(a.dance.paths().getFirst().from());}
        MageClient.INSTANCE.visual(c,c.id,"START",a.center,c.direction,(long)Math.ceil(a.duration)+(id.equals(DemonHunterSkills.CHAOS)||id.equals(DemonHunterSkills.BEAM)||a.dance!=null?0:600),0,a.dance==null?null:a.dance.json(state().serverNow()));
        if(id.equals(DemonHunterSkills.RUSH)||id.equals(DemonHunterSkills.HUNT))p.setDeltaMovement(velocity(a));CareerClient.INSTANCE.publish();
    }
    private static void position(Vec3 desired){var p=player();p.setPos(safe(p.position(),desired));p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;}
    private static void prepareDance(Action a){
        var timing=new DemonHunterChoreography.Dance(a.duration,Math.max(1,(int)a.cast.parameters.get("slashes",2)));var random=new Random(a.cast.id.hashCode());var paths=new ArrayList<DemonHunterChoreography.Segment>();Vec3 cursor=a.from;
        for(int i=0;i<timing.slashes();i++){double angle=random.nextDouble()*Math.PI*2;Vec3 edge=a.center.add(Math.cos(angle)*3,0,Math.sin(angle)*3),from=safe(cursor,edge),to=safe(from,a.center.scale(2).subtract(edge));paths.add(new DemonHunterChoreography.Segment(from,to));cursor=to;}
        Vec3 peak=safe(cursor,a.center.add(0,5,0));a.dance=new DemonHunterChoreography.Scene(timing,paths,new DemonHunterChoreography.Segment(peak,safe(peak,a.center)));
    }
    private static Vec3 velocity(Action a){
        if(a.cast.skill.id.equals(DemonHunterSkills.HUNT))return player().getLookAngle().normalize().scale(DemonHunterChoreography.HUNT_SPEED/20);
        double progress=player().position().subtract(a.from).dot(a.cast.direction),left=Math.max(0,12-progress);
        double desired=12*Math.clamp((ClientUi.now()-a.at)/a.duration,0,1);
        return a.cast.direction.scale(Math.min(left,Math.max(0,desired-progress)));
    }
    /** One native travel step owns movement, collision and the usual player position packet. */
    public static Vec3 movementVelocity(){
        var a=primary;if(a==null||a.target!=null||!Set.of(DemonHunterSkills.RUSH,DemonHunterSkills.HUNT).contains(a.cast.skill.id))return null;
        if(!valid(a)){finish(a,true);return null;}long now=ClientUi.now();if(a.moveAt<0)a.moveAt=now;
        if(a.cast.skill.id.equals(DemonHunterSkills.HUNT)&&now-a.moveAt>=DemonHunterChoreography.HUNT_DURATION)return Vec3.ZERO;
        player().stopFallFlying();player().setNoGravity(true);player().fallDistance=0;return velocity(a);
    }
    private static boolean valid(Action a){return player()!=null&&Minecraft.getInstance().level!=null&&state().ready()&&!state().own().dead&&!player().isDeadOrDying()&&a.session.equals(state().session())&&a.generation==state().own().generation&&a.instance.equals(state().own().instance)&&a.job.equals(state().progress().jobId())&&a.cast.dimension.equals(player().level().dimension().identifier().toString());}
    private static void hit(Action a,LivingEntity target,double coefficient,String phase,String group){MageClient.INSTANCE.issue(a.cast,target,coefficient,3,phase,group);}
    private static void area(Action a,Vec3 center,double radius,double coefficient,String phase,String group){for(var target:SkillController.INSTANCE.targets(new AABB(center,center).inflate(radius,3,radius))){Vec3 near=closest(target,center.add(0,.8,0));if(near.subtract(center).horizontalDistanceSqr()<=radius*radius&&SkillController.INSTANCE.sight(center.add(0,.8,0),target.getBoundingBox().getCenter()))hit(a,target,coefficient,phase,group);}}
    private static Vec3 closest(LivingEntity e,Vec3 p){var b=e.getBoundingBox();return new Vec3(Math.clamp(p.x,b.minX,b.maxX),Math.clamp(p.y,b.minY,b.maxY),Math.clamp(p.z,b.minZ,b.maxZ));}
    private static void path(Action a,Vec3 from,Vec3 to,boolean first){
        var targets=SkillController.INSTANCE.targets(new AABB(from,to).inflate(.7,1,.7)).stream().filter(e->!a.struck.contains(e.getUUID())&&SkillGeometry.intersects(e.getBoundingBox().inflate(.4),from.add(0,.8,0),to.add(0,.8,0))).sorted(Comparator.comparingDouble(e->e.distanceToSqr(from))).toList();
        for(var target:targets){if(!SkillController.INSTANCE.sight(from.add(0,.8,0),target.getBoundingBox().getCenter()))continue;if(first){var state=SkillsClient.current().context(target);if(state==null)continue;a.struck.add(target.getUUID());a.target=target;a.targetGeneration=state.life.generation;a.comboAt=ClientUi.now();a.index=0;player().setDeltaMovement(Vec3.ZERO);hit(a,target,a.cast.parameters.get("finisher",0),"impact","");var combo=new JsonObject();combo.addProperty("at",SkillsClient.current().serverNow());combo.addProperty("huntComboDuration",800/a.cast.speed);combo.addProperty("huntComboSlashes",8);MageClient.INSTANCE.visual(a.cast,a.cast.id,"HIT",target.position(),a.cast.direction,5200,++a.sequence,combo);return;}a.struck.add(target.getUUID());hit(a,target,a.cast.parameters.get("damage",0),"strike","");}
    }
    private static void beam(Action a){
        var p=player();Vec3 from=p.getEyePosition(),direction=p.getLookAngle().normalize(),end=from.add(direction.scale(16));var wall=p.level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        for(var target:SkillController.INSTANCE.targets(new AABB(from,end).inflate(1.5))){var box=target.getBoundingBox().inflate(1.5);if((box.clip(from,end).isPresent()||box.contains(from))&&SkillController.INSTANCE.sight(from,target.getBoundingBox().getCenter()))hit(a,target,a.cast.parameters.get("damage",0),"pulse","");}
        var motion=new JsonObject();motion.addProperty("range",from.distanceTo(end));MageClient.INSTANCE.visual(a.cast,a.cast.id,"SEGMENT",from,direction,DemonHunterChoreography.BEAM_DURATION,++a.sequence,motion);
    }
    public static void tick(){
        if(player()==null||!state().ready())return;long now=ClientUi.now(),wall=state().serverNow();var r=resource();boolean changed=false;long combatUntil=state().resources().combatUntil+wall-now;
        if(state().resources().combatUntil>now)r.decayAt=combatUntil;else if(r.fury>0){long due=Math.max(combatUntil,r.decayAt);if(due==0)due=wall;if(wall>=due){int ticks=1+(int)((wall-due)/1000);r.fury=Math.max(0,r.fury-10*ticks);r.decayAt=due+1000L*ticks;changed=true;}}
        if(r.metamorphosisUntil>0&&r.metamorphosisUntil<=wall){r.metamorphosisUntil=0;r.metamorphosisCast="";changed=true;}
        r.gains.entrySet().removeIf(e->e.getValue()<wall-60000);
        if(aura!=null){var a=aura;if(!valid(a)){finish(a,true);}else{while(a.index<6&&now-a.at>=(a.index+1)*1000){area(a,player().position(),4,a.cast.parameters.get("damage",0),"pulse","pulse/"+a.index++);}if(now-a.at>=6000)finish(a,false);}}
        var a=primary;if(a==null){if(changed)CareerClient.INSTANCE.publish();return;}if(!valid(a)){finish(a,true);return;}
        var p=player();String id=a.cast.skill.id;long elapsed=now-a.at;double dt=Math.clamp((now-a.last)/1000d,0,.25);a.last=now;
        if((id.equals(DemonHunterSkills.BEAM)||id.equals(DemonHunterSkills.HUNT))&&(CombatContentTypes.controlled(p)&&!steadfast()||id.equals(DemonHunterSkills.BEAM)&&(!p.getOffhandItem().isEmpty()||cn.yumis.fight.modules.equipment.api.EquipmentItems.view(p.getMainHandItem())==null||!cn.yumis.fight.modules.equipment.api.EquipmentItems.view(p.getMainHandItem()).instance().toString().equals(a.cast.weapon)))){finish(a,true);return;}
        if(id.equals(DemonHunterSkills.CHAOS)){
            while(a.index<2&&elapsed>=a.index*150/a.cast.speed){Vec3 from=p.getEyePosition(),dir=new Vec3(a.cast.direction.x,0,a.cast.direction.z).normalize();for(var target:SkillController.INSTANCE.targets(p.getBoundingBox().inflate(4,2,4))){Vec3 delta=target.getBoundingBox().getCenter().subtract(from);double front=delta.dot(dir),side=Math.abs(delta.x*dir.z-delta.z*dir.x);if(front>=-.3&&front<=3+target.getBbWidth()*.5&&side<=1.5+target.getBbWidth()*.5&&SkillController.INSTANCE.sight(from,target.getBoundingBox().getCenter()))hit(a,target,a.cast.parameters.get("damage",0),"strike","");}MageClient.INSTANCE.visual(a.cast,a.cast.id,"HIT",p.position(),a.cast.direction,1000,++a.sequence,null);a.index++;}if(elapsed>=300/a.cast.speed)finish(a,false);
        }else if(id.equals(DemonHunterSkills.BEAM)){
            p.move(MoverType.SELF,a.from.lerp(a.to,Math.clamp(elapsed/(double)DemonHunterChoreography.BEAM_RISE,0,1)).subtract(p.position()));p.setDeltaMovement(Vec3.ZERO);
            while(a.index<DemonHunterChoreography.BEAM_HITS&&elapsed>=DemonHunterChoreography.beamHitAt(a.index)){beam(a);a.index++;r.fury=Math.min(DemonHunterRules.MAX_FURY,r.fury+10);changed=true;}if(elapsed>=DemonHunterChoreography.BEAM_DURATION)finish(a,false);
        }else if(id.equals(DemonHunterSkills.DANCE)){
            var timing=a.dance.timing();while(a.index<timing.slashes()&&elapsed>=timing.slashHitAt(a.index)){var route=a.dance.paths().get(a.index);if(a.stage!=a.index)position(route.from());position(route.to());a.stage=a.index++;area(a,a.center,3,a.cast.parameters.get("damage",0),"strike","");MageClient.INSTANCE.visual(a.cast,a.cast.id,"HIT",a.center,route.direction(),(long)a.duration,++a.sequence,null);}
            int active=timing.slashIndex(elapsed);if(active>=0){if(a.stage!=active){position(a.dance.paths().get(active).from());a.stage=active;}position(a.dance.point(elapsed));}else if(elapsed>=timing.fallAt()){if(a.stage!=timing.slashes()){position(a.dance.fall().from());a.stage=timing.slashes();}position(a.dance.point(elapsed));}if(elapsed>=a.duration){area(a,a.center,3,a.cast.parameters.get("finisher",0),"strike","");MageClient.INSTANCE.visual(a.cast,a.cast.id,"FINISH",p.position(),Vec3.ZERO,(long)a.duration+600,++a.sequence,null);finish(a,false);}
        }else if(id.equals(DemonHunterSkills.RUSH)||id.equals(DemonHunterSkills.HUNT)&&a.target==null){
            Vec3 current=p.position();a.travelled+=a.previous.distanceTo(current);path(a,a.previous,current,id.equals(DemonHunterSkills.HUNT));a.previous=current;
            if(a.target==null){Vec3 direction=id.equals(DemonHunterSkills.RUSH)?a.cast.direction:p.getLookAngle().normalize();Vec3 probe=current.add(direction.scale(.1));boolean blocked=safe(current,probe).distanceToSqr(probe)>.0001;
                boolean done=id.equals(DemonHunterSkills.RUSH)?a.to.subtract(current).dot(a.cast.direction)<=.001:a.moveAt>=0&&now-a.moveAt>=DemonHunterChoreography.HUNT_DURATION;
                if(blocked||done||elapsed>a.duration+1800)finish(a,false);else p.setDeltaMovement(velocity(a));}
        }else if(id.equals(DemonHunterSkills.HUNT)){
            var target=state().context(a.target);if(!a.target.isAlive()||target==null||target.life.dead||state().predicted(target.life)<=0||target.life.generation!=a.targetGeneration){finish(a,false);return;}
            Vec3 near=a.target.position().subtract(p.position());if(near.length()>2)p.move(MoverType.SELF,near.normalize().scale(Math.min(near.length()-2,10*dt)));p.setDeltaMovement(Vec3.ZERO);
            if(p.distanceToSqr(a.target)>9||!SkillController.INSTANCE.sight(p.getEyePosition(),a.target.getBoundingBox().getCenter())){finish(a,false);return;}
            while(a.index<8&&now-a.comboAt>=(a.index+1)*100/a.cast.speed){hit(a,a.target,a.cast.parameters.get("damage",0),"strike","");MageClient.INSTANCE.visual(a.cast,a.cast.id,"HIT",p.position(),a.target.position().subtract(p.position()).normalize(),5200,++a.sequence,null);a.index++;}if(a.index>=8)finish(a,false);
        }else if(id.equals(DemonHunterSkills.META)){
            double u=Math.clamp(elapsed/a.duration,0,1);position(a.from.lerp(a.to,u).add(0,4*a.arc*u*(1-u),0));if(u>=1){if(p.position().distanceToSqr(a.to)>.04||!floor(p.position())){finish(a,true);return;}r.metamorphosisUntil=wall+20000;r.metamorphosisCast=a.cast.id;area(a,p.position(),4,a.cast.parameters.get("damage",0),"strike","");MageClient.INSTANCE.visual(a.cast,a.cast.id,"FINISH",p.position(),Vec3.ZERO,1500,++a.sequence,null);finish(a,false);changed=true;}
        }
        p.fallDistance=0;if(changed)CareerClient.INSTANCE.publish();
    }
    public static void contact(SkillCast source,cn.yumis.fight.modules.attributes.api.Stats stats,String basicCycle,boolean basic,double actual){
        if(actual<=0)return;if(basic&&!basicCycle.isEmpty()&&stats.weaponType.equals("WARGLAIVE")){gain("basic/"+basicCycle,10);return;}if(source==null)return;
        if(source.skill.id.equals(DemonHunterSkills.GLAIVE))gain("glaive/"+source.id,20);else if(source.skill.id.equals(DemonHunterSkills.AURA))gain("aura/"+source.id+"/"+source.contactGroup,5);
    }
    public static void gain(String token,int amount){if(!state().progress().baseJob().equals(cn.yumis.fight.modules.character.api.JobCatalog.DEMON_HUNTER)||state().own().dead||resource().gains.putIfAbsent(token,state().serverNow())!=null)return;resource().fury=Math.min(100,resource().fury+amount);resource().decayAt=Math.max(state().serverNow(),state().resources().combatUntil+state().serverNow()-ClientUi.now());CareerClient.INSTANCE.publish();}
    private static void finish(Action a,boolean cancelled){
        if(a==null)return;if(a==aura){aura=null;resource().auraUntil=0;resource().auraCast="";}if(a==primary){primary=null;var p=player();if(p!=null&&!a.cast.skill.id.equals(DemonHunterSkills.CHAOS)){p.setDeltaMovement(Vec3.ZERO);p.setNoGravity(a.previousGravity);p.fallDistance=0;}if(a.cast.skill.id.equals(DemonHunterSkills.DANCE))Effects.VFX.completeCamera(UUID.fromString(a.cast.id));}
        if(player()!=null)MageClient.INSTANCE.visual(a.cast,a.cast.id,cancelled?"CANCEL":"END",player().position(),Vec3.ZERO,1000,++a.sequence,null);CareerClient.INSTANCE.ended(a.cast);CareerClient.INSTANCE.publish();
    }
    public static void cancel(String id){if(primary!=null&&primary.cast.id.equals(id))finish(primary,true);if(aura!=null&&aura.cast.id.equals(id))finish(aura,true);}
    public static void clear(){var p=player();if(primary!=null&&p!=null){p.setDeltaMovement(Vec3.ZERO);p.setNoGravity(primary.previousGravity);p.fallDistance=0;}if(primary!=null&&primary.cast.skill.id.equals(DemonHunterSkills.DANCE))Effects.VFX.completeCamera(UUID.fromString(primary.cast.id));primary=aura=null;aimed=null;}
    public static int hud(GuiGraphicsExtractor g,int x,int y){
        if(!state().progress().baseJob().equals(cn.yumis.fight.modules.character.api.JobCatalog.DEMON_HUNTER))return y;
        x=Math.clamp(x,80,g.guiWidth()-78);var s=resource();long now=state().serverNow();var font=UiFonts.font();
        if(s.auraUntil>now){CareerHud.timer(g,SkillCatalog.require(DemonHunterSkills.AURA),x,y,s.auraUntil-now,6000,0xffb3fa76);y+=68;}
        if(s.metamorphosisUntil>now){g.text(font,"恶魔变形 "+NumberText.number((s.metamorphosisUntil-now)/1000d)+"秒",x-69,y,0xffd5ff9c,true);y+=18;}
        long age=beamAge();if(age>=0){long left=Math.max(0,DemonHunterChoreography.BEAM_DURATION-age);SkillCastBar.render(g,g.guiWidth(),g.guiHeight(),"眼棱 · 引导 "+String.format(Locale.ROOT,"%.1f",left/1000d)+"秒",left/(double)DemonHunterChoreography.BEAM_DURATION,0xffb3fa76);}return y;
    }
}
