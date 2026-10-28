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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.*;

/** Validates career resources and joins on-hit effects to the existing combat WAL receipt. */
public final class CareerSkillService implements CombatPorts.SkillEffects,JobTransitions {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static <T>T copy(T v,Class<T> type){return JSON.fromJson(JSON.toJson(v),type);}
    private static final Identifier STEADFAST=Identifier.parse("yumis_dungeon_and_fight:frenzy_knockback");
    private final Journal journal;
    private final MinecraftServer server;
    private final Function<ServerPlayer,CombatSession> sessions;
    private final Function<ServerPlayer,Progress> progress;
    private final Function<ServerPlayer,Stats> stats;
    private final BiPredicate<ServerPlayer,UUID> friendly;
    private final Map<UUID,CareerState> states=new HashMap<>();
    private final Map<UUID,Map<String,Cast>> casts=new HashMap<>();
    private final Map<UUID,Set<String>> bloodCredits=new HashMap<>();
    private final Map<UUID,Long> invulnerable=new HashMap<>();
    private static final class Cast {
        String id,skill,session,dimension,instance;
        long generation,expires,started;
        SkillParameters.Snapshot parameters;
        Stats stats;
        EquipmentEffects.Snapshot equipment;
        Vec3 origin,center,direction;
        final Set<String> hits=new HashSet<>();
        final Map<UUID,Long> controls=new HashMap<>();
        boolean healed,terminal;
        UUID orbTarget;
        long orbGeneration;
    }
    public CareerSkillService(Journal journal,MinecraftServer server,Function<ServerPlayer,CombatSession> sessions,Function<ServerPlayer,Progress> progress,Function<ServerPlayer,Stats> stats,BiPredicate<ServerPlayer,UUID> friendly){this.journal=journal;this.server=server;this.sessions=sessions;this.progress=progress;this.stats=stats;this.friendly=friendly;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){
        router.register("skills","careerCast",this::start);
        
        router.register("skills","careerCancel",this::cancel);
        router.register("skills","careerState",(p,j)->send(p));
    }
    private CareerState state(ServerPlayer p){
        var session=sessions.apply(p);String dimension=p.level().dimension().identifier().toString();
        CareerState value=states.get(p.getUUID());
        if(value==null||!value.session.equals(session.id.toString())||value.generation!=session.life.generation||!value.job.equals(progress.apply(p).jobId())||!value.dimension.equals(dimension)||!value.instance.equals(session.life.instance)||session.life.dead){
            clear(p);value=new CareerState();value.session=session.id.toString();value.generation=session.life.generation;value.job=progress.apply(p).jobId();value.dimension=dimension;value.instance=session.life.instance;value.nextBrand=System.currentTimeMillis()+5000;states.put(p.getUUID(),value);
        }
        return value;
    }
    public CareerState snapshot(ServerPlayer p){
        CareerState view=copy(state(p),CareerState.class);view.fields.clear();long now=System.currentTimeMillis();
        for(var entry:states.entrySet()){
            var other=entry.getValue();
            var actorPlayer=server.getPlayerList().getPlayer(entry.getKey());
            if(actorPlayer!=null&&actorPlayer.level()==p.level()&&actorPlayer.distanceToSqr(p)<=4096&&!sessions.apply(actorPlayer).life.dead){
                var actor=new CareerState.Actor();actor.owner=actorPlayer.getStringUUID();actor.keiga=SkillLearning.effective(stats.apply(actorPlayer),progress.apply(actorPlayer),SkillCatalog.require(CareerSkills.KEIGA));actor.brands=other.brands;actor.blood=other.blood;actor.frenzy=other.frenzy;actor.kallaUntil=other.kallaUntil;actor.eyeUntil=other.eyeUntil;actor.bloodUntil=other.bloodUntil;view.actors.add(actor);
            }
            var f=other.field;
            if(f==null||f.expires<=now||!f.dimension.equals(view.dimension)||!f.instance.equals(view.instance))continue;
            var owner=server.getPlayerList().getPlayer(entry.getKey());
            if(owner==null||sessions.apply(owner).life.dead||other.generation!=sessions.apply(owner).life.generation)continue;
            var field=copy(f,CareerState.Field.class);field.friendly=entry.getKey().equals(p.getUUID())||friendly.test(p,entry.getKey());view.fields.add(field);
        }
        return view;
    }
    public Stats currentStats(ServerPlayer p){var view=snapshot(p);return CareerRules.current(SkillPreview.currentStats(stats.apply(p),sessions.apply(p).resources,System.currentTimeMillis()),progress.apply(p),view,sessions.apply(p).life.q,view.red(p.getX(),p.getY(),p.getZ(),System.currentTimeMillis()));}
    public boolean invulnerable(ServerPlayer p){state(p);return invulnerable.getOrDefault(p.getUUID(),0L)>System.currentTimeMillis();}
    public boolean frenzy(ServerPlayer p){return state(p).frenzy&&!sessions.apply(p).life.dead;}
    public void clear(ServerPlayer p){states.remove(p.getUUID());casts.remove(p.getUUID());bloodCredits.remove(p.getUUID());invulnerable.remove(p.getUUID());for(var kind:List.of(Attributes.KNOCKBACK_RESISTANCE,Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)){var attribute=p.getAttribute(kind);if(attribute!=null)attribute.removeModifier(STEADFAST);}}
    private Cast cast(ServerPlayer p,String id){return casts.getOrDefault(p.getUUID(),Map.of()).get(id);}
    private void send(ServerPlayer p){Network.send(p,"careerState",snapshot(p));}
    private void ensureActor(ServerPlayer p){if(sessions.apply(p).life.dead||p.isDeadOrDying()||p.hasEffect(CombatContentTypes.STUN))throw new IllegalArgumentException("当前不能施放技能");var gear=EquipmentItems.gear(p.getMainHandItem());if(gear==null||gear.slot!=Slot.WEAPON||!gear.valid(progress.apply(p),EquipmentEligibility.allowance(p)))throw new IllegalArgumentException("需要可用的职业武器");}
    private void start(ServerPlayer p,JsonObject request){
        String id=request.get("id").getAsString();UUID.fromString(id);
        var session=sessions.apply(p);if(!request.get("session").getAsString().equals(session.id.toString())||request.get("generation").getAsLong()!=session.life.generation)return;
        String receipt="skillStart/"+p.getUUID()+"/"+session.id+"/"+id;
        JsonElement previous=journal.get(receipt);if(!previous.isJsonNull()){Network.send(p,"careerStarted",previous);return;}
        try{
            ensureActor(p);var s=state(p);long now=System.currentTimeMillis();String skillId=request.get("skill").getAsString();var content=CareerSkills.content(skillId);
            if(content==null||content.skill().passive())throw new IllegalArgumentException("不是主动职业技能");
            var skill=content.skill();var player=progress.apply(p);Stats equipment=SkillPreview.currentStats(stats.apply(p),session.resources,now);int rank=SkillLearning.effective(equipment,player,skill);
            if(rank<=0)throw new IllegalArgumentException("职业不符或技能未学习");
            boolean terminal=skillId.equals(CareerSkills.KALLA)&&s.kallaUntil>now||skillId.equals(CareerSkills.EYE)&&s.eyeUntil>0||skillId.equals(CareerSkills.SWORD)&&s.bloodUntil>0||skillId.equals(CareerSkills.FRENZY)&&s.frenzy;
            int slot=request.has("slot")?request.get("slot").getAsInt():-1;
            if(!terminal&&(slot<0||slot>=player.slots.length||!player.slots[slot].equals(skillId)))throw new IllegalArgumentException("技能绑定已改变");
            if(!terminal&&session.resources.cooldowns.getOrDefault(skillId,0L)>now&&!session.resources.training)throw new IllegalArgumentException("技能冷却中");
            if((skillId.equals(CareerSkills.ORB)||skillId.equals(CareerSkills.AGNI))&&s.brands<1)throw new IllegalArgumentException("至少需要一枚波动刻印");
            var resources=copy(session.resources,cn.yumis.fight.modules.combat.api.Runtime.class);var life=copy(session.life,Life.class);
            resources.equipment=session.resources.equipment;
            var cost=SkillCosts.resolve(skill,rank,equipment,resources.equipment,CareerRules.magic(skill,player));
            double hpCost=terminal?0:content.hpCost();if(!terminal)SkillCosts.pay(cost,resources,life,equipment.get(Stat.HEALTH),hpCost);
            if(!terminal)EquipmentBuffs.imprint(resources,resources.equipment,now);
            equipment=SkillPreview.currentStats(stats.apply(p),resources,now);
            var next=copy(s,CareerState.class);int fieldRank=skillId.equals(CareerSkills.BLACHE)&&next.field!=null&&next.field.expires>now?next.field.rank:0;
            int brands=next.brands,blood=next.blood;
            var parameters=SkillParameters.forCast(p.getUUID(),skill,rank,equipment.parameters);
            Stats resolved=CareerRules.current(equipment,player,next,life.q,snapshot(p).red(p.getX(),p.getY(),p.getZ(),now));
            // State phases hold their first-cast snapshot, even after the weapon or effective rank changes.
            if(terminal){var original=casts.getOrDefault(p.getUUID(),Map.of()).values().stream().filter(c->c.skill.equals(skillId)&&!c.terminal).max(Comparator.comparingLong(c->c.started)).orElse(null);if(original!=null){parameters=original.parameters;resolved=copy(original.stats,Stats.class);rank=parameters.rank();}}
            parameters=CareerRules.resourceSnapshot(parameters,brands,blood,fieldRank,terminal);
            resolved=CareerRules.castDamage(resolved,parameters);
            var parameterValues=new LinkedHashMap<>(parameters.values());
            if(terminal)parameterValues.put("actionDuration",skillId.equals(CareerSkills.KALLA)?.9:skillId.equals(CareerSkills.EYE)?1.2:2d);
            if(skillId.equals(CareerSkills.ORB))parameterValues.put("duration",5d);
            parameters=new SkillParameters.Snapshot(skill,rank,parameterValues);
            if(s.eyeUntil>now&&(skillId.equals(CareerSkills.ICE)||skillId.equals(CareerSkills.FIRE))){resolved.modifier("final").mut+=.20;resolved.modifiers.put("careerEye",1d);}
            Vec3 origin=p.position(),direction=horizontal(p.getLookAngle()),center=origin;
            if(skillId.equals(CareerSkills.SWORD)&&terminal)center=origin.add(direction.scale(StormChoreography.CENTER_OFFSET));
            if(skillId.equals(CareerSkills.FIELD)){
                var hit=p.level().clip(new ClipContext(p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(8)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                Vec3 candidate=hit.getType()==HitResult.Type.MISS?origin.add(direction.scale(4)):hit.getLocation();
                var floor=p.level().clip(new ClipContext(candidate.add(0,1.2,0),candidate.add(0,-3,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                if(floor.getType()==HitResult.Type.MISS||origin.distanceToSqr(floor.getLocation())>64)throw new IllegalArgumentException("8格内没有可放置鬼阵的地面");
                center=floor.getLocation();var f=new CareerState.Field();f.id=id;f.owner=p.getStringUUID();f.rank=rank;f.x=center.x;f.y=center.y;f.z=center.z;f.dimension=next.dimension;f.instance=next.instance;f.expires=now+12000;next.field=f;
            }
            if(skillId.equals(CareerSkills.BLACHE))next.field=null;
            if(skillId.equals(CareerSkills.ORB)||skillId.equals(CareerSkills.AGNI))next.brands=0;
            if(skillId.equals(CareerSkills.FRENZY))next.frenzy=!terminal;
            if(skillId.equals(CareerSkills.KALLA))next.kallaUntil=terminal?0:now+20000;
            if(skillId.equals(CareerSkills.EYE))next.eyeUntil=terminal?0:now+20000;
            if(skillId.equals(CareerSkills.SWORD)){next.bloodUntil=terminal?0:now+60000;next.blood=0;}
            if(CareerRules.producesBrand(skillId))next.brands=Math.min(5,next.brands+1);
            if(!terminal&&!resources.training)resources.cooldowns.put(skillId,now+(long)(SkillCosts.resolve(skill,rank,resolved,resources.equipment,CareerRules.magic(skill,player)).cooldown()*1000));
            resources.controlEpoch++;resources.recordedAt=now;
            Cast accepted=new Cast();accepted.id=id;accepted.skill=skillId;accepted.session=session.id.toString();accepted.generation=life.generation;accepted.dimension=next.dimension;accepted.instance=next.instance;accepted.started=now;accepted.expires=now+(long)((skillId.equals(CareerSkills.FIELD)?12:Math.max(parameters.get("duration",1),5))*1000)+5000;accepted.parameters=parameters;accepted.stats=resolved;accepted.equipment=resources.equipment;accepted.origin=origin;accepted.center=center;accepted.direction=direction;accepted.terminal=terminal;
            if(skillId.equals(CareerSkills.CRASH)||skillId.equals(CareerSkills.ERUPTION))accepted.expires=now+35000;
            JsonObject ack=new JsonObject();ack.addProperty("id",id);ack.addProperty("skill",skillId);ack.addProperty("slot",slot);ack.addProperty("serverNow",now);ack.addProperty("session",session.id.toString());ack.addProperty("generation",life.generation);ack.addProperty("rank",rank);ack.addProperty("terminal",terminal);ack.add("parameters",JSON.toJsonTree(parameters.values()));ack.add("stats",JSON.toJsonTree(resolved));ack.add("equipmentEffects",JSON.toJsonTree(resources.equipment));ack.add("origin",JSON.toJsonTree(new double[]{origin.x,origin.y,origin.z}));ack.add("center",JSON.toJsonTree(new double[]{center.x,center.y,center.z}));ack.add("direction",JSON.toJsonTree(new double[]{direction.x,direction.y,direction.z}));ack.add("resources",JSON.toJsonTree(resources));ack.add("life",JSON.toJsonTree(life));
            journal.begin();journal.put("resources/"+p.getUUID(),resources);journal.put("life/"+p.getUUID(),life);journal.put(receipt,ack);journal.finish();
            session.resources=resources;session.life=life;states.put(p.getUUID(),next);casts.computeIfAbsent(p.getUUID(),k->new LinkedHashMap<>()).put(id,accepted);
            if(skillId.equals(CareerSkills.SWORD))bloodCredits.remove(p.getUUID());
            if(content.invulnerable()||terminal&&(skillId.equals(CareerSkills.EYE)||skillId.equals(CareerSkills.SWORD)))invulnerable.put(p.getUUID(),now+(long)((skillId.equals(CareerSkills.EYE)?1200:2000)/Math.max(.01,resolved.get(Stat.CAST_SPEED))));
            if(skillId.equals(CareerSkills.PHANTOM))invulnerable.put(p.getUUID(),now+250);
            if(!terminal&&(hpCost>0||cost.life()>0)){p.setHealth((float)Math.max(1,life.q*p.getMaxHealth()));Network.send(p,"health",life);}
            Network.send(p,"careerStarted",ack);send(p);
        }catch(Exception error){journal.abort();JsonObject rejected=new JsonObject();rejected.addProperty("id",id);rejected.addProperty("error",Objects.toString(error.getMessage(),"施放失败"));Network.send(p,"careerRejected",rejected);}
    }
    public void baseSucceeded(ServerPlayer p,SkillDefinition definition){if(progress.apply(p).hasJob(JobCatalog.ASURA)&&SkillLearning.effective(stats.apply(p),progress.apply(p),SkillCatalog.require(CareerSkills.BRANDS))>0&&CareerRules.producesBrand(definition.id)){var s=state(p);s.brands=Math.min(5,s.brands+1);send(p);}}
    public Stats castStats(ServerPlayer p,cn.yumis.fight.modules.combat.api.Runtime resources){var view=snapshot(p);return CareerRules.current(SkillPreview.currentStats(stats.apply(p),resources,System.currentTimeMillis()),progress.apply(p),view,sessions.apply(p).life.q,view.red(p.getX(),p.getY(),p.getZ(),System.currentTimeMillis()));}
    public Stats sourceStats(ServerPlayer p,JsonObject report){Cast value=report.has("careerCast")?cast(p,report.get("careerCast").getAsString()):null;return value==null?null:value.stats;}
    public EquipmentEffects.Snapshot sourceEffects(ServerPlayer p,JsonObject report){Cast value=report.has("careerCast")?cast(p,report.get("careerCast").getAsString()):null;return value==null?null:value.equipment;}
    private void cancel(ServerPlayer p,JsonObject j){var c=cast(p,j.get("id").getAsString());if(c!=null){c.expires=0;if(c.terminal&&(c.skill.equals(CareerSkills.SWORD)||c.skill.equals(CareerSkills.EYE)))invulnerable.remove(p.getUUID());}}
    @Override public void reconcile(ServerPlayer p,cn.yumis.fight.modules.combat.api.Runtime previous,cn.yumis.fight.modules.combat.api.Runtime incoming){for(var content:CareerSkills.all()){String id=content.skill().id;long until=previous.cooldowns.getOrDefault(id,0L);if(until>incoming.cooldowns.getOrDefault(id,0L))incoming.cooldowns.put(id,until);}}
    private String hitKey(JsonObject report){return report.get("careerIndex").getAsInt()+"/"+report.get("target").getAsString()+"/"+report.get("generation").getAsLong();}
    @Override public boolean direct(ServerPlayer p,JsonObject report){Cast c=report.has("careerCast")?cast(p,report.get("careerCast").getAsString()):null;return c==null||!c.skill.equals(CareerSkills.FIELD)&&!(c.skill.equals(CareerSkills.KALLA)&&report.get("careerIndex").getAsInt()>=64);}
    @Override public void validate(ServerPlayer p,LivingEntity target,JsonObject report){
        if(!report.has("careerCast"))return;Cast c=cast(p,report.get("careerCast").getAsString());var s=sessions.apply(p);long now=System.currentTimeMillis();int index=report.get("careerIndex").getAsInt();
        if(c==null||!c.session.equals(s.id.toString())||c.generation!=s.life.generation||!c.dimension.equals(p.level().dimension().identifier().toString())||!c.instance.equals(s.life.instance)||c.expires<now||index<0||index>CareerSkills.hitLimit(c.skill,c.terminal)||c.hits.contains(hitKey(report)))throw new IllegalArgumentException("技能命中阶段或生命已失效");
        if(c.skill.equals(CareerSkills.ORB)&&(index>=(int)c.parameters.get("slashes",0)||c.orbTarget!=null&&(!c.orbTarget.equals(target.getUUID())||c.orbGeneration!=report.get("generation").getAsLong())))throw new IllegalArgumentException("鬼印珠目标或攻击次数无效");
        if(c.skill.equals(CareerSkills.FIELD)&&(index<16?index>11||c.parameters.rank()<8:c.parameters.rank()<15))throw new IllegalArgumentException("鬼阵效果尚未解锁");
        if(report.has("careerControl")){double seconds=report.get("careerControl").getAsDouble();if(!Double.isFinite(seconds)||seconds<0||seconds>CareerSkills.controlLimit(c.skill))throw new IllegalArgumentException("控制参数无效");}
    }
    @Override public CombatPorts.Commit prepare(ServerPlayer p,LivingEntity target,JsonObject report,Receipt receipt,Life actorLife){
        if(!receipt.accepted()||receipt.actual()<=0||target==p)return CombatPorts.Commit.NONE;
        Cast c=report.has("careerCast")?cast(p,report.get("careerCast").getAsString()):null;
        boolean heal=c!=null&&c.skill.equals(CareerSkills.HAND)&&!c.healed;
        Life next=heal?actorLife:null;if(heal)LifeRules.apply(next,.10,true);
        String credit=report.has("basicCycle")?report.get("basicCycle").getAsString():c!=null?c.id:report.has("sourceCast")?report.get("sourceCast").getAsString():"";
        return new CombatPorts.Commit(){
            
            public void publish(){
                if(c!=null)c.hits.add(hitKey(report));
                if(c!=null&&c.skill.equals(CareerSkills.ORB)&&c.orbTarget==null){c.orbTarget=target.getUUID();c.orbGeneration=report.get("generation").getAsLong();}
                if(next!=null){c.healed=true;sessions.apply(p).life=next;p.setHealth((float)Math.max(1,p.getMaxHealth()*next.q));Network.send(p,"health",next);}
                if(!credit.isEmpty()&&direct(p,report)&&(report.has("basicCycle")||c!=null||report.has("sourceCast")||cast(p,credit)!=null))credit(p,credit);
                if(c!=null&&report.has("careerControl")&&(!c.skill.equals(CareerSkills.FIELD)||c.controls.getOrDefault(target.getUUID(),0L)<=System.currentTimeMillis())){control(p,target,report.get("careerControl").getAsDouble());c.controls.put(target.getUUID(),System.currentTimeMillis()+3000);}
                if(c!=null&&c.skill.equals(CareerSkills.HAND)&&!kind(target).equals("BOSS")){Vec3 delta=p.position().add(p.getLookAngle().multiply(1,0,1)).subtract(target.position());target.move(MoverType.SELF,delta.normalize().scale(Math.min(2,delta.length())));}
            }
        };
    }
    private void credit(ServerPlayer p,String id){var s=state(p);if(s.bloodUntil<=System.currentTimeMillis())return;var used=bloodCredits.computeIfAbsent(p.getUUID(),k->new HashSet<>());if(used.add(id)){s.blood++;send(p);}}
    private void control(ServerPlayer p,LivingEntity target,double seconds){
        if(seconds<=0||target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||target instanceof net.minecraft.world.entity.boss.wither.WitherBoss)return;
        String kind=kind(target);
        if(kind.equals("BOSS"))return;if(kind.equals("ELITE"))seconds*=.5;
        target.addEffect(new net.minecraft.world.effect.MobEffectInstance(CombatContentTypes.STUN,(int)Math.max(1,Math.ceil(seconds*20))));
    }
    private String kind(LivingEntity target){
        if(target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon||target instanceof net.minecraft.world.entity.boss.wither.WitherBoss)return "BOSS";
        var tag=target.getPersistentData();String kind=tag.getStringOr("ydnf_kind","");
        // Dungeon targets expose their combat kind through the synchronized target attachment.
        String data=target.getData(CombatContentTypes.TARGET);
        try{if(!data.isBlank()){var t=JsonParser.parseString(data).getAsJsonObject();if(t.has("kind"))kind=t.get("kind").getAsString();if(kind.equals("NORMAL")&&t.has("elite")&&t.get("elite").getAsBoolean())kind="ELITE";}}catch(Exception ignored){}
        return kind;
    }
    @Override public Change prepare(ServerPlayer p,Progress next,String destination){
        var session=sessions.apply(p);long now=System.currentTimeMillis();
        if(!session.life.instance.isEmpty())throw new IllegalArgumentException("请先退出副本再重转");
        if(session.resources.cooldowns.values().stream().anyMatch(until->until>now))throw new IllegalArgumentException("所有技能冷却结束后才可重转");
        SkillLearning.refundCareer(next);return clear(p,next);
    }
    @Override public Change clear(ServerPlayer p,Progress next){
        var session=sessions.apply(p);var resources=copy(session.resources,cn.yumis.fight.modules.combat.api.Runtime.class);resources.controlEpoch++;
        resources.buffs.remove("yumis_dungeon_and_fight:overdrive");resources.absorption.remove("overdrivePower");
        return new Change(Map.of("resources/"+p.getUUID(),JSON.toJsonTree(resources)),()->{session.resources=resources;clear(p);send(p);});
    }
    public void tick(){
        long now=System.currentTimeMillis();
        for(var p:server.getPlayerList().getPlayers()){
            var s=state(p);if(sessions.apply(p).life.dead)continue;
            if(s.field!=null&&s.field.expires<=now)s.field=null;
            if(s.kallaUntil<=now)s.kallaUntil=0;
            if(s.eyeUntil>now){
                double radius=casts.getOrDefault(p.getUUID(),Map.of()).values().stream().filter(c->c.skill.equals(CareerSkills.EYE)&&!c.terminal).max(Comparator.comparingLong(c->c.started)).map(c->c.parameters.get("range",16)).orElse(16d);
                for(var enemy:p.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(radius,4,radius),e->e.isAlive()&&(e instanceof net.minecraft.world.entity.monster.Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon))){
                    if(enemy.position().subtract(p.position()).horizontalDistanceSqr()>radius*radius||Math.abs(enemy.getY()-p.getY())>4)continue;
                    var block=p.level().clip(new ClipContext(p.getEyePosition(),enemy.getBoundingBox().getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                    if(block.getType()==HitResult.Type.MISS)enemy.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS,20,0,false,true));
                }
            }
            if(progress.apply(p).hasJob(JobCatalog.ASURA)&&SkillLearning.effective(stats.apply(p),progress.apply(p),SkillCatalog.require(CareerSkills.BRANDS))>0){while(now>=s.nextBrand){s.brands=Math.min(5,s.brands+1);s.nextBrand+=5000;}}else{s.brands=0;s.nextBrand=now+5000;}
            for(var kind:List.of(Attributes.KNOCKBACK_RESISTANCE,Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)){var attribute=p.getAttribute(kind);if(attribute!=null){attribute.removeModifier(STEADFAST);if(s.frenzy)attribute.addTransientModifier(new AttributeModifier(STEADFAST,1,AttributeModifier.Operation.ADD_VALUE));}}
            var table=casts.get(p.getUUID());if(table!=null)table.values().removeIf(c->c.expires<now-5000&&!(c.skill.equals(CareerSkills.SWORD)&&!c.terminal&&s.bloodUntil>0));
            send(p);
        }
        states.keySet().removeIf(id->server.getPlayerList().getPlayer(id)==null);
    }
    private static Vec3 horizontal(Vec3 v){var h=new Vec3(v.x,0,v.z);return h.lengthSqr()<.001?new Vec3(0,0,1):h.normalize();}
}
