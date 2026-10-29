package cn.yumis.fight.modules.skills.application;
import cn.yumis.fight.architecture.CommandRouter;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.equipment.api.*;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.function.*;
/** Ordinary and equipment skills prepare payment and lifecycle once, then publish the accepted cast. */
public final class SkillStartService implements CombatPorts.SkillEffects {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}
    public record Accepted(String session,long generation,String dimension,long expires,Stats stats,EquipmentEffects.Snapshot effects,String skill){}
    @FunctionalInterface public interface Action {Vec3 prepare(ServerPlayer player);}
    private static final Map<String,Action> ACTIONS=new LinkedHashMap<>();
    static {ACTIONS.put(EquipmentEffects.BLINK,SkillStartService::blink);}
    private final Journal journal;private final Function<ServerPlayer,CombatSession> sessions;private final Function<ServerPlayer,Progress> progress;
    private final Function<ServerPlayer,Stats> stats;private final BiFunction<ServerPlayer,cn.yumis.fight.modules.combat.api.Runtime,Stats> castStats;private final BiConsumer<ServerPlayer,SkillDefinition> success;
    private final Predicate<UUID> locked;private final Map<UUID,Map<String,Accepted>> casts=new HashMap<>();
    private BiConsumer<ServerPlayer,SkillDefinition> preflight=(p,s)->{};
    private BiConsumer<ServerPlayer,JsonObject> accepted=(p,j)->{};
    private BiConsumer<ServerPlayer,String> cancelled=(p,id)->{};
    public void lifecycle(BiConsumer<ServerPlayer,SkillDefinition> before,BiConsumer<ServerPlayer,JsonObject> after,BiConsumer<ServerPlayer,String> aborted){preflight=Objects.requireNonNull(before);accepted=Objects.requireNonNull(after);cancelled=Objects.requireNonNull(aborted);}
    public SkillStartService(Journal journal,Function<ServerPlayer,CombatSession> sessions,Function<ServerPlayer,Progress> progress,Function<ServerPlayer,Stats> stats,BiFunction<ServerPlayer,cn.yumis.fight.modules.combat.api.Runtime,Stats> castStats,BiConsumer<ServerPlayer,SkillDefinition> success,Predicate<UUID> locked){this.journal=journal;this.sessions=sessions;this.progress=progress;this.stats=stats;this.castStats=castStats;this.success=success;this.locked=locked;}
    public void register(CommandRouter<ServerPlayer,JsonObject> router){router.register("skills","skillCast",this::start);}
    public Accepted source(ServerPlayer p,String id){return casts.getOrDefault(p.getUUID(),Map.of()).get(id);}
    @Override public Stats sourceStats(ServerPlayer p,JsonObject report){var accepted=report.has("sourceCast")?source(p,report.get("sourceCast").getAsString()):null;return accepted==null?null:accepted.stats;}
    @Override public EquipmentEffects.Snapshot sourceEffects(ServerPlayer p,JsonObject report){var accepted=report.has("sourceCast")?source(p,report.get("sourceCast").getAsString()):null;return accepted==null?null:accepted.effects;}
    public void start(ServerPlayer p,JsonObject request){
        CombatSession session=sessions.apply(p);String id=request.get("id").getAsString();UUID.fromString(id);String key="skillStart/"+p.getUUID()+"/"+session.id+"/"+id;
        JsonElement previous=journal.get(key);if(!previous.isJsonNull()){Network.send(p,previous.getAsJsonObject().has("error")?"careerRejected":"careerStarted",previous);return;}
        boolean paymentWritten=false;
        try{
            if(!session.id.toString().equals(request.get("session").getAsString())||session.life.generation!=request.get("generation").getAsLong()||session.life.dead||p.isDeadOrDying()||p.hasEffect(CombatContentTypes.STUN)||locked.test(p.getUUID()))throw new IllegalArgumentException("当前不能施放技能");
            SkillDefinition skill=SkillCatalog.require(request.get("skill").getAsString());if(skill.passive()||CareerSkills.contains(skill.id))throw new IllegalArgumentException("无效的技能入口");
            Progress player=progress.apply(p);Stats equipment=stats.apply(p);EquipmentEffects.Snapshot effects=session.resources.equipment;int rank=SkillLearning.effective(equipment,player,skill),slot=request.get("slot").getAsInt();
            if(rank<=0||slot<0||slot>=player.slots.length||!player.slots[slot].equals(skill.id))throw new IllegalArgumentException("技能来源或绑定已改变");
            if(!skill.weaponAllowed(player,equipment))throw new IllegalArgumentException("主手需要本职业可用武器");
            Gear hand=EquipmentItems.gear(p.getMainHandItem());if(!skill.equipmentGranted&&(hand==null||hand.slot!=Slot.WEAPON||!hand.valid(player,EquipmentEligibility.allowance(p))))throw new IllegalArgumentException("需要可用武器");
            long now=System.currentTimeMillis();if(!session.resources.training&&session.resources.cooldowns.getOrDefault(skill.id,0L)>now)throw new IllegalArgumentException("技能冷却中");
            Action action=ACTIONS.get(skill.id);Vec3 destination=action==null?null:action.prepare(p);
            preflight.accept(p,skill);
            var resources=copy(session.resources,cn.yumis.fight.modules.combat.api.Runtime.class);resources.equipment=effects;Life life=copy(session.life,Life.class);
            EquipmentBuffs.prune(resources,effects,now,false);EquipmentBuffs.imprint(resources,effects,now);
            Stats resolved=CareerRules.eyeDamage(castStats.apply(p,resources),skill);var cost=SkillCosts.resolve(skill,rank,resolved,effects,CareerRules.magic(skill,player));SkillCosts.pay(cost,resources,life,resolved.get(Stat.HEALTH),0);
            if(!resources.training)resources.cooldowns.put(skill.id,now+(long)(cost.cooldown()*1000));resources.controlEpoch++;resources.recordedAt=now;
            var parameters=WeaponSkills.skill(SkillParameters.forCast(p.getUUID(),skill,rank,resolved.parameters),player,resolved);
            JsonObject ack=new JsonObject();ack.addProperty("id",id);ack.addProperty("skill",skill.id);ack.addProperty("slot",slot);ack.addProperty("serverNow",now);ack.addProperty("session",session.id.toString());ack.addProperty("generation",life.generation);ack.addProperty("rank",rank);ack.addProperty("terminal",false);ack.addProperty("instant",action!=null||MageSkills.contains(skill.id));
            ack.add("parameters",JSON.toJsonTree(parameters.values()));ack.add("stats",JSON.toJsonTree(resolved));ack.add("equipmentEffects",JSON.toJsonTree(effects));ack.add("resources",JSON.toJsonTree(resources));ack.add("life",JSON.toJsonTree(life));
            Vec3 origin=MageSkills.contains(skill.id)?p.getEyePosition():p.position(),direction=MageSkills.contains(skill.id)?p.getLookAngle().normalize():new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize();ack.add("origin",JSON.toJsonTree(new double[]{origin.x,origin.y,origin.z}));ack.add("center",JSON.toJsonTree(new double[]{origin.x,origin.y,origin.z}));ack.add("direction",JSON.toJsonTree(new double[]{direction.x,direction.y,direction.z}));
            var originalResources=session.resources;var originalLife=session.life;
            journal.begin();journal.put("resources/"+p.getUUID(),resources);journal.put("life/"+p.getUUID(),life);journal.put(key,ack);journal.finish();paymentWritten=true;session.resources=resources;session.life=life;
            casts.computeIfAbsent(p.getUUID(),k->new LinkedHashMap<>()).put(id,new Accepted(session.id.toString(),life.generation,p.level().dimension().identifier().toString(),now+20000,resolved,effects,skill.id));
            casts.get(p.getUUID()).entrySet().removeIf(entry->entry.getValue().expires<now);
            if(destination!=null){p.teleportTo(p.level(),destination.x,destination.y,destination.z,Set.of(),p.getYRot(),p.getXRot(),true);p.fallDistance=0;}
            if(cost.life()>0){p.setHealth((float)Math.max(1,p.getMaxHealth()*life.q));Network.send(p,"health",life);}
            try{accepted.accept(p,ack);}catch(RuntimeException failure){
                cancelled.accept(p,id);casts.get(p.getUUID()).remove(id);var rejection=new JsonObject();rejection.addProperty("id",id);rejection.addProperty("error",Objects.toString(failure.getMessage(),"施放失败"));
                journal.transaction(Map.of("resources/"+p.getUUID(),JSON.toJsonTree(originalResources),"life/"+p.getUUID(),JSON.toJsonTree(originalLife),key,rejection));session.resources=originalResources;session.life=originalLife;
                if(cost.life()>0){p.setHealth((float)Math.max(1,p.getMaxHealth()*originalLife.q));Network.send(p,"health",originalLife);}Network.send(p,"careerRejected",rejection);return;
            }
            success.accept(p,skill);Network.send(p,"careerStarted",ack);
        }catch(Exception error){journal.abort();if(!paymentWritten)cancelled.accept(p,id);JsonObject rejected=new JsonObject();rejected.addProperty("id",id);rejected.addProperty("error",Objects.toString(error.getMessage(),"施放失败"));Network.send(p,"careerRejected",rejected);}
    }
    @Override public void validate(ServerPlayer p,LivingEntity target,JsonObject report){if(!report.has("sourceCast"))return;Accepted accepted=source(p,report.get("sourceCast").getAsString());var session=sessions.apply(p);if(accepted==null||MageSkills.contains(accepted.skill)||!accepted.session.equals(session.id.toString())||accepted.generation!=session.life.generation||!accepted.dimension.equals(p.level().dimension().identifier().toString())||accepted.expires<System.currentTimeMillis())throw new IllegalArgumentException("技能起手快照已失效或缺少动作凭据");}
    @Override public void reconcile(ServerPlayer p,cn.yumis.fight.modules.combat.api.Runtime previous,cn.yumis.fight.modules.combat.api.Runtime incoming){for(var skill:SkillCatalog.all())if(!CareerSkills.contains(skill.id)){long until=previous.cooldowns.getOrDefault(skill.id,0L);if(until>incoming.cooldowns.getOrDefault(skill.id,0L))incoming.cooldowns.put(skill.id,until);}}
    private static Vec3 blink(ServerPlayer p){
        Vec3 origin=p.position(),direction=new Vec3(p.getLookAngle().x,0,p.getLookAngle().z).normalize();if(direction.lengthSqr()<.001)throw new IllegalArgumentException("没有有效前进方向");
        double limit=5;
        for(double height: new double[]{.1,p.getBbHeight()*.5,p.getEyeHeight()}){Vec3 start=origin.add(0,height,0);var obstruction=p.level().clip(new ClipContext(start,start.add(direction.scale(5)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(obstruction.getType()!=HitResult.Type.MISS)limit=Math.min(limit,Math.max(0,start.distanceTo(obstruction.getLocation())-p.getBbWidth()*.5-.02));}
        for(double distance=limit;distance>=.25;distance-=.25){Vec3 desired=origin.add(direction.scale(distance));var floor=p.level().clip(new ClipContext(desired.add(0,1,0),desired.add(0,-4.99,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(floor.getType()==HitResult.Type.MISS)continue;Vec3 point=floor.getLocation().add(0,.001,0);BlockPos pos=BlockPos.containing(point);
            if(origin.distanceToSqr(point)>25||!p.level().hasChunkAt(pos)||!p.level().getWorldBorder().isWithinBounds(pos))continue;AABB box=p.getBoundingBox().move(point.subtract(origin));
            if(p.level().noCollision(p,box)&&!p.level().containsAnyLiquid(box)&&p.level().getBlockState(BlockPos.containing(point.add(0,-.02,0))).blocksMotion())return point;
        }
        throw new IllegalArgumentException("前方5格内没有可落地位置");
    }
}
