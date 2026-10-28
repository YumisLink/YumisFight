package cn.yumis.fight.modules.combat.application;
import cn.yumis.fight.modules.combat.api.Receipt;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.combat.api.CombatTarget;
import cn.yumis.fight.modules.combat.api.CombatSession;
import cn.yumis.fight.modules.combat.api.CombatPorts;
import cn.yumis.fight.modules.presentation.api.ModText;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;
import cn.yumis.fight.modules.combat.api.CombatTiming;

import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.foundation.Journal;

import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import java.util.*;
import java.util.function.*;
/** Receipt validation and lifecycle. Rewards and encounter effects participate in the same WAL batch. */
public final class CombatApplication {
    private java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.attributes.api.Stats> equipmentStats;
    private java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.character.api.Progress> equipmentProgress;
    private java.util.function.Predicate<ServerPlayer> equipmentSync=p->true;
    private java.util.function.Consumer<ServerPlayer> equipmentProfile=p->{};
    public void equipment(java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.attributes.api.Stats> stats,java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.character.api.Progress> progress,java.util.function.Predicate<ServerPlayer> sync,java.util.function.Consumer<ServerPlayer> profile){equipmentStats=stats;equipmentProgress=progress;equipmentSync=sync;equipmentProfile=profile;}
    private CombatPorts.SkillEffects skills=new CombatPorts.SkillEffects(){};
    public void skills(CombatPorts.SkillEffects value){skills=java.util.Objects.requireNonNull(value);}
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}

    private final Journal journal;
    private final MinecraftServer server;
    private final Function<ServerPlayer,? extends CombatSession> sessions;
    private final Predicate<Entity> managed;
    private final Function<LivingEntity,? extends CombatTarget> targets;
    private final CombatPorts.Encounters dungeons;
    private final CombatPorts.Rewards loot;
    private final CombatPorts.NativeHits nativeHits;
    private final CombatPorts.Attacks basicAttacks;
    private final CombatPorts.Recorder damageMeter;
    private final Consumer<Boolean> projection;
    private final BiConsumer<ServerPlayer,String> message;
    public CombatApplication(Journal journal,MinecraftServer server,Function<ServerPlayer,? extends CombatSession> sessions,Predicate<Entity> managed,Function<LivingEntity,? extends CombatTarget> targets,CombatPorts.Encounters dungeons,CombatPorts.Rewards loot,CombatPorts.NativeHits nativeHits,CombatPorts.Attacks basicAttacks,CombatPorts.Recorder damageMeter,Consumer<Boolean> projection,BiConsumer<ServerPlayer,String> message){this.journal=journal;this.server=server;this.sessions=sessions;this.managed=managed;this.targets=targets;this.dungeons=dungeons;this.loot=loot;this.nativeHits=nativeHits;this.basicAttacks=basicAttacks;this.damageMeter=damageMeter;this.projection=projection;this.message=message;}
    public void runtime(ServerPlayer p,JsonObject j){
        CombatSession s=sessions.apply(p);
        if(j.has("session")&&!s.id.toString().equals(j.get("session").getAsString()))return;
        long generation=j.has("generation")?j.get("generation").getAsLong():s.life.generation;
        if(generation!=s.life.generation)return;
        cn.yumis.fight.modules.combat.api.Runtime r=JSON.fromJson(j.has("state")?j.get("state"):j,cn.yumis.fight.modules.combat.api.Runtime.class);
        r.validate();
        long issued=j.has("serverIssuedAt")?j.get("serverIssuedAt").getAsLong():r.recordedAt;
        if(r.controlEpoch!=s.resources.controlEpoch||issued<s.resources.recordedAt)return;
        long local=r.recordedAt;
        // These fields belong to the administrator command, never to combat prediction uploads.
        r.training=s.resources.training;
        r.trainingBoundary=s.resources.trainingBoundary;
        // Accepted damage events own these clocks; prediction uploads cannot extend them.
        r.combatUntil=s.resources.combatUntil;
        r.shieldRecoveryAt=s.resources.shieldRecoveryAt;
        r.cooldowns.replaceAll((k,v)->issued+Math.max(0,v-local));
        r.buffs.replaceAll((k,v)->issued+Math.max(0,v-local));
        r.absorptionUntil.replaceAll((k,v)->v==Long.MAX_VALUE?v:issued+Math.max(0,v-local));
        r.recordedAt=issued;
        r.equipment=s.resources.equipment;
        r.equipmentBuffs=copy(s.resources,cn.yumis.fight.modules.combat.api.Runtime.class).equipmentBuffs;
        skills.reconcile(p,s.resources,r);
        s.resources=r;
        try{
            journal.put("resources/"+p.getUUID(),r);
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
    public void report(ServerPlayer p,String action,JsonObject j){
        CombatSession s=sessions.apply(p);
        String key="receipt/"+p.getUUID()+"/"+s.id+"/"+j.get("operation").getAsLong();
        cn.yumis.fight.modules.combat.api.Runtime previousResources=copy(s.resources,cn.yumis.fight.modules.combat.api.Runtime.class);
        boolean durable=false;
        try{
            JsonElement previous=journal.get(key);
            if(!previous.isJsonNull()){
                JsonObject r=previous.getAsJsonObject();
                if(!r.get("intent").equals(j))throw new IllegalArgumentException("操作号与原伤害内容冲突");
                Network.send(p,"ack",r.get("ack"));
                return;
            }
            if(!s.id.toString().equals(j.get("session").getAsString())||j.get("operation").getAsLong()<=0)throw new IllegalArgumentException("会话已失效");
            if(!j.has("sourceGeneration")||j.get("sourceGeneration").getAsLong()!=s.life.generation||s.life.dead)throw new IllegalArgumentException("施加者生命已失效");
            boolean heal=action.equals("heal"),outcomeOnly=action.equals("nativeOutcome");
            if(j.has("heal")&&j.get("heal").getAsBoolean()!=heal)throw new IllegalArgumentException("伤害类型不一致");
            UUID id=UUID.fromString(j.get("target").getAsString());
            LivingEntity entity=id.equals(p.getUUID())?p:(LivingEntity)p.level().getEntity(id);
            if(entity==null||!managed.test(entity))throw new IllegalArgumentException("目标不存在");
            if(entity instanceof Player&&entity!=p)throw new IllegalArgumentException("PVP已关闭");
            if(heal&&entity!=p)throw new IllegalArgumentException("仅支持本人恢复");
            CombatTarget target=entity==p?null:targets.apply(entity);
            Life life=entity==p?copy(s.life,Life.class):target.life();
            double hitMaximum=target==null||equipmentProgress==null?0:target.maximumHealth(equipmentProgress.apply(p).level,equipmentSync.test(p));
            if(!j.has("dimension")||!j.get("dimension").getAsString().equals(p.level().dimension().identifier().toString()))throw new IllegalArgumentException("维度已改变");
            if(j.get("generation").getAsLong()!=life.generation||j.get("basis").getAsLong()!=life.basis)throw new IllegalArgumentException("生命或扩血基准已失效");
            String instance=j.has("instance")?j.get("instance").getAsString():"";
            if(!instance.equals(life.instance)||!instance.isEmpty()&&!dungeons.participant(p,instance))throw new IllegalArgumentException("副本轮次已失效");
            boolean nativeHit=nativeHits.validate(p,entity,j);
            if(heal&&nativeHit)throw new IllegalArgumentException("原生命中不能用于治疗");
            JsonObject dot=null;
            if(j.has("effect")){
                dot=validateDot(p,j,target);
            }
            double fraction=j.get("fraction").getAsDouble();
            String outcome=j.has("outcome")?j.get("outcome").getAsString():"HIT";
            if(!Set.of("HIT","DODGE","IMMUNE").contains(outcome)||!outcome.equals("HIT")&&fraction!=0)throw new IllegalArgumentException("命中结果无效");
            double taken=j.has("damageTaken")?j.get("damageTaken").getAsDouble():0;
            finite(taken);
            if(taken<0||!outcome.equals("HIT")&&taken!=0)throw new IllegalArgumentException("承伤结果无效");
            if(outcomeOnly){
                boolean bossHit=entity==p&&outcome.equals("HIT")&&taken>0&&j.has("feedbackEntity")&&p.level().getEntity(UUID.fromString(j.get("feedbackEntity").getAsString())) instanceof LivingEntity origin&&managed.test(origin);
                if(heal||entity!=p||fraction!=0||!nativeHit&&!bossHit)throw new IllegalArgumentException("零伤害命中来源无效");
            }
            basicAttacks.validate(p,j);
            skills.validate(p,entity,j);
            Receipt receipt=outcomeOnly?cn.yumis.fight.modules.combat.api.LifeRules.noDamageReceipt(life):cn.yumis.fight.modules.combat.api.LifeRules.apply(life,fraction,heal);
            Life actorLife=entity==p?life:copy(s.life,Life.class);
            CombatPorts.Commit skillCommit=skills.prepare(p,entity,j,receipt,actorLife);
            String previousBuffs=JSON.toJson(s.resources.equipmentBuffs);
            JsonObject ack=new JsonObject();
            ack.addProperty("session",s.id.toString());
            ack.add("operation",j.get("operation"));
            ack.addProperty("target",id.toString());
            ack.addProperty("generation",life.generation);
            ack.add("receipt",JSON.toJsonTree(receipt));
            JsonObject stored=new JsonObject();
            stored.add("intent",j);
            stored.add("ack",ack);
            journal.begin();
            if(j.has("runtime"))runtime(p,j.getAsJsonObject("runtime"));
            if(!heal&&(receipt.actual()>0||entity==p&&outcome.equals("HIT")&&taken>0)){
                long eventTime=nativeHit?nativeHits.occurredAt(j):j.has("occurredAt")?Math.min(System.currentTimeMillis(),j.get("occurredAt").getAsLong()):System.currentTimeMillis();
                if(entity==p)CombatTiming.taken(s.resources,eventTime);else CombatTiming.dealt(s.resources,eventTime);
                journal.put("resources/"+p.getUUID(),s.resources);
                damageMeter.record(p,target,receipt.actual(),eventTime);
            }
            ack.addProperty("combatUntil",s.resources.combatUntil);
            ack.addProperty("shieldRecoveryAt",s.resources.shieldRecoveryAt);
            if(dot!=null){
                dot.addProperty("nextIndex",dot.get("nextIndex").getAsInt()+1);
                dot.addProperty("nextAt",System.currentTimeMillis()+1000);
                journal.put("ailments/"+id+"/"+p.getUUID()+"/BLEED",dot);
            }
            if(nativeHit)nativeHits.commit(p,j);
            journal.put("life/"+id,life);
            if(target!=null){
                target.life(life);
                if(receipt.actual()>0&&!heal)target.contributions().merge(p.getStringUUID(),receipt.actual(),Double::sum);
                journal.put("target/"+id,target);
                if(receipt.accepted()&&!receipt.deathId().isEmpty()&&journal.get("death/"+receipt.deathId()).isJsonNull()){
                    journal.put("death/"+receipt.deathId(),p.getUUID());
                    loot.death(p,entity,target);
                }
            }
            if(!heal&&entity!=p&&equipmentStats!=null){
                var actorStats=equipmentStats.apply(p);var effects=basicAttacks.sourceEffects(p,j);if(effects==null)effects=skills.sourceEffects(p,j);if(effects==null)effects=s.resources.equipment;
                var launched=basicAttacks.sourceStats(p,j);if(launched==null)launched=skills.sourceStats(p,j);if(launched==null)launched=actorStats;
                cn.yumis.fight.modules.combat.api.EquipmentHitEffects.apply(target,receipt,!j.has("effect")&&!j.has("reflected")&&skills.direct(p,j),launched,actorStats.get(cn.yumis.fight.modules.attributes.api.Stat.HEALTH),effects,actorLife,s.resources,hitMaximum,System.currentTimeMillis());
                journal.put("resources/"+p.getUUID(),s.resources);
            }
            if(entity!=p&&actorLife.revision!=s.life.revision)journal.put("life/"+p.getUUID(),actorLife);
            if(actorLife.dead){s.resources.equipmentBuffs.clear();journal.put("resources/"+p.getUUID(),s.resources);}
            ack.add("ownerLife",JSON.toJsonTree(actorLife));ack.add("equipmentBuffs",JSON.toJsonTree(s.resources.equipmentBuffs));ack.addProperty("serverNow",System.currentTimeMillis());
            JsonObject ailment=null;
            if(j.has("ailment")&&receipt.actual()>0)ailment=applyAilment(p,entity,j.getAsJsonObject("ailment"),target);
            skillCommit.persist();
            journal.put(key,stored);
            journal.finish();
            durable=true;
            skillCommit.publish();
            if(entity!=p&&actorLife.revision>=s.life.revision){s.life=actorLife;if(!s.life.dead)p.setHealth((float)Math.max(.001,p.getMaxHealth()*s.life.q));}
            if(!previousBuffs.equals(JSON.toJson(s.resources.equipmentBuffs)))equipmentProfile.accept(p);
            basicAttacks.committed(j);
            if(ailment!=null){
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("ailments",JSON.toJson(ailment)));
                if(ailment.get("kind").getAsString().equals("STUN")){
                    entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN,(int)Math.max(1,(ailment.get("expires").getAsLong()-System.currentTimeMillis()+49)/50)));
                    dungeons.cancelBoss(entity.getUUID());
                }
            }
            if(life.dead)dungeons.died(entity);
            if(entity==p)s.life=life;
            else entity.setData(cn.yumis.fight.modules.combat.api.CombatContentTypes.TARGET,JSON.toJson(target));
            nativeHits.suppressProjectionFeedback(nativeHit&&life.dead);
            projection.accept(true);
            try{
                if(life.dead&&!entity.isDeadOrDying())entity.hurtServer(p.level(),entity==p?entity.damageSources().genericKill():nativeHit?nativeHits.source(j):entity.damageSources().playerAttack(p),Float.MAX_VALUE);
                else if(!life.dead)entity.setHealth((float)Math.max(.001,entity.getMaxHealth()*life.q));
            }
            finally{
                projection.accept(false);
                nativeHits.suppressProjectionFeedback(false);
            }
            if(nativeHit)nativeHits.complete(j);
            Network.send(p,"ack",ack);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("health",JSON.toJson(life)));
            if(!heal&&!nativeHit&&(receipt.actual()>0||entity==p&&taken>0)){
                entity.hurtTime=10;
                entity.hurtDuration=10;
                LivingEntity feedbackAttacker=p;
                if(entity==p&&j.has("feedbackEntity")&&p.level().getEntity(UUID.fromString(j.get("feedbackEntity").getAsString())) instanceof LivingEntity origin&&managed.test(origin))feedbackAttacker=origin;
                var feedbackSource=entity==p?(feedbackAttacker==p?entity.damageSources().generic():entity.damageSources().mobAttack(feedbackAttacker)):entity.damageSources().playerAttack(p);
                p.level().broadcastDamageEvent(entity,feedbackSource);
                if(!j.has("effect")){
                    double dx=feedbackAttacker.getX()-entity.getX(),dz=feedbackAttacker.getZ()-entity.getZ();
                    entity.indicateDamage(dx,dz);
                    if(entity==p&&feedbackAttacker!=p){entity.hurtMarked=true;entity.knockback(.4,dx,dz,feedbackSource,0f);}
                }
                if(entity!=p&&j.has("knockback")){
                    JsonObject kb=j.getAsJsonObject("knockback");
                    double strength=kb.get("strength").getAsDouble();
                    finite(strength);
                    if(strength>=0){
                        double dx=p.getX()-entity.getX(),dz=p.getZ()-entity.getZ();
                        if(kb.has("inward")&&kb.get("inward").getAsBoolean()){
                            dx=entity.getX()-kb.get("centerX").getAsDouble();
                            dz=entity.getZ()-kb.get("centerZ").getAsDouble();
                        }
                        entity.knockback(strength,dx,dz,entity.damageSources().playerAttack(p),0f);
                    }
                }
            }
            loot.publish();
        }
        catch(Exception error){
            journal.abort();
            if(!durable){
                s.resources=previousResources;
                loot.abort();
            }
            else{
                p.connection.disconnect(cn.yumis.fight.modules.presentation.api.ModText.of("战斗结果已保存，投影应用失败，请重连："+error.getMessage()));
                return;
            }
            message.accept(p,error.getMessage());
            JsonObject ack=new JsonObject();
            ack.addProperty("session",s.id.toString());
            ack.add("operation",j.get("operation"));
            ack.add("receipt",JSON.toJsonTree(new Receipt(false,error.getMessage(),0,0,0,"")));
            Network.send(p,"ack",ack);
        }
    }
    public JsonObject validateDot(ServerPlayer p,JsonObject report,CombatTarget target){
        if(target==null)throw new IllegalArgumentException("持续伤害目标无效");
        String key="ailments/"+target.id()+"/"+p.getUUID()+"/BLEED";
        JsonObject effect=journal.get(key).isJsonObject()?journal.get(key).getAsJsonObject():null;
        CombatSession owner=sessions.apply(p);
        if(effect==null||owner.life.dead||!effect.get("session").getAsString().equals(owner.id.toString())||effect.get("ownerGeneration").getAsLong()!=owner.life.generation||effect.get("generation").getAsLong()!=target.life().generation||effect.get("basis").getAsLong()!=target.life().basis||effect.get("expires").getAsLong()<System.currentTimeMillis()||!effect.getAsJsonObject("change").get("effect").equals(report.get("effect"))||effect.get("nextIndex").getAsInt()!=report.get("index").getAsInt())throw new IllegalArgumentException("持续伤害生命周期或顺序已失效");
        return effect;
    }
    public JsonObject applyAilment(ServerPlayer p,LivingEntity entity,JsonObject change,CombatTarget target)throws Exception{
        if(target==null||target.life().dead)return null;
        String kind=change.get("kind").getAsString();
        if(!Set.of("BLEED","SHOCK","STUN").contains(kind))throw new IllegalArgumentException("未知异常类型");
        long ticks=change.get("durationTicks").getAsLong();
        if(ticks<=0)throw new IllegalArgumentException("异常持续时间无效");
        String key="ailments/"+entity.getUUID()+"/"+p.getUUID()+"/"+kind;
        JsonElement previous=journal.get(key);
        JsonObject state=new JsonObject();
        state.addProperty("target",entity.getStringUUID());
        state.addProperty("generation",target.life().generation);
        state.addProperty("basis",target.life().basis);
        state.addProperty("owner",p.getStringUUID());
        state.addProperty("ownerGeneration",sessions.apply(p).life.generation);
        state.addProperty("session",sessions.apply(p).id.toString());
        state.addProperty("kind",kind);
        state.addProperty("dimension",entity.level().dimension().identifier().toString());
        state.addProperty("instance",target.life().instance);
        long expires=Math.addExact(System.currentTimeMillis(),Math.multiplyExact(ticks,50));
        if(kind.equals("STUN")&&previous.isJsonObject())expires=Math.max(expires,previous.getAsJsonObject().get("expires").getAsLong());
        state.addProperty("expires",expires);
        state.add("change",change);
        state.addProperty("nextIndex",0);
        state.addProperty("nextAt",kind.equals("BLEED")&&previous.isJsonObject()?previous.getAsJsonObject().get("nextAt").getAsLong():System.currentTimeMillis()+1000);
        journal.put(key,state);
        return state;
    }
    public void expireAilments(){
        long now=System.currentTimeMillis();
        for(String key:journal.keys("ailments/")){
            JsonElement raw=journal.get(key);
            if(!raw.isJsonObject())continue;
            JsonObject j=raw.getAsJsonObject();
            ServerPlayer owner=server.getPlayerList().getPlayer(UUID.fromString(j.get("owner").getAsString()));
            LivingEntity entity=null;
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(j.get("dimension").getAsString())));
            if(level!=null&&level.getEntity(UUID.fromString(j.get("target").getAsString())) instanceof LivingEntity living)entity=living;
            boolean valid=owner!=null&&entity!=null&&entity.isAlive()&&j.get("expires").getAsLong()>now;
            if(valid){
                CombatSession source=sessions.apply(owner);
                CombatTarget target=targets.apply(entity);
                valid=!source.life.dead&&source.id.toString().equals(j.get("session").getAsString())&&source.life.generation==j.get("ownerGeneration").getAsLong()&&owner.level()==entity.level()&&target.life().generation==j.get("generation").getAsLong()&&target.life().basis==j.get("basis").getAsLong()&&source.life.instance.equals(j.get("instance").getAsString());
            }
            if(!valid)try{
                journal.put(key,null);
                if(entity!=null)net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("ailmentsClear",JSON.toJson(j)));
            }
            catch(Exception e){
                throw new IllegalStateException(e);
            }
        }
    }
}
