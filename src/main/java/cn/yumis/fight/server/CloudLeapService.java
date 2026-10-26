package cn.yumis.fight.server;

import cn.yumis.fight.FightMod;
import cn.yumis.fight.mixin.ConnectionAccessor;
import cn.yumis.fight.network.Network;
import cn.yumis.fight.rules.Stamina;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Movement is authorized and charged exclusively on the server. */
public final class CloudLeapService {
    private final ServerRuntime runtime;
    private final Map<UUID,State> states=new HashMap<>();
    private static final class State {
        Stamina stamina=new Stamina();
        boolean uiOpen,exempt;
        int sent=-1;
        final Set<String> operations=new HashSet<>();
        final ArrayDeque<String> order=new ArrayDeque<>();
    }
    public CloudLeapService(ServerRuntime runtime){this.runtime=runtime;}
    private State state(ServerPlayer player){return states.computeIfAbsent(player.getUUID(),id->new State());}
    private boolean exempt(ServerPlayer player){return player.isCreative()||runtime.session(player).resources.training;}
    public void reset(ServerPlayer player){states.put(player.getUUID(),new State());player.removeEffect(FightMod.CLOUD_STEP);}
    public void logout(ServerPlayer player){states.remove(player.getUUID());}
    public void ui(ServerPlayer player,JsonObject input){
        if(valid(player,input))state(player).uiOpen=input.get("open").getAsBoolean();
    }
    private boolean valid(ServerPlayer player,JsonObject input){
        var session=runtime.session(player);
        return input.has("session")&&session.id.toString().equals(input.get("session").getAsString())
            &&input.has("generation")&&session.life.generation==input.get("generation").getAsLong();
    }
    public JsonObject snapshot(ServerPlayer player){
        var session=runtime.session(player);var state=state(player);var result=new JsonObject();
        result.addProperty("session",session.id.toString());result.addProperty("generation",session.life.generation);
        result.addProperty("value",state.stamina.value);result.addProperty("maximum",Stamina.MAXIMUM);
        result.addProperty("tick",runtime.server.getTickCount());result.addProperty("exempt",exempt(player));
        return result;
    }
    private void sync(ServerPlayer player){
        State state=state(player);state.sent=state.stamina.value;state.exempt=exempt(player);
        Network.send(player,"movement",snapshot(player));
    }
    public void tick(){
        long tick=runtime.server.getTickCount();
        for(ServerPlayer player:runtime.server.getPlayerList().getPlayers()){
            State state=state(player);boolean free=exempt(player);
            if(player.isAlive()&&!runtime.session(player).life.dead)state.stamina.recover(tick,player.onGround(),free);
            if(state.exempt!=free||tick%5==0&&state.sent!=state.stamina.value)sync(player);
        }
    }
    public void request(ServerPlayer player,JsonObject input){
        if(!valid(player,input))return;
        State state=state(player);var session=runtime.session(player);
        String operation=input.get("operation").getAsString();
        if(operation.length()>80)throw new IllegalArgumentException("无效移动操作");
        if(state.operations.contains(operation)){sync(player);return;}
        state.operations.add(operation);state.order.addLast(operation);
        if(state.order.size()>256)state.operations.remove(state.order.removeFirst());
        try{
            if(!input.has("dimension")||!player.level().dimension().identifier().toString().equals(input.get("dimension").getAsString()))return;
            if(!player.isAlive()||session.life.dead||player.isSpectator()||player.isPassenger()||player.isSleeping()||player.getAbilities().flying
                ||state.uiOpen||player.containerMenu!=player.inventoryMenu||runtime.inventory.locked(player.getUUID())
                ||player.hasEffect(FightMod.STUN)||runtime.visuals.movementLocked(player)
                ||((ConnectionAccessor)player.connection).ydnf$awaitingPosition()!=null)return;
            long tick=runtime.server.getTickCount();
            if(!state.stamina.ready(tick))return;
            if(!state.stamina.use(tick,exempt(player)))throw new IllegalArgumentException("体力不足（需要20）");
            boolean vertical=input.has("vertical")&&input.get("vertical").getAsBoolean();
            double yaw=Math.toRadians(player.getYRot());
            Vec3 velocity=vertical?new Vec3(0,1.1,0):new Vec3(-Math.sin(yaw)*1.08,.49,Math.cos(yaw)*1.08);
            player.stopFallFlying();player.setDeltaMovement(velocity);player.setOnGround(false);player.resetFallDistance();
            player.hurtMarked=true;player.connection.resetFlyingTicks();
            player.addEffect(new MobEffectInstance(FightMod.CLOUD_STEP,Stamina.PROTECTION,0,false,false,true));
        }
        finally{sync(player);}
    }
}
