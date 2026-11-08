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
    private java.util.function.Consumer<ServerPlayer> equipmentProfile=p->{};
    public void equipment(java.util.function.Consumer<ServerPlayer> profile){equipmentProfile=java.util.Objects.requireNonNull(profile);}
    private CombatPorts.SkillEffects skills=new CombatPorts.SkillEffects(){};
    public void skills(CombatPorts.SkillEffects value){skills=java.util.Objects.requireNonNull(value);}
    private CombatPorts.Training training;
    public void training(CombatPorts.Training value){training=java.util.Objects.requireNonNull(value);}
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
    private final CombatPorts.Recorder damageMeter;
    private final Consumer<Boolean> projection;
    private final BiConsumer<ServerPlayer,String> message;
    public CombatApplication(Journal journal,MinecraftServer server,Function<ServerPlayer,? extends CombatSession> sessions,Predicate<Entity> managed,Function<LivingEntity,? extends CombatTarget> targets,CombatPorts.Encounters dungeons,CombatPorts.Rewards loot,CombatPorts.NativeHits nativeHits,CombatPorts.Recorder damageMeter,Consumer<Boolean> projection,BiConsumer<ServerPlayer,String> message){this.journal=journal;this.server=server;this.sessions=sessions;this.managed=managed;this.targets=targets;this.dungeons=dungeons;this.loot=loot;this.nativeHits=nativeHits;this.damageMeter=damageMeter;this.projection=projection;this.message=message;}
    public boolean runtime(ServerPlayer p,JsonObject j){
        CombatSession s=sessions.apply(p);
        if(j.has("session")&&!s.id.toString().equals(j.get("session").getAsString()))return false;
        long generation=j.has("generation")?j.get("generation").getAsLong():s.life.generation;
        if(generation!=s.life.generation)return false;
        cn.yumis.fight.modules.combat.api.Runtime r=JSON.fromJson(j.has("state")?j.get("state"):j,cn.yumis.fight.modules.combat.api.Runtime.class);
        r.validate();
        long issued=j.has("serverIssuedAt")?j.get("serverIssuedAt").getAsLong():r.recordedAt;
        if(r.controlEpoch!=s.resources.controlEpoch)return false;
        if(r.sequence<=s.resources.sequence)return false;
        long local=r.recordedAt;
        // These fields belong to the administrator command, never to combat prediction uploads.
        r.training=s.resources.training;
        r.trainingBoundary=s.resources.trainingBoundary;
        r.combatUntil=issued+r.combatUntil-r.recordedAt;
        r.shieldRecoveryAt=issued+r.shieldRecoveryAt-r.recordedAt;
        r.cooldowns.replaceAll((k,v)->issued+Math.max(0,v-local));
        r.buffs.replaceAll((k,v)->issued+Math.max(0,v-local));
        r.absorptionUntil.replaceAll((k,v)->v==Long.MAX_VALUE?v:issued+Math.max(0,v-local));
        for(var buff:r.equipmentBuffs.values())buff.expires=issued+Math.max(0,buff.expires-local);
        r.recordedAt=issued;
        r.equipment=s.resources.equipment;
        try{
            journal.put("resources/"+p.getUUID(),r);
            s.resources=r;
            return true;
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
            boolean heal=action.equals("heal"),outcomeOnly=action.equals("nativeOutcome"),resourceCost=j.has("resourceCost")&&j.get("resourceCost").getAsBoolean();
            if(j.has("heal")&&j.get("heal").getAsBoolean()!=heal)throw new IllegalArgumentException("伤害类型不一致");
            UUID id=UUID.fromString(j.get("target").getAsString());
            LivingEntity entity=id.equals(p.getUUID())?p:(LivingEntity)p.level().getEntity(id);
            if(entity==null||!managed.test(entity))throw new IllegalArgumentException("目标不存在");
            if(entity instanceof Player&&entity!=p)throw new IllegalArgumentException("PVP已关闭");
            if(heal&&entity!=p)throw new IllegalArgumentException("仅支持本人恢复");
            CombatTarget target=entity==p?null:targets.apply(entity);
            Life life=entity==p?copy(s.life,Life.class):target.life();
            if(!j.has("dimension")||!j.get("dimension").getAsString().equals(p.level().dimension().identifier().toString()))throw new IllegalArgumentException("维度已改变");
            if(j.get("generation").getAsLong()!=life.generation||j.get("basis").getAsLong()!=life.basis)throw new IllegalArgumentException("生命或扩血基准已失效");
            String instance=j.has("instance")?j.get("instance").getAsString():"";
            if(!instance.equals(life.instance)||!instance.isEmpty()&&!dungeons.participant(p,instance))throw new IllegalArgumentException("副本轮次已失效");
            boolean nativeHit=j.has("nativeRoot");
            if(heal&&nativeHit)throw new IllegalArgumentException("原生命中不能用于治疗");
            JsonObject dot=null;
            if(j.has("effect")){
                String kind=j.has("effectKind")?j.get("effectKind").getAsString():"BLEED";
                String effectKey="ailments/"+life.target+"/"+p.getUUID()+"/"+kind+(ElementAccumulation.kind(kind)==null?"":"/"+j.get("effect").getAsString());
                JsonElement storedEffect=journal.get(effectKey);if(storedEffect.isJsonObject())dot=storedEffect.getAsJsonObject();
            }
            double fraction=j.get("fraction").getAsDouble();
            double[] components=damageComponents(j,fraction);
            String outcome=j.has("outcome")?j.get("outcome").getAsString():"HIT";
            if(!Set.of("HIT","DODGE","IMMUNE").contains(outcome)||!outcome.equals("HIT")&&fraction!=0)throw new IllegalArgumentException("命中结果无效");
            double taken=j.has("damageTaken")?j.get("damageTaken").getAsDouble():0;
            finite(taken);
            if(taken<0||!outcome.equals("HIT")&&taken!=0)throw new IllegalArgumentException("承伤结果无效");
            if(outcomeOnly){
                if(heal||entity!=p||fraction!=0)throw new IllegalArgumentException("零伤害结果格式无效");
            }
            long eventTime=j.has("occurredAt")?Math.min(System.currentTimeMillis(),j.get("occurredAt").getAsLong()):System.currentTimeMillis();
            if(target!=null&&target.training()){
                if(training==null||heal)throw new IllegalArgumentException("训练会话不可用");
                training.validate(p,target,eventTime);
            }
            Receipt receipt=outcomeOnly?cn.yumis.fight.modules.combat.api.LifeRules.noDamageReceipt(life):target!=null?target.receive(fraction,heal):cn.yumis.fight.modules.combat.api.LifeRules.apply(life,fraction,heal);
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
            if(j.has("runtime"))ack.addProperty("runtimeApplied",runtime(p,j.getAsJsonObject("runtime")));
            ack.addProperty("runtimeRecordedAt",s.resources.recordedAt);
            if(!heal&&!resourceCost&&(receipt.actual()>0||entity==p&&outcome.equals("HIT")&&taken>0)){
                ack.addProperty("combatDelayMillis",j.has("combatDelayMillis")?j.get("combatDelayMillis").getAsLong():0);
                journal.put("resources/"+p.getUUID(),s.resources);
                if(target!=null&&target.training())training.record(p,target,receipt.actual(),eventTime);
                else damageMeter.record(p,target,receipt.actual(),eventTime);
            }
            ack.addProperty("combatUntil",s.resources.combatUntil);
            ack.addProperty("shieldRecoveryAt",s.resources.shieldRecoveryAt);
            if(dot!=null){
                dot.addProperty("nextIndex",dot.get("nextIndex").getAsInt()+1);
                dot.addProperty("nextAt",dot.has("settleUntil")?dot.get("nextAt").getAsLong()+1000:System.currentTimeMillis()+1000);
                journal.put(ailmentKey(dot),dot);
            }
            if(nativeHit)nativeHits.commit(p,j);
            var elementalAilments=new ArrayList<JsonObject>();
            if(entity==p){
                if(j.has("elemental"))life.elemental=JSON.fromJson(j.get("elemental"),ElementAccumulation.State.class);
                if(j.has("elementalAilments"))for(JsonElement value:j.getAsJsonArray("elementalAilments")){var state=value.getAsJsonObject().deepCopy();if(!state.get("target").getAsString().equals(p.getStringUUID())||state.get("generation").getAsLong()!=life.generation)throw new IllegalArgumentException("异常状态归属无效");journal.put(ailmentKey(state),state);elementalAilments.add(state);}
            }else if(!heal&&receipt.actual()>0&&!life.dead&&fraction>0&&j.has("damageComponents")){
                var source=j.has("sourceStats")?JSON.fromJson(j.get("sourceStats"),cn.yumis.fight.modules.attributes.api.Stats.class):null;
                var effects=j.has("sourceEffects")?JSON.fromJson(j.get("sourceEffects"),cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot.class):null;
                if(dot!=null&&dot.has("settleUntil")){source=JSON.fromJson(dot.getAsJsonObject("change").get("stats"),cn.yumis.fight.modules.attributes.api.Stats.class);effects=JSON.fromJson(dot.getAsJsonObject("change").get("equipment"),cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot.class);}
                long now=System.currentTimeMillis();var accumulated=ElementAccumulation.advance(life.elemental,life,components,receipt.actual()/fraction,source,target.bossOrElite(),now);life.elemental=accumulated.state();
                for(var kind:accumulated.triggered())elementalAilments.add(applyElementalAilment(p,entity,life,kind,target.bossOrElite(),source,effects,now));
            }
            ack.add("elemental",JSON.toJsonTree(life.elemental));
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
            if(entity!=p&&j.has("ownerHealing")){double healing=j.get("ownerHealing").getAsDouble();finite(healing);if(healing<0)throw new IllegalArgumentException("恢复份额不可为负");if(healing>0)cn.yumis.fight.modules.combat.api.LifeRules.apply(actorLife,healing,true);}
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
                if(life.dead&&!entity.isDeadOrDying())entity.hurtServer(p.level(),entity==p?entity.damageSources().genericKill():nativeHit&&nativeHits.source(j)!=null?nativeHits.source(j):entity.damageSources().playerAttack(p),Float.MAX_VALUE);
                else if(!life.dead)entity.setHealth((float)Math.max(.001,entity.getMaxHealth()*life.q));
            }
            finally{
                projection.accept(false);
                nativeHits.suppressProjectionFeedback(false);
            }
            if(nativeHit)nativeHits.complete(j);
            Network.send(p,"ack",ack);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("health",JSON.toJson(life)));
            for(var elemental:elementalAilments){
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("ailments",JSON.toJson(elemental)));
                var kind=ElementAccumulation.kind(elemental.get("kind").getAsString());
                entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(CombatContentTypes.elementalEffect(kind),(int)Math.max(1,(elemental.get("expires").getAsLong()-System.currentTimeMillis()+49)/50)));
                if(kind==ElementAccumulation.Kind.FREEZE)dungeons.cancelBoss(entity.getUUID());
            }
            if(!heal&&!resourceCost&&!nativeHit&&(receipt.actual()>0||entity==p&&taken>0)){
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
            if(j.has("effectKind")&&ElementAccumulation.kind(j.get("effectKind").getAsString())!=null)try{
                String effectKey="ailments/"+UUID.fromString(j.get("target").getAsString())+"/"+p.getUUID()+"/"+j.get("effectKind").getAsString()+"/"+UUID.fromString(j.get("effect").getAsString());
                JsonElement effect=journal.get(effectKey);if(effect.isJsonObject())Network.send(p,"ailments",effect);
            }catch(IllegalArgumentException ignored){}
        }
    }
    private static double[] damageComponents(JsonObject report,double fraction){
        double[] values=new double[5];if(!report.has("damageComponents"))return values;
        JsonArray raw=report.getAsJsonArray("damageComponents");if(raw.size()!=5)throw new IllegalArgumentException("元素伤害分量数量无效");
        double total=0;for(int i=0;i<5;i++){values[i]=raw.get(i).getAsDouble();finite(values[i]);if(values[i]<0)throw new IllegalArgumentException("元素伤害分量不可为负");total+=values[i];}
        finite(total);finite(fraction);if(Math.abs(total-fraction)>Math.max(1e-10,Math.ulp(Math.max(total,fraction))*16))throw new IllegalArgumentException("元素伤害分量与伤害份额不一致");return values;
    }
    private static String ailmentKey(JsonObject effect){
        String key="ailments/"+effect.get("target").getAsString()+"/"+effect.get("owner").getAsString()+"/"+effect.get("kind").getAsString();
        return effect.has("settleUntil")?key+"/"+effect.getAsJsonObject("change").get("effect").getAsString():key;
    }
    private JsonObject applyElementalAilment(ServerPlayer p,LivingEntity entity,Life life,ElementAccumulation.Kind kind,boolean strong,cn.yumis.fight.modules.attributes.api.Stats stats,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot equipment,long now)throws Exception{
        JsonObject state=new JsonObject(),change=new JsonObject();
        change.addProperty("kind",kind.id);change.addProperty("effect",UUID.randomUUID().toString());change.addProperty("strong",strong);
        if(kind.damaging()){
            change.add("stats",JSON.toJsonTree(stats==null?new cn.yumis.fight.modules.attributes.api.Stats():stats));
            change.add("equipment",JSON.toJsonTree(equipment==null?new cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot():equipment));
        }
        state.addProperty("target",entity.getStringUUID());state.addProperty("generation",life.generation);state.addProperty("basis",life.basis);
        state.addProperty("owner",p.getStringUUID());state.addProperty("ownerGeneration",sessions.apply(p).life.generation);state.addProperty("session",sessions.apply(p).id.toString());
        state.addProperty("kind",kind.id);state.addProperty("dimension",life.dimension);state.addProperty("instance",life.instance);
        state.addProperty("expires",life.elemental.until[kind.ordinal()]);state.add("change",change);
        state.addProperty("settleUntil",life.elemental.until[kind.ordinal()]+(kind.damaging()?ElementAccumulation.SETTLEMENT_GRACE:0));
        state.addProperty("nextIndex",0);state.addProperty("nextAt",now+(kind==ElementAccumulation.Kind.IGNITE?0:1000));
        journal.put(ailmentKey(state),state);return state;
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
            var elemental=ElementAccumulation.kind(j.get("kind").getAsString());
            if(entity instanceof ServerPlayer){if((j.has("settleUntil")?j.get("settleUntil"):j.get("expires")).getAsLong()<=now)try{journal.put(key,null);}catch(java.io.IOException failure){throw new IllegalStateException(failure);}continue;}
            CombatTarget target=entity==null||entity instanceof Player?null:targets.apply(entity);
            Life life=entity instanceof ServerPlayer player?sessions.apply(player).life:target==null?null:target.life();
            if(elemental!=null&&life!=null&&life.elemental.matches(life)&&life.elemental.until[elemental.ordinal()]==j.get("expires").getAsLong()&&j.get("expires").getAsLong()<=now)entity.removeEffect(CombatContentTypes.elementalEffect(elemental));
            boolean valid=owner!=null&&entity!=null&&entity.isAlive()&&life!=null&&!life.dead&&(j.has("settleUntil")?j.get("settleUntil"):j.get("expires")).getAsLong()>now;
            if(valid){
                CombatSession source=sessions.apply(owner);
                valid=!source.life.dead&&source.id.toString().equals(j.get("session").getAsString())&&source.life.generation==j.get("ownerGeneration").getAsLong()&&owner.level()==entity.level()&&life.generation==j.get("generation").getAsLong()&&(elemental!=null||life.basis==j.get("basis").getAsLong())&&source.life.instance.equals(j.get("instance").getAsString())&&life.instance.equals(j.get("instance").getAsString());
            }
            if(!valid)try{
                boolean released=elemental!=null&&life!=null&&life.generation==j.get("generation").getAsLong()&&life.elemental.until[elemental.ordinal()]==j.get("expires").getAsLong()&&life.elemental.until[elemental.ordinal()]>now;
                Life next=released?copy(life,Life.class):life;
                journal.begin();
                if(released){next.elemental.until[elemental.ordinal()]=now;next.revision++;journal.put("life/"+next.target,next);if(target!=null){target.life(next);journal.put("target/"+target.id(),target);}}
                journal.put(key,null);
                journal.finish();
                if(released){
                    if(entity instanceof ServerPlayer player)sessions.apply(player).life=next;
                    else entity.setData(CombatContentTypes.TARGET,JSON.toJson(target));
                    entity.removeEffect(CombatContentTypes.elementalEffect(elemental));
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("health",JSON.toJson(next)));
                }
                if(entity!=null)net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("ailmentsClear",JSON.toJson(j)));
            }
            catch(Exception e){
                journal.abort();
                throw new IllegalStateException(e);
            }
        }
    }
}
