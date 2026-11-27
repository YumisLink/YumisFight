package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.combat.api.CombatTiming;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.equipment.api.*;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Oracle commands, weapon choice, poses and contacts all belong to the owner. */
public final class OracleClient {
    public static final OracleClient INSTANCE=new OracleClient();
    private static final class Run {
        SkillCast cast;Stats frozen;SkillBehaviors.Context context;int index=-1,count,originalSlot,stepSlot=-1;UUID originalWeapon;
        String originalType,pausedInstruction="";long instructionLeft,stageAt;double moved;Vec3 axis;LivingEntity target;
        long targetGeneration,totalMillis;long[] stepMillis;boolean resolved;final Map<UUID,LivingEntity> contacts=new LinkedHashMap<>();
    }
    private static final class EchoSlash {
        SkillCast cast;Vec3 origin,end,direction;double range,width,height,damage,knockback,sweepDegrees;
        boolean path,fan;long fireAt;String visualPhase;
    }
    private static final class Shockwave {
        SkillCast cast;Vec3 center;double damage,radius;String holdKey;
    }
    private final Map<String,Run> runs=new LinkedHashMap<>();
    private final List<EchoSlash> echoes=new ArrayList<>();
    private final ArrayDeque<Shockwave> shockwaves=new ArrayDeque<>();
    private final LinkedHashMap<String,Set<UUID>> shockwaveHits=new LinkedHashMap<>();
    private final LinkedHashMap<String,List<Vec3>> shockwaveVisuals=new LinkedHashMap<>();
    private long quakeSerial;
    private final Random random=new Random();
    private OracleClient(){}
    private static SkillsClient state(){return SkillsClient.current();}
    private static net.minecraft.client.player.LocalPlayer player(){return Minecraft.getInstance().player;}
    static void installHooks(){
        SkillCastHooks.onRequest(SkillCastHooks.Stage.PREPARE_STATS,req->req.prepared=INSTANCE.prepareStats(req.skill,req.prepared));
        SkillCastHooks.onRequest(SkillCastHooks.Stage.POST_PAY_STATS,req->req.prepared=OracleRules.castStats(req.prepared,req.skill));
        SkillCastHooks.onRequest(SkillCastHooks.Stage.RESOLVE_PARAMETERS,req->{
            req.parameters=INSTANCE.parameters(req.parameters,req.prepared,req.nextView,req.oracleReplicaStacks,req.wall);
            req.origin=INSTANCE.origin(req.parameters,req.origin);
            req.direction=INSTANCE.direction(req.parameters,req.direction);
            req.center=req.origin;
        });
        SkillCastHooks.onHit(INSTANCE::onHit);
        SkillCastHooks.onVisual(event->{
            String skillId=event.cast().skill.id;
            boolean isReplica=skillId.equals(OracleSkills.REPLICA);
            boolean instructed=event.cast().stats.modifiers.getOrDefault("oracleInstruction",0d)>0;
            if(isReplica){
                event.packet().addProperty("sweep",event.cast().charging);
                event.packet().addProperty("color",0xf2cb72);
                Run run=INSTANCE.runs.get(event.cast().id);
                if(run!=null&&run.index>=0)event.packet().addProperty("slashAngle",(double)Math.clamp(run.index,0,180));
            }else if(OracleSkills.contains(skillId)){
                // Dual-state Oracle palette: monochrome Black Silence ink normally; Oracle Sacred Gold when fulfilling an Instruction!
                event.packet().addProperty("color",instructed?0xf2cb72:0xdce4ee);
                event.packet().addProperty("sweep",instructed);
                if(skillId.equals(OracleSkills.SMILE)&&event.phase().equals("START")&&event.cast().parameters.values().containsKey("oracleFromX")){
                    var m=new com.google.gson.JsonObject();
                    m.addProperty("fromX",event.cast().parameters.get("oracleFromX",0));
                    m.addProperty("fromY",event.cast().parameters.get("oracleFromY",0));
                    m.addProperty("fromZ",event.cast().parameters.get("oracleFromZ",0));
                    event.packet().add("motion",m);
                }
            }
            if((skillId.equals(OracleSkills.THRUST)||skillId.equals(OracleSkills.RIFT))&&event.phase().equals("START")){
                boolean thrust=skillId.equals(OracleSkills.THRUST);
                event.packet().add("motion",SkillController.motion(event.cast().parameters.get("range",thrust?7:6),(thrust?250:350)/event.cast().speed,false));
            }
        });
    }
    public static void install(){
        SkillBehaviors.registerStart(OracleSkills.RIFT,(x,c,now)->{c.hitIndex=0;x.path(c,x.player().position(),x.player().position(),3.6,3.4,c.parameters.get("damage",0),.2);emitShortswordWave(x,c);PlayerPoseChannel.lock(c);});
        SkillBehaviors.registerStart(OracleSkills.THRUST,(x,c,now)->{emitShortswordWave(x,c);PlayerPoseChannel.lock(c);});
        SkillBehaviors.registerStart(OracleSkills.SMILE,(x,c,now)->{x.player().setPos(c.origin);x.player().setDeltaMovement(Vec3.ZERO);PlayerPoseChannel.lock(c);});
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
        var result=OracleRules.instruction(source);int cap=OracleRules.cap(state().progress());int gained=skill.id.equals(OracleSkills.REPLICA)?0:Math.min(cap,command.hermes+OracleRules.hermesGain(source))-command.hermes;result.modifier("final").mut+=gained*OracleRules.layer(rank);return result;
    }
    public OracleRules.CastResult prepare(CareerState next,SkillDefinition skill,Stats stats,long now){
        return OracleRules.resolveCast(next.oracle,state().progress(),skill,stats,now);
    }
    public void onAfterCooldownPlaced(OracleState next,Map<String,Long> cooldowns,String castSkillId,boolean fulfilledInstruction,Stats stats,long now){
        OracleRules.resetSmallSkillCooldown(next,cooldowns,castSkillId,fulfilledInstruction,stats,now,random);
    }
    public SkillParameters.Snapshot parameters(SkillParameters.Snapshot input,Stats stats,CareerState next,int resolvedReplicaStacks,long now){
        if(!OracleSkills.contains(input.skill().id))return input;var values=new LinkedHashMap<>(input.values());
        if(input.skill().id.equals(OracleSkills.REPLICA))values.put("replicaStacks",(double)Math.max(1,resolvedReplicaStacks>0?resolvedReplicaStacks:OracleRules.replicaStacks(stats,state().progress(),next.oracle.hermes)));
        if(input.skill().id.equals(OracleSkills.SMILE)){
            double searchRange=10d*("GREATSWORD".equals(stats.weaponType)&&TowerBattleCard.ORACLE_GREATSWORD_CLEAVE.held(stats)?1d+TowerBattleCard.ORACLE_GREATSWORD_CLEAVE.ratio("range"):1d);
            var from=player().position();var target=target(null,searchRange);Vec3 point=target==null?from:behind(target),direction=target==null?flat(player().getLookAngle()):flat(target.position().subtract(point));values.put("oracleFromX",from.x);values.put("oracleFromY",from.y);values.put("oracleFromZ",from.z);values.put("oracleX",point.x);values.put("oracleY",point.y);values.put("oracleZ",point.z);values.put("oracleDX",direction.x);values.put("oracleDZ",direction.z);
        }
        return new SkillParameters.Snapshot(input.skill(),input.rank(),values);
    }
    public Vec3 origin(SkillParameters.Snapshot p,Vec3 fallback){return p.values().containsKey("oracleX")?new Vec3(p.get("oracleX",0),p.get("oracleY",0),p.get("oracleZ",0)):fallback;}
    public Vec3 direction(SkillParameters.Snapshot p,Vec3 fallback){return p.values().containsKey("oracleDX")?new Vec3(p.get("oracleDX",0),0,p.get("oracleDZ",1)):fallback;}
    private void onHit(SkillCastHooks.HitEvent event){
        SkillCast source=event.source();
        if(source==null||event.actual()<=0||!OracleSkills.ACTIONS.contains(source.skill.id))return;
        Target target=state().context(event.target());
        int stunTicks=OracleRules.bluntStunTicks(event.stats(),source.skill,target!=null&&target.boss());
        if(stunTicks>0){
            JsonObject existing=event.report().has("ailment")&&event.report().get("ailment").isJsonObject()?event.report().getAsJsonObject("ailment"):null;
            if(existing==null||!"STUN".equals(existing.get("kind").getAsString())||existing.get("durationTicks").getAsLong()<stunTicks){
                JsonObject stun=new JsonObject();
                stun.addProperty("kind","STUN");
                stun.addProperty("durationTicks",stunTicks);
                event.report().add("ailment",stun);
            }
        }
        if(!source.passiveDamage&&"BLUNT".equals(event.stats().weaponType)&&TowerBattleCard.ORACLE_BLUNT_QUAKE.held(event.stats())){
            double quakeDamage=source.parameters.get("damage",0)*TowerBattleCard.ORACLE_BLUNT_QUAKE.ratio("damage");
            if(quakeDamage>0){
                var sw=new Shockwave();
                sw.cast=source;
                sw.center=event.target().position();
                sw.damage=quakeDamage;
                sw.radius=TowerBattleCard.ORACLE_BLUNT_QUAKE.value("radius");
                sw.holdKey="oracle_quake_"+(++quakeSerial);
                shockwaves.addLast(sw);
                TrainingCombat.hold(source.id,sw.holdKey,ClientUi.now()+600L);
            }
        }
    }
    private static void emitShortswordWave(SkillBehaviors.Context x,SkillCast c){
        if(!"SHORTSWORD".equals(c.stats.weaponType)||!TowerBattleCard.ORACLE_SHORTSWORD_WAVE.held(c.stats))return;
        double coeff=c.parameters.get("damage",0)*TowerBattleCard.ORACLE_SHORTSWORD_WAVE.ratio("damage");
        if(coeff<=0)return;
        x.wave(x.player().position().add(0,.8,0),c.direction,TowerBattleCard.ORACLE_SHORTSWORD_WAVE.value("range"),3.6,2.4,c.stats,c.skill,coeff,true,false);
    }
    private void scheduleEcho(SkillCast c,long now,boolean path,double range,double width,double height,double baseDamage,double knockback,boolean fan,double sweepDegrees,String visualPhase){
        if(!"LIGHTSABER".equals(c.stats.weaponType)||!TowerBattleCard.ORACLE_LIGHTSABER_ECHO.held(c.stats))return;
        double echoDamage=baseDamage*TowerBattleCard.ORACLE_LIGHTSABER_ECHO.ratio("damage");
        if(echoDamage<=0||player()==null)return;
        var echo=new EchoSlash();
        echo.cast=c;
        echo.path=path;
        echo.origin=path?c.origin:player().position();
        echo.end=path?player().position():echo.origin;
        if(path&&echo.end.distanceToSqr(echo.origin)<.25)echo.end=echo.origin.add(c.direction.scale(Math.min(2.5,range)));
        echo.direction=c.direction;
        echo.range=range;
        echo.width=width;
        echo.height=height;
        echo.damage=echoDamage;
        echo.knockback=knockback;
        echo.fan=fan;
        echo.sweepDegrees=sweepDegrees;
        echo.visualPhase=visualPhase;
        long delay=Math.max(80L,Math.round(150.0/Math.max(.01,c.speed)));
        echo.fireAt=now+delay;
        echoes.add(echo);
        TrainingCombat.hold(c.id,"oracle_echo",echo.fireAt+600L);
    }
    private static SkillCast derivedCast(SkillCast source,int hitIndex,boolean passiveDamage,double sweepDegrees){
        var c=new SkillCast();
        c.id=source.id;c.skill=source.skill;c.parameters=source.parameters;c.stats=source.stats;c.equipment=source.equipment;
        c.weapon=source.weapon;c.start=source.start;c.origin=source.origin;c.direction=source.direction;c.center=source.center;
        c.speed=source.speed;c.dimension=source.dimension;c.session=source.session;c.generation=source.generation;c.job=source.job;c.instance=source.instance;
        c.hitIndex=hitIndex;c.passiveDamage=passiveDamage;c.sweepDegrees=sweepDegrees;
        return c;
    }
    private void echoVisual(EchoSlash echo){
        var p=player();if(p==null||!state().ready())return;
        long serverNow=state().serverNow();
        boolean gold=echo.cast.stats.modifiers.getOrDefault("oracleInstruction",0d)>0;
        String id=UUID.nameUUIDFromBytes((echo.cast.id+"/oracle_echo").getBytes(StandardCharsets.UTF_8)).toString();
        Vec3 pos=echo.path?echo.origin:p.position();
        double hScale=echo.cast.parameters.get("meleeScale",1);
        String[] phases={"START",echo.visualPhase};
        for(int seq=0;seq<phases.length;seq++){
            JsonObject j=new JsonObject();
            j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);
            j.addProperty("id",id);j.addProperty("kind",echo.cast.skill.id);j.addProperty("phase",phases[seq]);
            j.addProperty("sequence",seq);j.addProperty("seed",id.hashCode());j.addProperty("startedAt",serverNow);j.addProperty("at",serverNow);
            j.addProperty("duration",650L);j.addProperty("cosmeticOnly",true);
            j.addProperty("color",gold?0xf2cb72:0xdce4ee);j.addProperty("sweep",gold);
            j.addProperty("weaponType","LIGHTSABER");j.addProperty("horizontalScale",hScale);
            j.addProperty("x",pos.x);j.addProperty("y",pos.y);j.addProperty("z",pos.z);
            j.addProperty("px",pos.x);j.addProperty("py",pos.y);j.addProperty("pz",pos.z);
            j.addProperty("dx",echo.direction.x);j.addProperty("dy",0d);j.addProperty("dz",echo.direction.z);
            Effects.receive(j);state().send("visual",j);
        }
    }
    private void quakeVisual(Shockwave sw,boolean withAudio){
        var p=player();if(p==null||!state().ready())return;
        long serverNow=state().serverNow();
        boolean gold=sw.cast.stats.modifiers.getOrDefault("oracleInstruction",0d)>0;
        String id=UUID.nameUUIDFromBytes((sw.cast.id+"/"+sw.holdKey).getBytes(StandardCharsets.UTF_8)).toString();
        String[] phases={"START","HIT"};
        for(int seq=0;seq<phases.length;seq++){
            JsonObject j=new JsonObject();
            j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);
            j.addProperty("id",id);j.addProperty("kind",OracleSkills.WILL);j.addProperty("phase",phases[seq]);
            j.addProperty("sequence",seq);j.addProperty("seed",id.hashCode());j.addProperty("startedAt",serverNow);j.addProperty("at",serverNow);
            j.addProperty("duration",560L);j.addProperty("cosmeticOnly",true);
            j.addProperty("color",gold?0xf2cb72:0xdce4ee);j.addProperty("sweep",gold);
            j.addProperty("weaponType","BLUNT");j.addProperty("horizontalScale",sw.radius);j.addProperty("slashAngle",withAudio?1d:0d);
            j.addProperty("x",sw.center.x);j.addProperty("y",sw.center.y);j.addProperty("z",sw.center.z);
            j.addProperty("px",sw.center.x);j.addProperty("py",sw.center.y);j.addProperty("pz",sw.center.z);
            j.addProperty("dx",sw.cast.direction.x);j.addProperty("dy",0d);j.addProperty("dz",sw.cast.direction.z);
            Effects.receive(j);state().send("visual",j);
        }
    }
    private void start(SkillBehaviors.Context x,SkillCast c,long now){
        var r=new Run();r.cast=c;r.context=x;r.frozen=c.stats.copy();r.originalSlot=player().getInventory().getSelectedSlot();var gear=EquipmentItems.view(player().getMainHandItem());r.originalWeapon=gear.instance();r.originalType=gear.weapon().name();r.axis=c.direction;r.stageAt=now;
        int stacks=(int)c.parameters.get("replicaStacks",1);
        r.count=stacks==9?OracleSkills.FULL_STEPS:stacks;
        r.stepMillis=new long[r.count];
        for(int i=0;i<r.count;i++){
            long clipLength=i<OracleSkills.PRELUDE_STEPS?PlayerPoseChannel.length(stepClip(c,i)):0;
            double step=clipLength>0?clipLength:OracleSkills.stepDuration(i,c.parameters)*1000;
            r.stepMillis[i]=PlayerPoseChannel.scaled(step,c.speed);
            r.totalMillis+=r.stepMillis[i];
        }
        var values=new LinkedHashMap<>(c.parameters.values());
        values.put("duration",r.totalMillis*c.speed/1000d);
        c.parameters=new SkillParameters.Snapshot(c.skill,c.parameters.rank(),values);
        runs.put(c.id,r);var command=CareerClient.INSTANCE.view.oracle;command.cast=c.id;command.castingUntil=state().serverNow()+r.totalMillis+5000;
        if(!command.instruction.isEmpty()){r.pausedInstruction=command.instruction;r.instructionLeft=Math.max(0,command.instructionUntil-state().serverNow());command.instructionPausedLeft=r.instructionLeft;}
        TrainingCombat.hold(c.id,"oracle",now+r.totalMillis+5000);CareerClient.INSTANCE.publish();
    }
    private static String stepClip(SkillCast c,int index){
        if(index==OracleSkills.FINISH_STEP)return "replica_finish";
        if(OracleSkills.combo(index))return "replica_combo";
        return OracleSkills.ACTIONS.get(OracleSkills.action(c.id.hashCode(),index));
    }
    public boolean locked(){return !runs.isEmpty();}
    private void finish(Run r){
        runs.remove(r.cast.id);if(player()!=null)player().getInventory().setSelectedSlot(r.originalSlot);
        var command=CareerClient.INSTANCE.view.oracle;if(!r.pausedInstruction.isEmpty()&&command.instruction.equals(r.pausedInstruction))command.instructionUntil=state().serverNow()+r.instructionLeft;
        command.instructionPausedLeft=0;command.cast="";command.castingUntil=0;command.hermes=0;command.hermesUntil=0;TrainingCombat.release(r.cast.id,"oracle");if(MageClient.INSTANCE.valid(r.cast))CareerClient.INSTANCE.publish();
    }
    public void cancel(String id){var r=runs.get(id);if(r!=null)finish(r);}
    public void clear(){
        for(var r:new ArrayList<>(runs.values()))finish(r);
        runs.clear();
        for(var echo:echoes)TrainingCombat.release(echo.cast.id,"oracle_echo");
        echoes.clear();
        for(var sw:shockwaves)TrainingCombat.release(sw.cast.id,sw.holdKey);
        shockwaves.clear();
        shockwaveHits.clear();
        shockwaveVisuals.clear();
    }
    public void tick(SkillBehaviors.Context context,long localNow){
        for(var it=echoes.iterator();it.hasNext();){
            var echo=it.next();
            if(!MageClient.INSTANCE.valid(echo.cast)||player()==null){TrainingCombat.release(echo.cast.id,"oracle_echo");it.remove();continue;}
            if(localNow<echo.fireAt)continue;
            var derived=derivedCast(echo.cast,10,false,echo.sweepDegrees);
            derived.origin=echo.origin;derived.direction=echo.direction;derived.center=echo.origin;
            if(echo.path)context.path(derived,echo.origin,echo.end,echo.width,echo.height,echo.damage,echo.knockback);
            else context.slash(derived,echo.range,echo.width,echo.height,echo.damage,echo.knockback,echo.fan);
            echoVisual(echo);
            TrainingCombat.release(echo.cast.id,"oracle_echo");
            it.remove();
        }
        while(!shockwaves.isEmpty()){
            var sw=shockwaves.removeFirst();
            if(!MageClient.INSTANCE.valid(sw.cast)||player()==null){TrainingCombat.release(sw.cast.id,sw.holdKey);continue;}
            var hitSet=shockwaveHits.computeIfAbsent(sw.cast.id,k->new HashSet<>());
            while(shockwaveHits.size()>64)shockwaveHits.remove(shockwaveHits.keySet().iterator().next());
            var visuals=shockwaveVisuals.computeIfAbsent(sw.cast.id,k->new ArrayList<>());
            while(shockwaveVisuals.size()>64)shockwaveVisuals.remove(shockwaveVisuals.keySet().iterator().next());
            boolean farEnough=visuals.size()<3&&visuals.stream().allMatch(v->v.distanceToSqr(sw.center)>=2.25);
            if(farEnough){
                boolean first=visuals.isEmpty();
                visuals.add(sw.center);
                quakeVisual(sw,first);
            }
            var quake=derivedCast(sw.cast,20,true,0);
            quake.center=sw.center;
            Vec3 eye=sw.center.add(0,.8,0);
            for(var enemy:SkillController.INSTANCE.targets(new AABB(sw.center,sw.center).inflate(sw.radius,3.0,sw.radius))){
                Vec3 aim=enemy.getBoundingBox().getCenter();
                double reach=sw.radius+enemy.getBbWidth()*.5;
                double dx=aim.x-sw.center.x,dz=aim.z-sw.center.z;
                if(dx*dx+dz*dz<=reach*reach&&Math.abs(enemy.getY()-sw.center.y)<=3.0&&SkillController.INSTANCE.sight(eye,aim)&&hitSet.add(enemy.getUUID())){
                    SkillController.INSTANCE.hitCast(quake,enemy,sw.damage,.25);
                }
            }
            TrainingCombat.release(sw.cast.id,sw.holdKey);
        }
        for(var r:new ArrayList<>(runs.values()))if(!MageClient.INSTANCE.valid(r.cast)||localNow-r.cast.start>r.totalMillis+5000)finish(r);
        var career=CareerClient.INSTANCE.view;var command=career.oracle;var progress=state().progress();if(!progress.hasJob(JobCatalog.ORACLE))return;long now=state().serverNow();int rank=OracleRules.rank(state().stats(),progress);String before=command.instruction+"/"+command.hermes+"/"+command.karma+"/"+command.weapon+"/"+(command.nextForcedInstruction==null?"":command.nextForcedInstruction);
        OracleRules.preparation(command,progress,CombatTiming.out(state().resources(),localNow),locked(),now);
        if(rank<=0){if(!locked()&&command.initialized){var reset=new OracleState();reset.awakeningObserved=command.awakeningObserved;reset.awakeningLearned=command.awakeningLearned;reset.awakeningGrantPending=command.awakeningGrantPending;reset.outOfCombatSince=command.outOfCombatSince;reset.hermes=command.hermes;career.oracle=reset;}return;}
        if(!command.initialized){command.initialized=true;command.nextInstruction=now+10000;command.weaponUntil=0;}
        if(now>=command.weaponUntil){var choices=new ArrayList<>(OracleSkills.WEAPONS);choices.remove(command.weapon);command.weapon=choices.get(random.nextInt(choices.size()));command.weaponUntil=now+30000;}
        if(command.karmaUntil<=now)command.karma=0;if(locked())return;
        int cap=OracleRules.cap(progress);if(command.hermes>cap){command.hermes=cap;command.hermesUntil=now+30000;command.instruction="";command.nextInstruction=now;}
        if(command.hermes==9){command.hermesUntil=0;command.instruction=OracleSkills.REPLICA;command.instructionUntil=0;command.nextForcedInstruction="";}
        else{
            if(command.hermes>0&&command.hermesUntil<=now){command.hermes=0;command.hermesUntil=0;}
            if(!command.instruction.isEmpty()&&command.instructionUntil<=now){if(!CombatTiming.out(state().resources(),localNow)){command.karma=Math.min(5,command.karma+1);command.karmaUntil=now+15000;}command.instruction="";command.nextInstruction=now;}
            OracleRules.pickNextInstruction(command,progress,state().resources().cooldowns,localNow,now,random);
        }
        String after=command.instruction+"/"+command.hermes+"/"+command.karma+"/"+command.weapon+"/"+(command.nextForcedInstruction==null?"":command.nextForcedInstruction);
        if(!before.equals(after))CareerClient.INSTANCE.publish();
    }
    private static void normal(SkillBehaviors.Context x,SkillCast c,long now,long elapsed){
        String id=c.skill.id;double damage=c.parameters.get("damage",0);
        if(id.equals(OracleSkills.DOWN)&&elapsed>=300&&!c.finisher){c.finisher=true;c.hitIndex=0;x.player().swing(net.minecraft.world.InteractionHand.MAIN_HAND);double range=c.parameters.get("range",4.8);x.slash(c,range,3.6,3.4,damage,.3,false);x.visual(c,"HIT");emitShortswordWave(x,c);INSTANCE.scheduleEcho(c,now,false,range,3.6,3.4,damage,.15,false,0,"HIT");PlayerPoseChannel.release(c);}
        if(id.equals(OracleSkills.SWEEP)&&elapsed>=500&&!c.finisher){c.finisher=true;c.hitIndex=0;c.sweepDegrees=145;double range=c.parameters.get("range",5.8);x.slash(c,range,5.8,3.4,damage,.4,true);x.visual(c,"RELEASE");emitShortswordWave(x,c);INSTANCE.scheduleEcho(c,now,false,range,5.8,3.4,damage,.2,true,145,"RELEASE");PlayerPoseChannel.release(c);}
        if(id.equals(OracleSkills.THRUST)||id.equals(OracleSkills.RIFT)){double length=c.parameters.get("range",id.equals(OracleSkills.THRUST)?7:6),duration=id.equals(OracleSkills.THRUST)?250:350,desired=length*Math.clamp(elapsed/duration,0,1);Vec3 from=x.player().position();move(x,c,desired-c.moved);c.moved=desired;c.hitIndex=0;x.path(c,from,x.player().position(),id.equals(OracleSkills.THRUST)?3.2:3.6,3.4,damage,.2);if(now-c.visualTrail>=80){x.visual(c,"TRAIL");c.visualTrail=now;}}
        if(id.equals(OracleSkills.SMILE))for(int i=c.slashes;i<2&&elapsed>=(i==0?100:300);i++){c.hit.clear();c.hitIndex=i;x.slash(c,c.parameters.get("range",4.8),3.6,3.4,damage*.5,.2,false);x.visual(c,"HIT");if(i==0)emitShortswordWave(x,c);c.slashes++;}
        if(id.equals(OracleSkills.SMILE)&&c.slashes==2){if(!c.finisher){c.finisher=true;INSTANCE.scheduleEcho(c,now,false,c.parameters.get("range",4.8),3.6,3.4,damage,.15,false,0,"HIT");}PlayerPoseChannel.release(c);}
        if((id.equals(OracleSkills.THRUST)&&elapsed>=250)||(id.equals(OracleSkills.RIFT)&&elapsed>=350)){
            if(!c.finisher){c.finisher=true;boolean thrust=id.equals(OracleSkills.THRUST);INSTANCE.scheduleEcho(c,now,true,c.parameters.get("range",thrust?7:6),thrust?3.2:3.6,3.4,damage,.1,false,0,"HIT");}
            PlayerPoseChannel.release(c);
        }
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
        r.stageAt=at;r.moved=0;r.resolved=false;r.contacts.clear();player().setPos(c.origin);player().setDeltaMovement(Vec3.ZERO);player().fallDistance=0;
        PlayerPoseChannel.playAt(c,stepClip(c,index),r.stepMillis[index],c.direction,false,at);
        r.context.visual(c,"SEGMENT");return true;
    }
    private boolean alive(LivingEntity e,long generation){if(e==null||!e.isAlive())return false;var t=state().context(e);return t!=null&&!t.life.dead&&state().predicted(t.life)>0&&(generation<0||t.life.generation==generation);}
    private void combo(Run r,Vec3 from,Vec3 to,boolean completed){
        var c=r.cast;double width=3.6*c.parameters.get("meleeScale",1);Vec3 start=from.add(0,.8,0),end=to.add(0,.8,0);
        for(var target:SkillController.INSTANCE.targets(new AABB(start,end).inflate(width/2,1.7,width/2)))if(SkillGeometry.intersects(target.getBoundingBox().inflate(width/2,1.7,width/2),start,end)&&SkillController.INSTANCE.sight(start,target.getBoundingBox().getCenter()))r.contacts.putIfAbsent(target.getUUID(),target);
        if(r.resolved)return;LivingEntity target=alive(r.target,r.targetGeneration)?r.contacts.get(r.target.getUUID()):completed?r.contacts.values().stream().filter(e->alive(e,-1)).min(Comparator.comparingDouble(e->e.distanceToSqr(c.origin))).orElse(null):null;
        if(target!=null){c.hitIndex=r.index*2;SkillController.INSTANCE.hitCast(c,target,c.parameters.get("damage",0)*.05,.4);r.resolved=true;}
    }
    private void replica(SkillBehaviors.Context x,SkillCast c,long now,long elapsed,long stage,double speed){
        var r=runs.get(c.id);if(r==null){x.cancel(false);return;}if(r.stepSlot>=0)player().getInventory().setSelectedSlot(r.stepSlot);
        if(r.index<0&&!next(r,now)){finish(r);x.cancel(true);return;}
        for(int transitions=0;transitions<=r.count;transitions++){
            int index=r.index;long duration=r.stepMillis[index];double local=Math.clamp(now-r.stageAt,0,duration)*speed,damage=c.parameters.get("damage",0);boolean done=now-r.stageAt>=duration;
            if(OracleSkills.combo(index)){double comboMove=Math.max(0,c.parameters.get("comboMoveDistance",8.5)),comboStep=Math.max(.05,c.parameters.get("comboStepSeconds",OracleSkills.COMBO_INTERVAL));Vec3 from=x.player().position();if(r.target!=null){double desired=comboMove*Math.clamp(local/(comboStep*1000),0,1);move(x,c,desired-r.moved);r.moved=desired;}combo(r,from,x.player().position(),done);if(!c.finisher){c.finisher=true;x.visual(c,"HIT");}}
            else if(index==OracleSkills.FINISH_STEP){if(local>=600){double finisherMove=Math.max(0,c.parameters.get("range",9)),finisherShare=Math.clamp(c.parameters.get("finisherShare",.35),0,1);if(r.target!=null){double desired=finisherMove*Math.clamp((local-600)/200,0,1);move(x,c,desired-r.moved);r.moved=desired;}c.hitIndex=index*2;x.path(c,c.origin,r.target!=null?x.player().position():c.origin.add(c.direction.scale(finisherMove)),3.8,3.6,damage*finisherShare,.4);if(!c.finisher){c.finisher=true;c.charging=false;x.visual(c,"FINISH");}}}
            else{int action=OracleSkills.action(c.id.hashCode(),index);double part=damage*.0625;if(r.target!=null){double desired=5.5*Math.clamp(local/250,0,1);move(x,c,desired-r.moved);r.moved=desired;}Vec3 end=r.target!=null?x.player().position():c.origin.add(c.direction.scale(5.5));c.hitIndex=index*2+c.slashes;x.path(c,c.origin,end,action==1?3.2:3.6,3.4,action==3?part*.5:part,.2);if(action==3&&local>=175&&c.slashes==0){c.slashes=1;c.hit.clear();c.hitIndex=index*2+1;x.path(c,c.origin,end,3.6,3.4,part*.5,.2);x.visual(c,"HIT");}if(!c.finisher&&local>=100){c.finisher=true;x.visual(c,action==2?"RELEASE":"HIT");}}
            if(now-c.visualTrail>=80){x.visual(c,"TRAIL");c.visualTrail=now;}if(!done)return;
            long nextAt=r.stageAt+duration;if(!next(r,nextAt)){finish(r);x.cancel(true);return;}
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
    private static Vec3 flat(Vec3 v){var result=new Vec3(v.x,0,v.z);return result.lengthSqr()<.0001?new Vec3(0,0,1):result.normalize();}
}
