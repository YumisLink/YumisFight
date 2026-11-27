package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.skills.api.client.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.combat.api.CombatRules;
import cn.yumis.fight.modules.equipment.api.EquipmentEffects;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import cn.yumis.fight.modules.combat.api.ProjectileSpawn;
import java.util.function.Consumer;
import java.util.*;

/** Owner-side spells, summon combat and life. Server messages describe world AI contacts. */
public final class MageClient {
    private record Spell(Consumer<SkillCast> start,boolean releaseAtStart){}
    private static final Map<String,Spell> SPELLS=new LinkedHashMap<>();
    public static final MageClient INSTANCE=new MageClient();
    private static boolean installed;
    private static final Gson JSON=new Gson();
    public long summonUntil,frenzyUntil;
    public final Map<String,JsonObject> army=new LinkedHashMap<>();
    public int summonRank;
    public SkillPreview.Damage summonedMelee,summonedStone,summonedTriple;
    private String session="",job="",dimension="",instance="";private long generation=-1,lastPetTick,lastPetUpload;
    private final Map<String,Long> environment=new HashMap<>();
    private final Set<String> received=new HashSet<>();
    private final Set<String> roots=new HashSet<>();private final Random random=new Random();
    private MageClient(){}
    private void ensureContext(){var state=SkillsClient.current();var p=Minecraft.getInstance().player;if(p==null||!state.ready())return;String d=p.level().dimension().identifier().toString();if(!session.equals(state.session())||generation!=state.own().generation||!job.equals(state.progress().jobId())||!dimension.equals(d)||!instance.equals(state.own().instance)){clear();session=state.session();generation=state.own().generation;job=state.progress().jobId();dimension=d;instance=state.own().instance;}}
    private boolean valid(JsonObject j){ensureContext();var state=SkillsClient.current();return j.get("session").getAsString().equals(state.session())&&j.get("generation").getAsLong()==state.own().generation&&!state.own().dead&&j.get("job").getAsString().equals(state.progress().jobId())&&j.get("dimension").getAsString().equals(dimension)&&j.get("instance").getAsString().equals(instance);}
    public void clear(){for(String root:roots){TrainingCombat.release(root,"mage");TrainingCombat.actionFinished(root);}MageSkillBehaviors.clear();SummonerClient.clear();SunMoonClient.clear();DemonHunterClient.clear();ElementalistClient.clear();BattleMageClient.clear();WitchClient.clear();army.clear();environment.clear();lastPetTick=lastPetUpload=0;frenzyUntil=0;received.clear();roots.clear();summonUntil=0;summonRank=0;summonedMelee=summonedStone=summonedTriple=null;}
    public static void register(String id,Consumer<SkillCast> starter){register(id,starter,true);}
    static void registerAction(String id,Consumer<SkillCast> starter){register(id,starter,false);}
    private static void register(String id,Consumer<SkillCast> starter,boolean releaseAtStart){
        if(SPELLS.putIfAbsent(id,new Spell(starter,releaseAtStart))!=null)throw new IllegalArgumentException("Duplicate local spell: "+id);
    }
    private static void install(){if(installed)return;installed=true;MageSkillBehaviors.install();SunMoonClient.install();DemonHunterClient.install();ElementalistClient.install();BattleMageClient.install();WitchClient.install();}
    public void prepare(SkillDefinition skill){ensureContext();install();MageSkillBehaviors.prepare(skill);String unavailable=SunMoonRules.unavailableReason(skill,CareerClient.INSTANCE.view.sunMoon.heart,CareerClient.INSTANCE.view.sunMoon.stacks);if(!unavailable.isEmpty())throw new IllegalArgumentException(unavailable);}
    public boolean start(SkillCast cast){
        install();var starter=SPELLS.get(cast.skill.id);if(starter==null)return false;
        cast.stats=cast.stats.copy();cast.stats.modifiers.put("sourceLevel",(double)SkillsClient.current().progress().level);
        TrainingCombat.begin(cast.id,cast.skill.title);TrainingCombat.hold(cast.id,"mage",ClientUi.now()+Math.max(20000,(long)(cast.parameters.get("summonDuration",0)*1000)));
        try{
            PlayerPoseChannel.begin(cast);
            starter.start().accept(cast);
            if(starter.releaseAtStart())PlayerPoseChannel.release(cast);
            if(SunMoonSkills.contains(cast.skill.id)){var view=CareerClient.INSTANCE.view;long wallNow=SkillsClient.current().serverNow();view.sunMoon=SunMoonRules.released(view.sunMoon,cast.skill,cast.parameters.rank(),SkillsClient.current().progress().awakened(),cast.parameters,cast.stats,wallNow);SunMoonClient.onSkillReleased(cast);CareerClient.INSTANCE.publish();}
            if(ElementalistSkills.contains(cast.skill.id)||(MageSkills.contains(cast.skill.id)&&SkillsClient.current().progress().hasJob(cn.yumis.fight.modules.character.api.JobCatalog.ELEMENTALIST))){
                var view=CareerClient.INSTANCE.view;
                view.elementalist=ElementalistRules.onSkillReleased(view.elementalist,cast.skill,SkillsClient.current().progress(),cast.stats,SkillsClient.current().serverNow());
                ElementalistClient.onSkillReleased(cast);
                CareerClient.INSTANCE.publish();
            }
        }
        catch(RuntimeException failure){PlayerPoseChannel.cancel(cast);finished(cast);SkillsClient.current().message(Objects.toString(failure.getMessage(),"技能执行失败"));}
        return true;
    }
    public boolean valid(SkillCast c){var state=SkillsClient.current();var p=Minecraft.getInstance().player;return p!=null&&state.ready()&&!state.own().dead&&c.session.equals(state.session())&&c.generation==state.own().generation&&c.job.equals(state.progress().jobId())&&c.instance.equals(state.own().instance)&&c.dimension.equals(p.level().dimension().identifier().toString());}
    public void projectile(SkillCast c){MageSkillBehaviors.projectile(c);}
    public void finished(SkillCast c){CareerClient.INSTANCE.ended(c);}
    public void cancel(SkillCast c){MageSkillBehaviors.cancel(c.id);SunMoonClient.cancel(c.id);DemonHunterClient.cancel(c.id);ElementalistClient.cancel(c.id);BattleMageClient.cancel(c.id);WitchClient.cancel(c.id);}
    public boolean issue(SkillCast c,LivingEntity target,double coefficient,int element,String phase,String group){
        return issueElementalist(c,target,coefficient,element,0,phase,group);
    }
    public boolean issueElementalist(SkillCast c,LivingEntity target,double coefficient,int element,double controlSeconds,String phase,String group){
        return issueElementalist(c,target,coefficient,element,controlSeconds,phase,group,"");
    }
    public boolean issueElementalist(SkillCast c,LivingEntity target,double coefficient,int element,double controlSeconds,String phase,String group,String endElemental){
        if(!valid(c))return false;var source=new SkillCast();source.id=c.id;source.skill=c.skill;source.parameters=c.parameters;source.stats=c.stats;source.equipment=c.equipment;source.start=c.start;source.dimension=c.dimension;source.session=c.session;source.generation=c.generation;source.job=c.job;source.instance=c.instance;source.elementOverride=element;source.controlSeconds=controlSeconds;source.damagePhase=phase;source.contactGroup=group;source.endElemental=endElemental==null?"":endElemental;source.dot=!phase.isEmpty()&&SkillDamagePhases.require(c.skill,phase).dot();
        return MageSkillBehaviors.contact(source,target,coefficient);
    }
    public boolean issueMulti(SkillCast c,LivingEntity target,double coefficient,int[] elements,double controlSeconds,String phase,String group){
        if(!valid(c))return false;var source=new SkillCast();source.id=c.id;source.skill=c.skill;source.parameters=c.parameters;source.stats=c.stats;source.equipment=c.equipment;source.start=c.start;source.dimension=c.dimension;source.session=c.session;source.generation=c.generation;source.job=c.job;source.instance=c.instance;source.elementsOverride=elements;source.controlSeconds=controlSeconds;source.damagePhase=phase;source.contactGroup=group;source.dot=!phase.isEmpty()&&SkillDamagePhases.require(c.skill,phase).dot();
        return MageSkillBehaviors.contact(source,target,coefficient);
    }
    public void visual(SkillCast c,String id,String phase,Vec3 point,Vec3 direction,long duration,int sequence,JsonObject motion){
        visualAt(c,id,phase,point,direction,c.start,duration,sequence,motion);
    }
    public void visualAt(SkillCast c,String id,String phase,Vec3 point,Vec3 direction,long startedLocal,long duration,int sequence,JsonObject motion){
        var state=SkillsClient.current();var p=Minecraft.getInstance().player;if(p==null||!state.ready())return;long now=state.serverNow();var j=new JsonObject();
        j.addProperty("caster",p.getStringUUID());j.addProperty("session",state.session());j.addProperty("generation",state.own().generation);j.addProperty("dimension",c.dimension);j.addProperty("instance",c.instance);j.addProperty("id",id);j.addProperty("kind",c.skill.id);j.addProperty("phase",phase);j.addProperty("sequence",sequence);j.addProperty("seed",id.hashCode());j.addProperty("startedAt",now-Math.max(0L,ClientUi.now()-startedLocal));j.addProperty("at",now);j.addProperty("serverNow",now);j.addProperty("duration",Math.max(1,duration));j.addProperty("cosmeticOnly",true);j.addProperty("color",SkillPresentation.color(c.skill,false));if(!c.summonMove.isEmpty()){j.addProperty("summonMove",c.summonMove);j.addProperty("color",c.summonMove.startsWith("ice")||c.summonMove.startsWith("water")?0x87d9ef:c.summonMove.contains("flame")||c.summonMove.contains("fire")||c.summonMove.contains("meteor")||c.summonMove.contains("bomb")?0xf6a04b:c.summonMove.startsWith("shadow")?0xa383df:c.summonMove.startsWith("king")||c.summonMove.startsWith("element")||c.summonMove.startsWith("light")?0x91ddff:0xe7ce84);}
        for(String axis:List.of("x","y","z")){double value=axis.equals("x")?point.x:axis.equals("y")?point.y:point.z;j.addProperty(axis,value);j.addProperty("p"+axis,value);j.addProperty("d"+axis,axis.equals("x")?direction.x:axis.equals("y")?direction.y:direction.z);}if(motion!=null)j.add("motion",motion);Effects.receive(j);state.send("visual",j);
    }
    public void state(JsonObject j){
        if(!valid(j))return;
        if(j.has("kind")){
            String kind=j.get("kind").getAsString();
            if(kind.equals("FRENZY")){frenzyUntil=j.get("until").getAsLong();return;}
            String source=j.get("source").getAsString();
            if(j.get("until").getAsLong()>0){
                var old=army.get(source);
                if(old==null||old.get("sequence").getAsLong()<j.get("sequence").getAsLong())army.put(source,j);
            }else army.remove(source);
            if(!kind.equals("HERDER")){
                if(j.get("until").getAsLong()==0){TrainingCombat.release(source,"mage");TrainingCombat.actionFinished(source);roots.remove(source);}
                return;
            }
            long maxHerder=0;JsonObject latestHerder=null;
            for(var entry:army.values())if("HERDER".equals(entry.get("kind").getAsString())){long u=entry.get("until").getAsLong();if(u>maxHerder){maxHerder=u;latestHerder=entry;}}
            summonUntil=maxHerder;
            if(latestHerder!=null&&latestHerder.has("damage")){
                summonRank=latestHerder.get("rank").getAsInt();var damage=latestHerder.getAsJsonObject("damage");
                summonedMelee=JSON.fromJson(damage.get("melee"),SkillPreview.Damage.class);summonedStone=JSON.fromJson(damage.get("stone"),SkillPreview.Damage.class);summonedTriple=JSON.fromJson(damage.get("triple"),SkillPreview.Damage.class);
            }
            if(summonUntil==0){summonRank=0;summonedMelee=summonedStone=summonedTriple=null;TrainingCombat.release(source,"mage");TrainingCombat.actionFinished(source);roots.remove(source);}
            return;
        }
        summonUntil=j.get("until").getAsLong();if(j.has("damage")){summonRank=j.get("rank").getAsInt();var damage=j.getAsJsonObject("damage");summonedMelee=JSON.fromJson(damage.get("melee"),SkillPreview.Damage.class);summonedStone=JSON.fromJson(damage.get("stone"),SkillPreview.Damage.class);summonedTriple=JSON.fromJson(damage.get("triple"),SkillPreview.Damage.class);}if(summonUntil==0){summonRank=0;summonedMelee=summonedStone=summonedTriple=null;String source=j.get("source").getAsString();TrainingCombat.release(source,"mage");TrainingCombat.actionFinished(source);roots.remove(source);}
    }
    public void action(JsonObject j){
        if(!valid(j)||!received.add(j.get("id").getAsString()))return;if(received.size()>4096)received.clear();String source=j.get("source").getAsString();
        if(roots.add(source))TrainingCombat.begin(source,SkillCatalog.require(j.get("skill").getAsString()).title);
        long exp=j.get("sourceExpires").getAsLong();
        TrainingCombat.hold(source,"mage",ClientUi.now()+(SummonerSkills.isPermanent(exp)?30000L:Math.max(0,exp-SkillsClient.current().serverNow())));
        MageSkillBehaviors.action(j);
    }
    public static boolean decorate(SkillCast source,JsonObject extra){if(source==null||source.summonSource.isBlank())return false;extra.addProperty("summonSource",source.summonSource);extra.addProperty("summonSlow",source.summonSlow);return true;}
    private JsonObject pet(String source){return army.get(source);}
    private void petUpload(JsonObject pet,boolean hurt){var state=SkillsClient.current();var j=new JsonObject();j.addProperty("session",state.session());j.addProperty("generation",state.own().generation);j.add("source",pet.get("source"));pet.addProperty("sequence",pet.get("sequence").getAsLong()+1);for(String key:List.of("sequence","health","shield","lastHurt"))j.add(key,pet.get(key));j.addProperty("hurt",hurt);state.send(hurt?"herderReceive":"herderSnapshot",j);}
    void applyDirectDamageToPet(JsonObject pet,double damage,long now,Stats ownerStats){
        if(pet==null||pet.get("health").getAsDouble()<=0||!Double.isFinite(damage)||damage<=0)return;
        double shield=pet.get("shield").getAsDouble();
        double nextShield=Math.max(0,shield-damage);
        double nextHealth=Math.max(0,pet.get("health").getAsDouble()-Math.max(0,damage-shield));
        pet.addProperty("shield",nextShield);pet.addProperty("health",nextHealth);pet.addProperty("lastHurt",now);
        petUpload(pet,true);
        if(nextHealth<=0)SummonerClient.onSpiritDied(pet,ownerStats);
    }
    public void incoming(JsonObject j){
        if(!valid(j)||!received.add("incoming/"+j.get("root").getAsString()))return;if(received.size()>4096)received.clear();var pet=pet(j.get("source").getAsString());if(pet==null||pet.get("health").getAsDouble()<=0)return;Stats stats=JSON.fromJson(j.get("stats"),Stats.class);long now=SkillsClient.current().serverNow();String kind=j.get("kind").getAsString();boolean direct=j.get("direct").getAsBoolean();double damage;
        Stats ownerStats=SkillController.INSTANCE.snapshot();
        SummonedCreature petEntity=SummonerClient.findSummonEntity(pet);
        boolean inAura=petEntity!=null&&SummonerClient.inSandorAura(petEntity.position(),ownerStats);
        if(inAura){
            stats=stats.copy();
            double def=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_SANDOR_PALADIN.ratio("defense");
            stats.mut(Stat.PHYSICAL_DEFENSE,def);stats.mut(Stat.MAGICAL_DEFENSE,def);
        }
        if(direct){var attacker=JSON.fromJson(j.get("attacker"),cn.yumis.fight.modules.dungeons.api.Target.class);int at=j.get("attackerLevel").getAsInt();damage=SummonRules.incoming(stats,attacker.attack(at),false,-1,at,j.get("ownerLevel").getAsInt(),j.get("guard").getAsBoolean()?.65*.75:.65);}
        else{
            long delay=switch(kind){case "lava","inFire","onFire","drown","inWall","outOfWorld","outsideBorder"->1000;case "cactus","sweetBerryBush","hotFloor","cramming","sting"->500;case "freeze"->2000;default->0;};String key=pet.get("source").getAsString()+"/"+kind;if(delay>0&&environment.getOrDefault(key,0L)>now)return;if(delay>0)environment.put(key,now+delay);
            double fall=j.get("fall").getAsDouble(),fraction=switch(kind){case "lava","outOfWorld","outsideBorder"->.2;case "drown","fallingBlock","anvil","fallingStalactite"->.1;case "lightningBolt"->.25;case "cramming","magic"->.3;case "fall"->Math.max(0,(fall-3)*.05);case "stalagmite"->Math.max(0,(fall-1)*.1);case "trident"->.4;case "thorns"->Math.max(0,j.get("points").getAsDouble())*.05;case "arrow","mobProjectile"->Math.ceil(Math.max(0,j.get("velocity").getAsDouble()*2))*.05;case "explosion","badRespawnPoint","fireworks"->{double f=j.has("distance")?1-j.get("distance").getAsDouble()/8:0;yield f<=0?0:((f*f+f)*.5*7*8+1)*.05;}default->.05;};
            if(kind.equals("poison"))fraction=Math.min(fraction,Math.max(0,pet.get("health").getAsDouble()/pet.get("maximum").getAsDouble()-.001));if(Set.of("inFire","onFire","lava").contains(kind)&&j.get("fireResistance").getAsBoolean()||kind.equals("drown")&&j.get("waterBreathing").getAsBoolean())fraction=0;damage=pet.get("maximum").getAsDouble()*fraction;
        }
        if(inAura)damage*=Math.max(0,1-cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_SANDOR_PALADIN.ratio("reduction"));
        if(!Double.isFinite(damage)||damage<=0||direct&&CombatRules.evades(stats.get(Stat.EVASION),random::nextDouble))return;
        applyDirectDamageToPet(pet,damage,now,ownerStats);
    }
    private void tickPets(){
        long now=SkillsClient.current().serverNow();double dt=lastPetTick==0?0:Math.max(0,Math.min(.25,(now-lastPetTick)/1000d));lastPetTick=now;
        Stats ownerStats=SkillController.INSTANCE.snapshot();
        boolean canUpload=now-lastPetUpload>=250;
        for(var pet:army.values()){
            double hp=pet.get("health").getAsDouble();if(hp<=0)continue;
            double maxHp=pet.get("maximum").getAsDouble(),maxShield=pet.get("maximumShield").getAsDouble(),shield=pet.get("shield").getAsDouble();
            boolean changed=SummonerClient.consumeDirtyPet(pet.get("source").getAsString());
            if(now-pet.get("lastHurt").getAsLong()>=5000&&shield<maxShield){
                shield=Math.min(maxShield,shield+maxShield*.2*dt);
                pet.addProperty("shield",shield);changed=true;
            }
            SummonedCreature petEntity=SummonerClient.findSummonEntity(pet);
            if(petEntity!=null&&SummonerClient.inSandorAura(petEntity.position(),ownerStats)){
                double rate=cn.yumis.fight.modules.dungeons.api.TowerBattleCard.SUMMONER_SANDOR_PALADIN.ratio("regen")*dt;
                if(hp<maxHp){pet.addProperty("health",Math.min(maxHp,hp+maxHp*rate));changed=true;}
                if(shield<maxShield){pet.addProperty("shield",Math.min(maxShield,shield+maxShield*rate));changed=true;}
            }
            if(changed&&canUpload)petUpload(pet,false);
        }
        if(canUpload)lastPetUpload=now;
    }
    public void tick(SkillBehaviors.Context context){ensureContext();CareerClient.INSTANCE.tickRenew();MageSkillBehaviors.tick();SummonerClient.tick(ClientUi.now());SunMoonClient.tick();
        var state=SkillsClient.current();var mc=Minecraft.getInstance();
        if(state.own().dead){clear();return;}if(mc.level==null)return;tickPets();

    }
}
