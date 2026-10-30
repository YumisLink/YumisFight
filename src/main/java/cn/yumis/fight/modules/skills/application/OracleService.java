package cn.yumis.fight.modules.skills.application;

import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.*;

/** Oracle content lifecycle. Geometry/damage stay client-calculated; this service authorizes poses and resources. */
final class OracleService {
    private static final Gson JSON=new Gson();
    private final Function<ServerPlayer,CareerState> states;
    private final Function<ServerPlayer,Progress> progress;
    private final Function<ServerPlayer,Stats> stats;
    private final Function<ServerPlayer,CombatSession> sessions;
    private final Function<LivingEntity,Target> targetStates;
    private final Consumer<ServerPlayer> releaseProtection;
    private final Map<UUID,Map<String,Run>> runs=new HashMap<>();
    private final Random random=new Random();
    private static final class Run {
        String id,session,dimension,instance;long generation,started,until,lastStep;double speed;Stats stats;
        int originalSlot,next,played,steps;boolean cancelled,restored,finished,noFinisher;UUID target,originalWeapon;String originalType;Vec3 axis;
        String pausedInstruction="";long instructionLeft;
        final Map<Integer,JsonObject> replies=new HashMap<>(),poses=new HashMap<>();final Map<Integer,Stats> snapshots=new HashMap<>();
        final Map<Integer,UUID> targets=new HashMap<>();final Map<Integer,Long> poseTimes=new HashMap<>();
        final Set<Integer> consumed=new HashSet<>();
    }
    OracleService(Function<ServerPlayer,CareerState> states,Function<ServerPlayer,Progress> progress,Function<ServerPlayer,Stats> stats,Function<ServerPlayer,CombatSession> sessions,Function<LivingEntity,Target> targetStates,Consumer<ServerPlayer> releaseProtection){this.states=states;this.progress=progress;this.stats=stats;this.sessions=sessions;this.targetStates=targetStates;this.releaseProtection=releaseProtection;}
    void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("skills","oracleStep",this::step);router.register("skills","oraclePose",this::pose);router.register("skills","oracleFinish",this::finish);}
    boolean binding(ServerPlayer p,JsonObject request){return OracleRules.bound(progress.apply(p),states.apply(p).oracle,request.get("slot").getAsInt(),request.get("skill").getAsString(),System.currentTimeMillis());}
    void input(ServerPlayer p,JsonObject request){
        var player=progress.apply(p);String id=request.get("skill").getAsString();int slot=request.get("slot").getAsInt(),index=OracleSkills.ACTIONS.indexOf(id);
        if(index<0||slot<0||slot>=player.slots.length||player.learned(id)<=0||!player.slots[slot].equals(id)&&!binding(p,request)||!OracleRules.matches(states.apply(p).oracle,SkillCatalog.require(id),System.currentTimeMillis()))return;
        int selected=weaponSlot(p,OracleSkills.WEAPONS.get(index));if(selected>=0){p.getInventory().setSelectedSlot(selected);p.connection.send(new ClientboundSetHeldSlotPacket(selected));}
    }
    private int weaponSlot(ServerPlayer p,String type){
        for(int slot=0;slot<9;slot++){var gear=EquipmentItems.gear(p.getInventory().getItem(slot));if(gear!=null&&gear.slot==Slot.WEAPON&&gear.weapon.name().equals(type)&&gear.valid(progress.apply(p),EquipmentEligibility.allowance(p)))return slot;}return -1;
    }
    private int fallbackWeaponSlot(ServerPlayer p){
        var player=progress.apply(p);var allowed=JobCatalog.weapons(player.jobId());
        for(int slot=0;slot<9;slot++){var gear=EquipmentItems.gear(p.getInventory().getItem(slot));if(gear!=null&&gear.slot==Slot.WEAPON&&allowed.contains(gear.weapon.name())&&gear.valid(player,EquipmentEligibility.allowance(p)))return slot;}return -1;
    }
    private int stepWeaponSlot(ServerPlayer p,Run run,int index,String type){
        if(index<OracleSkills.PRELUDE_STEPS)return weaponSlot(p,type);
        int original=originalSlot(p,run);return original>=0||OracleSkills.combo(index)?original:fallbackWeaponSlot(p);
    }
    boolean prepare(ServerPlayer p,CareerState next,SkillDefinition skill,long now){
        int rank=OracleRules.rank(stats.apply(p),progress.apply(p));
        if(skill.id.equals(OracleSkills.REPLICA)&&(rank<=0||next.oracle.hermes<1))throw new IllegalArgumentException("至少需要一层代行-赫尔墨斯");
        if(rank<=0)return false;
        return OracleRules.succeed(next.oracle,progress.apply(p),skill,rank,now);
    }
    Stats preview(ServerPlayer p,SkillDefinition skill,Stats source){
        var state=states.apply(p).oracle;int rank=OracleRules.rank(source,progress.apply(p));long now=System.currentTimeMillis();
        if(rank<=0||!OracleRules.matches(state,skill,now))return source;
        Stats result=OracleRules.instruction(source);int gained=state.hermes<OracleRules.cap(progress.apply(p))?1:0;
        result.modifier("final").mut+=gained*OracleRules.layer(rank);result.modifiers.put("oracleInstructionAt",(double)now);return result;
    }
    void succeeded(ServerPlayer p,SkillDefinition skill,Stats prepared){long at=prepared.modifiers.getOrDefault("oracleInstructionAt",0d).longValue();if(at>0)OracleRules.succeed(states.apply(p).oracle,progress.apply(p),skill,OracleRules.rank(prepared,progress.apply(p)),at);}
    SkillParameters.Snapshot parameters(ServerPlayer p,SkillParameters.Snapshot input,CareerState next,long now){
        if(!OracleSkills.contains(input.skill().id))return input;
        var values=new LinkedHashMap<>(input.values());
        if(input.skill().id.equals(OracleSkills.REPLICA)){
            int stacks=next.oracle.hermes;values.put("replicaStacks",(double)stacks);values.put("duration",stacks==9?OracleSkills.FULL_DURATION:stacks*.35);
            next.oracle.castingUntil=now+Math.max(1,(long)(values.get("duration")*1000/Math.max(.01,stats.apply(p).get(Stat.CAST_SPEED))));
        }
        if(input.skill().id.equals(OracleSkills.SMILE)){
            var target=target(p,null,10,null);Vec3 point=target==null?p.position():behind(p,target),direction=target==null?flat(p.getLookAngle()):flat(target.position().subtract(point));
            values.put("oracleX",point.x);values.put("oracleY",point.y);values.put("oracleZ",point.z);values.put("oracleDX",direction.x);values.put("oracleDZ",direction.z);
        }
        return new SkillParameters.Snapshot(input.skill(),input.rank(),values);
    }
    Vec3 origin(SkillParameters.Snapshot parameters,Vec3 fallback){return parameters.values().containsKey("oracleX")?new Vec3(parameters.get("oracleX",0),parameters.get("oracleY",0),parameters.get("oracleZ",0)):fallback;}
    Vec3 direction(SkillParameters.Snapshot parameters,Vec3 fallback){return parameters.values().containsKey("oracleDX")?new Vec3(parameters.get("oracleDX",0),0,parameters.get("oracleDZ",1)):fallback;}
    long expires(SkillParameters.Snapshot parameters,Stats snapshot,long now,long fallback){return parameters.skill().id.equals(OracleSkills.REPLICA)?now+(long)(parameters.get("duration",6.7)*1000/Math.max(.01,snapshot.get(Stat.CAST_SPEED)))+5000:fallback;}
    void accepted(ServerPlayer p,String id,SkillDefinition skill,SkillParameters.Snapshot parameters,Stats snapshot,Vec3 origin,Vec3 direction,long now){
        if(skill.id.equals(OracleSkills.SMILE)){teleport(p,origin,direction);return;}
        if(!skill.id.equals(OracleSkills.REPLICA))return;
        var session=sessions.apply(p);var run=new Run();run.id=id;run.session=session.id.toString();run.generation=session.life.generation;run.dimension=p.level().dimension().identifier().toString();run.instance=session.life.instance;
        run.originalSlot=p.getInventory().getSelectedSlot();var original=EquipmentItems.gear(p.getMainHandItem());run.originalWeapon=original.instance;run.originalType=original.weapon.name();run.started=now;run.speed=Math.max(.01,snapshot.get(Stat.CAST_SPEED));run.until=now+(long)(parameters.get("duration",OracleSkills.FULL_DURATION)*1000/run.speed)+5000;run.stats=snapshot.copy();run.axis=direction;
        int stacks=(int)parameters.get("replicaStacks",1);run.steps=stacks==9?OracleSkills.FULL_STEPS:stacks;
        runs.computeIfAbsent(p.getUUID(),key->new HashMap<>()).put(id,run);var state=states.apply(p).oracle;state.cast=id;state.castingUntil=run.until;
        if(!state.instruction.isEmpty()){run.pausedInstruction=state.instruction;run.instructionLeft=Math.max(0,state.instructionUntil-now);state.instructionPausedLeft=run.instructionLeft;}
    }
    boolean locked(ServerPlayer p){long now=System.currentTimeMillis();return states.apply(p).oracle.castingUntil>now||runs.getOrDefault(p.getUUID(),Map.of()).values().stream().anyMatch(run->!run.cancelled&&!run.restored&&run.until>now);}
    boolean slotChange(ServerPlayer p){if(!locked(p))return false;p.connection.send(new ClientboundSetHeldSlotPacket(p.getInventory().getSelectedSlot()));return true;}
    void clear(ServerPlayer p){var all=runs.remove(p.getUUID());if(all!=null)for(var run:all.values())restore(p,run);}
    boolean cancel(ServerPlayer p,String id){
        var run=runs.getOrDefault(p.getUUID(),Map.of()).get(id);if(run==null)return false;run.cancelled=true;
        var state=states.apply(p).oracle;if(state.cast.equals(id)){restore(p,run);resume(state,run,System.currentTimeMillis());state.cast="";state.castingUntil=0;state.hermes=0;state.hermesUntil=0;}return true;
    }
    private void restore(ServerPlayer p,Run run){if(run.restored)return;run.restored=true;p.getInventory().setSelectedSlot(run.originalSlot);p.connection.send(new ClientboundSetHeldSlotPacket(run.originalSlot));}
    private void resume(OracleState state,Run run,long now){if(!run.pausedInstruction.isEmpty()&&state.instruction.equals(run.pausedInstruction))state.instructionUntil=now+run.instructionLeft;state.instructionPausedLeft=0;}
    private void finish(ServerPlayer p,JsonObject request){
        String id=request.get("id").getAsString();var run=runs.getOrDefault(p.getUUID(),Map.of()).get(id);long now=System.currentTimeMillis();
        if(run!=null&&run.finished){finishReply(p,run);return;}
        if(run==null||run.cancelled||run.next!=run.steps||run.played!=run.steps||now+100<run.lastStep+(long)(OracleSkills.stepDuration(run.steps-1)*1000/run.speed))return;
        complete(p,run,false,now);
    }
    private void complete(ServerPlayer p,Run run,boolean noFinisher,long now){
        var state=states.apply(p).oracle;if(!state.cast.equals(run.id))return;run.finished=true;run.noFinisher=noFinisher;restore(p,run);releaseProtection.accept(p);resume(state,run,now);state.cast="";state.castingUntil=0;state.hermes=0;state.hermesUntil=0;tick(p,states.apply(p),now);finishReply(p,run);
    }
    private void finishReply(ServerPlayer p,Run run){JsonObject reply=new JsonObject();reply.addProperty("id",run.id);reply.addProperty("finished",true);if(run.noFinisher)reply.addProperty("noFinisher",true);Network.send(p,"oracleStep",reply);}
    void tick(ServerPlayer p,CareerState career,long now){
        var state=career.oracle;int rank=OracleRules.rank(stats.apply(p),progress.apply(p));
        var owned=runs.get(p.getUUID());if(owned!=null)owned.values().removeIf(run->run.restored&&run.until+10000<now);
        var active=runs.getOrDefault(p.getUUID(),Map.of()).values().stream().filter(run->!run.cancelled&&!run.restored).max(Comparator.comparingLong(run->run.started)).orElse(null);
        if(active!=null){state.cast=active.id;state.castingUntil=active.until;}
        OracleRules.preparation(state,progress.apply(p),CombatTiming.out(sessions.apply(p).resources,now),state.castingUntil>now||active!=null,now);
        if(rank<=0){
            if(active!=null&&active.until>now)return;if(active!=null)restore(p,active);
            if(state.initialized||active!=null){var cleared=new OracleState();cleared.awakeningObserved=state.awakeningObserved;cleared.awakeningLearned=state.awakeningLearned;cleared.awakeningGrantPending=state.awakeningGrantPending;cleared.outOfCombatSince=state.outOfCombatSince;cleared.hermes=state.hermes;career.oracle=cleared;}return;
        }
        if(!state.initialized){state.initialized=true;state.nextInstruction=now+10000;state.weaponUntil=0;}
        if(now>=state.weaponUntil){var choices=new ArrayList<>(OracleSkills.WEAPONS);choices.remove(state.weapon);state.weapon=choices.get(random.nextInt(choices.size()));state.weaponUntil=now+30000;}
        if(state.karmaUntil<=now)state.karma=0;
        if(state.castingUntil>now)return;
        if(!state.cast.isEmpty()){
            var run=runs.getOrDefault(p.getUUID(),Map.of()).get(state.cast);if(run!=null){restore(p,run);resume(state,run,now);}
            state.cast="";state.castingUntil=0;state.hermes=0;state.hermesUntil=0;
        }
        var all=runs.get(p.getUUID());if(all!=null)all.values().removeIf(run->run.until+10000<now);
        int cap=OracleRules.cap(progress.apply(p));if(state.hermes>cap){state.hermes=cap;state.hermesUntil=now+30000;state.instruction="";state.nextInstruction=now;}
        if(state.hermes==9){state.hermesUntil=0;state.instruction=OracleSkills.REPLICA;state.instructionUntil=0;return;}
        if(state.hermes>0&&state.hermesUntil<=now){state.hermes=0;state.hermesUntil=0;}
        if(!state.instruction.isEmpty()&&state.instructionUntil<=now){
            if(!CombatTiming.out(sessions.apply(p).resources,now)){state.karma=Math.min(5,state.karma+1);state.karmaUntil=now+15000;}
            state.instruction="";state.nextInstruction=now;
        }
        if(state.instruction.isEmpty()&&now>=state.nextInstruction){
            var player=progress.apply(p);var cooldowns=sessions.apply(p).resources.cooldowns;
            var pool=SkillCatalog.all().stream().filter(s->!s.passive()&&!s.equipmentGranted&&!s.id.equals(OracleSkills.REPLICA)&&s.available(player)&&player.learned(s.id)>0&&cooldowns.getOrDefault(s.id,0L)-now<5000).toList();
            if(pool.isEmpty()){state.nextInstruction=now+1000;return;}
            state.instruction=pool.get(random.nextInt(pool.size())).id;state.instructionUntil=now+10000;
        }
    }
    Stats source(ServerPlayer p,JsonObject report){
        if(!report.has("careerCast"))return null;var run=runs.getOrDefault(p.getUUID(),Map.of()).get(report.get("careerCast").getAsString());
        return run==null?null:run.snapshots.get(report.get("careerIndex").getAsInt()/2);
    }
    void validate(ServerPlayer p,JsonObject report){
        if(!report.has("careerCast"))return;var run=runs.getOrDefault(p.getUUID(),Map.of()).get(report.get("careerCast").getAsString());
        if(run!=null){int hit=report.get("careerIndex").getAsInt(),step=hit/2;
            if(run.cancelled||!run.snapshots.containsKey(step)||step>=run.played||step>=run.steps||hit%2==1&&(step>=8||OracleSkills.action(run.id.hashCode(),step)!=3))throw new IllegalArgumentException("觉醒动作尚未获准");
            if(OracleSkills.combo(step)&&!Objects.equals(run.targets.get(step),UUID.fromString(report.get("target").getAsString())))throw new IllegalArgumentException("连斩目标与授权目标不一致");
            if(OracleSkills.combo(step)){var pose=run.poses.get(step);if(pose==null||!pose.has("targetGeneration")||pose.get("targetGeneration").getAsLong()!=report.get("generation").getAsLong()||pose.get("targetBasis").getAsLong()!=report.get("basis").getAsLong())throw new IllegalArgumentException("连斩目标生命已变化");}
        }
    }
    long occurredAt(ServerPlayer p,JsonObject report){
        if(!report.has("careerCast")||!report.has("careerIndex"))return -1;var run=runs.getOrDefault(p.getUUID(),Map.of()).get(report.get("careerCast").getAsString());int step=report.get("careerIndex").getAsInt()/2;
        return run==null||!OracleSkills.combo(step)?-1:run.poseTimes.getOrDefault(step,-1L);
    }
    void committed(ServerPlayer p,JsonObject report){
        if(!report.has("careerCast")||!report.has("careerIndex"))return;var run=runs.getOrDefault(p.getUUID(),Map.of()).get(report.get("careerCast").getAsString());int step=report.get("careerIndex").getAsInt()/2;
        if(run!=null&&OracleSkills.combo(step))run.consumed.add(step);
    }
    private void step(ServerPlayer p,JsonObject request){
        String id=request.get("id").getAsString();int index=request.get("step").getAsInt();var run=runs.getOrDefault(p.getUUID(),Map.of()).get(id);if(run==null||run.cancelled)return;
        if(run.replies.containsKey(index)){Network.send(p,"oracleStep",run.replies.get(index));return;}
        long now=System.currentTimeMillis();var session=sessions.apply(p);
        if(index!=run.next||index<0||index>=run.steps||!run.session.equals(session.id.toString())||run.generation!=session.life.generation||session.life.dead||!run.dimension.equals(p.level().dimension().identifier().toString())||!run.instance.equals(session.life.instance)||now>run.until||index>0&&now+300<run.lastStep+(long)(OracleSkills.stepDuration(index-1)*1000/run.speed))return;
        long at=index==0?now:run.lastStep+(long)(OracleSkills.stepDuration(index-1)*1000/run.speed);grant(p,run,index,at);
        // Prefetch the whole combo; the pose for each strike still confirms its actual target and weapon.
        if(index==OracleSkills.PRELUDE_STEPS)for(int i=index+1;i<OracleSkills.FINISH_STEP;i++)grant(p,run,i,at+(long)((i-index)*OracleSkills.COMBO_INTERVAL*1000/run.speed));
    }
    private void pose(ServerPlayer p,JsonObject request){
        String id=request.get("id").getAsString();int index=request.get("step").getAsInt();var run=runs.getOrDefault(p.getUUID(),Map.of()).get(id);long now=System.currentTimeMillis();var session=sessions.apply(p);
        if(run==null||run.cancelled||!run.session.equals(session.id.toString())||run.generation!=session.life.generation||session.life.dead||!run.dimension.equals(p.level().dimension().identifier().toString())||!run.instance.equals(session.life.instance)||now>run.until)return;
        if(run.poses.containsKey(index)){if(request.has("retarget")&&request.get("retarget").getAsBoolean()&&request.has("previousTarget"))retarget(p,run,index,request);else Network.send(p,"oracleStep",run.poses.get(index));return;}
        if(run.restored||index!=run.played||!run.replies.containsKey(index)||now+100<run.started+(long)(OracleSkills.stepAt(index)*1000/run.speed))return;
        var cached=run.replies.get(index);String type=cached.get("weaponType").getAsString();int selected=stepWeaponSlot(p,run,index,type);
        if(index==OracleSkills.FINISH_STEP&&selected<0){complete(p,run,true,now);return;}
        if(selected>=0){p.getInventory().setSelectedSlot(selected);p.connection.send(new ClientboundSetHeldSlotPacket(selected));if(index==OracleSkills.FINISH_STEP)type=EquipmentItems.gear(p.getInventory().getItem(selected)).weapon.name();}
        LivingEntity enemy=target(p,run.target,12,null);run.target=enemy==null?null:enemy.getUUID();Vec3 origin=enemy==null?p.position():around(p,enemy,id.hashCode(),index),direction=enemy==null?run.axis:flat(enemy.position().subtract(origin));
        var snapshot=OracleRules.stepStats(run.stats,states.apply(p).oracle,type,selected>=0);run.snapshots.put(index,snapshot);if(enemy!=null)run.targets.put(index,enemy.getUUID());run.poseTimes.put(index,now);run.played++;
        var ack=cached.deepCopy();ack.addProperty("pose",true);ack.addProperty("hasTarget",enemy!=null);ack.addProperty("target",enemy==null?"":enemy.getStringUUID());ack.addProperty("weaponType",type);ack.add("origin",JSON.toJsonTree(new double[]{origin.x,origin.y,origin.z}));ack.add("direction",JSON.toJsonTree(new double[]{direction.x,0,direction.z}));ack.add("stats",JSON.toJsonTree(snapshot));
        if(enemy!=null&&OracleSkills.combo(index))targetData(ack,enemy);run.poses.put(index,ack);
        // The client already owns the approved pose. A second vanilla teleport would rewind its in-flight dash.
        p.setPos(origin);p.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));p.setYHeadRot(p.getYRot());p.connection.resetPosition();p.fallDistance=0;Network.send(p,"oracleStep",ack);
    }
    private void targetData(JsonObject ack,LivingEntity enemy){
        var targetState=targetStates.apply(enemy);var box=enemy.getBoundingBox();ack.addProperty("targetEntity",enemy.getId());ack.addProperty("targetGeneration",targetState.life.generation);ack.addProperty("targetBasis",targetState.life.basis);ack.add("targetBox",JSON.toJsonTree(new double[]{box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ}));
    }
    private void retarget(ServerPlayer p,Run run,int index,JsonObject request){
        String expectedTarget=request.get("previousTarget").getAsString();
        var old=run.poses.get(index);if(!OracleSkills.combo(index)||old==null)return;
        if(!old.get("target").getAsString().equals(expectedTarget)){Network.send(p,"oracleStep",old);return;}
        if(run.consumed.contains(index)){JsonObject denied=new JsonObject();denied.addProperty("id",run.id);denied.addProperty("step",index);denied.addProperty("retargetDenied",true);denied.addProperty("retargetFrom",expectedTarget);Network.send(p,"oracleStep",denied);return;}
        UUID previous=run.targets.get(index);if(previous==null)return;
        var entity=p.level().getEntity(previous);
        if(entity instanceof LivingEntity living&&living.isAlive()){
            var life=targetStates.apply(living).life;
            if(!life.dead&&life.generation==old.get("targetGeneration").getAsLong()&&life.basis==old.get("targetBasis").getAsLong()){JsonObject pending=new JsonObject();pending.addProperty("id",run.id);pending.addProperty("step",index);pending.addProperty("retargetPending",true);pending.addProperty("retargetFrom",expectedTarget);Network.send(p,"oracleStep",pending);return;}
        }
        JsonArray offered=request.has("candidates")&&request.get("candidates").isJsonArray()?request.getAsJsonArray("candidates"):new JsonArray();if(offered.size()>128)return;
        JsonArray coordinates=old.getAsJsonArray("origin");Vec3 origin=new Vec3(coordinates.get(0).getAsDouble(),coordinates.get(1).getAsDouble(),coordinates.get(2).getAsDouble());LivingEntity enemy=null;double nearest=Double.POSITIVE_INFINITY;
        for(var value:offered){UUID candidate;long generation,basis;try{var item=value.getAsJsonObject();candidate=UUID.fromString(item.get("target").getAsString());generation=item.get("generation").getAsLong();basis=item.get("basis").getAsLong();}catch(RuntimeException invalid){continue;}var found=p.level().getEntity(candidate);if(!(found instanceof LivingEntity next)||candidate.equals(previous)||!eligibleFrom(p,next,origin,12))continue;var life=targetStates.apply(next).life;if(life.dead||life.generation!=generation||life.basis!=basis)continue;double distance=next.distanceToSqr(origin);if(distance<nearest){nearest=distance;enemy=next;}}
        var ack=old.deepCopy();ack.addProperty("retarget",true);ack.addProperty("retargetFrom",expectedTarget);ack.addProperty("hasTarget",enemy!=null);ack.addProperty("target",enemy==null?"":enemy.getStringUUID());
        ack.remove("targetEntity");ack.remove("targetGeneration");ack.remove("targetBasis");ack.remove("targetBox");if(enemy==null)run.targets.remove(index);else{run.targets.put(index,enemy.getUUID());targetData(ack,enemy);}if(Objects.equals(run.target,previous))run.target=enemy==null?null:enemy.getUUID();run.poseTimes.put(index,System.currentTimeMillis());run.poses.put(index,ack);Network.send(p,"oracleStep",ack);
    }
    private void grant(ServerPlayer p,Run run,int index,long at){
        int action=index>=OracleSkills.PRELUDE_STEPS?4:OracleSkills.action(run.id.hashCode(),index);String type=index>=OracleSkills.PRELUDE_STEPS?run.originalType:OracleSkills.WEAPONS.get(action);int selected=stepWeaponSlot(p,run,index,type);
        if(index==OracleSkills.FINISH_STEP&&selected>=0)type=EquipmentItems.gear(p.getInventory().getItem(selected)).weapon.name();
        LivingEntity enemy=target(p,run.target,12,null);
        Vec3 point=enemy==null?p.position():around(p,enemy,run.id.hashCode(),index),direction=enemy==null?run.axis:flat(enemy.position().subtract(point));run.axis=direction;
        Stats stepStats=OracleRules.stepStats(run.stats,states.apply(p).oracle,type,selected>=0);run.snapshots.put(index,stepStats);
        JsonObject ack=new JsonObject();ack.addProperty("id",run.id);ack.addProperty("step",index);ack.addProperty("action",action);ack.addProperty("hasTarget",enemy!=null);ack.addProperty("target",enemy==null?"":enemy.getStringUUID());ack.addProperty("weaponType",type);ack.add("stats",JSON.toJsonTree(stepStats));ack.add("origin",JSON.toJsonTree(new double[]{point.x,point.y,point.z}));ack.add("direction",JSON.toJsonTree(new double[]{direction.x,0,direction.z}));
        run.replies.put(index,ack);run.next=index+1;run.lastStep=at;Network.send(p,"oracleStep",ack);
    }
    private int originalSlot(ServerPlayer p,Run run){for(int slot=0;slot<9;slot++){var item=EquipmentItems.gear(p.getInventory().getItem(slot));if(item!=null&&item.instance.equals(run.originalWeapon)&&item.valid(progress.apply(p),EquipmentEligibility.allowance(p)))return slot;}return -1;}
    private Vec3 around(ServerPlayer p,LivingEntity target,int seed,int index){
        double first=new Random(seed).nextDouble()*Math.PI*2+index*2.399963229728653;
        for(int attempt=0;attempt<16;attempt++){
            double angle=first+attempt*Math.PI/8;Vec3 candidate=target.position().add(Math.cos(angle)*3.5,0,Math.sin(angle)*3.5);
            var floor=p.level().clip(new ClipContext(candidate.add(0,1,0),candidate.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(floor.getType()==HitResult.Type.MISS)continue;
            Vec3 point=floor.getLocation().add(0,.001,0);var pos=BlockPos.containing(point);AABB box=p.getBoundingBox().move(point.subtract(p.position()));
            if(!p.level().hasChunkAt(pos)||!p.level().getWorldBorder().isWithinBounds(pos)||!p.level().noCollision(p,box)||p.level().containsAnyLiquid(box))continue;
            if(p.level().clip(new ClipContext(point.add(0,p.getEyeHeight(),0),target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS)return point;
        }return p.position();
    }
    private LivingEntity target(ServerPlayer p,UUID locked,double range,UUID excluded){
        if(locked!=null&&!locked.equals(excluded)&&p.level().getEntity(locked) instanceof LivingEntity e&&eligible(p,e,range))return e;
        var candidates=p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(range,4,range),e->!e.getUUID().equals(excluded)&&eligible(p,e,range));
        Vec3 from=p.getEyePosition(),end=from.add(p.getLookAngle().scale(range));
        var aimed=candidates.stream().filter(e->e.getBoundingBox().inflate(.2).clip(from,end).isPresent()).min(Comparator.comparingDouble(e->from.distanceToSqr(e.getBoundingBox().inflate(.2).clip(from,end).orElseThrow()))).orElse(null);
        if(aimed!=null)return aimed;Vec3 look=flat(p.getLookAngle());
        return candidates.stream().filter(e->flat(e.position().subtract(p.position())).dot(look)>=.5).min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
    }
    private boolean targetAllowed(ServerPlayer p,LivingEntity target){return target!=p&&target.isAlive()&&(!(target instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag bag)||!bag.timed()||bag.ownedBy(p)&&!bag.finished())&&(target instanceof Enemy||target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||target instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag);}
    private boolean eligible(ServerPlayer p,LivingEntity target,double range){return targetAllowed(p,target)&&p.distanceToSqr(target)<=range*range&&p.hasLineOfSight(target);}
    private boolean eligibleFrom(ServerPlayer p,LivingEntity target,Vec3 origin,double range){return targetAllowed(p,target)&&target.distanceToSqr(origin)<=range*range&&p.level().clip(new ClipContext(origin.add(0,p.getEyeHeight(),0),target.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;}
    private Vec3 behind(ServerPlayer p,LivingEntity target){
        Vec3 back=flat(target.getLookAngle()).scale(-1),side=new Vec3(-back.z,0,back.x);
        for(double distance:new double[]{1.5,2,1})for(double offset:new double[]{0,.5,-.5}){
            Vec3 candidate=target.position().add(back.scale(distance)).add(side.scale(offset));var floor=p.level().clip(new ClipContext(candidate.add(0,1,0),candidate.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(floor.getType()==HitResult.Type.MISS)continue;Vec3 point=floor.getLocation().add(0,.001,0);var pos=BlockPos.containing(point);
            if(p.position().distanceToSqr(point)>144||!p.level().hasChunkAt(pos)||!p.level().getWorldBorder().isWithinBounds(pos))continue;
            if(p.level().clip(new ClipContext(p.getEyePosition(),point.add(0,p.getEyeHeight(),0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)continue;
            if(p.level().noCollision(p,p.getBoundingBox().move(point.subtract(p.position())))&&!p.level().containsAnyLiquid(p.getBoundingBox().move(point.subtract(p.position()))))return point;
        }return p.position();
    }
    private void teleport(ServerPlayer p,Vec3 point,Vec3 direction){float yaw=(float)Math.toDegrees(Math.atan2(-direction.x,direction.z));p.teleportTo(p.level(),point.x,point.y,point.z,Set.of(),yaw,p.getXRot(),true);p.fallDistance=0;}
    private static Vec3 flat(Vec3 source){Vec3 v=new Vec3(source.x,0,source.z);return v.lengthSqr()<.0001?new Vec3(0,0,1):v.normalize();}
}
