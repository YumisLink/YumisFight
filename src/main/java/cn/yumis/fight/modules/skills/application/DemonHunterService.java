package cn.yumis.fight.modules.skills.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.*;

/** Demon hunter content: approved movement, contact schedules and transaction-owned fury. */
public final class DemonHunterService implements CombatPorts.SkillEffects {
    private static final Gson JSON=new Gson();
    private static final Identifier STEADFAST=Identifier.parse("yumis_dungeon_and_fight:demon_charge_steadfast");
    private final MageSkillService mage;private final CombatPorts.Attacks basics;private final Journal journal;private final MinecraftServer server;
    private final Function<ServerPlayer,CombatSession> sessions;private final Function<ServerPlayer,Progress> progress;
    private final Function<ServerPlayer,DemonHunterState> read;private final BiConsumer<ServerPlayer,DemonHunterState> write;private final Consumer<ServerPlayer> sync;
    private record Landing(Vec3 point,double arc){}
    private final Map<String,Action> actions=new LinkedHashMap<>();private final Map<UUID,Landing> aimed=new HashMap<>();
    private static final class Action {
        MageSkillService.Source source;Vec3 center,from,to,peak;List<Vec3> edges=new ArrayList<>();UUID target;long targetGeneration,start,last,next,stageAt;double duration,speed,arc;int index,stage,sequence;Set<UUID> struck=new HashSet<>();
    }
    public DemonHunterService(MageSkillService mage,CombatPorts.Attacks basics,Journal journal,MinecraftServer server,Function<ServerPlayer,CombatSession> sessions,Function<ServerPlayer,Progress> progress,Function<ServerPlayer,DemonHunterState> read,BiConsumer<ServerPlayer,DemonHunterState> write,Consumer<ServerPlayer> sync){
        this.mage=mage;this.basics=basics;this.journal=journal;this.server=server;this.sessions=sessions;this.progress=progress;this.read=read;this.write=write;this.sync=sync;
        for(String id:DemonHunterSkills.ALL)if(!id.equals(DemonHunterSkills.SIGHT)){mage.spell(id,this::release);mage.reduction(id,p->{for(var a:new ArrayList<>(actions.values()))if(a.source.owner.equals(p.getUUID())&&a.source.skill.id.equals(id))finish(a,true);mage.cancelContent(p,List.of(id));});}
        mage.contentLifecycle(this::tick,this::clear);
    }
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("skills","demonCancel",(p,j)->{if(!sessions.apply(p).id.toString().equals(j.get("session").getAsString()))return;var a=actions.get(j.get("id").getAsString());if(a!=null&&a.source.owner.equals(p.getUUID())&&a.source.skill.id.equals(DemonHunterSkills.BEAM))finish(a,true);});}
    public Stats castStats(ServerPlayer p,SkillDefinition skill,Stats source){return skill.id.equals(DemonHunterSkills.META)?DemonHunterRules.transformed(source):source;}
    public void preflight(ServerPlayer p,SkillDefinition skill){
        for(var a:new ArrayList<>(actions.values()))if(a.source.owner.equals(p.getUUID())&&a.source.skill.id.equals(DemonHunterSkills.BEAM)){if(skill.id.equals(DemonHunterSkills.BEAM))throw new IllegalArgumentException("眼棱正在引导");finish(a,true);}
        if(!DemonHunterSkills.contains(skill.id))return;
        if(!p.getOffhandItem().isEmpty())throw new IllegalArgumentException("使用战刃需要副手为空");
        if(read.apply(p).fury<DemonHunterRules.cost(skill.id))throw new IllegalArgumentException("怒气不足");
        if(skill.id.equals(DemonHunterSkills.META))aimed.put(p.getUUID(),landing(p));
    }
    public SkillStartService.Payment payment(ServerPlayer p,SkillDefinition skill,Stats stats,JsonObject ack){
        if(!DemonHunterSkills.contains(skill.id))return SkillStartService.Payment.NONE;
        var before=read.apply(p).copy();var next=before.copy();int cost=DemonHunterRules.cost(skill.id);if(next.fury<cost)throw new IllegalArgumentException("怒气不足");next.fury-=cost;
        if(skill.id.equals(DemonHunterSkills.AURA)){next.auraUntil=ack.get("serverNow").getAsLong()+6000;next.auraCast=ack.get("id").getAsString();}
        ack.addProperty("instant",true);String key=key(p);return new SkillStartService.Payment(Map.of(key,JSON.toJsonTree(next)),Map.of(key,JSON.toJsonTree(before)),()->{write.accept(p,next);sync.accept(p);},()->{write.accept(p,before);sync.accept(p);});
    }
    private String key(ServerPlayer p){return "skillState/demon/"+p.getUUID()+"/"+sessions.apply(p).id;}
    private void save(ServerPlayer p,DemonHunterState next){try{journal.transaction(Map.of(key(p),JSON.toJsonTree(next)));}catch(java.io.IOException failure){throw new IllegalStateException("怒气状态提交失败",failure);}write.accept(p,next);sync.accept(p);}
    private Action primary(ServerPlayer p){return actions.values().stream().filter(a->a.source.owner.equals(p.getUUID())&&!a.source.skill.id.equals(DemonHunterSkills.AURA)).findFirst().orElse(null);}
    public boolean locked(ServerPlayer p){return primary(p)!=null;}
    public boolean hardLocked(ServerPlayer p){var a=primary(p);return a!=null&&!a.source.skill.id.equals(DemonHunterSkills.BEAM);}
    public boolean invulnerable(ServerPlayer p){var a=primary(p);return a!=null&&mage.valid(a.source)&&(a.source.skill.id.equals(DemonHunterSkills.DANCE)||a.source.skill.id.equals(DemonHunterSkills.META)||a.source.skill.id.equals(DemonHunterSkills.HUNT)&&a.target!=null);}
    public boolean steadfast(ServerPlayer p){var a=primary(p);return a!=null&&mage.valid(a.source)&&a.source.skill.id.equals(DemonHunterSkills.HUNT)&&a.target==null;}
    private Runnable release(MageSkillService.Source source){
        if(source.skill.id.equals(DemonHunterSkills.GLAIVE))return ()->mage.startProjectile(source);
        var p=mage.owner(source);var a=new Action();a.source=source;a.start=a.last=a.stageAt=System.currentTimeMillis();a.speed=Math.max(.01,source.stats.get(Stat.CAST_SPEED));a.center=p.position();a.from=p.position();a.to=a.from;a.duration=source.parameters.get("duration",1)*1000/(source.skill.id.equals(DemonHunterSkills.AURA)||source.skill.id.equals(DemonHunterSkills.BEAM)||source.skill.id.equals(DemonHunterSkills.HUNT)?1:a.speed);a.next=a.start;
        if(source.skill.id.equals(DemonHunterSkills.META)){var landing=aimed.remove(p.getUUID());if(landing==null)throw new IllegalStateException("觉醒落点缺失");a.to=landing.point();a.arc=landing.arc();}
        if(source.skill.id.equals(DemonHunterSkills.RUSH))a.to=safe(p,a.from,a.from.add(source.direction.scale(12)));
        if(source.skill.id.equals(DemonHunterSkills.DANCE)){var random=new Random(source.id.hashCode());int count=(int)source.parameters.get("slashes",2);for(int i=0;i<count;i++){double angle=random.nextDouble()*Math.PI*2;a.edges.add(a.center.add(Math.cos(angle)*3,0,Math.sin(angle)*3));}}
        return ()->{
            if(source.skill.id.equals(DemonHunterSkills.AURA))for(var old:new ArrayList<>(actions.values()))if(old.source.owner.equals(source.owner)&&old.source.skill.id.equals(DemonHunterSkills.AURA))finish(old,false);
            actions.put(source.id,a);if(gravity(a))DemonHunterMotion.begin(p);
            if(source.skill.id.equals(DemonHunterSkills.HUNT))protection(p,true);
            motion(a,source.skill.id.equals(DemonHunterSkills.BEAM)?"BEAM":source.skill.id.equals(DemonHunterSkills.HUNT)?"CHARGE":source.skill.id.equals(DemonHunterSkills.AURA)?"AURA":"START",a.from,a.to,a.start,(long)a.duration,a.arc);
            mage.visual(source,source.id,"START",a.center,source.direction,(long)Math.ceil(a.duration)+(source.skill.id.equals(DemonHunterSkills.CHAOS)?0:600),0,null);
        };
    }
    private static boolean floor(ServerPlayer p,Vec3 point){return p.level().clip(new ClipContext(point.add(0,.04,0),point.add(0,-.12,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS;}
    private static Landing landing(ServerPlayer p){
        Vec3 aim=SkillTargeting.ground(p,16,e->MageSkillService.eligible(p,e)).point();var ground=p.level().clip(new ClipContext(aim.add(0,.1,0),aim.add(0,-16,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(ground.getType()==HitResult.Type.MISS)throw new IllegalArgumentException("没有可安全落地的位置");Vec3 to=ground.getLocation().add(0,.01,0),from=p.position();var box=p.getBoundingBox().move(to.subtract(from));
        if(from.distanceToSqr(to)>256||!p.level().noCollision(p,box)||p.level().containsAnyLiquid(box)||!p.level().getWorldBorder().isWithinBounds(BlockPos.containing(to)))throw new IllegalArgumentException("落点空间不足或超出16格");
        for(double height:new double[]{3,2,1,0}){boolean valid=true;for(int i=1;i<=80;i++){double u=i/80d;Vec3 point=from.lerp(to,u).add(0,4*height*u*(1-u),0);var pos=BlockPos.containing(point);if(!p.level().hasChunkAt(pos)||!p.level().getWorldBorder().isWithinBounds(pos)||!p.level().noCollision(p,p.getBoundingBox().move(point.subtract(from)))){valid=false;break;}}if(valid)return new Landing(to,height);}
        throw new IllegalArgumentException("无法安全跃向该落点");
    }
    private static boolean gravity(Action a){return Set.of(DemonHunterSkills.RUSH,DemonHunterSkills.DANCE,DemonHunterSkills.HUNT,DemonHunterSkills.META).contains(a.source.skill.id);}
    private static boolean sight(ServerPlayer p,Vec3 from,LivingEntity target){return p.level().clip(new ClipContext(from,target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;}
    private static Vec3 safe(ServerPlayer p,Vec3 from,Vec3 destination){
        Vec3 delta=destination.subtract(from);int steps=Math.max(1,(int)Math.ceil(delta.length()/.2));Vec3 result=from;
        for(int i=1;i<=steps;i++){Vec3 point=from.add(delta.scale(i/(double)steps));var pos=BlockPos.containing(point);var box=p.getBoundingBox().move(point.subtract(p.position()));if(!p.level().hasChunkAt(pos)||!p.level().getWorldBorder().isWithinBounds(pos)||!p.level().noCollision(p,box))break;result=point;}return result;
    }
    private void position(ServerPlayer p,Vec3 desired){p.setPos(safe(p,p.position(),desired));p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;}
    private void area(Action a,Vec3 center,double radius,double coefficient,String phase,String group){
        var p=mage.owner(a.source);for(var target:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius,3,radius),e->MageSkillService.eligible(p,e))){var near=MageSkillService.closest(target,center.add(0,.8,0));if(near.subtract(center).horizontalDistanceSqr()<=radius*radius&&sight(p,center.add(0,.8,0),target))mage.issuePhase(a.source,target,center,null,false,coefficient,3,phase,group);}
    }
    private void strike(Action a){var p=mage.owner(a.source);Vec3 from=p.getEyePosition(),direction=new Vec3(a.source.direction.x,0,a.source.direction.z).normalize();for(var target:p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(4,2,4),e->MageSkillService.eligible(p,e))){var delta=target.getBoundingBox().getCenter().subtract(from);double forward=delta.dot(direction),side=Math.abs(delta.x*direction.z-delta.z*direction.x);if(forward>=-.3&&forward<=3+target.getBbWidth()*.5&&side<=1.5+target.getBbWidth()*.5&&sight(p,from,target))mage.issuePhase(a.source,target,p.position(),null,false,a.source.parameters.get("damage",0),3,"strike","");}}
    private void path(Action a,Vec3 from,Vec3 to,boolean first){
        var p=mage.owner(a.source);var list=p.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(.7,1,.7),e->MageSkillService.eligible(p,e)&&!a.struck.contains(e.getUUID())&&SkillGeometry.intersects(e.getBoundingBox().inflate(.4),from.add(0,.8,0),to.add(0,.8,0))).stream().sorted(Comparator.comparingDouble(e->e.distanceToSqr(from))).toList();
        for(var target:list){if(!sight(p,from.add(0,.8,0),target))continue;a.struck.add(target.getUUID());if(first){a.target=target.getUUID();a.targetGeneration=mage.targetGeneration(target);a.stageAt=System.currentTimeMillis();a.index=0;protection(p,false);mage.issuePhase(a.source,target,target.position(),null,false,a.source.parameters.get("finisher",0),3,"impact","");mage.visual(a.source,a.source.id,"HIT",target.position(),a.source.direction,5200,++a.sequence,null);motion(a,"COMBO",p.position(),target.position(),a.stageAt,(long)(800/a.speed),0);return;}mage.issuePhase(a.source,target,target.position(),null,false,a.source.parameters.get("damage",0),3,"strike","");}
    }
    private void beam(Action a){
        var p=mage.owner(a.source);Vec3 from=p.getEyePosition(),direction=p.getLookAngle().normalize(),end=from.add(direction.scale(16));var wall=p.level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(wall.getType()!=HitResult.Type.MISS)end=wall.getLocation();
        for(var target:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(1.5),e->MageSkillService.eligible(p,e))){var contact=target.getBoundingBox().inflate(1.5).clip(from,end);if((contact.isPresent()||target.getBoundingBox().inflate(1.5).contains(from))&&sight(p,from,target))mage.issuePhase(a.source,target,from,null,false,a.source.parameters.get("damage",0),3,"pulse","");}
        var motion=new JsonObject();motion.addProperty("range",from.distanceTo(end));mage.visual(a.source,a.source.id,"SEGMENT",from,direction,5300,++a.sequence,motion);
    }
    private void dance(Action a,long now){
        var p=mage.owner(a.source);long elapsed=now-a.start;double half=a.duration/2,interval=half/a.edges.size();
        while(a.index<a.edges.size()&&elapsed>=a.index*interval){
            if(a.stage!=a.index+1){a.from=safe(p,p.position(),a.edges.get(a.index));a.to=safe(p,a.from,a.center.scale(2).subtract(a.edges.get(a.index)));position(p,a.from);a.stage=a.index+1;motion(a,"SLASH",a.from,a.to,a.start+(long)(a.index*interval),(long)interval,0);}
            double u=Math.clamp((elapsed-a.index*interval)/interval,0,1);position(p,a.from.lerp(a.to,u));if(u<1)break;area(a,a.center,3,a.source.parameters.get("damage",0),"strike","");mage.visual(a.source,a.source.id,"HIT",a.center,a.to.subtract(a.from),Math.round(a.duration)+600,++a.sequence,null);a.index++;
        }
        if(elapsed>=half){
            if(a.stage<100){a.from=p.position();a.peak=safe(p,a.from,a.center.add(0,5,0));a.stage=100;motion(a,"RISE",a.from,a.peak,a.start+(long)half,(long)(half/2),0);}
            if(elapsed<half*1.5)position(p,a.from.lerp(a.peak,Math.clamp((elapsed-half)/(half/2),0,1)));
            else {if(a.stage==100){a.stage=101;motion(a,"FALL",a.peak,a.center,a.start+(long)(half*1.5),(long)(half/2),0);}position(p,a.peak.lerp(a.center,Math.clamp((elapsed-half*1.5)/(half/2),0,1)));}
        }
        if(elapsed>=a.duration){area(a,a.center,3,a.source.parameters.get("finisher",0),"strike","");mage.visual(a.source,a.source.id,"FINISH",p.position(),Vec3.ZERO,Math.round(a.duration)+600,++a.sequence,null);finish(a,false);}
    }
    private void tick(long now){
        for(var p:server.getPlayerList().getPlayers())if(progress.apply(p).baseJob().equals(JobCatalog.DEMON_HUNTER)){var before=read.apply(p);var next=before.copy();boolean changed=false;long until=sessions.apply(p).resources.combatUntil;
            if(now<until)next.decayAt=until;else if(next.fury>0){long due=Math.max(until,next.decayAt);if(due==0)due=now;if(now>=due){int ticks=1+(int)((now-due)/1000);next.fury=Math.max(0,next.fury-10*ticks);next.decayAt=due+1000L*ticks;changed=true;}}
            if(next.auraUntil>0&&next.auraUntil<=now){next.auraUntil=0;next.auraCast="";changed=true;}if(next.metamorphosisUntil>0&&next.metamorphosisUntil<=now){next.metamorphosisUntil=0;next.metamorphosisCast="";changed=true;}
            if(changed)save(p,next);else if(next.decayAt!=before.decayAt)write.accept(p,next);
        }
        for(var a:new ArrayList<>(actions.values())){
            var p=mage.owner(a.source);if(p==null||!mage.valid(a.source)){finish(a,true);continue;}
            String id=a.source.skill.id;boolean beam=id.equals(DemonHunterSkills.BEAM);
            if((beam||id.equals(DemonHunterSkills.HUNT))&&(p.hasEffect(CombatContentTypes.STUN)&&!steadfast(p)||beam&&(!p.getOffhandItem().isEmpty()||!a.source.weapon.equals(weapon(p))))){finish(a,true);continue;}
            long elapsed=now-a.start;double dt=Math.min(.25,Math.max(0,now-a.last)/1000d);a.last=now;
            try{
                if(id.equals(DemonHunterSkills.AURA)){while(a.index<6&&elapsed>=(a.index+1)*1000){area(a,p.position(),4,a.source.parameters.get("damage",0),"pulse","pulse/"+a.index);a.index++;}if(elapsed>=6000)finish(a,false);}
                else if(id.equals(DemonHunterSkills.CHAOS)){while(a.index<2&&elapsed>=a.index*150/a.speed){strike(a);mage.visual(a.source,a.source.id,"HIT",p.position(),a.source.direction,1000,++a.sequence,null);a.index++;}if(elapsed>=300/a.speed)finish(a,false);}
                else if(beam){while(a.index<20&&elapsed>=(a.index+1)*250){beam(a);a.index++;}if(elapsed>=5000)finish(a,false);}
                else if(id.equals(DemonHunterSkills.DANCE))dance(a,now);
                else if(id.equals(DemonHunterSkills.RUSH)){var before=p.position();position(p,a.from.lerp(a.to,Math.clamp(elapsed/a.duration,0,1)));path(a,before,p.position(),false);if(elapsed>=a.duration)finish(a,false);}
                else if(id.equals(DemonHunterSkills.HUNT)){
                    if(a.target==null){var before=p.position();var desired=before.add(p.getLookAngle().normalize().scale(10*dt));var to=safe(p,before,desired);path(a,before,to,true);if(a.target==null)position(p,to);if(a.target==null&&(elapsed>=4000||to.distanceToSqr(desired)>.0001))finish(a,false);}
                    else {var entity=p.level().getEntity(a.target);if(!(entity instanceof LivingEntity target)||!mage.targetAlive(target)||mage.targetGeneration(target)!=a.targetGeneration){finish(a,false);continue;}Vec3 near=target.position().subtract(p.position());if(near.length()>2)position(p,p.position().add(near.normalize().scale(Math.min(near.length()-2,10*dt))));if(p.distanceToSqr(target)>9||!sight(p,p.getEyePosition(),target)){finish(a,false);continue;}while(a.index<8&&now-a.stageAt>=(a.index+1)*100/a.speed){mage.issuePhase(a.source,target,p.position(),null,false,a.source.parameters.get("damage",0),3,"strike","");mage.visual(a.source,a.source.id,"HIT",p.position(),target.position().subtract(p.position()).normalize(),5200,++a.sequence,null);a.index++;}if(a.index>=8)finish(a,false);}
                }
                else if(id.equals(DemonHunterSkills.META)){double u=Math.clamp(elapsed/a.duration,0,1);position(p,a.from.lerp(a.to,u).add(0,4*a.arc*u*(1-u),0));if(u>=1){if(p.position().distanceToSqr(a.to)>.04||!floor(p,p.position())){finish(a,true);continue;}var state=read.apply(p).copy();state.metamorphosisUntil=now+20000;state.metamorphosisCast=a.source.id;save(p,state);area(a,p.position(),4,a.source.parameters.get("damage",0),"strike","");mage.visual(a.source,a.source.id,"FINISH",p.position(),Vec3.ZERO,1500,++a.sequence,null);finish(a,false);}}
            }catch(RuntimeException failure){finish(a,true);p.sendOverlayMessage(net.minecraft.network.chat.Component.literal(Objects.toString(failure.getMessage(),"技能执行失败")));}
        }
    }
    private static String weapon(ServerPlayer p){var hand=EquipmentItems.gear(p.getMainHandItem());return hand==null?"":hand.instance.toString();}
    private static void protection(ServerPlayer p,boolean active){for(var kind:List.of(Attributes.KNOCKBACK_RESISTANCE,Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)){var attribute=p.getAttribute(kind);if(attribute==null)continue;attribute.removeModifier(STEADFAST);if(active)attribute.addTransientModifier(new AttributeModifier(STEADFAST,1,AttributeModifier.Operation.ADD_VALUE));}}
    private void motion(Action a,String phase,Vec3 from,Vec3 to,long at,long duration,double arc){var p=mage.owner(a.source);if(p==null)return;var j=new JsonObject();j.addProperty("id",a.source.id);j.addProperty("skill",a.source.skill.id);j.addProperty("session",a.source.session);j.addProperty("generation",a.source.generation);j.addProperty("phase",phase);j.addProperty("dimension",a.source.dimension);j.addProperty("instance",a.source.instance);j.addProperty("job",a.source.job);j.addProperty("at",at);j.addProperty("duration",Math.max(1,duration));j.addProperty("arc",arc);j.addProperty("target",a.target==null?"":a.target.toString());j.addProperty("targetEntity",a.target==null||p.level().getEntity(a.target)==null?-1:p.level().getEntity(a.target).getId());j.add("from",JSON.toJsonTree(new double[]{from.x,from.y,from.z}));j.add("to",JSON.toJsonTree(new double[]{to.x,to.y,to.z}));Network.send(p,"demonMotion",j);}
    private void finish(Action a,boolean cancelled){if(actions.remove(a.source.id)==null)return;var p=mage.owner(a.source);if(p!=null){if(gravity(a))DemonHunterMotion.restore(p);if(a.source.skill.id.equals(DemonHunterSkills.HUNT))protection(p,false);motion(a,"END",p.position(),p.position(),System.currentTimeMillis(),1,0);mage.visual(a.source,a.source.id,cancelled?"CANCEL":"END",p.position(),Vec3.ZERO,1000,++a.sequence,null);}a.source.naturalEnd=true;if(cancelled)a.source.cancelled=true;mage.finished(a.source);}
    private void clear(UUID owner){for(var a:new ArrayList<>(actions.values()))if(a.source.owner.equals(owner))finish(a,true);aimed.remove(owner);}
    public void cancelled(ServerPlayer p,String id){var a=actions.get(id);if(a!=null)finish(a,true);aimed.remove(p.getUUID());}
    @Override public CombatPorts.Commit prepare(ServerPlayer p,LivingEntity target,JsonObject report,Receipt receipt,Life actor){
        if(!receipt.accepted()||receipt.actual()<=0||target==p||actor.dead||!progress.apply(p).baseJob().equals(JobCatalog.DEMON_HUNTER))return CombatPorts.Commit.NONE;
        String token="";int gain=0;var contact=mage.contact(p,report);var basicStats=basics.sourceStats(p,report);
        if(report.has("basicCycle")&&report.has("basicSlash")&&!report.has("sourceCast")&&!report.has("mageAttack")&&basicStats!=null&&basicStats.weaponType.equals("WARGLAIVE")){token="basic/"+report.get("basicCycle").getAsString();gain=10;}
        else if(contact!=null&&contact.skill().equals(DemonHunterSkills.GLAIVE)){token="glaive/"+contact.cast();gain=20;}
        else if(contact!=null&&contact.skill().equals(DemonHunterSkills.AURA)){token="aura/"+contact.cast()+"/"+contact.group();gain=5;}
        if(gain==0)return CombatPorts.Commit.NONE;var current=read.apply(p);if(current.gains.containsKey(token))return CombatPorts.Commit.NONE;
        var next=current.copy();long now=System.currentTimeMillis();next.gains.entrySet().removeIf(e->e.getValue()<now-60000);next.gains.put(token,now);next.fury=Math.min(100,next.fury+gain);next.decayAt=Math.max(now,sessions.apply(p).resources.combatUntil);
        return new CombatPorts.Commit(){public void persist()throws Exception{journal.put(key(p),next);}public void publish(){write.accept(p,next);sync.accept(p);}};
    }
    private static String weaponType(ServerPlayer p){var g=EquipmentItems.gear(p.getMainHandItem());return g==null?"":g.weapon.name();}
}
