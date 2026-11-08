package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.combat.api.ProjectileSpawn;
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
    public void renewSummons(){if(!state().progress().hasJob(JobCatalog.SUMMONER)||waiting())return;renewQueue.clear();for(String id:SummonerSkills.RENEW)if(SkillLearning.effective(state().stats(),state().progress(),SkillCatalog.require(id))>0)renewQueue.add(id);nextRenew();}
    public void tickRenew(){if(ClientUi.now()>=renewAfter)nextRenew();}
    public void stopRenewing(){renewQueue.clear();}
    private void nextRenew(){if(waiting()||renewQueue.isEmpty())return;String id=renewQueue.remove();request(SkillCatalog.require(id),-1);renewAfter=ClientUi.now()+600;}
    private final Map<String,SkillCast> sources=new HashMap<>();
    private final Map<String,Long> fieldTicks=new HashMap<>(),freezes=new HashMap<>();
    private final Map<UUID,Flame> flames=new HashMap<>();
    private final List<Orb> orbs=new ArrayList<>();
    private final List<CareerWave> waves=new ArrayList<>();
    private final List<FireBall> fireBalls=new ArrayList<>();
    private final Map<String,OrbClock> orbClocks=new HashMap<>();
    private final List<FlameBolt> bolts=new ArrayList<>();
    private final AsuraClient asura=new AsuraClient();
    private String session="",job="",dimension="",instance="";
    private long generation=-1,lastBloodGain,lastEyeProjection;
    private boolean dead;private final Set<String> healed=new HashSet<>();
    private static final class Flame {LivingEntity target;SkillCast source;int stacks,sequence;long expires,next,started;String visualId;}
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
    public void clear(){healed.clear();lastBloodGain=lastEyeProjection=0;renewQueue.clear();asura.clear();OracleClient.INSTANCE.clear();for(var source:sources.values()){SkillController.INSTANCE.stopVisual(source);TrainingCombat.release(source.id,"persistent");TrainingCombat.actionFinished(source.id);}for(var orb:orbs){orbVisual(orb,"CANCEL");TrainingCombat.release(orb.source.id,"orb");}for(var wave:waves)TrainingCombat.release(wave.source.id,"wave");for(var bolt:bolts)TrainingCombat.release(bolt.source.id,bolt.id);for(var mark:flames.values()){attachmentVisual(mark,"CANCEL");TrainingCombat.release(mark.source.id,"flame/"+mark.target.getUUID());}for(var ball:fireBalls)TrainingCombat.release(ball.source.id,"fire");fireBalls.clear();orbClocks.clear();sources.clear();fieldTicks.clear();freezes.clear();flames.clear();orbs.clear();waves.clear();bolts.clear();view=new CareerState();}
    public void ensureContext(){
        var p=Minecraft.getInstance().player;if(p==null||!state().ready())return;String current=p.level().dimension().identifier().toString();boolean changed=!session.equals(state().session())||generation!=state().own().generation||!job.equals(state().progress().jobId())||!dimension.equals(current)||!instance.equals(state().own().instance)||dead!=state().own().dead;
        if(!changed)return;clear();session=state().session();generation=state().own().generation;job=state().progress().jobId();dimension=current;instance=state().own().instance;dead=state().own().dead;view.nextBrand=state().serverNow()+5000;DemonHunterClient.clear();if(!dead)publish();
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
        if(id.equals(CareerSkills.BRANDS))view.nextBrand=state().serverNow()+5000;
        var packet=new JsonObject();packet.addProperty("session",state().session());packet.addProperty("generation",state().own().generation);packet.addProperty("skill",id);state().send("summonReduce",packet);publish();
    }
    public void hit(LivingEntity target,boolean direct,JsonObject report,double actual){
        if(actual<=0)return;long now=state().serverNow();boolean changed=false;
        if(view.bloodUntil>now&&view.blood<SkillResourceRules.BLOOD_CAP&&now-lastBloodGain>=SkillResourceRules.BLOOD_INTERVAL){view.blood++;lastBloodGain=now;changed=true;}
        if(report.has("careerCast")){String id=report.get("careerCast").getAsString();var source=sources.get(id);if(source!=null){report.addProperty("careerSkill",source.skill.id);if(source.skill.id.equals(CareerSkills.HAND)&&healed.add(id)){double recovery=Math.min(.10,1-state().predicted(state().own()));report.addProperty("ownerHealing",report.has("ownerHealing")?report.get("ownerHealing").getAsDouble()+recovery:recovery);}}}
        if(changed)publish();
    }
    public void released(int slot){var cast=SkillController.INSTANCE.cast;if(cast!=null&&cast.slot%8==slot&&cast.charging)cast.release=true;}
    public boolean waiting(){return ChantClient.active();}
    private static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}
    public void publish(){
        var p=Minecraft.getInstance().player;if(p==null||!state().ready())return;
        view.session=state().session();view.generation=state().own().generation;view.job=state().progress().jobId();view.dimension=p.level().dimension().identifier().toString();view.instance=state().own().instance;view.sequence++;
        ownField();var actor=new CareerState.Actor();actor.owner=p.getStringUUID();actor.keiga=SkillLearning.effective(state().stats(),state().progress(),SkillCatalog.require(CareerSkills.KEIGA));actor.brands=view.brands;actor.blood=view.blood;actor.frenzy=view.frenzy;actor.kallaUntil=view.kallaUntil;actor.eyeUntil=view.eyeUntil;actor.bloodUntil=view.bloodUntil;actor.sunMoonHeart=view.sunMoon.heart;actor.sunMoonStacks=view.sunMoon.stacks;actor.metamorphosisUntil=view.demonHunter.metamorphosisUntil;actor.auraUntil=view.demonHunter.auraUntil;view.actors.removeIf(a->a.owner.equals(actor.owner));view.actors.add(actor);
        view.steadfast=frenzy()||DemonHunterClient.steadfast();view.invulnerable=SkillController.INSTANCE.invulnerable();
        var packet=new JsonObject();packet.addProperty("session",view.session);packet.addProperty("generation",view.generation);packet.add("state",JSON.toJsonTree(view));state().send("careerSnapshot",packet);state().upload();
    }
    public void request(SkillDefinition skill,int slot){
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null||!state().ready()||state().own().dead||p.isDeadOrDying()||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(p))return;
        ensureContext();
        try{
            if(!DemonHunterClient.beforeRequest(skill)||ChantClient.active())return;
            var progress=state().progress();Stats base=state().weaponStats(p.getMainHandItem());Stats equipment=SkillPreview.currentStats(base,state().resources(),ClientUi.now());
            int rank=SkillLearning.effective(equipment,progress,skill);if(rank<=0||skill.passive())return;
            if(!skill.weaponAllowed(progress,equipment))throw new IllegalArgumentException("主手需要本职业可用武器");
            if(equipment.weaponType.equals("WARGLAIVE")&&!p.getOffhandItem().isEmpty())throw new IllegalArgumentException("使用战刃需要副手为空");
            long now=ClientUi.now(),wall=state().serverNow();String id=skill.id;
            boolean terminal=id.equals(CareerSkills.KALLA)&&view.kallaUntil>wall||id.equals(CareerSkills.EYE)&&view.eyeUntil>0||id.equals(CareerSkills.SWORD)&&view.bloodUntil>0||id.equals(CareerSkills.FRENZY)&&view.frenzy;
            if(!terminal&&!p.isCreative()&&!state().resources().training&&state().resources().cooldowns.getOrDefault(id,0L)>now)throw new IllegalArgumentException("技能冷却中");
            if((id.equals(CareerSkills.ORB)||id.equals(CareerSkills.AGNI))&&view.brands<1)throw new IllegalArgumentException("至少需要一枚波动刻印");
            var next=copy(view,CareerState.class);var resources=copy(state().resources(),cn.yumis.fight.modules.combat.api.Runtime.class);resources.equipment=cn.yumis.fight.modules.equipment.api.EquipmentStats.projectileEffects(p,progress,p.getMainHandItem());
            var life=copy(state().own(),cn.yumis.fight.modules.combat.api.Life.class);life.q=state().predicted(state().own());double before=life.q;
            var content=CareerSkills.content(id);double hpCost=terminal||content==null?0:content.hpCost();
            Stats prepared=CareerRules.current(equipment,progress,next,life,life.q,view.red(p.getX(),p.getY(),p.getZ(),wall),view.ownField(p.getX(),p.getY(),p.getZ(),wall),wall);
            prepared=SunMoonRules.forCast(CareerRules.eyeDamage(prepared,skill),skill);prepared=OracleClient.INSTANCE.prepareStats(skill,prepared);
            if(id.equals(DemonHunterSkills.META))prepared=DemonHunterRules.transformed(prepared);
            MageClient.INSTANCE.prepare(skill);DemonHunterClient.prepare(skill);Vec3 destination=SkillBehaviors.prepare(skill);
            cn.yumis.fight.modules.combat.api.EquipmentBuffs.prune(resources,resources.equipment,now,false);
            boolean career=CareerSkills.contains(id);
            if(!career&&!terminal){cn.yumis.fight.modules.combat.api.EquipmentBuffs.imprint(resources,resources.equipment,now);equipment=SkillPreview.currentStats(base,resources,now);prepared=CareerRules.current(equipment,progress,next,life,life.q,view.red(p.getX(),p.getY(),p.getZ(),wall),view.ownField(p.getX(),p.getY(),p.getZ(),wall),wall);prepared=OracleClient.INSTANCE.prepareStats(skill,SunMoonRules.forCast(CareerRules.eyeDamage(prepared,skill),skill));if(id.equals(DemonHunterSkills.META))prepared=DemonHunterRules.transformed(prepared);}
            var cost=SkillCosts.resolve(skill,rank,career?equipment:prepared,resources.equipment,CareerRules.magic(skill,progress));
            if(!terminal)SkillCosts.pay(cost,resources,life,career?equipment.get(Stat.HEALTH):prepared.get(Stat.HEALTH),hpCost);
            if(career&&!terminal)cn.yumis.fight.modules.combat.api.EquipmentBuffs.imprint(resources,resources.equipment,now);
            equipment=SkillPreview.currentStats(base,resources,now);
            boolean instruction=OracleClient.INSTANCE.prepare(next,skill,prepared,wall);
            int brands=next.brands,blood=next.blood,fieldRank=id.equals(CareerSkills.BLACHE)&&next.field!=null&&next.field.expires>wall?next.field.rank:0;
            prepared=CareerRules.current(equipment,progress,next,life,life.q,view.red(p.getX(),p.getY(),p.getZ(),wall),next.ownField(p.getX(),p.getY(),p.getZ(),wall),wall);
            if(instruction)prepared=OracleRules.instruction(prepared);prepared=SunMoonRules.forCast(prepared,skill);if(id.equals(DemonHunterSkills.META))prepared=DemonHunterRules.transformed(prepared);
            var parameters=WeaponSkills.skill(SkillParameters.forCast(p.getUUID(),skill,rank,prepared.parameters),progress,prepared);
            if(terminal){var original=sources.values().stream().filter(c->c.skill.id.equals(id)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null);if(original!=null){parameters=original.parameters;prepared=original.stats.copy();rank=parameters.rank();}}
            parameters=CareerRules.resourceSnapshot(parameters,brands,blood,fieldRank,terminal);parameters=OracleClient.INSTANCE.parameters(parameters,next,wall);
            prepared=CareerRules.eyeDamage(CareerRules.castDamage(prepared,parameters),skill);
            var values=new LinkedHashMap<>(parameters.values());if(terminal)values.put("actionDuration",id.equals(CareerSkills.KALLA)?.9:id.equals(CareerSkills.EYE)?1.2:2d);
            Vec3 origin=p.position(),direction=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize(),center=origin;
            if(id.equals(CareerSkills.FIRE)){Vec3 from=p.getEyePosition(),aim=p.getLookAngle().normalize();var wallHit=p.level().clip(new ClipContext(from,from.add(aim.scale(10)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));Vec3 to=wallHit.getType()==HitResult.Type.MISS?from.add(aim.scale(10)):wallHit.getLocation().subtract(aim.scale(.05));if(to.subtract(from).dot(aim)<0)to=from;var spawn=ProjectileSpawn.inWorld(p,from,aim,from.distanceTo(to),2.5,e->e.isAlive()&&e instanceof net.minecraft.world.entity.monster.Enemy);from=spawn.position();new FireWavePath(from,to,1.5,Math.max(1,from.distanceTo(to)/15*1000)).write(values);origin=from;direction=aim;center=from;}
            parameters=new SkillParameters.Snapshot(skill,rank,values);origin=OracleClient.INSTANCE.origin(parameters,origin);direction=OracleClient.INSTANCE.direction(parameters,direction);center=origin;
            if(id.equals(CareerSkills.SWORD)&&terminal)center=origin.add(direction.scale(StormChoreography.CENTER_OFFSET));
            if(destination!=null)center=destination;
            String castId=UUID.randomUUID().toString();
            if(id.equals(CareerSkills.FIELD)){center=SkillTargeting.ground(p,8,e->e.isAlive()&&e instanceof net.minecraft.world.entity.monster.Enemy).point();var f=new CareerState.Field();f.id=castId;f.owner=p.getStringUUID();f.rank=rank;f.x=center.x;f.y=center.y;f.z=center.z;f.dimension=p.level().dimension().identifier().toString();f.instance=state().own().instance;f.expires=wall+12000;next.field=f;}
            if(id.equals(CareerSkills.BLACHE))next.field=null;
            if(id.equals(CareerSkills.ORB)||id.equals(CareerSkills.AGNI))next.brands=0;
            if(id.equals(CareerSkills.FRENZY))next.frenzy=!terminal;
            if(id.equals(CareerSkills.KALLA))next.kallaUntil=terminal?0:wall+20000;
            if(id.equals(CareerSkills.EYE)){next.eyeUntil=terminal?0:wall+20000;next.eyeBonusMut=terminal?0:parameters.get("eyeBonusMut",.20);}
            if(id.equals(CareerSkills.SWORD)){next.bloodUntil=terminal?0:wall+60000;next.blood=0;}
            if(CareerRules.producesBrand(id))next.brands=Math.min(5,next.brands+1);
            if(SunMoonSkills.contains(id))next.sunMoon=SunMoonRules.paid(next.sunMoon,skill,prepared);
            if(DemonHunterSkills.contains(id))next.demonHunter.fury-=DemonHunterRules.cost(id);
            if(!terminal&&!resources.training&&!p.isCreative())resources.cooldowns.put(id,now+(long)(SkillCosts.resolve(skill,rank,prepared,resources.equipment,CareerRules.magic(skill,progress)).cooldown()*1000));resources.recordedAt=now;
            var c=new SkillCast();c.id=castId;c.skill=skill;c.slot=slot;c.parameters=parameters;c.stats=prepared;c.equipment=resources.equipment;c.weapon=prepared.weaponInstance;c.terminal=terminal;c.origin=origin;c.direction=direction;c.center=center;c.start=c.stageStart=now;c.speed=Math.max(.01,prepared.get(Stat.CAST_SPEED));c.dimension=p.level().dimension().identifier().toString();c.charging=SkillPresentation.charging(skill);
            c.session=state().session();c.generation=state().own().generation;c.job=state().progress().jobId();c.instance=state().own().instance;
            state().resources(resources);view=next;sources.put(c.id,c);
            if(before>life.q){var extra=new JsonObject();extra.addProperty("resourceCost",true);state().report(state().own(),before-life.q,false,equipment.get(Stat.HEALTH),0xe05360,false,extra);}
            publish();
            Runnable release=()->{if(!MageClient.INSTANCE.start(c))SkillController.INSTANCE.begin(c);};
            if(skill.chantSeconds>0)ChantClient.begin(c,release);else release.run();
        }catch(IllegalArgumentException failure){state().message(failure.getMessage());}
    }
    public Stats snapshot(Stats base){var p=Minecraft.getInstance().player;long now=state().serverNow();return p==null?base:CareerRules.current(base,state().progress(),view,state().own(),state().predicted(state().own()),view.red(p.getX(),p.getY(),p.getZ(),now),view.ownField(p.getX(),p.getY(),p.getZ(),now),now);}
    public boolean frenzy(){return view.frenzy&&state().progress().hasJob(JobCatalog.BERSERKER);}
    public boolean eye(){return view.eyeUntil>state().serverNow();}
    public int visualColor(Stats stats,int fallback){return stats.modifiers.getOrDefault("careerEye",0d)>0?CareerRules.EYE_COLOR:fallback;}
    public int basicColor(Stats stats){if(stats.modifiers.getOrDefault("basicDark",0d)>0)return DemonHunterRules.COLOR;if(stats.modifiers.getOrDefault("careerEye",0d)>0)return CareerRules.EYE_COLOR;if(stats.modifiers.getOrDefault("careerFrenzy",0d)>0)return 0xe63951;return SkillLearning.effective(stats,state().progress(),SkillCatalog.require(CareerSkills.KEIGA))>0?0x8e56d9:0xb4f2ff;}
    public boolean invulnerable(SkillCast c){if(c==null)return false;if(c.skill.id.equals(CareerSkills.PHANTOM))return (ClientUi.now()-c.start)*c.speed<=250;return c.terminal&&(c.skill.id.equals(CareerSkills.EYE)||c.skill.id.equals(CareerSkills.SWORD));}
    void eyeStarted(SkillCast cast,long now){
        for(var old:sources.values())if(!old.id.equals(cast.id)&&old.skill.id.equals(CareerSkills.EYE)&&!old.terminal)TrainingCombat.release(old.id,"persistent");
        TrainingCombat.hold(cast.id,"persistent",now+20000);
    }
    public void cancel(SkillCast c){if(c==null)return;OracleClient.INSTANCE.cancel(c.id);MageClient.INSTANCE.cancel(c);ended(c);}
    public void ended(SkillCast c){sources.remove(c.id);TrainingCombat.release(c.id,"persistent");TrainingCombat.release(c.id,"mage");TrainingCombat.actionFinished(c.id);}
    public void decorate(SkillCast source,LivingEntity target,JsonObject extra){
        if(source==null)return;
        if(!source.eyeGear.isEmpty()){extra.addProperty("eyeGear",source.eyeGear);extra.add("eyePoint",JSON.toJsonTree(new double[]{source.center.x,source.center.y,source.center.z}));}
        if(MageClient.decorate(source,extra))return;
        if(CareerSkills.contains(source.skill.id)){extra.addProperty("careerCast",source.id);extra.addProperty("careerIndex",source.hitIndex);if(source.skill.id.equals(CareerSkills.ORB))extra.addProperty("orbAt",source.scheduledHitAt);if(source.controlSeconds>0){String key=source.id+"/"+target.getUUID();long now=ClientUi.now();if(!source.skill.id.equals(CareerSkills.FIELD)||freezes.getOrDefault(key,0L)<=now){extra.addProperty("careerControl",source.controlSeconds);freezes.put(key,now+3000);}}}
        else extra.addProperty("sourceCast",source.id);
    }
    public void orb(SkillCast c,SkillBehaviors.Context context){Orb orb=new Orb();orb.source=c;orb.position=c.origin.add(0,.8,0);orb.remaining=c.parameters.get("range",10);orb.next=ClientUi.now();orb.visualId=c.id;orb.visualStarted=c.start;orb.visualSequence=c.visualSequence;orbs.add(orb);c.center=orb.position.add(0,-.8,0);TrainingCombat.hold(c.id,"orb",ClientUi.now()+12000);orbVisual(orb,"RELEASE");}
    private void orbVisual(Orb orb,String phase){
        var p=Minecraft.getInstance().player;if(p==null)return;long now=ClientUi.now();
        if(!phase.equals("CANCEL")&&!phase.equals("FINISH")&&now-orb.visualStarted>=30000){orbVisual(orb,"CANCEL");orb.visualId=UUID.nameUUIDFromBytes((orb.source.id+"/orb/"+(++orb.visualEpoch)).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();orb.visualStarted=now;orb.visualSequence=-1;orbVisual(orb,"START");phase="RELEASE";}
        JsonObject j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);j.addProperty("id",orb.visualId);j.addProperty("kind",CareerSkills.ORB);j.addProperty("phase",phase);j.addProperty("sequence",++orb.visualSequence);j.addProperty("seed",orb.visualId.hashCode());j.addProperty("startedAt",state().serverNow()-(now-orb.visualStarted));j.addProperty("at",state().serverNow());j.addProperty("duration",12000);j.addProperty("cosmeticOnly",true);j.addProperty("color",0xc9bbeb);
        j.addProperty("x",orb.position.x);j.addProperty("y",orb.position.y);j.addProperty("z",orb.position.z);j.addProperty("px",orb.position.x);j.addProperty("py",orb.position.y);j.addProperty("pz",orb.position.z);j.addProperty("dx",orb.source.direction.x);j.addProperty("dy",orb.source.direction.y);j.addProperty("dz",orb.source.direction.z);
        JsonObject motion=SkillController.motion(orb.target==null?orb.remaining:0,orb.target==null?orb.remaining/8*1000:0,false);
        if(orb.target!=null){motion.addProperty("target",orb.target.getStringUUID());motion.addProperty("targetEntity",orb.target.getId());}j.add("motion",motion);Effects.receive(j);
        state().send("visual",j);
    }
    public void wave(SkillCast c,SkillBehaviors.Context context){
        if(c.skill.id.equals(CareerSkills.FIRE)){FireBall ball=new FireBall();ball.source=c;ball.path=FireWavePath.from(c.parameters);ball.position=ball.path.origin();ball.next=ClientUi.now()+50;fireBalls.add(ball);c.center=ball.position;TrainingCombat.hold(c.id,"fire",ClientUi.now()+5000);context.visual(c,"RELEASE");return;}
        CareerWave wave=new CareerWave();wave.source=c;wave.position=c.origin.add(0,1.2,0);wave.next=ClientUi.now();c.center=wave.position;waves.add(wave);TrainingCombat.hold(c.id,"wave",ClientUi.now()+5000);context.visual(c,"RELEASE");
    }
    public boolean basic(Stats stats,String cycle,JsonObject content){
        if(asura.basic(cycle,content,sources))return true;
        long now=state().serverNow();if(view.kallaUntil<=now)return false;
        SkillCast source=sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.KALLA)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null);if(source==null)return false;
        if(!content.has("kallaProjectile"))return false;var launch=content.getAsJsonObject("kallaProjectile");FlameBolt bolt=new FlameBolt();bolt.source=source;bolt.origin=bolt.position=ProjectileSpawn.vector(launch.getAsJsonArray("origin"));bolt.direction=ProjectileSpawn.vector(launch.getAsJsonArray("direction"));bolt.remaining=bolt.range=launch.get("range").getAsDouble();bolt.started=launch.get("at").getAsLong();bolt.next=ClientUi.now()-Math.clamp(state().serverNow()-bolt.started,0,2000);bolt.id=UUID.nameUUIDFromBytes((cycle+"/kalla").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();bolts.add(bolt);TrainingCombat.hold(source.id,bolt.id,ClientUi.now()+2000);flameVisual(bolt,"START");return false;
    }
    public JsonObject basicContent(String cycle){
        var p=Minecraft.getInstance().player;var result=new JsonObject();long now=state().serverNow();
        Vec3 direction=p.getLookAngle().normalize(),origin=p.getEyePosition().add(0,-.3,0);
        var eye=view.eyeUntil>now?sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.EYE)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null):null;
        java.util.function.Predicate<LivingEntity> enemy=e->e.isAlive()&&(e instanceof net.minecraft.world.entity.monster.Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon);
        if(eye!=null){var spawn=ProjectileSpawn.inWorld(p,origin,direction,AsuraRules.GEAR_RANGE,.75,enemy);result.addProperty("eyeCast",eye.id);result.add("eyeProjectile",ProjectileSpawn.snapshot(spawn,direction,now));}
        if(view.kallaUntil>now){var spawn=ProjectileSpawn.inWorld(p,origin,direction,10,.35,enemy);result.add("kallaProjectile",ProjectileSpawn.snapshot(spawn,direction,now));}
        return result;
    }
    private void flameVisual(FlameBolt b,String phase){var p=Minecraft.getInstance().player;if(p==null)return;JsonObject j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);j.addProperty("id",b.id);j.addProperty("kind",CareerSkills.KALLA);j.addProperty("phase",phase);j.addProperty("sequence",b.sequence++);j.addProperty("seed",b.id.hashCode());j.addProperty("startedAt",b.started);j.addProperty("at",state().serverNow());j.addProperty("duration",2000);j.addProperty("color",0x8f43d0);j.addProperty("x",b.origin.x);j.addProperty("y",b.origin.y);j.addProperty("z",b.origin.z);j.addProperty("px",b.position.x);j.addProperty("py",b.position.y);j.addProperty("pz",b.position.z);j.addProperty("dx",b.direction.x);j.addProperty("dy",b.direction.y);j.addProperty("dz",b.direction.z);if(phase.equals("START")){var motion=SkillController.motion(b.range,1000d*b.range/15,false);motion.addProperty("at",b.started);j.add("motion",motion);}Effects.receive(j);state().send("visual",j);}
    private void flame(LivingEntity target,SkillCast source){
        var mark=flames.get(target.getUUID());boolean first=mark==null||!mark.source.id.equals(source.id);
        if(first){if(mark!=null){attachmentVisual(mark,"CANCEL");TrainingCombat.release(mark.source.id,"flame/"+target.getUUID());}mark=new Flame();mark.target=target;mark.source=source;mark.next=ClientUi.now()+500;mark.started=ClientUi.now();mark.visualId=UUID.nameUUIDFromBytes((source.id+"/flame/"+target.getUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();flames.put(target.getUUID(),mark);}
        mark.stacks=Math.min(3,mark.stacks+1);mark.expires=ClientUi.now()+3000;TrainingCombat.hold(source.id,"flame/"+target.getUUID(),mark.expires+50);attachmentVisual(mark,first?"START":"SEGMENT");
    }
    private void attachmentVisual(Flame mark,String phase){
        var p=Minecraft.getInstance().player;if(p==null)return;Vec3 at=mark.target.getBoundingBox().getCenter();JsonObject j=new JsonObject();j.addProperty("caster",p.getStringUUID());j.addProperty("session",state().session());j.addProperty("generation",state().own().generation);j.addProperty("id",mark.visualId);j.addProperty("kind",CareerSkills.KALLA);j.addProperty("phase",phase);j.addProperty("sequence",mark.sequence++);j.addProperty("seed",mark.visualId.hashCode());j.addProperty("startedAt",state().serverNow()-(ClientUi.now()-mark.started));j.addProperty("at",state().serverNow());j.addProperty("duration",Math.min(40000,mark.expires-mark.started));j.addProperty("cosmeticOnly",true);j.addProperty("terminal",true);j.addProperty("charge",mark.stacks/3d);j.addProperty("color",0x772daa);j.addProperty("x",at.x);j.addProperty("y",at.y);j.addProperty("z",at.z);j.addProperty("px",at.x);j.addProperty("py",at.y);j.addProperty("pz",at.z);j.addProperty("dx",0);j.addProperty("dy",0);j.addProperty("dz",1);
        var motion=SkillController.motion(0,0,false);motion.addProperty("target",mark.target.getStringUUID());motion.addProperty("targetEntity",mark.target.getId());j.add("motion",motion);Effects.receive(j);
        state().send("visual",j);
    }
    public Vec3 firstTarget(SkillCast c,double length,double width){var p=Minecraft.getInstance().player;LivingEntity closest=null;double min=length+1;for(var e:SkillController.INSTANCE.targets(new AABB(c.origin,c.origin.add(c.direction.scale(length))).inflate(width/2,2,width/2))){Vec3 delta=e.position().subtract(c.origin);double forward=delta.dot(c.direction);if(forward>=0&&forward<min&&SkillController.INSTANCE.sight(c.origin.add(0,1,0),e.getBoundingBox().getCenter())){closest=e;min=forward;}}return closest==null?c.origin.add(c.direction.scale(length)):closest.position();}
    public void tick(SkillBehaviors.Context context){
        var mc=Minecraft.getInstance();var p=mc.player;if(p==null||mc.level==null)return;
        ensureContext();if(state().own().dead)return;
        long now=ClientUi.now(),wall=state().serverNow();
        OracleClient.INSTANCE.tick(now);boolean dirty=false;
        if(view.field!=null&&view.field.expires<=wall){view.field=null;dirty=true;}if(view.kallaUntil>0&&view.kallaUntil<=wall){view.kallaUntil=0;dirty=true;}
        if(state().progress().hasJob(JobCatalog.ASURA)&&SkillLearning.effective(state().stats(),state().progress(),SkillCatalog.require(CareerSkills.BRANDS))>0){if(view.nextBrand<=0)view.nextBrand=wall+5000;while(wall>=view.nextBrand){view.brands=Math.min(5,view.brands+1);view.nextBrand+=5000;dirty=true;}}else if(view.brands!=0){view.brands=0;view.nextBrand=wall+5000;dirty=true;}
        if(view.eyeUntil>wall&&wall-lastEyeProjection>=1000){lastEyeProjection=wall;var targets=new JsonArray();for(var target:SkillController.INSTANCE.targets(p.getBoundingBox().inflate(16,4,16)))if(target.position().subtract(p.position()).horizontalDistanceSqr()<=256&&Math.abs(target.getY()-p.getY())<=4&&SkillController.INSTANCE.sight(p.getEyePosition(),target.getBoundingBox().getCenter()))targets.add(target.getStringUUID());var packet=new JsonObject();packet.addProperty("session",state().session());packet.addProperty("generation",state().own().generation);packet.add("targets",targets);state().send("careerBlind",packet);}
        if(view.invulnerable!=SkillController.INSTANCE.invulnerable()||view.steadfast!=(frenzy()||DemonHunterClient.steadfast()))dirty=true;if(dirty)publish();
        for(var source:sources.values()){
            if(source.skill.id.equals(CareerSkills.FIELD)&&(now-source.start>=12000||view.field!=null&&view.field.id.equals(source.id)&&view.field.expires<=wall))TrainingCombat.release(source.id,"persistent");
            if(source.skill.id.equals(CareerSkills.KALLA)&&!source.terminal&&(now-source.start>=20000||view.kallaUntil>0&&view.kallaUntil<=wall))TrainingCombat.release(source.id,"persistent");
            if(source.skill.id.equals(CareerSkills.EYE)&&!source.terminal&&(now-source.start>=20000||view.eyeUntil>0&&view.eyeUntil<=wall))TrainingCombat.release(source.id,"persistent");
        }
        cn.yumis.fight.modules.equipment.api.EquipmentStats.applyMovement(p,SkillController.INSTANCE.snapshot());
        if(view.field!=null&&view.field.expires>wall){SkillCast c=sources.get(view.field.id);if(c!=null){long next=fieldTicks.getOrDefault(c.id,c.start+1000);while(now>=next&&next<c.start+12001){int index=(int)((next-c.start)/1000)-1;c.center=new Vec3(view.field.x,view.field.y,view.field.z);c.dot=true;c.hit.clear();if(view.field.rank>=8){c.hitIndex=index;c.elementOverride=2;c.controlSeconds=.5;context.area(c,5,c.parameters.get("iceDot",0),false);}if(view.field.rank>=15){c.hit.clear();c.hitIndex=16+index;c.elementOverride=3;c.controlSeconds=0;context.area(c,5,c.parameters.get("darkDot",0),false);}next+=1000;}fieldTicks.put(c.id,next);}}
        for(var bolt:new ArrayList<>(bolts)){while(now>=bolt.next&&bolt.remaining>0){double step=Math.min(.75,bolt.remaining);Vec3 from=bolt.position,to=from.add(bolt.direction.scale(step));if(!SkillController.INSTANCE.sight(from,to)){bolt.remaining=0;break;}bolt.position=to;bolt.remaining-=step;bolt.next+=50;var hit=SkillController.INSTANCE.targets(new AABB(from,to).inflate(.35)).stream().findFirst().orElse(null);if(hit!=null){flame(hit,bolt.source);bolt.remaining=0;}}if(bolt.remaining<=0){flameVisual(bolt,"FINISH");TrainingCombat.release(bolt.source.id,bolt.id);bolts.remove(bolt);}}
        for(var mark:new ArrayList<>(flames.values())){if(!mark.target.isAlive()||mark.expires<now){attachmentVisual(mark,"CANCEL");TrainingCombat.release(mark.source.id,"flame/"+mark.target.getUUID());flames.remove(mark.target.getUUID());continue;}while(mark.next<=now&&mark.next<=mark.expires){SkillCast c=mark.source;c.dot=true;c.elementOverride=3;c.controlSeconds=0;c.hitIndex=64+(int)((mark.next-c.start)/500);context.hit(c,mark.target,c.parameters.get("flameDot",0)*mark.stacks);mark.next+=500;}}
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
            SkillCast c=orb.source;c.hitIndex=orb.index;c.scheduledHitAt=Math.max(clock.lastServer+SkillResourceRules.ORB_INTERVAL,state().serverNow());
            if(context.hit(c,orb.target,c.parameters.get("damage",0))){orb.index++;orb.next=now+SkillResourceRules.ORB_INTERVAL;clock.next=orb.next;clock.lastServer=c.scheduledHitAt;}
        }
        orbClocks.entrySet().removeIf(entry->entry.getValue().next+5000<now);
        asura.tick(context,sources.values().stream().filter(c->c.skill.id.equals(CareerSkills.EYE)&&!c.terminal).max(Comparator.comparingLong(c->c.start)).orElse(null),view.eyeUntil);
        for(var ball:new ArrayList<>(fireBalls))advanceFire(ball,context,now);
        for(var wave:new ArrayList<>(waves))advance(wave,context,now);
        if(!waiting()&&SkillController.INSTANCE.cast==null&&!cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(p)){
            if(view.eyeUntil>0&&view.eyeUntil<=wall)request(SkillCatalog.require(CareerSkills.EYE),-1);
            else if(view.bloodUntil>0&&view.bloodUntil<=wall)request(SkillCatalog.require(CareerSkills.SWORD),-1);
        }
        sources.values().removeIf(c->orbs.stream().noneMatch(o->o.source==c)&&fireBalls.stream().noneMatch(ball->ball.source==c)&&!(c.skill.id.equals(CareerSkills.SWORD)&&!c.terminal&&view.bloodUntil>0)&&now-c.start>Math.max(45000,(long)(c.parameters.get("duration",1)*1000)+5000));
    }
    private void advanceFire(FireBall ball,SkillBehaviors.Context context,long now){
        SkillCast c=ball.source;TrainingCombat.touch(c.id);var mc=Minecraft.getInstance();
        if(now-c.start>10000){context.visual(c,"CANCEL");TrainingCombat.release(c.id,"fire");cancel(c);fireBalls.remove(ball);return;}
        if(ball.confirmed){
            c.center=ball.position;c.hitIndex=0;
            if(!ball.explosionShown){
                for(var target:SkillController.INSTANCE.targets(new AABB(c.center,c.center).inflate(2.5,4,2.5)))if(target.getBoundingBox().getCenter().distanceToSqr(c.center)<=Math.pow(2.5+target.getBbWidth(),2)+4&&SkillController.INSTANCE.sight(c.center,target.getBoundingBox().getCenter()))ball.impactTargets.add(target.getUUID());
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
        TrainingCombat.touch(wave.source.id);SkillCast c=wave.source;double range=c.parameters.get("range",12),width=2+2*c.moved;
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
