package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.combat.api.CombatTiming;
import cn.yumis.fight.modules.equipment.api.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Oracle commands, weapon choice, poses and contacts all belong to the owner. */
public final class OracleClient {
    public static final OracleClient INSTANCE=new OracleClient();
    private static final class Run {
        SkillCast cast;Stats frozen;SkillBehaviors.Context context;int index=-1,count,originalSlot,stepSlot=-1;UUID originalWeapon;
        String originalType,pausedInstruction="";long instructionLeft,stageAt;double moved;Vec3 axis;LivingEntity target;
        long targetGeneration;boolean resolved;final Map<UUID,LivingEntity> contacts=new LinkedHashMap<>();
    }
    private final Map<String,Run> runs=new LinkedHashMap<>();
    private final Random random=new Random();
    private OracleClient(){}
    private static SkillsClient state(){return SkillsClient.current();}
    private static net.minecraft.client.player.LocalPlayer player(){return Minecraft.getInstance().player;}
    public static void install(){
        SkillBehaviors.registerStart(OracleSkills.RIFT,(x,c,now)->{c.hitIndex=0;x.path(c,x.player().position(),x.player().position(),3,3,c.parameters.get("damage",0),.2);});
        SkillBehaviors.registerStart(OracleSkills.SMILE,(x,c,now)->{x.player().setPos(c.origin);heading(c.direction);x.player().setDeltaMovement(Vec3.ZERO);});
        for(String id:OracleSkills.ACTIONS)SkillBehaviors.register(id,(x,c,now,elapsed,stage,speed)->normal(x,c,now,elapsed));
        SkillBehaviors.registerStart(OracleSkills.REPLICA,INSTANCE::start);SkillBehaviors.register(OracleSkills.REPLICA,INSTANCE::replica);
    }
    public SkillDefinition resolve(SkillDefinition binding){if(!binding.bindingProxy)return binding;String id=CareerClient.INSTANCE.view.oracle.instruction;if(id.isEmpty()||!SkillCatalog.contains(id)){state().message("当前没有待执行指令");return null;}return SkillCatalog.require(id);}
    public void prepareInput(SkillDefinition binding){
        var command=CareerClient.INSTANCE.view.oracle;String id=binding.bindingProxy?command.instruction:binding.id;int index=OracleSkills.ACTIONS.indexOf(id);
        if(index<0||state().progress().learned(id)<=0||!OracleRules.matches(command,SkillCatalog.require(id),state().serverNow()))return;int selected=weaponSlot(OracleSkills.WEAPONS.get(index));if(selected>=0)player().getInventory().setSelectedSlot(selected);
    }
    public Stats prepareStats(SkillDefinition skill,Stats source){
        var command=CareerClient.INSTANCE.view.oracle;int rank=OracleRules.rank(source,state().progress());if(rank<=0||!OracleRules.matches(command,skill,state().serverNow()))return source;
        var result=OracleRules.instruction(source);int gained=command.hermes<OracleRules.cap(state().progress())?1:0;result.modifier("final").mut+=gained*OracleRules.layer(rank);return result;
    }
    public boolean prepare(CareerState next,SkillDefinition skill,Stats stats,long now){int rank=OracleRules.rank(stats,state().progress());if(skill.id.equals(OracleSkills.REPLICA)&&(rank<=0||next.oracle.hermes<1))throw new IllegalArgumentException("至少需要一层代行-赫尔墨斯");return rank>0&&OracleRules.succeed(next.oracle,state().progress(),skill,rank,now);}
    public SkillParameters.Snapshot parameters(SkillParameters.Snapshot input,CareerState next,long now){
        if(!OracleSkills.contains(input.skill().id))return input;var values=new LinkedHashMap<>(input.values());
        if(input.skill().id.equals(OracleSkills.REPLICA)){int stacks=next.oracle.hermes;values.put("replicaStacks",(double)stacks);values.put("duration",stacks==9?OracleSkills.FULL_DURATION:stacks*.35);}
        if(input.skill().id.equals(OracleSkills.SMILE)){var target=target(null,10);Vec3 point=target==null?player().position():behind(target),direction=target==null?flat(player().getLookAngle()):flat(target.position().subtract(point));values.put("oracleX",point.x);values.put("oracleY",point.y);values.put("oracleZ",point.z);values.put("oracleDX",direction.x);values.put("oracleDZ",direction.z);}
        return new SkillParameters.Snapshot(input.skill(),input.rank(),values);
    }
    public Vec3 origin(SkillParameters.Snapshot p,Vec3 fallback){return p.values().containsKey("oracleX")?new Vec3(p.get("oracleX",0),p.get("oracleY",0),p.get("oracleZ",0)):fallback;}
    public Vec3 direction(SkillParameters.Snapshot p,Vec3 fallback){return p.values().containsKey("oracleDX")?new Vec3(p.get("oracleDX",0),0,p.get("oracleDZ",1)):fallback;}
    private void start(SkillBehaviors.Context x,SkillCast c,long now){
        var r=new Run();r.cast=c;r.context=x;r.frozen=c.stats.copy();r.originalSlot=player().getInventory().getSelectedSlot();var gear=EquipmentItems.view(player().getMainHandItem());r.originalWeapon=gear.instance();r.originalType=gear.weapon().name();r.axis=c.direction;r.stageAt=now;
        int stacks=(int)c.parameters.get("replicaStacks",1);r.count=stacks==9?OracleSkills.FULL_STEPS:stacks;runs.put(c.id,r);var command=CareerClient.INSTANCE.view.oracle;command.cast=c.id;command.castingUntil=state().serverNow()+(long)(c.parameters.get("duration",OracleSkills.FULL_DURATION)*1000/c.speed)+5000;
        if(!command.instruction.isEmpty()){r.pausedInstruction=command.instruction;r.instructionLeft=Math.max(0,command.instructionUntil-state().serverNow());command.instructionPausedLeft=r.instructionLeft;}
        TrainingCombat.hold(c.id,"oracle",ClientUi.now()+(long)(c.parameters.get("duration",OracleSkills.FULL_DURATION)*1000/c.speed)+5000);CareerClient.INSTANCE.publish();
    }
    public boolean locked(){return !runs.isEmpty();}
    private void finish(Run r){
        runs.remove(r.cast.id);if(player()!=null)player().getInventory().setSelectedSlot(r.originalSlot);
        var command=CareerClient.INSTANCE.view.oracle;if(!r.pausedInstruction.isEmpty()&&command.instruction.equals(r.pausedInstruction))command.instructionUntil=state().serverNow()+r.instructionLeft;
        command.instructionPausedLeft=0;command.cast="";command.castingUntil=0;command.hermes=0;command.hermesUntil=0;TrainingCombat.release(r.cast.id,"oracle");if(MageClient.INSTANCE.valid(r.cast))CareerClient.INSTANCE.publish();
    }
    public void cancel(String id){var r=runs.get(id);if(r!=null)finish(r);}
    public void clear(){for(var r:new ArrayList<>(runs.values()))finish(r);runs.clear();}
    public void tick(long localNow){
        for(var r:new ArrayList<>(runs.values()))if(!MageClient.INSTANCE.valid(r.cast)||localNow-r.cast.start>r.cast.parameters.get("duration",OracleSkills.FULL_DURATION)*1000/r.cast.speed+5000)finish(r);
        var career=CareerClient.INSTANCE.view;var command=career.oracle;var progress=state().progress();if(!progress.hasJob(JobCatalog.ORACLE))return;long now=state().serverNow();int rank=OracleRules.rank(state().stats(),progress);String before=command.instruction+"/"+command.hermes+"/"+command.karma+"/"+command.weapon;
        OracleRules.preparation(command,progress,CombatTiming.out(state().resources(),localNow),locked(),now);
        if(rank<=0){if(!locked()&&command.initialized){var reset=new OracleState();reset.awakeningObserved=command.awakeningObserved;reset.awakeningLearned=command.awakeningLearned;reset.awakeningGrantPending=command.awakeningGrantPending;reset.outOfCombatSince=command.outOfCombatSince;reset.hermes=command.hermes;career.oracle=reset;}return;}
        if(!command.initialized){command.initialized=true;command.nextInstruction=now+10000;command.weaponUntil=0;}
        if(now>=command.weaponUntil){var choices=new ArrayList<>(OracleSkills.WEAPONS);choices.remove(command.weapon);command.weapon=choices.get(random.nextInt(choices.size()));command.weaponUntil=now+30000;}
        if(command.karmaUntil<=now)command.karma=0;if(locked())return;
        int cap=OracleRules.cap(progress);if(command.hermes>cap){command.hermes=cap;command.hermesUntil=now+30000;command.instruction="";command.nextInstruction=now;}
        if(command.hermes==9){command.hermesUntil=0;command.instruction=OracleSkills.REPLICA;command.instructionUntil=0;}
        else{
            if(command.hermes>0&&command.hermesUntil<=now){command.hermes=0;command.hermesUntil=0;}
            if(!command.instruction.isEmpty()&&command.instructionUntil<=now){if(!CombatTiming.out(state().resources(),localNow)){command.karma=Math.min(5,command.karma+1);command.karmaUntil=now+15000;}command.instruction="";command.nextInstruction=now;}
            if(command.instruction.isEmpty()&&now>=command.nextInstruction){var pool=SkillCatalog.all().stream().filter(s->!s.passive()&&!s.equipmentGranted&&!s.id.equals(OracleSkills.REPLICA)&&s.available(progress)&&progress.learned(s.id)>0&&state().resources().cooldowns.getOrDefault(s.id,0L)-localNow<5000).toList();if(pool.isEmpty())command.nextInstruction=now+1000;else{command.instruction=pool.get(random.nextInt(pool.size())).id;command.instructionUntil=now+10000;}}
        }
        if(!before.equals(command.instruction+"/"+command.hermes+"/"+command.karma+"/"+command.weapon))CareerClient.INSTANCE.publish();
    }
    private static void normal(SkillBehaviors.Context x,SkillCast c,long now,long elapsed){
        String id=c.skill.id;double damage=c.parameters.get("damage",0);
        if(id.equals(OracleSkills.DOWN)&&elapsed>=300&&!c.finisher){c.finisher=true;c.hitIndex=0;x.player().swing(net.minecraft.world.InteractionHand.MAIN_HAND);x.slash(c,c.parameters.get("range",4),3,3,damage,.3,false);x.visual(c,"HIT");}
        if(id.equals(OracleSkills.SWEEP)&&elapsed>=500&&!c.finisher){c.finisher=true;c.hitIndex=0;c.sweepDegrees=145;x.slash(c,c.parameters.get("range",5),5,3,damage,.4,true);x.visual(c,"RELEASE");}
        if(id.equals(OracleSkills.THRUST)||id.equals(OracleSkills.RIFT)){double length=c.parameters.get("range",id.equals(OracleSkills.THRUST)?6:5),duration=id.equals(OracleSkills.THRUST)?250:350,desired=length*Math.clamp(elapsed/duration,0,1);Vec3 from=x.player().position();move(x,c,desired-c.moved);c.moved=desired;c.hitIndex=0;x.path(c,from,x.player().position(),id.equals(OracleSkills.THRUST)?2.5:3,3,damage,.2);if(now-c.visualTrail>=80){x.visual(c,"TRAIL");c.visualTrail=now;}}
        if(id.equals(OracleSkills.SMILE))for(int i=c.slashes;i<2&&elapsed>=(i==0?100:300);i++){c.hit.clear();c.hitIndex=i;x.slash(c,c.parameters.get("range",4),3,3,damage*.5,.2,false);x.visual(c,"HIT");c.slashes++;}
        if(elapsed>=c.parameters.get("duration",.6)*1000)x.cancel(true);
    }
    private int originalSlot(Run r){for(int slot=0;slot<9;slot++){var gear=EquipmentItems.view(player().getInventory().getItem(slot));if(gear!=null&&gear.instance().equals(r.originalWeapon)&&gear.valid(state().progress(),EquipmentEligibility.allowance(player())))return slot;}return -1;}
    private int weaponSlot(String type){for(int slot=0;slot<9;slot++){var gear=EquipmentItems.view(player().getInventory().getItem(slot));if(gear!=null&&gear.slot()==Slot.WEAPON&&gear.weapon().name().equals(type)&&gear.valid(state().progress(),EquipmentEligibility.allowance(player())))return slot;}return -1;}
    private int fallbackSlot(){for(int slot=0;slot<9;slot++){var gear=EquipmentItems.view(player().getInventory().getItem(slot));if(gear!=null&&JobCatalog.weapons(state().progress().jobId()).contains(gear.weapon().name())&&gear.valid(state().progress(),EquipmentEligibility.allowance(player())))return slot;}return -1;}
    private boolean next(Run r,long at){
        int index=++r.index;if(index>=r.count)return false;var c=r.cast;int action=index>=8?4:OracleSkills.action(c.id.hashCode(),index);String type=index>=8?r.originalType:OracleSkills.WEAPONS.get(action);int selected=index<8?weaponSlot(type):originalSlot(r);if(index==OracleSkills.FINISH_STEP&&selected<0)selected=fallbackSlot();if(index==OracleSkills.FINISH_STEP&&selected<0)return false;
        if(selected>=0){player().getInventory().setSelectedSlot(selected);r.stepSlot=selected;if(index==OracleSkills.FINISH_STEP)type=EquipmentItems.view(player().getMainHandItem()).weapon().name();}
        r.target=target(r.target,12);var targetState=r.target==null?null:state().context(r.target);r.targetGeneration=targetState==null?-1:targetState.life.generation;
        c.origin=r.target==null?player().position():around(r.target,c.id.hashCode(),index);c.direction=r.target==null?r.axis:flat(r.target.position().subtract(c.origin));r.axis=c.direction;c.center=c.origin;c.stats=OracleRules.stepStats(r.frozen,CareerClient.INSTANCE.view.oracle,type,selected>=0);c.visualWeapon=type;c.hit.clear();c.slashes=0;c.finisher=false;c.release=false;c.sweepDegrees=0;c.charging=index==OracleSkills.FINISH_STEP;
        r.stageAt=at;r.moved=0;r.resolved=false;r.contacts.clear();player().setPos(c.origin);heading(c.direction);player().setDeltaMovement(Vec3.ZERO);player().fallDistance=0;player().swing(net.minecraft.world.InteractionHand.MAIN_HAND);r.context.visual(c,"SEGMENT");return true;
    }
    private boolean alive(LivingEntity e,long generation){if(e==null||!e.isAlive())return false;var t=state().context(e);return t!=null&&!t.life.dead&&state().predicted(t.life)>0&&(generation<0||t.life.generation==generation);}
    private void combo(Run r,Vec3 from,Vec3 to,boolean completed){
        var c=r.cast;double width=3*c.parameters.get("meleeScale",1);Vec3 start=from.add(0,.8,0),end=to.add(0,.8,0);
        for(var target:SkillController.INSTANCE.targets(new AABB(start,end).inflate(width/2,1.5,width/2)))if(SkillGeometry.intersects(target.getBoundingBox().inflate(width/2,1.5,width/2),start,end)&&SkillController.INSTANCE.sight(start,target.getBoundingBox().getCenter()))r.contacts.putIfAbsent(target.getUUID(),target);
        if(r.resolved)return;LivingEntity target=alive(r.target,r.targetGeneration)?r.contacts.get(r.target.getUUID()):completed?r.contacts.values().stream().filter(e->alive(e,-1)).min(Comparator.comparingDouble(e->e.distanceToSqr(c.origin))).orElse(null):null;
        if(target!=null){c.hitIndex=r.index*2;SkillController.INSTANCE.hitCast(c,target,c.parameters.get("damage",0)*.05,.4);r.resolved=true;}
    }
    private void replica(SkillBehaviors.Context x,SkillCast c,long now,long elapsed,long stage,double speed){
        var r=runs.get(c.id);if(r==null){x.cancel(false);return;}if(r.stepSlot>=0)player().getInventory().setSelectedSlot(r.stepSlot);
        if(r.index<0&&!next(r,now)){finish(r);x.cancel(true);return;}
        for(int transitions=0;transitions<=r.count;transitions++){
            int index=r.index;double local=Math.min(Math.max(0,(now-r.stageAt)*speed),OracleSkills.stepDuration(index)*1000),damage=c.parameters.get("damage",0);boolean done=(now-r.stageAt)*speed>=OracleSkills.stepDuration(index)*1000;
            if(OracleSkills.combo(index)){Vec3 from=x.player().position();if(r.target!=null){double desired=8*Math.clamp(local/(OracleSkills.COMBO_INTERVAL*1000),0,1);move(x,c,desired-r.moved);r.moved=desired;}combo(r,from,x.player().position(),done);if(!c.finisher){c.finisher=true;x.visual(c,"HIT");}}
            else if(index==OracleSkills.FINISH_STEP){if(local>=600){if(r.target!=null){double desired=8*Math.clamp((local-600)/200,0,1);move(x,c,desired-r.moved);r.moved=desired;}c.hitIndex=index*2;x.path(c,c.origin,r.target!=null?x.player().position():c.origin.add(c.direction.scale(8)),3,3,damage*.35,.4);if(!c.finisher){c.finisher=true;c.charging=false;x.visual(c,"FINISH");}}}
            else{int action=OracleSkills.action(c.id.hashCode(),index);double part=damage*.0625;if(r.target!=null){double desired=5*Math.clamp(local/250,0,1);move(x,c,desired-r.moved);r.moved=desired;}Vec3 end=r.target!=null?x.player().position():c.origin.add(c.direction.scale(5));c.hitIndex=index*2+c.slashes;x.path(c,c.origin,end,action==1?2.5:3,3,action==3?part*.5:part,.2);if(action==3&&local>=175&&c.slashes==0){c.slashes=1;c.hit.clear();c.hitIndex=index*2+1;x.path(c,c.origin,end,3,3,part*.5,.2);x.visual(c,"HIT");}if(!c.finisher&&local>=100){c.finisher=true;x.visual(c,action==2?"RELEASE":"HIT");}}
            if(now-c.visualTrail>=80){x.visual(c,"TRAIL");c.visualTrail=now;}if(!done)return;
            long nextAt=r.stageAt+(long)(OracleSkills.stepDuration(index)*1000/speed);if(!next(r,nextAt)){finish(r);x.cancel(true);return;}
        }
    }
    private LivingEntity target(LivingEntity locked,double range){
        var p=player();if(alive(locked,-1)&&p.distanceToSqr(locked)<=range*range&&p.hasLineOfSight(locked))return locked;
        var candidates=SkillController.INSTANCE.targets(p.getBoundingBox().inflate(range,4,range)).stream().filter(e->alive(e,-1)&&p.distanceToSqr(e)<=range*range&&p.hasLineOfSight(e)).toList();Vec3 from=p.getEyePosition(),end=from.add(p.getLookAngle().scale(range));
        var aimed=candidates.stream().filter(e->e.getBoundingBox().inflate(.2).clip(from,end).isPresent()).min(Comparator.comparingDouble(e->from.distanceToSqr(e.getBoundingBox().inflate(.2).clip(from,end).orElseThrow()))).orElse(null);if(aimed!=null)return aimed;Vec3 look=flat(p.getLookAngle());return candidates.stream().filter(e->flat(e.position().subtract(p.position())).dot(look)>=.5).min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
    }
    private Vec3 around(LivingEntity target,int seed,int index){
        var p=player();double first=new Random(seed).nextDouble()*Math.PI*2+index*2.399963229728653;
        for(int attempt=0;attempt<16;attempt++){double angle=first+attempt*Math.PI/8;Vec3 candidate=target.position().add(Math.cos(angle)*3.5,0,Math.sin(angle)*3.5);var floor=p.level().clip(new ClipContext(candidate.add(0,1,0),candidate.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(floor.getType()==HitResult.Type.MISS)continue;Vec3 point=floor.getLocation().add(0,.001,0);var box=p.getBoundingBox().move(point.subtract(p.position()));if(p.level().hasChunkAt(BlockPos.containing(point))&&p.level().getWorldBorder().isWithinBounds(BlockPos.containing(point))&&p.level().noCollision(p,box)&&!p.level().containsAnyLiquid(box)&&SkillController.INSTANCE.sight(point.add(0,p.getEyeHeight(),0),target.getBoundingBox().getCenter()))return point;}return p.position();
    }
    private Vec3 behind(LivingEntity target){
        var p=player();Vec3 back=flat(target.getLookAngle()).scale(-1),side=new Vec3(-back.z,0,back.x);
        for(double distance:new double[]{1.5,2,1})for(double offset:new double[]{0,.5,-.5}){Vec3 candidate=target.position().add(back.scale(distance)).add(side.scale(offset));var floor=p.level().clip(new ClipContext(candidate.add(0,1,0),candidate.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(floor.getType()==HitResult.Type.MISS)continue;Vec3 point=floor.getLocation().add(0,.001,0);var box=p.getBoundingBox().move(point.subtract(p.position()));if(p.position().distanceToSqr(point)<=144&&p.level().hasChunkAt(BlockPos.containing(point))&&p.level().getWorldBorder().isWithinBounds(BlockPos.containing(point))&&SkillController.INSTANCE.sight(p.getEyePosition(),point.add(0,p.getEyeHeight(),0))&&p.level().noCollision(p,box)&&!p.level().containsAnyLiquid(box))return point;}return p.position();
    }
    private static void move(SkillBehaviors.Context x,SkillCast c,double amount){if(amount<=0)return;x.player().move(MoverType.SELF,c.direction.scale(amount));x.player().setDeltaMovement(Vec3.ZERO);x.player().fallDistance=0;c.center=x.player().position();}
    private static void heading(Vec3 direction){float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));player().setYRot(yaw);player().setYHeadRot(yaw);}
    private static Vec3 flat(Vec3 v){var result=new Vec3(v.x,0,v.z);return result.lengthSqr()<.0001?new Vec3(0,0,1):result.normalize();}
}
