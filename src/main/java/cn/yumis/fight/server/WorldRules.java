package cn.yumis.fight.server;
import cn.yumis.fight.FightMod;
import cn.yumis.fight.game.GameItems;
import cn.yumis.fight.network.Network;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Models.JSON;
import com.google.gson.JsonObject;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.server.level.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
public final class WorldRules {
    private WorldRules(){
    }
    public static void tick(ServerRuntime runtime,int tick){
        for(ServerLevel level:runtime.server.getAllLevels())for(Entity raw:level.getAllEntities())if(raw instanceof LivingEntity entity&&runtime.managed(entity)&&!(entity instanceof ServerPlayer)){
            Target target=null;
            if(entity instanceof WitherBoss||entity instanceof EnderDragon)target=runtime.target(entity);
            else if(!runtime.journal.get("target/"+entity.getUUID()).isJsonNull())target=runtime.journal.get("target/"+entity.getUUID(),Target.class,null);
            if(target==null)continue;
            if(entity instanceof WitherBoss wither){
                if(wither.getInvulnerableTicks()>0&&tick%10==0)heal(runtime,entity,target,1/30d);
                else if(wither.getInvulnerableTicks()==0&&tick%20==0)heal(runtime,entity,target,1/300d);
            }
            if(entity instanceof EnderDragon dragon&&tick%10==0&&dragon.nearestCrystal!=null&&!dragon.nearestCrystal.isRemoved())heal(runtime,entity,target,.005);
            runtime.bridge=true;
            try{
                if(target.life.dead&&!entity.isDeadOrDying()){
                    UUID killer=runtime.journal.get("death/"+target.id+"/"+target.life.generation,UUID.class,null);
                    ServerPlayer player=killer==null?null:runtime.server.getPlayerList().getPlayer(killer);
                    entity.hurtServer(level,player==null?entity.damageSources().genericKill():entity.damageSources().playerAttack(player),Float.MAX_VALUE);
                }
                else if(!target.life.dead&&entity.isAlive())entity.setHealth((float)Math.max(.001,entity.getMaxHealth()*target.life.q));
            }
            finally{
                runtime.bridge=false;
            }
        }
    }
    public static void heal(ServerRuntime runtime,LivingEntity entity,Target target,double fraction){
        if(target.life.dead||target.life.q>=1||fraction<=0)return;
        target.life.q=Math.min(1,target.life.q+fraction);
        target.life.revision++;
        try{
            runtime.journal.begin();
            runtime.journal.put("life/"+entity.getUUID(),target.life);
            runtime.journal.put("target/"+entity.getUUID(),target);
            runtime.journal.finish();
            entity.setData(FightMod.TARGET,JSON.toJson(target));
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("health",JSON.toJson(target.life)));
        }
        catch(Exception e){
            runtime.journal.abort();
            throw new IllegalStateException(e);
        }
    }
    public static void nativeHeal(net.neoforged.neoforge.event.entity.living.LivingHealEvent event){
        if(!ServerRuntime.ready()||event.getEntity().level().isClientSide())return;
        var runtime=ServerRuntime.get();
        if(runtime.bridge||!runtime.managed(event.getEntity()))return;
        event.setCanceled(true);
        if(event.getEntity() instanceof WitherBoss wither&&event.getAmount()==5)heal(runtime,wither,runtime.target(wither),5/300d);
        if(event.getEntity() instanceof ServerPlayer player){
            JsonObject j=new JsonObject();
            j.addProperty("session",runtime.session(player).id.toString());
            j.addProperty("generation",runtime.session(player).life.generation);
            j.addProperty("points",event.getAmount());
            j.addProperty("root",UUID.randomUUID().toString());
            Network.send(player,"healing",j);
        }
    }
}
