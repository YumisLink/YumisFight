package cn.yumis.fight.modules.dungeons.application;


import cn.yumis.fight.network.Network;
import cn.yumis.fight.modules.dungeons.api.Target;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
public final class WorldHealthService {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    private final net.minecraft.server.MinecraftServer server;
    private final cn.yumis.fight.foundation.Journal journal;
    private final java.util.function.Function<LivingEntity,Target> targets;
    private final java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.combat.api.CombatSession> sessions;
    private final java.util.function.BooleanSupplier bridging;
    private final java.util.function.Consumer<Boolean> bridge;
    public WorldHealthService(net.minecraft.server.MinecraftServer server,cn.yumis.fight.foundation.Journal journal,java.util.function.Function<LivingEntity,Target> targets,java.util.function.Function<ServerPlayer,cn.yumis.fight.modules.combat.api.CombatSession> sessions,java.util.function.BooleanSupplier bridging,java.util.function.Consumer<Boolean> bridge){this.server=server;this.journal=journal;this.targets=targets;this.sessions=sessions;this.bridging=bridging;this.bridge=bridge;}

    public void tick(int tick){
        for(ServerLevel level:server.getAllLevels())for(Entity raw:level.getAllEntities())if(raw instanceof LivingEntity entity&&cn.yumis.fight.modules.combat.api.ManagedEntities.includes(entity)&&!(entity instanceof ServerPlayer)){
            Target target=null;
            if(entity instanceof WitherBoss||entity instanceof EnderDragon)target=targets.apply(entity);
            else if(!journal.get("target/"+entity.getUUID()).isJsonNull())target=journal.get("target/"+entity.getUUID(),Target.class,null);
            if(target==null)continue;
            if(entity instanceof WitherBoss wither){
                if(wither.getInvulnerableTicks()>0&&tick%10==0)heal(entity,target,1/30d);
                else if(wither.getInvulnerableTicks()==0&&tick%20==0)heal(entity,target,1/300d);
            }
            if(entity instanceof EnderDragon dragon&&tick%10==0&&dragon.nearestCrystal!=null&&!dragon.nearestCrystal.isRemoved())heal(entity,target,.005);
            bridge.accept(true);
            try{
                if(target.life.dead&&!entity.isDeadOrDying()){
                    UUID killer=journal.get("death/"+target.id+"/"+target.life.generation,UUID.class,null);
                    ServerPlayer player=killer==null?null:server.getPlayerList().getPlayer(killer);
                    entity.hurtServer(level,player==null?entity.damageSources().genericKill():entity.damageSources().playerAttack(player),Float.MAX_VALUE);
                }
                else if(!target.life.dead&&entity.isAlive())entity.setHealth((float)Math.max(.001,entity.getMaxHealth()*target.life.q));
            }
            finally{
                bridge.accept(false);
            }
        }
    }
    public void heal(LivingEntity entity,Target target,double fraction){
        if(target.life.dead||target.life.q>=1||fraction<=0)return;
        target.life.q=Math.min(1,target.life.q+fraction);
        target.life.revision++;
        try{
            journal.begin();
            journal.put("life/"+entity.getUUID(),target.life);
            journal.put("target/"+entity.getUUID(),target);
            journal.finish();
            entity.setData(cn.yumis.fight.modules.combat.api.CombatContentTypes.TARGET,JSON.toJson(target));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("health",JSON.toJson(target.life)));
        }
        catch(Exception e){
            journal.abort();
            throw new IllegalStateException(e);
        }
    }
    public void nativeHeal(net.neoforged.neoforge.event.entity.living.LivingHealEvent event){
        if(event.getEntity().level().isClientSide())return;
        if(bridging.getAsBoolean()||!cn.yumis.fight.modules.combat.api.ManagedEntities.includes(event.getEntity()))return;
        event.setCanceled(true);
        if(event.getEntity() instanceof WitherBoss wither&&event.getAmount()==5)heal(wither,targets.apply(wither),5/300d);
        if(event.getEntity() instanceof ServerPlayer player){
            JsonObject j=new JsonObject();
            j.addProperty("session",sessions.apply(player).id.toString());
            j.addProperty("generation",sessions.apply(player).life.generation);
            j.addProperty("points",event.getAmount());
            j.addProperty("root",UUID.randomUUID().toString());
            Network.send(player,"healing",j);
        }
    }
}
