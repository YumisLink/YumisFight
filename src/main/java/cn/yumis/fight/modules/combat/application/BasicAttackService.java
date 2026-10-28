package cn.yumis.fight.modules.combat.application;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.combat.api.BasicAttackProfile;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** An attack cycle survives weapon switching. Each slash can damage a target only once. */
public class BasicAttackService implements cn.yumis.fight.modules.combat.api.CombatPorts.Attacks {
    private record Cycle(UUID player,String session,long generation,long start,double interval,int slashes,Set<String> hits,net.minecraft.world.phys.Vec3 origin,net.minecraft.world.phys.Vec3 direction,double range,double speed,cn.yumis.fight.modules.attributes.api.Stats stats,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects){}
    private java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.attributes.api.Stats> sourceStats;
    private java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot> sourceEffects;
    public BasicAttackService sources(java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.attributes.api.Stats> stats,java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot> effects){sourceStats=stats;sourceEffects=effects;return this;}
    public cn.yumis.fight.modules.attributes.api.Stats sourceStats(ServerPlayer player,JsonObject report){var c=report.has("basicCycle")?cycles.get(report.get("basicCycle").getAsString()):null;return c==null?null:c.stats;}
    public cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot sourceEffects(ServerPlayer player,JsonObject report){var c=report.has("basicCycle")?cycles.get(report.get("basicCycle").getAsString()):null;return c==null?null:c.effects;}
    private final Map<UUID,Double> next=new HashMap<>();
    private final Map<String,Cycle> cycles=new HashMap<>();
    private final java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.combat.api.CombatSession> sessions;
    private final java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.character.api.Progress> progress;
    private final java.util.function.Function<ServerPlayer,BasicAttackProfile> profile;
    public BasicAttackService(java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.combat.api.CombatSession> sessions,java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.character.api.Progress> progress,java.util.function.Function<ServerPlayer,BasicAttackProfile> profile){this.sessions=sessions;this.progress=progress;this.profile=profile;}
    public void start(ServerPlayer player,JsonObject j){
        var session=sessions.apply(player);long now=System.currentTimeMillis();
        String id=j.get("id").getAsString();
        if(cycles.containsKey(id))throw new IllegalArgumentException("普攻周期已使用");
        var gear=EquipmentItems.gear(player.getMainHandItem());
        if(gear==null||gear.slot!=Slot.WEAPON||!gear.valid(progress.apply(player),cn.yumis.fight.modules.equipment.api.EquipmentEligibility.allowance(player))||session.life.dead||player.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN))throw new IllegalArgumentException("当前不能普通攻击");
        double ready=next.getOrDefault(player.getUUID(),0d);
        if(now+50<ready)throw new IllegalArgumentException("普通攻击冷却中");
        var attack=profile.apply(player);double interval=attack.interval();
        next.put(player.getUUID(),Math.max(now,ready)+interval);
        int slashes=attack.slashes();
        cycles.put(id,new Cycle(player.getUUID(),session.id.toString(),session.life.generation,now,interval,slashes,new HashSet<>(),player.getEyePosition(),player.getLookAngle().normalize(),cn.yumis.fight.modules.equipment.api.WeaponTraits.basic(gear.weapon.name()).range(),cn.yumis.fight.modules.equipment.api.WeaponTraits.basic(gear.weapon.name()).projectileSpeed(),sourceStats==null?null:sourceStats.apply(player),sourceEffects==null?null:sourceEffects.apply(player)));
        cycles.entrySet().removeIf(e->now-e.getValue().start>30000);
    }
    public void validate(ServerPlayer player,JsonObject j){
        if(!j.has("basicCycle"))return;
        Cycle c=cycles.get(j.get("basicCycle").getAsString());var s=sessions.apply(player);
        int slash=j.get("basicSlash").getAsInt();long now=System.currentTimeMillis();
        if(c==null||!c.player.equals(player.getUUID())||!c.session.equals(s.id.toString())||c.generation!=s.life.generation||slash<0||slash>=c.slashes||now+50<c.start+slash*c.interval/2||now>c.start+c.interval+2000||c.hits.contains(slash+"/"+j.get("target").getAsString()))throw new IllegalArgumentException("普通攻击周期无效");
        if(c.speed>0){
            if(!j.has("projectilePoint")||!c.hits.isEmpty())throw new IllegalArgumentException("飞弹已消失或缺少碰撞位置");var pos=j.getAsJsonArray("projectilePoint");var point=new net.minecraft.world.phys.Vec3(pos.get(0).getAsDouble(),pos.get(1).getAsDouble(),pos.get(2).getAsDouble());double distance=c.origin.distanceTo(point);
            if(!Double.isFinite(distance)||distance>c.range+.3||distance>c.speed*(Math.max(0,j.get("occurredAt").getAsLong()-c.start+200)/1000d)+.5||point.subtract(c.origin).cross(c.direction).lengthSqr()>.5)throw new IllegalArgumentException("飞弹位置或飞行时间无效");
            var target=player.level().getEntity(java.util.UUID.fromString(j.get("target").getAsString()));if(target==null||!target.getBoundingBox().inflate(.6).contains(point))throw new IllegalArgumentException("飞弹目标位置已变化");
            var block=player.level().clip(new net.minecraft.world.level.ClipContext(c.origin,point,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player));if(block.getType()!=net.minecraft.world.phys.HitResult.Type.MISS&&c.origin.distanceTo(block.getLocation())<distance-.2)throw new IllegalArgumentException("飞弹被方块阻挡");
            for(var other:player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new net.minecraft.world.phys.AABB(c.origin,point).inflate(.2),entity->entity!=target&&(entity instanceof net.minecraft.world.entity.monster.Enemy||entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon))){var collision=other.getBoundingBox().inflate(.15).clip(c.origin,point);if(collision.isPresent()&&c.origin.distanceTo(collision.get())<distance-.2)throw new IllegalArgumentException("飞弹先命中其他敌人");}
        }

    }
    public void committed(JsonObject j){if(j.has("basicCycle")){Cycle c=cycles.get(j.get("basicCycle").getAsString());if(c!=null)c.hits.add(j.get("basicSlash").getAsInt()+"/"+j.get("target").getAsString());}}
}
