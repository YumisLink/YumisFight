package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.combat.api.ActionStatus;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.combat.api.ProjectileSpawn;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Client-side career geometry and effect clocks; all results use the normal receipt path. */
public final class CareerClient {
    private static final Gson JSON=new Gson();
    public static final CareerClient INSTANCE=new CareerClient();
    public CareerState view=new CareerState();
    private final ArrayDeque<String> renewQueue=new ArrayDeque<>();private long renewAfter;
    public void renewSummons(){
        if(!state().progress().hasJob(JobCatalog.SUMMONER)||waiting())return;
        renewQueue.clear();
        Stats snap=SkillController.INSTANCE.snapshot();
        boolean kingLaserCommand=TowerBattleCard.SUMMONER_KING_LASER.held(snap)&&SummonerClient.isKingAlive(state().serverNow());
        for(String id:SummonerSkills.RENEW){
            if(id.equals(SummonerSkills.KING)&&kingLaserCommand)continue;
            if(SkillLearning.effective(state().stats(),state().progress(),SkillCatalog.require(id))>0)renewQueue.add(id);
        }
        nextRenew();
    }
    public void tickRenew(){if(ClientUi.now()>=renewAfter)nextRenew();}
    public void stopRenewing(){renewQueue.clear();}
    private void nextRenew(){if(waiting()||!SkillController.INSTANCE.readyToCast(ClientUi.now())||renewQueue.isEmpty())return;String id=renewQueue.remove();request(SkillCatalog.require(id),-1);renewAfter=ClientUi.now()+600;}
    private final Map<String,SkillCast> sources=new HashMap<>();
    private final Map<String,Long> fieldTicks=new HashMap<>(),freezes=new HashMap<>();
    private final List<Orb> orbs=new ArrayList<>();
    private final List<CareerWave> waves=new ArrayList<>();
    private final List<FireBall> fireBalls=new ArrayList<>();
    private final Map<String,OrbClock> orbClocks=new HashMap<>();
    private final List<FlameBolt> bolts=new ArrayList<>();
    private final AsuraClient asura=new AsuraClient();
    private String session="",job="",dimension="",instance="";
    private long generation=-1,lastBloodGain,lastEyeProjection,lastLampPublish,lastAuraFieldPublish;
    private int kallaBoltIndex;
    private boolean dead;private final Set<String> healed=new HashSet<>();
    private static final class Orb {SkillCast source;LivingEntity target;Vec3 position;double remaining=10;long targetGeneration,next,lastKeepalive,visualStarted;int index,visualSequence,visualEpoch;String visualId;}
    private static final class OrbClock {long next,lastServer;}
    private static final class FireBall {SkillCast source;FireWavePath path;Vec3 position;long next,lastSend;double progress;boolean waiting,confirmed,explosionShown;final Set<UUID> captured=new LinkedHashSet<>(),impactTargets=new LinkedHashSet<>();}
    private static final class CareerWave {
        SkillCast source;Vec3 position;double travelled;long next;
        final Map<UUID,Integer> hits=new HashMap<>();
        final Map<UUID,Long> nextHit=new HashMap<>();
    }
    private static final class FlameBolt {SkillCast source;Vec3 origin,position,direction;double remaining,range;long next,started;String id;int sequence;}
    private CareerClient(){}
    private SkillsClient state(){return SkillsClient.current();}
    private SkillParameters.Snapshot brandParameters(){var p=Minecraft.getInstance().player;if(p==null)return null;var skill=SkillCatalog.require(CareerSkills.BRANDS);var stats=state().stats();return SkillParameters.forCast(p.getUUID(),skill,Math.max(1,SkillLearning.effective(stats,state().progress(),skill)),stats.parameters);}
    private long brandGapMillis(){var brandParams=brandParameters();return brandParams==null?5000:Math.max(50,(long)(brandParams.get("brandInterval",5)*1000));}
    int brandCap(){var brandParams=brandParameters();return brandParams==null?5:Math.max(1,(int)brandParams.get("brandCap",5));}
    void gainBrand(int amount){
        if(amount<=0||!state().ready()||state().own().dead||!state().progress().hasJob(JobCatalog.ASURA))return;
        if(SkillLearning.effective(state().stats(),state().progress(),SkillCatalog.require(CareerSkills.BRANDS))<=0)return;
        int next=Math.min(brandCap(),view.brands+amount);
        if(next!=view.brands){view.brands=next;publish();}
    }
    void startHomingIce(SkillCast cast){asura.startHomingIce(cast);}
    public void clear(){healed.clear();lastBloodGain=lastEyeProjection=lastLampPublish=lastAuraFieldPublish=0;kallaBoltIndex=0;renewQueue.clear();asura.clear();BerserkerClient.clear();SoulBenderClient.clear();OracleClient.INSTANCE.clear();ElementalistChargeClient.reset();ElementalistClient.clear();WitchClient.clear();for(var source:sources.values()){SkillController.INSTANCE.stopVisual(source);TrainingCombat.release(source.id,"persistent");TrainingCombat.actionFinished(source.id);}for(var orb:orbs){orbVisual(orb,"CANCEL");TrainingCombat.release(orb.source.id,"orb");}for(var wave:waves)TrainingCombat.release(wave.source.id,"wave");for(var bolt:bolts)TrainingCombat.release(bolt.source.id,bolt.id);for(var ball:fireBalls)TrainingCombat.release(ball.source.id,"fire");fireBalls.clear();orbClocks.clear();sources.clear();fieldTicks.clear();freezes.clear();orbs.clear();waves.clear();bolts.clear();view=new CareerState();}
    public void ensureContext(){
        var p=Minecraft.getInstance().player;if(p==null||!state().ready())return;String current=p.level().dimension().identifier().toString();boolean changed=!session.equals(state().session())||generation!=state().own().generation||!job.equals(state().progress().jobId())||!dimension.equals(current)||!instance.equals(state().own().instance)||dead!=state().own().dead;
        if(!changed)return;clear();session=state().session();generation=state().own().generation;job=state().progress().jobId();dimension=current;instance=state().own().instance;dead=state().own().dead;view.nextBrand=state().serverNow()+brandGapMillis();DemonHunterClient.clear();
        if(!dead&&state().progress().hasJob(JobCatalog.SUN_MOON)&&state().progress().awakened()&&TowerBattleCard.SUNMOON_PARADOX_LOOP.held(state().stats()))view.sunMoon.paradox=Math.max(view.sunMoon.paradox,1);
        if(!dead)publish();
    }
    private void ownField(){String owner=Minecraft.getInstance().player.getStringUUID();view.fields.removeIf(f->f.owner.equals(owner));if(view.field!=null){var f=copy(view.field,CareerState.Field.class);f.friendly=true;view.fields.add(f);}}
    public void receive(JsonObject data){
        CareerState next=JSON.fromJson(data,CareerState.class);if(!state().session().equals(next.session)||next.generation!=state().own().generation||!next.job.equals(state().progress().jobId()))return;
        ensureContext();if(next.sequence>view.sequence)view=next;else{view.fields=next.fields;view.actors=next.actors;}ownField();
    }
    public void reduced(JsonObject event){
        if(!state().session().equals(event.get("session").getAsString())||event.get("generation").getAsLong()!=state().own().generation)return;String id=event.get("skill").getAsString();SkillReductions.apply(id,view,state().resources());view.reductions.merge(id,1L,Math::addExact);
        for(var source:new ArrayList<>(sources.values()))if(source.skill.id.equals(id)||SunMoonSkills.contains(id)&&SunMoonSkills.contains(source.skill.id)){SkillController.INSTANCE.stopVisual(source);cancel(source);}
        if(SkillController.INSTANCE.cast!=null&&SkillController.INSTANCE.cast.skill.id.equals(id))SkillController.INSTANCE.cancel(false);
        if(id.equals(CareerSkills.BRANDS))view.nextBrand=state().serverNow()+brandGapMillis();
        var packet=new JsonObject();packet.addProperty("session",state().session());packet.addProperty("generation",state().own().generation);packet.addProperty("skill",id);state().send("summonReduce",packet);publish();
    }
    public void hit(LivingEntity target,boolean direct,JsonObject report,double actual){
        if(actual<=0)return;long now=state().serverNow();boolean changed=false;
        var sword=sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.SWORD)).max(Comparator.comparingLong(c->c.start)).orElse(null);int bloodCap=sword==null?SkillResourceRules.BLOOD_CAP:(int)Math.max(1,sword.parameters.get("bloodCap",SkillResourceRules.BLOOD_CAP));long bloodGap=sword==null?SkillResourceRules.BLOOD_INTERVAL:Math.max(50,(long)(sword.parameters.get("bloodInterval",SkillResourceRules.BLOOD_INTERVAL/1000d)*1000));if(view.bloodUntil>now&&view.blood<bloodCap&&now-lastBloodGain>=bloodGap){view.blood++;lastBloodGain=now;changed=true;}
        if(report.has("careerCast")){String id=report.get("careerCast").getAsString();var source=sources.get(id);if(source!=null){report.addProperty("careerSkill",source.skill.id);if(source.skill.id.equals(CareerSkills.HAND)){if(healed.add(id)){double healing=.10*cn.yumis.fight.modules.dungeons.api.TowerCardEffects.healingFactor(source.stats);double recovery=Math.min(healing,1-state().predicted(state().own()));report.addProperty("ownerHealing",report.has("ownerHealing")?report.get("ownerHealing").getAsDouble()+recovery:recovery);}BerserkerClient.onHandHit(source);}}}
        if(changed)publish();
    }
    public void released(int slot){var cast=SkillController.INSTANCE.cast;if(cast!=null&&cast.slot%8==slot&&cast.charging)cast.release=true;}
    public boolean waiting(){return ChantClient.active()||ElementalistChargeClient.active()||BattleMageChargeClient.active();}
    private static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}
    private static final String FRENZY_LEASE="career/frenzy";
    private void syncFrenzyLease(){
        if(frenzy()){
            if(!SkillController.INSTANCE.statusLeases().hasOwner(FRENZY_LEASE,ActionStatus.STEADFAST,ClientUi.now()))
                SkillController.INSTANCE.acquireStatus(FRENZY_LEASE,ActionStatus.STEADFAST);
        }else{
            SkillController.INSTANCE.releaseStatus(FRENZY_LEASE,ActionStatus.STEADFAST);
        }
    }
    static void installHooks(){
        SkillCastHooks.onRequest(SkillCastHooks.Stage.VALIDATE_REQUEST,req->{
            String id=req.skill.id;
            if((id.equals(CareerSkills.ORB)||id.equals(CareerSkills.AGNI))&&req.currentView.brands<1)
                throw new IllegalArgumentException("至少需要一枚波动刻印");
        });
        SkillCastHooks.onRequest(SkillCastHooks.Stage.PREPARE_STATS,req->{
            req.prepared=CareerRules.eyeDamage(req.prepared,req.skill);
        });
        SkillCastHooks.onRequest(SkillCastHooks.Stage.RESOLVE_PARAMETERS,req->{
            String id=req.skill.id;
            if(id.equals(CareerSkills.FIRE)&&!TowerBattleCard.ASURA_FIRE_TRIPLE.held(req.prepared)){
                var p=Minecraft.getInstance().player;
                Vec3 from=p.getEyePosition(),aim=p.getLookAngle().normalize();
                Vec3 end=from.add(aim.scale(ProjectileSpawn.extendedRange(10)));
                var wallHit=p.level().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                Vec3 to=wallHit.getType()==HitResult.Type.MISS?end:wallHit.getLocation().subtract(aim.scale(.05));
                if(to.subtract(from).dot(aim)<0)to=from;
                var spawn=ProjectileSpawn.inWorld(p,from,aim,from.distanceTo(to),2.5,SkillController::canTarget);
                from=spawn.position();
                var values=new LinkedHashMap<>(req.parameters.values());
                new FireWavePath(from,to,1.5,Math.max(1,from.distanceTo(to)/15*1000)).write(values);
                req.parameters=new SkillParameters.Snapshot(req.skill,req.parameters.rank(),values);
                req.origin=from;req.direction=aim;req.center=from;
            }
            if(id.equals(CareerSkills.SWORD)&&req.terminal){
                req.center=req.origin.add(req.direction.scale(StormChoreography.CENTER_OFFSET));
            }
        });
        SkillCastHooks.onRequest(SkillCastHooks.Stage.COMMIT_STATE,req->{
            String id=req.skill.id;
            if(id.equals(CareerSkills.BLACHE)){
                boolean keepField=TowerBattleCard.SB_BLACHE_VOID.held(req.prepared);
                if(req.nextView.field!=null&&req.nextView.field.expires>req.wall)
                    SoulBenderVisuals.recordDevouredField(req.origin,new Vec3(req.nextView.field.x,req.nextView.field.y,req.nextView.field.z),CareerSkills.ghosts(req.nextView.field.rank,req.nextView.field.follow));
                if(!keepField)req.nextView.field=null;
            }
            if(id.equals(CareerSkills.ORB))req.nextView.brands=TowerBattleCard.ASURA_ORB_OVERDRIVE.held(req.prepared)?Math.max(0,req.nextView.brands-TowerBattleCard.ASURA_ORB_OVERDRIVE.count("cost")):0;
            if(id.equals(CareerSkills.AGNI))req.nextView.brands=0;
            if(id.equals(CareerSkills.FRENZY))req.nextView.frenzy=!req.terminal;
            if(id.equals(CareerSkills.KALLA))req.nextView.kallaUntil=req.terminal?0:req.wall+(long)(req.parameters.get("duration",20)*1000);
            if(id.equals(CareerSkills.EYE)){req.nextView.eyeUntil=req.terminal?0:req.wall+(long)(req.parameters.get("duration",20)*1000);req.nextView.eyeBonusMut=req.terminal?0:req.parameters.get("eyeBonusMut",.20);}
            if(id.equals(CareerSkills.SWORD)){req.nextView.bloodUntil=req.terminal?0:req.wall+(long)(req.parameters.get("duration",60)*1000);req.nextView.blood=0;}
            if(CareerRules.producesBrand(id))req.nextView.brands=Math.min(INSTANCE.brandCap(),req.nextView.brands+1);
        });
        SkillCastHooks.onCast(SkillCastHooks.Stage.ON_CAST_START,event->{
            var c=event.cast();long now=event.now();String id=c.skill.id;
            if(id.equals(CareerSkills.FIELD))TrainingCombat.hold(c.id,"persistent",now+(long)(c.parameters.get("duration",12)*1000));
            if(id.equals(CareerSkills.KALLA)&&!c.terminal)TrainingCombat.hold(c.id,"persistent",now+(long)(c.parameters.get("duration",20)*1000));
            if(id.equals(CareerSkills.PHANTOM)){
                long invMs=Math.max(1,(long)(c.parameters.get("invulnerableMillis",250)/c.speed));
                SkillController.INSTANCE.acquireStatus(c.id,ActionStatus.INVULNERABLE,now+invMs);
            }
            if(id.equals(CareerSkills.BLADE)&&TowerBattleCard.BERSERKER_BLADE_ASSAULT.held(c.stats)){
                long invMs=Math.max(1,Math.round(240.0/c.speed));
                SkillController.INSTANCE.acquireStatus(c.id,ActionStatus.INVULNERABLE,now+invMs);
            }
            if(c.terminal&&(id.equals(CareerSkills.EYE)||id.equals(CareerSkills.SWORD))){
                SkillController.INSTANCE.acquireStatus(c.id,ActionStatus.INVULNERABLE);
            }
        });
        SkillCastHooks.onHit(event->{
            INSTANCE.hit(event.target(),event.direct(),event.report(),event.actual());
            if(event.source()!=null&&event.actual()>0)SoulBenderClient.schedulePhantomRift(event.source(),event.target());
        });
        SkillCastHooks.onVisual(event->{
            var c=event.cast();String phase=event.phase();var j=event.packet();String skill=c.skill.id;
            if(CareerSkills.contains(skill)){
                j.addProperty("terminal",c.terminal);j.addProperty("sweep",c.terminal);
                if(skill.equals(CareerSkills.FIELD)){j.addProperty("x",c.center.x);j.addProperty("y",c.center.y);j.addProperty("z",c.center.z);j.addProperty("px",c.center.x);j.addProperty("py",c.center.y);j.addProperty("pz",c.center.z);j.addProperty("charge",Math.min(1,c.parameters.rank()/15d));if(TowerBattleCard.SB_AURA_FIELD.held(c.stats))j.addProperty("dual",true);}
                if(skill.equals(CareerSkills.TOMBSTONE)){j.addProperty("horizontalScale",Math.clamp(c.parameters.get("slashes",10)/10.0,1.0,4.0));if(TowerBattleCard.SB_TOMB_DETACH.held(c.stats))j.addProperty("sweep",true);}
                if(skill.equals(CareerSkills.MOONLIGHT)&&phase.equals("SEGMENT")){j.addProperty("sweep",SoulBenderRules.moonlightFullMoon(c.segment));j.addProperty("charge",Math.clamp(c.segment/6.0,0.0,1.0));}
                if(skill.equals(CareerSkills.PHANTOM)){j.addProperty("horizontalScale",Math.max(.5,c.parameters.get("width",3)/3.0));if(phase.equals("SEGMENT"))j.addProperty("charge",Math.clamp(c.segment/4.0,0.0,1.0));}
                if(skill.equals(CareerSkills.ORB)){j.addProperty("px",c.center.x);j.addProperty("py",c.center.y+.8);j.addProperty("pz",c.center.z);}
                if(skill.equals(CareerSkills.CRESCENT)||skill.equals(CareerSkills.FIRE)||skill.equals(CareerSkills.SWORD)&&c.terminal){j.addProperty("px",c.center.x);j.addProperty("py",c.center.y);j.addProperty("pz",c.center.z);}
                if(skill.equals(CareerSkills.BLADE)&&phase.equals("FINISH")){j.addProperty("px",c.center.x);j.addProperty("py",c.center.y);j.addProperty("pz",c.center.z);}
                if(skill.equals(CareerSkills.BLADE)&&TowerBattleCard.BERSERKER_BLADE_ASSAULT.held(c.stats))j.addProperty("sweep",true);
                if(skill.equals(CareerSkills.BLADE)&&TowerBattleCard.BERSERKER_BLADE_THROW.held(c.stats))j.addProperty("dual",true);
                if(skill.equals(CareerSkills.HAND)&&TowerBattleCard.BERSERKER_HAND_CLAW.held(c.stats))j.addProperty("dual",true);
                if(c.finisher&&(skill.equals(CareerSkills.CRASH)||skill.equals(CareerSkills.ERUPTION))){j.addProperty("px",c.center.x);j.addProperty("py",c.center.y);j.addProperty("pz",c.center.z);}
                if(skill.equals(CareerSkills.CRASH))j.addProperty("horizontalScale",Math.clamp(c.parameters.get("range",4.0)/4.0,0.5,4.0));
                if(skill.equals(CareerSkills.HAND))j.addProperty("horizontalScale",Math.clamp(c.parameters.get("range",4.0)/4.0,0.5,4.0));
                if(skill.equals(CareerSkills.RAGE)){
                    j.addProperty("horizontalScale",Math.clamp(c.parameters.get("range",5.0)/5.0,0.5,4.0));
                    int rageHits=(int)Math.max(1,c.parameters.get("slashes",5));
                    long rageGap=Math.max(50L,Math.round(c.parameters.get("interval",.15)*1000));
                    long rageDur=(long)Math.ceil(((rageHits-1)*rageGap+750L)/Math.max(.01,c.speed));
                    j.addProperty("duration",Math.max(j.get("duration").getAsLong(),rageDur));
                }
                if(skill.equals(CareerSkills.BLADE))j.addProperty("horizontalScale",Math.clamp(c.parameters.get("blastRadius",4.0)/4.0,0.5,4.0));
                if(skill.equals(CareerSkills.ERUPTION)){
                    j.addProperty("horizontalScale",Math.clamp(c.parameters.get("range",7.0)/7.0,0.5,4.0));
                    int erHits=(int)Math.max(1,c.parameters.get("slashes",4));
                    long erGap=Math.max(50L,Math.round(c.parameters.get("interval",.2)*1000));
                    long erDur=(long)Math.ceil((850L+erHits*erGap+700L)/Math.max(.01,c.speed));
                    j.addProperty("duration",Math.max(j.get("duration").getAsLong(),erDur));
                }
                if(skill.equals(CareerSkills.CRESCENT)){j.addProperty("horizontalScale",TowerBattleCard.ASURA_CRESCENT_MAX.held(c.stats)?1d+TowerBattleCard.ASURA_CRESCENT_MAX.ratio("size"):1d);if(!c.charging)j.addProperty("charge",c.moved);}
                if(skill.equals(CareerSkills.FIRE))j.addProperty("horizontalScale",Math.max(.5,c.parameters.get("blastRadius",2.5))/2.5d);
                if(skill.equals(CareerSkills.SWORD))j.addProperty("charge",Math.min(1,c.parameters.get("blood",0)/20));
                if(skill.equals(CareerSkills.AGNI)){int orbs=Math.clamp((int)Math.round(c.parameters.get("agniOrbs",c.parameters.get("resource",1))),1,SkillResourceRules.AGNI_MAX_ORBS);j.addProperty("charge",(double)orbs/SkillResourceRules.AGNI_MAX_ORBS);}
                if(skill.equals(CareerSkills.ORB))j.addProperty("charge",Math.clamp(c.parameters.get("resource",1)/5d,.2,1));
                if(skill.equals(CareerSkills.BLACHE))j.addProperty("charge",CareerSkills.ghosts((int)Math.round(c.parameters.get("fieldRank",0)),TowerBattleCard.SB_AURA_FIELD.held(c.stats))/4d);
                if(phase.equals("RELEASE")&&(skill.equals(CareerSkills.ORB)||skill.equals(CareerSkills.CRESCENT)||skill.equals(CareerSkills.FIRE))||phase.equals("FINISH")&&skill.equals(CareerSkills.FIRE))j.addProperty("cosmeticOnly",true);
                if(skill.equals(CareerSkills.PHANTOM)&&(phase.equals("START")||phase.equals("SEGMENT")))j.add("motion",SkillController.motion(c.parameters.get("dashDistance",8),c.parameters.get("dashSeconds",.25)*1000/c.speed,false));
                if(skill.equals(CareerSkills.CRESCENT)&&phase.equals("RELEASE")){long travel=(long)Math.ceil(c.parameters.get("range",12)/15*1000);j.add("motion",SkillController.motion(c.parameters.get("range",12),travel,false));j.addProperty("duration",Math.max(j.get("duration").getAsLong(),(ClientUi.now()-c.start)+travel+600L));}
                if(skill.equals(CareerSkills.FIRE)&&phase.equals("RELEASE")&&!TowerBattleCard.ASURA_FIRE_TRIPLE.held(c.stats)){var path=FireWavePath.from(c.parameters);long travel=(long)Math.ceil(path.millis());var m=SkillController.motion(path.origin().distanceTo(path.destination()),travel,false);m.addProperty("arcHeight",path.height());j.add("motion",m);j.addProperty("duration",Math.max(j.get("duration").getAsLong(),(ClientUi.now()-c.start)+travel+700L));}
                if(skill.equals(CareerSkills.FIRE)&&phase.equals("FINISH"))j.addProperty("duration",Math.max(j.get("duration").getAsLong(),(ClientUi.now()-c.start)+600L));
                if(skill.equals(CareerSkills.ORB)&&phase.equals("RELEASE"))j.add("motion",SkillController.motion(c.parameters.get("range",10),c.parameters.get("range",10)/8*1000,false));
                if((skill.equals(CareerSkills.CRASH)||skill.equals(CareerSkills.ERUPTION))&&phase.equals("START")){var m=SkillController.motion(skill.equals(CareerSkills.CRASH)?3:6,(skill.equals(CareerSkills.CRASH)?500:600)/c.speed,false);m.addProperty("arcHeight",skill.equals(CareerSkills.CRASH)?1.2:2);j.add("motion",m);}
            }
        });
    }
    public void publish(){
        var p=Minecraft.getInstance().player;if(p==null||!state().ready())return;
        syncFrenzyLease();
        view.session=state().session();view.generation=state().own().generation;view.job=state().progress().jobId();view.dimension=p.level().dimension().identifier().toString();view.instance=state().own().instance;view.sequence++;
        long serverNow=state().serverNow();long overdriveLeft=state().resources().buffs.getOrDefault("yumis_dungeon_and_fight:overdrive",0L)-ClientUi.now();boolean isOracle=state().progress().hasJob(JobCatalog.ORACLE);
        ownField();var actor=new CareerState.Actor();actor.owner=p.getStringUUID();actor.keiga=SkillLearning.effective(state().stats(),state().progress(),SkillCatalog.require(CareerSkills.KEIGA));actor.brands=view.brands;actor.blood=view.blood;actor.frenzy=view.frenzy;actor.kallaUntil=view.kallaUntil;actor.eyeUntil=view.eyeUntil;actor.bloodUntil=view.bloodUntil;actor.sunMoonHeart=view.sunMoon.heart;actor.sunMoonStacks=view.sunMoon.stacks;actor.metamorphosisUntil=view.demonHunter.metamorphosisUntil;actor.auraUntil=view.demonHunter.auraUntil;actor.elementLampsMask=view.elementalist.mask(serverNow);actor.battleMageChasers=view.battleMage.packedChasers(serverNow);actor.teanaUntil=view.battleMage.teanaUntil;actor.grooveStage=view.battleMage.grooveStage;actor.witchBroomGliding=view.witch.broomGliding;actor.witchCandyUntil=view.witch.candyBuffUntil;actor.witchMachine=view.witch.activeMachine==null?"":view.witch.activeMachine;actor.overdriveUntil=overdriveLeft>0?serverNow+overdriveLeft:0L;actor.oracleHermes=isOracle?view.oracle.hermes:0;actor.oracleHermesUntil=isOracle?view.oracle.hermesUntil:0L;actor.oracleInstructed=isOracle&&!view.oracle.instruction.isEmpty()&&(view.oracle.hermes==9||view.oracle.instructionUntil>serverNow);view.actors.removeIf(a->a.owner.equals(actor.owner));view.actors.add(actor);
        view.steadfast=SkillController.isSteadfast();view.invulnerable=SkillController.INSTANCE.invulnerable();
        var packet=new JsonObject();packet.addProperty("session",view.session);packet.addProperty("generation",view.generation);packet.add("state",JSON.toJsonTree(view));state().send("careerSnapshot",packet);state().upload();
    }
    public void request(SkillDefinition skill,int slot){
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null||!state().ready()||!state().combatAvailable()||state().own().dead||p.isDeadOrDying()||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(p))return;
        ensureContext();
        try{
            long now=ClientUi.now(),wall=state().serverNow();String id=skill.id;
            var progress=state().progress();Stats base=state().weaponStats(p.getMainHandItem());Stats equipment=SkillPreview.currentStats(base,state().resources(),now);
            int rank=SkillLearning.effective(equipment,progress,skill);if(rank<=0||skill.passive())return;
            boolean terminal=id.equals(CareerSkills.KALLA)&&view.kallaUntil>wall&&!TowerBattleCard.SB_KALLA_ETERNAL.held(equipment)||id.equals(CareerSkills.EYE)&&view.eyeUntil>0||id.equals(CareerSkills.SWORD)&&view.bloodUntil>0||id.equals(CareerSkills.FRENZY)&&view.frenzy||BattleMageClient.isTeanaTerminal(id,wall);
            var next=copy(view,CareerState.class);var resources=copy(state().resources(),cn.yumis.fight.modules.combat.api.Runtime.class);resources.equipment=cn.yumis.fight.modules.equipment.api.EquipmentStats.projectileEffects(p,progress,p.getMainHandItem());
            var life=copy(state().own(),cn.yumis.fight.modules.combat.api.Life.class);life.q=state().predicted(state().own());double before=life.q;
            var req=new SkillCastHooks.Request(skill,slot,rank,terminal,now,wall,progress,view,next,resources,life);
            req.prepared=equipment;
            SkillCastHooks.dispatch(SkillCastHooks.Stage.BEFORE_REQUEST,req);
            if(req.rejected||ChantClient.active()||ElementalistChargeClient.active()||BattleMageChargeClient.active()||!SkillController.INSTANCE.readyToCast(now,skill))return;
            if(!skill.weaponAllowed(progress,equipment))throw new IllegalArgumentException("主手需要本职业可用武器");
            if(equipment.weaponType.equals("WARGLAIVE")&&!p.getOffhandItem().isEmpty())throw new IllegalArgumentException("使用战刃需要副手为空");
            SkillCastHooks.dispatch(SkillCastHooks.Stage.VALIDATE_REQUEST,req);
            var preParams=SkillParameters.forCast(p.getUUID(),skill,rank,equipment.parameters);
            if(!terminal&&!p.isCreative()&&!state().resources().training&&state().resources().cooldowns.getOrDefault(id,0L)>now&&!ElementalistClient.hasCharge(skill,preParams,now)&&!BattleMageClient.hasCharge(skill,preParams,now))throw new IllegalArgumentException("技能冷却中");
            var content=CareerSkills.content(id);double hpCost=terminal||content==null?0:preParams.get("hpCost",content.hpCost());
            req.prepared=CareerRules.current(equipment,progress,next,life,life.q,view.red(p.getX(),p.getY(),p.getZ(),wall),view.ownField(p.getX(),p.getY(),p.getZ(),wall),wall);
            SkillCastHooks.dispatch(SkillCastHooks.Stage.PREPARE_STATS,req);
            MageClient.INSTANCE.prepare(skill);Vec3 destination=SkillBehaviors.prepare(skill);
            cn.yumis.fight.modules.combat.api.EquipmentBuffs.prune(resources,resources.equipment,now,false);
            boolean career=CareerSkills.contains(id);
            if(!career&&!terminal){
                cn.yumis.fight.modules.combat.api.EquipmentBuffs.imprint(resources,resources.equipment,now);
                equipment=SkillPreview.currentStats(base,resources,now);
                req.prepared=CareerRules.current(equipment,progress,next,life,life.q,view.red(p.getX(),p.getY(),p.getZ(),wall),view.ownField(p.getX(),p.getY(),p.getZ(),wall),wall);
                SkillCastHooks.dispatch(SkillCastHooks.Stage.PREPARE_STATS,req);
            }
            var cost=SkillCosts.resolve(skill,rank,career?equipment:req.prepared,resources.equipment,CareerRules.magic(skill,progress));
            double healthMaxForCost=career?equipment.get(Stat.HEALTH):req.prepared.get(Stat.HEALTH);
            if(!terminal)SkillCosts.pay(cost,resources,life,healthMaxForCost,hpCost);
            if(before>life.q&&TowerBattleCard.BERSERKER_BLOOD_AWAKENING.held(req.prepared)){
                BerserkerClient.applyBloodAwakeningShield(resources,healthMaxForCost,(before-life.q)*healthMaxForCost,now);
            }
            if(career&&!terminal)cn.yumis.fight.modules.combat.api.EquipmentBuffs.imprint(resources,resources.equipment,now);
            equipment=SkillPreview.currentStats(base,resources,now);
            var oracleCast=OracleClient.INSTANCE.prepare(next,skill,req.prepared,wall);
            req.oracleReplicaStacks=oracleCast.replicaStacks();
            int activeFieldRank=id.equals(CareerSkills.BLACHE)&&next.field!=null&&next.field.expires>wall?next.field.rank:0;
            int learnedFieldRank=id.equals(CareerSkills.BLACHE)?SkillLearning.effective(equipment,progress,SkillCatalog.require(CareerSkills.FIELD)):0;
            int brands=next.brands,blood=next.blood,fieldRank=id.equals(CareerSkills.BLACHE)?SoulBenderRules.blacheEffectiveFieldRank(activeFieldRank,learnedFieldRank,equipment):0;
            req.prepared=CareerRules.current(equipment,progress,next,life,life.q,view.red(p.getX(),p.getY(),p.getZ(),wall),next.ownField(p.getX(),p.getY(),p.getZ(),wall),wall);
            req.prepared=OracleRules.applyCastBonus(req.prepared,oracleCast);
            SkillCastHooks.dispatch(SkillCastHooks.Stage.POST_PAY_STATS,req);
            var parameters=WeaponSkills.skill(SkillParameters.forCast(p.getUUID(),skill,rank,req.prepared.parameters),progress,req.prepared);
            if(terminal){var original=sources.values().stream().filter(c->c.skill.id.equals(id)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null);if(original!=null){parameters=original.parameters;req.prepared=original.stats.copy();rank=parameters.rank();}}
            req.parameters=CareerRules.resourceSnapshot(parameters,req.prepared,brands,blood,fieldRank,terminal);
            req.origin=p.position();req.direction=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize();req.center=req.origin;
            SkillCastHooks.dispatch(SkillCastHooks.Stage.RESOLVE_PARAMETERS,req);
            req.prepared=CareerRules.eyeDamage(CareerRules.castDamage(req.prepared,req.parameters),skill);
            req.prepared=ElementalistRules.prepareCastStats(req.prepared,req.parameters);
            req.prepared=WitchRules.prepareCastStats(req.prepared,req.parameters);
            var values=new LinkedHashMap<>(req.parameters.values());if(terminal)values.put("actionDuration",id.equals(CareerSkills.KALLA)?.9:id.equals(CareerSkills.EYE)?1.2:2d);
            req.parameters=new SkillParameters.Snapshot(skill,rank,values);
            if(destination!=null)req.center=destination;
            String castId=UUID.randomUUID().toString();
            if(id.equals(CareerSkills.FIELD)){boolean aura=TowerBattleCard.SB_AURA_FIELD.held(req.prepared);req.center=aura?p.position():SkillTargeting.ground(p,8,SkillController::canTarget).point();var f=new CareerState.Field();f.id=castId;f.owner=p.getStringUUID();f.rank=rank;f.x=req.center.x;f.y=req.center.y;f.z=req.center.z;f.dimension=p.level().dimension().identifier().toString();f.instance=state().own().instance;f.expires=wall+(long)(req.parameters.get("duration",12)*1000);f.follow=aura;next.field=f;}
            SkillCastHooks.dispatch(SkillCastHooks.Stage.COMMIT_STATE,req);
            long cooldownMillis=0;
            if(!terminal&&!resources.training&&!p.isCreative()){
                cooldownMillis=(long)(SkillCosts.resolve(skill,rank,req.prepared,resources.equipment,CareerRules.magic(skill,progress)).cooldown()*1000);
                if(id.equals(SummonerSkills.KING)&&TowerBattleCard.SUMMONER_KING_LASER.held(req.prepared)){
                    if(SummonerClient.isKingAlive(wall)){
                        double baseCd=Math.max(1d,skill.cooldown(rank));
                        cooldownMillis=Math.max(500L,Math.round(cooldownMillis*(TowerBattleCard.SUMMONER_KING_LASER.value("commandcd")/baseCd)));
                    }else{
                        cooldownMillis=Math.min(cooldownMillis,1000L);
                    }
                }
                if(req.parameters.get("maxCharges",1.0)>1.0){
                    if(BattleMageSkills.contains(id))BattleMageClient.onCastPaid(skill,req.parameters,cooldownMillis,now,resources);
                    else ElementalistClient.onCastPaid(skill,req.parameters,cooldownMillis,now,resources);
                }
                else resources.cooldowns.put(id,now+cooldownMillis);
                OracleClient.INSTANCE.onAfterCooldownPlaced(next.oracle,resources.cooldowns,id,oracleCast.fulfilledInstruction(),req.prepared,now,wall);
            }
            resources.recordedAt=now;
            var c=new SkillCast();c.id=castId;c.skill=skill;c.slot=slot;c.parameters=req.parameters;c.stats=req.prepared;c.equipment=resources.equipment;c.weapon=req.prepared.weaponInstance;c.terminal=terminal;c.origin=req.origin;c.direction=req.direction;c.center=req.center;c.start=c.stageStart=now;c.speed=Math.max(.01,req.prepared.get(Stat.CAST_SPEED));c.dimension=p.level().dimension().identifier().toString();c.charging=SkillPresentation.charging(skill)&&!(id.equals(CareerSkills.CRESCENT)&&TowerBattleCard.ASURA_CRESCENT_MAX.held(req.prepared))&&!(id.equals(SkillCatalog.GHOST.id)&&TowerBattleCard.SB_INSTANT_SOUL.held(req.prepared));
            c.session=state().session();c.generation=state().own().generation;c.job=state().progress().jobId();c.instance=state().own().instance;
            c.cooldownMillis=cooldownMillis;
            SkillController.INSTANCE.releaseCompletedCast();
            state().resources(resources);view=next;sources.put(c.id,c);
            if(before>life.q){var extra=new JsonObject();extra.addProperty("resourceCost",true);state().report(state().own(),before-life.q,false,equipment.get(Stat.HEALTH),0xe05360,false,extra);}
            publish();
            Runnable release=()->{
                TowerSkillCombat.cast(c);
                if(!MageClient.INSTANCE.start(c))SkillController.INSTANCE.begin(c);
            };
            if(ElementalistClient.prepareCast(c,slot,progress,release))return;
            if(BattleMageClient.prepareCast(c,slot,progress,release))return;
            double chantSec=SunMoonSkills.contains(skill.id)?SunMoonRules.chantSeconds(skill,c.stats):skill.chantSeconds;
            if(chantSec>0)ChantClient.begin(c,release);
            else release.run();
        }catch(IllegalArgumentException failure){state().message(failure.getMessage());}
    }
    public Stats snapshot(Stats base){var p=Minecraft.getInstance().player;long now=state().serverNow();return p==null?base:SummonerClient.applyAuraStats(CareerRules.current(base,state().progress(),view,state().own(),state().predicted(state().own()),view.red(p.getX(),p.getY(),p.getZ(),now),view.ownField(p.getX(),p.getY(),p.getZ(),now),now));}
    public boolean frenzy(){return view.frenzy&&state().progress().hasJob(JobCatalog.BERSERKER);}
    public boolean eye(){return view.eyeUntil>state().serverNow();}
    public int visualColor(Stats stats,int fallback){return stats.modifiers.getOrDefault("careerEye",0d)>0?CareerRules.EYE_COLOR:fallback;}
    public int basicColor(Stats stats){if(stats.modifiers.getOrDefault("bmTeana",0d)>0)return BattleMageRules.COLOR_TEANA;if(state().ready()&&state().progress().hasJob(JobCatalog.BATTLE_MAGE))return BattleMageRules.COLOR_MARTIAL;if(stats.modifiers.getOrDefault("basicDark",0d)>0)return DemonHunterRules.COLOR;if(stats.modifiers.getOrDefault("careerEye",0d)>0)return CareerRules.EYE_COLOR;if(stats.modifiers.getOrDefault("careerFrenzy",0d)>0)return 0xe63951;return SkillLearning.effective(stats,state().progress(),SkillCatalog.require(CareerSkills.KEIGA))>0?0x8e56d9:0xb4f2ff;}
    public boolean invulnerable(SkillCast c){if(c==null)return false;if(c.skill.id.equals(CareerSkills.PHANTOM))return (ClientUi.now()-c.stageStart)*c.speed<=c.parameters.get("invulnerableMillis",250);return c.terminal&&(c.skill.id.equals(CareerSkills.EYE)||c.skill.id.equals(CareerSkills.SWORD));}
    void eyeStarted(SkillCast cast,long now){
        for(var old:sources.values())if(!old.id.equals(cast.id)&&old.skill.id.equals(CareerSkills.EYE)&&!old.terminal)TrainingCombat.release(old.id,"persistent");
        TrainingCombat.hold(cast.id,"persistent",now+20000);
    }
    public void cancel(SkillCast c){if(c==null)return;SkillController.INSTANCE.discardEffect(c);PlayerPoseChannel.cancel(c);OracleClient.INSTANCE.cancel(c.id);MageClient.INSTANCE.cancel(c);ended(c);}
    public void ended(SkillCast c){sources.remove(c.id);TrainingCombat.release(c.id,"persistent");TrainingCombat.release(c.id,"mage");TrainingCombat.actionFinished(c.id);}
    public void decorate(SkillCast source,LivingEntity target,JsonObject extra){
        if(source==null)return;
        if(!source.eyeGear.isEmpty()){extra.addProperty("eyeGear",source.eyeGear);extra.add("eyePoint",JSON.toJsonTree(new double[]{source.center.x,source.center.y,source.center.z}));}
        if(MageClient.decorate(source,extra))return;
        if(CareerSkills.contains(source.skill.id)){extra.addProperty("careerCast",source.id);extra.addProperty("careerIndex",source.hitIndex);if(source.contactGroup.equals("berserker_hand_claw"))extra.addProperty("careerHandClaw",true);if(source.skill.id.equals(CareerSkills.ORB))extra.addProperty("orbAt",source.scheduledHitAt);if(source.controlSeconds>0){String key=source.id+"/"+target.getUUID();long now=ClientUi.now();if(!source.skill.id.equals(CareerSkills.FIELD)||freezes.getOrDefault(key,0L)<=now){extra.addProperty("careerControl",source.controlSeconds);freezes.put(key,now+3000);}}}
        else{extra.addProperty("sourceCast",source.id);if(source.controlSeconds>0&&(ElementalistSkills.contains(source.skill.id)||BattleMageSkills.contains(source.skill.id)||WitchSkills.contains(source.skill.id)))extra.addProperty("careerControl",source.controlSeconds);}
        if(!source.endElemental.isEmpty())extra.addProperty(cn.yumis.fight.modules.combat.api.CombatEffect.END_ELEMENTAL,source.endElemental);
    }
    public void orb(SkillCast c,SkillBehaviors.Context context){Orb orb=new Orb();orb.source=c;orb.position=c.origin.add(0,.8,0);orb.remaining=c.parameters.get("range",10);orb.next=ClientUi.now();orb.visualId=c.id;orb.visualStarted=c.start;orb.visualSequence=c.visualSequence;orbs.add(orb);c.center=orb.position.add(0,-.8,0);TrainingCombat.hold(c.id,"orb",ClientUi.now()+12000);orbVisual(orb,"RELEASE");}
    private void orbVisual(Orb orb,String phase){
        var p=Minecraft.getInstance().player;if(p==null)return;long now=ClientUi.now();
        if(!phase.equals("CANCEL")&&!phase.equals("FINISH")&&now-orb.visualStarted>=30000){orbVisual(orb,"CANCEL");orb.visualId=UUID.nameUUIDFromBytes((orb.source.id+"/orb/"+(++orb.visualEpoch)).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();orb.visualStarted=now;orb.visualSequence=-1;orbVisual(orb,"START");phase="RELEASE";}
        JsonObject j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);j.addProperty("id",orb.visualId);j.addProperty("kind",CareerSkills.ORB);j.addProperty("phase",phase);j.addProperty("sequence",++orb.visualSequence);j.addProperty("seed",orb.visualId.hashCode());j.addProperty("startedAt",state().serverNow()-(now-orb.visualStarted));j.addProperty("at",state().serverNow());j.addProperty("duration",12000);j.addProperty("cosmeticOnly",true);j.addProperty("color",0xc9bbeb);j.addProperty("charge",Math.clamp(orb.source.parameters.get("resource",1)/5d,.2,1));j.addProperty("dual",orb.target!=null);
        j.addProperty("x",orb.position.x);j.addProperty("y",orb.position.y);j.addProperty("z",orb.position.z);j.addProperty("px",orb.position.x);j.addProperty("py",orb.position.y);j.addProperty("pz",orb.position.z);j.addProperty("dx",orb.source.direction.x);j.addProperty("dy",orb.source.direction.y);j.addProperty("dz",orb.source.direction.z);
        JsonObject motion=SkillController.motion(orb.target==null?orb.remaining:0,orb.target==null?orb.remaining/8*1000:0,false);
        if(orb.target!=null){motion.addProperty("target",orb.target.getStringUUID());motion.addProperty("targetEntity",orb.target.getId());}j.add("motion",motion);Effects.receive(j);
        state().send("visual",j);
    }
    public void wave(SkillCast c,SkillBehaviors.Context context){
        if(c.skill.id.equals(CareerSkills.FIRE)){
            if(TowerBattleCard.ASURA_FIRE_TRIPLE.held(c.stats)){asura.startFireBlasts(c,context);return;}
            FireBall ball=new FireBall();ball.source=c;ball.path=FireWavePath.from(c.parameters);ball.position=ball.path.origin();ball.next=ClientUi.now()+50;fireBalls.add(ball);c.center=ball.position;TrainingCombat.hold(c.id,"fire",ClientUi.now()+5000);context.visual(c,"RELEASE");return;
        }
        CareerWave wave=new CareerWave();wave.source=c;wave.position=c.origin.add(0,1.2,0);wave.next=ClientUi.now();c.center=wave.position;waves.add(wave);TrainingCombat.hold(c.id,"wave",ClientUi.now()+5000);context.visual(c,"RELEASE");
    }
    public boolean basic(Stats stats,String cycle,JsonObject content){
        if(TowerBattleCard.ASURA_BRAND_FLOW.held(stats)&&state().progress().hasJob(JobCatalog.ASURA)&&SkillLearning.effective(stats,state().progress(),SkillCatalog.require(CareerSkills.BRANDS))>0){
            if(asura.recordBasicAttack(TowerBattleCard.ASURA_BRAND_FLOW.count("hits")))gainBrand(TowerBattleCard.ASURA_BRAND_FLOW.count("gain"));
        }
        if(asura.basic(cycle,content,sources))return true;
        long now=state().serverNow();if(view.kallaUntil<=now)return false;
        SkillCast source=sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.KALLA)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null);if(source==null)return false;
        if(!content.has("kallaProjectile"))return false;var launch=content.getAsJsonObject("kallaProjectile");FlameBolt bolt=new FlameBolt();bolt.source=source;bolt.origin=bolt.position=ProjectileSpawn.vector(launch.getAsJsonArray("origin"));bolt.direction=ProjectileSpawn.vector(launch.getAsJsonArray("direction"));bolt.remaining=bolt.range=launch.get("range").getAsDouble();bolt.started=launch.get("at").getAsLong();bolt.next=ClientUi.now()-Math.clamp(state().serverNow()-bolt.started,0,2000);bolt.id=UUID.nameUUIDFromBytes((cycle+"/kalla").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();bolts.add(bolt);TrainingCombat.hold(source.id,bolt.id,ClientUi.now()+2000);flameVisual(bolt,"START",false);return false;
    }
    public JsonObject basicContent(String cycle){
        var p=Minecraft.getInstance().player;var result=new JsonObject();long now=state().serverNow();
        Vec3 direction=p.getLookAngle().normalize(),origin=p.getEyePosition().add(0,-.3,0);
        var eye=view.eyeUntil>now?sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.EYE)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null):null;
        if(eye!=null){var spawn=ProjectileSpawn.inWorld(p,origin,direction,Math.max(.5,eye.parameters.get("gearRange",ProjectileSpawn.extendedRange(AsuraRules.GEAR_RANGE))),.75,SkillController::canTarget);result.addProperty("eyeCast",eye.id);result.add("eyeProjectile",ProjectileSpawn.snapshot(spawn,direction,now));}
        if(view.kallaUntil>now){var kalla=sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.KALLA)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null);double boltRange=Math.max(.5,kalla==null?ProjectileSpawn.extendedRange(10):kalla.parameters.get("boltRange",ProjectileSpawn.extendedRange(10)));var spawn=ProjectileSpawn.inWorld(p,origin,direction,boltRange,.35,SkillController::canTarget);result.add("kallaProjectile",ProjectileSpawn.snapshot(spawn,direction,now));}
        return result;
    }
    private void flameVisual(FlameBolt b,String phase,boolean exploded){
        var p=Minecraft.getInstance().player;if(p==null)return;
        JsonObject j=new JsonObject();
        j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);
        j.addProperty("id",b.id);j.addProperty("kind",CareerSkills.KALLA);j.addProperty("phase",phase);
        j.addProperty("sequence",b.sequence++);j.addProperty("seed",b.id.hashCode());j.addProperty("startedAt",b.started);
        j.addProperty("at",state().serverNow());j.addProperty("duration",2000);j.addProperty("color",0x8f43d0);
        j.addProperty("sweep",exploded);
        j.addProperty("horizontalScale",Math.max(.5,b.source.parameters.get("blastRadius",2))/2d);
        j.addProperty("x",b.origin.x);j.addProperty("y",b.origin.y);j.addProperty("z",b.origin.z);
        j.addProperty("px",b.position.x);j.addProperty("py",b.position.y);j.addProperty("pz",b.position.z);
        j.addProperty("dx",b.direction.x);j.addProperty("dy",b.direction.y);j.addProperty("dz",b.direction.z);
        if(phase.equals("START")){var motion=SkillController.motion(b.range,1000d*b.range/15,false);motion.addProperty("at",b.started);j.add("motion",motion);}
        Effects.receive(j);state().send("visual",j);
    }
    private void detonateKallaBolt(FlameBolt bolt,LivingEntity directTarget,SkillBehaviors.Context context){
        SkillCast c=bolt.source;
        double radius=Math.max(.5,c.parameters.get("blastRadius",2));
        double coefficient=c.parameters.get("boltDamage",0);
        if(coefficient<=0)return;
        c.dot=false;c.elementOverride=3;c.controlSeconds=0;c.center=bolt.position;
        c.hitIndex=64+Math.floorMod(kallaBoltIndex++,63);
        Set<UUID> blasted=new LinkedHashSet<>();
        if(directTarget!=null&&directTarget.isAlive()&&blasted.add(directTarget.getUUID()))context.hit(c,directTarget,coefficient);
        for(var target:SkillController.INSTANCE.targets(new AABB(bolt.position,bolt.position).inflate(radius,Math.max(2,radius),radius))){
            if(!blasted.add(target.getUUID()))continue;
            Vec3 aim=target.getBoundingBox().getCenter();
            double reach=radius+target.getBbWidth()*.5;
            if(aim.distanceToSqr(bolt.position)<=reach*reach&&SkillController.INSTANCE.sight(bolt.position,aim))
                context.hit(c,target,coefficient);
        }
    }
    public void soulSlashVisual(SkillCast soul,Vec3 point,Vec3 direction,boolean inOwnField){
        var p=Minecraft.getInstance().player;if(p==null||!state().ready())return;
        long serverNow=state().serverNow();
        JsonObject j=new JsonObject();
        j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);
        j.addProperty("dimension",soul.dimension);j.addProperty("instance",soul.instance);
        j.addProperty("id",soul.id);j.addProperty("kind",CareerSkills.SOUL_SLASH);j.addProperty("phase","START");
        j.addProperty("sequence",0);j.addProperty("seed",soul.id.hashCode());
        j.addProperty("startedAt",serverNow);j.addProperty("at",serverNow);j.addProperty("serverNow",serverNow);
        j.addProperty("duration",850);j.addProperty("cosmeticOnly",true);
        j.addProperty("color",SkillPresentation.color(soul.skill,false));
        j.addProperty("charge",inOwnField?1d:0d);
        j.addProperty("x",point.x);j.addProperty("y",point.y);j.addProperty("z",point.z);
        j.addProperty("px",point.x);j.addProperty("py",point.y);j.addProperty("pz",point.z);
        j.addProperty("dx",direction.x);j.addProperty("dy",direction.y);j.addProperty("dz",direction.z);
        Effects.receive(j);state().send("visual",j);
    }
    public Vec3 firstTarget(SkillCast c,double length,double width){var p=Minecraft.getInstance().player;LivingEntity closest=null;double min=length+1;for(var e:SkillController.INSTANCE.targets(new AABB(c.origin,c.origin.add(c.direction.scale(length))).inflate(width/2,2,width/2))){Vec3 delta=e.position().subtract(c.origin);double forward=delta.dot(c.direction);if(forward>=0&&forward<min&&SkillController.INSTANCE.sight(c.origin.add(0,1,0),e.getBoundingBox().getCenter())){closest=e;min=forward;}}return closest==null?c.origin.add(c.direction.scale(length)):closest.position();}
    public void tick(SkillBehaviors.Context context){
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null)return;
        ensureContext();if(state().own().dead)return;
        long now=ClientUi.now(),wall=state().serverNow();
        OracleClient.INSTANCE.tick(context,now);boolean dirty=false;
        if(view.field!=null&&view.field.expires<=wall){view.field=null;dirty=true;}
        if(view.field!=null&&view.field.follow&&view.field.expires>wall){view.field.x=p.getX();view.field.y=p.getY();view.field.z=p.getZ();ownField();if(now-lastAuraFieldPublish>=250){lastAuraFieldPublish=now;dirty=true;}}
        if(view.kallaUntil>0&&view.kallaUntil<=wall){view.kallaUntil=0;dirty=true;}
        if(state().progress().hasJob(JobCatalog.ASURA)&&SkillLearning.effective(state().stats(),state().progress(),SkillCatalog.require(CareerSkills.BRANDS))>0){if(view.nextBrand<=0)view.nextBrand=wall+brandGapMillis();if(wall>=view.nextBrand){var brandParams=brandParameters();int cap=Math.max(1,brandParams==null?5:(int)brandParams.get("brandCap",5));long gap=Math.max(50,brandParams==null?5000:(long)(brandParams.get("brandInterval",5)*1000));while(wall>=view.nextBrand){view.brands=Math.min(cap,view.brands+1);view.nextBrand+=gap;dirty=true;}}}else if(view.brands!=0){view.brands=0;view.nextBrand=wall+5000;dirty=true;}
        if(view.eyeUntil>wall&&wall-lastEyeProjection>=1000){lastEyeProjection=wall;var targets=new JsonArray();for(var target:SkillController.INSTANCE.targets(p.getBoundingBox().inflate(16,4,16)))if(target.position().subtract(p.position()).horizontalDistanceSqr()<=256&&Math.abs(target.getY()-p.getY())<=4&&SkillController.INSTANCE.sight(p.getEyePosition(),target.getBoundingBox().getCenter()))targets.add(target.getStringUUID());var packet=new JsonObject();packet.addProperty("session",state().session());packet.addProperty("generation",state().own().generation);packet.add("targets",targets);state().send("careerBlind",packet);}
        if(view.elementalist.expire(wall)&&now-lastLampPublish>=250){lastLampPublish=now;dirty=true;}
        syncFrenzyLease();
        if(view.invulnerable!=SkillController.INSTANCE.invulnerable()||view.steadfast!=SkillController.isSteadfast())dirty=true;if(dirty)publish();
        for(var source:sources.values()){
            if(source.skill.id.equals(CareerSkills.FIELD)&&(now-source.start>=(long)(source.parameters.get("duration",12)*1000)||view.field!=null&&view.field.id.equals(source.id)&&view.field.expires<=wall))TrainingCombat.release(source.id,"persistent");
            if(source.skill.id.equals(CareerSkills.KALLA)&&!source.terminal&&(now-source.start>=(long)(source.parameters.get("duration",20)*1000)||view.kallaUntil>0&&view.kallaUntil<=wall))TrainingCombat.release(source.id,"persistent");
            if(source.skill.id.equals(CareerSkills.EYE)&&!source.terminal&&(now-source.start>=(long)(source.parameters.get("duration",20)*1000)||view.eyeUntil>0&&view.eyeUntil<=wall))TrainingCombat.release(source.id,"persistent");
        }
        cn.yumis.fight.modules.equipment.api.EquipmentStats.applyMovement(p,SkillController.INSTANCE.snapshot());
        if(view.field!=null&&view.field.expires>wall&&!view.field.follow){SkillCast c=sources.get(view.field.id);if(c!=null){long tick=Math.max(50,(long)(c.parameters.get("fieldTickInterval",1)*1000));long next=fieldTicks.getOrDefault(c.id,c.start+tick);long fieldEnd=c.start+(long)(c.parameters.get("duration",12)*1000)+1;double fieldRadius=Math.max(.5,c.parameters.get("fieldRadius",5));while(now>=next&&next<fieldEnd){int index=(int)((next-c.start)/tick)-1;c.center=new Vec3(view.field.x,view.field.y,view.field.z);c.dot=true;c.hit.clear();if(view.field.rank>=8){c.hitIndex=index;c.elementOverride=2;c.controlSeconds=c.parameters.get("controlSeconds",.5);context.area(c,fieldRadius,c.parameters.get("iceDot",0),false);}if(view.field.rank>=15){c.hit.clear();c.hitIndex=16+index;c.elementOverride=3;c.controlSeconds=0;context.area(c,fieldRadius,c.parameters.get("darkDot",0),false);}next+=tick;}fieldTicks.put(c.id,next);}}
        for(var bolt:new ArrayList<>(bolts)){
            boolean impacted=false;LivingEntity directHit=null;
            while(now>=bolt.next&&bolt.remaining>0){
                double step=Math.min(.75,bolt.remaining);Vec3 from=bolt.position,to=from.add(bolt.direction.scale(step));
                var blockHit=mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                Vec3 segmentEnd=blockHit.getType()!=HitResult.Type.MISS?blockHit.getLocation():to;
                var hit=SkillController.INSTANCE.targets(new AABB(from,segmentEnd).inflate(.35)).stream()
                    .filter(t->SkillController.INSTANCE.sight(from,t.getBoundingBox().getCenter()))
                    .min(Comparator.comparingDouble(t->from.distanceToSqr(t.getBoundingBox().getCenter()))).orElse(null);
                if(hit!=null){bolt.position=hit.getBoundingBox().getCenter();bolt.remaining=0;impacted=true;directHit=hit;break;}
                if(blockHit.getType()!=HitResult.Type.MISS){bolt.position=blockHit.getLocation().subtract(bolt.direction.scale(.05));bolt.remaining=0;impacted=true;break;}
                bolt.position=to;bolt.remaining-=step;bolt.next+=50;
            }
            if(bolt.remaining<=0){
                if(impacted)detonateKallaBolt(bolt,directHit,context);
                flameVisual(bolt,"FINISH",impacted);
                TrainingCombat.release(bolt.source.id,bolt.id);
                bolts.remove(bolt);
            }
        }
        for(var orb:new ArrayList<>(orbs)){
            SkillCast c=orb.source;
            TrainingCombat.touch(c.id);
            if(now-orb.lastKeepalive>=1000){orb.lastKeepalive=now;orbVisual(orb,"SEGMENT");}
            while(orb.target==null&&orb.remaining>0&&now>=orb.next){
                Vec3 from=orb.position,next=from.add(c.direction.scale(Math.min(.4,orb.remaining)));orb.next+=50;
                if(!SkillController.INSTANCE.sight(from,next)){orb.remaining=0;break;}
                orb.position=next;orb.remaining-=.4;
                var target=SkillController.INSTANCE.targets(new AABB(from,next).inflate(.8)).stream().filter(t->SkillController.INSTANCE.sight(from,t.getBoundingBox().getCenter())).min(Comparator.comparingDouble(t->from.distanceToSqr(t.getBoundingBox().getCenter()))).orElse(null);
                if(target!=null){var data=state().context(target);if(data!=null&&!data.life.dead){orb.target=target;orb.targetGeneration=data.life.generation;orb.next=now+Math.max(1,(long)(c.parameters.get("interval",.5)*1000));orbVisual(orb,"SEGMENT");}}
            }
            if(orb.target!=null){
                var data=state().context(orb.target);
                if(!orb.target.isAlive()||data==null||data.life.dead||data.life.generation!=orb.targetGeneration){c.center=orb.position.add(0,-.8,0);orbVisual(orb,"FINISH");TrainingCombat.release(c.id,"orb");orbs.remove(orb);continue;}
                orb.position=orb.target.getBoundingBox().getCenter();
                int count=(int)c.parameters.get("slashes",CareerSkills.orbHits(1));long interval=Math.max(1,(long)(c.parameters.get("interval",.5)*1000));
                // Shared target clocks below select only one eligible orb every 350 ms.
                if(orb.index>=count){c.center=orb.position.add(0,-.8,0);orbVisual(orb,"FINISH");TrainingCombat.release(c.id,"orb");orbs.remove(orb);continue;}
            }else if(orb.remaining<=0){c.center=orb.position.add(0,-.8,0);orbVisual(orb,"FINISH");TrainingCombat.release(c.id,"orb");orbs.remove(orb);continue;}
            c.center=orb.position.add(0,-.8,0);if(now-c.visualTrail>=100){context.visual(c,"TRAIL");c.visualTrail=now;}
        }
        for(var orb:orbs.stream().filter(o->o.target!=null&&o.index<o.source.parameters.get("slashes",0)&&o.next<=now).sorted(Comparator.comparingLong((Orb o)->o.next).thenComparing(o->o.source.id)).toList()){
            String key=orb.target.getStringUUID()+"/"+orb.targetGeneration;OrbClock clock=orbClocks.computeIfAbsent(key,k->new OrbClock());
            if(now<clock.next)continue;
            SkillCast c=orb.source;c.hitIndex=orb.index;long orbGap=Math.max(50,(long)(c.parameters.get("orbTickInterval",SkillResourceRules.ORB_INTERVAL/1000d)*1000));c.scheduledHitAt=Math.max(clock.lastServer+orbGap,state().serverNow());
            if(context.hit(c,orb.target,c.parameters.get("damage",0))){orb.index++;orb.next=now+orbGap;clock.next=orb.next;clock.lastServer=c.scheduledHitAt;}
        }
        orbClocks.entrySet().removeIf(entry->entry.getValue().next+5000<now);
        asura.tick(context,sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.EYE)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null),view.eyeUntil);
        SoulBenderClient.tick(context);
        BerserkerClient.tick(context);
        for(var ball:new ArrayList<>(fireBalls))advanceFire(ball,context,now);
        for(var wave:new ArrayList<>(waves))advance(wave,context,now);
        if(!waiting()&&SkillController.INSTANCE.cast==null&&!cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(p)){
            if(view.eyeUntil>0&&view.eyeUntil<=wall)request(SkillCatalog.require(CareerSkills.EYE),-1);
            else if(view.bloodUntil>0&&view.bloodUntil<=wall)request(SkillCatalog.require(CareerSkills.SWORD),-1);
        }
        sources.values().removeIf(c->orbs.stream().noneMatch(o->o.source==c)&&fireBalls.stream().noneMatch(ball->ball.source==c)&&!BerserkerClient.hasCast(c)&&!(c.skill.id.equals(CareerSkills.SWORD)&&!c.terminal&&view.bloodUntil>0)&&now-c.start>Math.max(45000,(long)(c.parameters.get("duration",1)*1000)+5000));
    }
    private void advanceFire(FireBall ball,SkillBehaviors.Context context,long now){
        SkillCast c=ball.source;TrainingCombat.touch(c.id);var mc=Minecraft.getInstance();
        if(now-c.start>10000){context.visual(c,"CANCEL");TrainingCombat.release(c.id,"fire");cancel(c);fireBalls.remove(ball);return;}
        if(ball.confirmed){
            c.center=ball.position;c.hitIndex=0;
            if(!ball.explosionShown){
                double radius=Math.max(.5,c.parameters.get("blastRadius",2.5));
                for(var target:SkillController.INSTANCE.targets(new AABB(c.center,c.center).inflate(radius,Math.max(4,radius),radius)))if(target.getBoundingBox().getCenter().distanceToSqr(c.center)<=Math.pow(radius+target.getBbWidth(),2)+4&&SkillController.INSTANCE.sight(c.center,target.getBoundingBox().getCenter()))ball.impactTargets.add(target.getUUID());
                ball.impactTargets.addAll(ball.captured);ball.explosionShown=true;context.visual(c,"FINISH");
            }
            for(UUID id:new ArrayList<>(ball.impactTargets)){
                var entity=mc.level.getEntity(id);if(!(entity instanceof LivingEntity target)||!target.isAlive()){ball.impactTargets.remove(id);continue;}
                var known=state().context(target);if(known==null)continue;if(known.life.dead||context.hit(c,target,c.parameters.get("damage",0)))ball.impactTargets.remove(id);
            }
            if(ball.impactTargets.isEmpty()){TrainingCombat.release(c.id,"fire");fireBalls.remove(ball);}return;
        }

        while(now>=ball.next&&!ball.waiting){
            double progress=Math.min(1,(ball.next-c.start)/ball.path.millis());Vec3 from=ball.position,to=ball.path.point(progress);ball.next+=50;
            var wall=mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
            boolean blocked=wall.getType()!=HitResult.Type.MISS;
            if(blocked)to=wall.getLocation().subtract(to.subtract(from).normalize().scale(.05));
            for(var target:SkillController.INSTANCE.targets(new AABB(from,to).inflate(2.5))){var data=state().context(target);if(data!=null&&!data.kind.equals("BOSS")&&target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)<1&&SkillController.INSTANCE.sight(to,target.getBoundingBox().getCenter()))ball.captured.add(target.getUUID());}
            ball.position=to;ball.progress=progress;c.center=to;moveCaptured(ball);
            if(blocked||progress>=1){ball.waiting=true;ball.confirmed=true;}
        }
    }
    private void moveCaptured(FireBall ball){var packet=new JsonObject();packet.addProperty("session",state().session());packet.addProperty("generation",state().own().generation);var moves=new JsonArray();for(UUID id:ball.captured){var entity=Minecraft.getInstance().level.getEntity(id);if(!(entity instanceof LivingEntity target))continue;Vec3 destination=ball.position.add(0,-target.getBbHeight()*.5,0);var move=new JsonObject();move.addProperty("target",id.toString());move.addProperty("x",destination.x);move.addProperty("y",destination.y);move.addProperty("z",destination.z);moves.add(move);}packet.add("moves",moves);state().send("careerMotion",packet);}
    private void advance(CareerWave wave,SkillBehaviors.Context context,long now){
        TrainingCombat.touch(wave.source.id);SkillCast c=wave.source;double sizeScale=TowerBattleCard.ASURA_CRESCENT_MAX.held(c.stats)?1d+TowerBattleCard.ASURA_CRESCENT_MAX.ratio("size"):1d;double range=c.parameters.get("range",12),width=(2+2*c.moved)*sizeScale;
        while(waves.contains(wave)&&now>=wave.next){
            Vec3 from=wave.position,next=from.add(c.direction.scale(Math.min(.75,range-wave.travelled)));long step=wave.next;wave.next+=50;
            if(!SkillController.INSTANCE.sight(from.add(0,.2,0),next.add(0,.2,0))){finish(wave,context);break;}
            wave.position=next;wave.travelled=Math.min(range,wave.travelled+.75);c.center=next;
            for(var target:SkillController.INSTANCE.targets(new AABB(from,next).inflate(width/2,width/2,width/2))){
                int index=wave.hits.getOrDefault(target.getUUID(),0);if(index>=3||step<wave.nextHit.getOrDefault(target.getUUID(),0L)||!SkillController.INSTANCE.sight(from,target.getBoundingBox().getCenter()))continue;
                c.hitIndex=index;double coefficient=c.parameters.get("damage",0)*(1+2*c.moved)/3;
                if(context.hit(c,target,coefficient)){wave.hits.put(target.getUUID(),index+1);wave.nextHit.put(target.getUUID(),step+50);}
            }
            if(wave.travelled>=range){finish(wave,context);break;}
        }
    }
    private void finish(CareerWave wave,SkillBehaviors.Context context){SkillCast c=wave.source;c.center=wave.position;context.visual(c,"CANCEL");TrainingCombat.release(c.id,"wave");waves.remove(wave);}
    private static Vec3 vector(JsonArray a){return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
}
