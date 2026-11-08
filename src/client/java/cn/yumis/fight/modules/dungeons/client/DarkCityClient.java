package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.combat.api.CombatContentTypes;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.*;

/** Only the local participant decides contacts. The injected hit port owns their damage calculation. */
public final class DarkCityClient {
    private static final Gson JSON=new Gson();
    private static final class Contact {
        final GblAttack attack;final String group;final Target source;long previous;
        Contact(GblAttack attack,String group,Target source,long now){this.attack=attack;this.group=group;this.source=source;previous=Math.max(attack.release(),now);}
    }
    private static final Map<String,Contact> contacts=new LinkedHashMap<>();
    private static final Map<String,Long> closed=new LinkedHashMap<>(),struck=new LinkedHashMap<>();
    private static Supplier<Life> own=Life::new;
    private static Consumer<JsonObject> hit=j->{};
    private static Object world;private static String epoch="";
    public static void register(Supplier<Life> life){own=life;net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(DarkCityClient::tick);}
    private static void state(){
        var mc=Minecraft.getInstance();Life life=own.get();String key=ClientUi.current().session()+"/"+life.generation+"/"+life.instance;
        if(world!=mc.level||!epoch.equals(key)){world=mc.level;epoch=key;contacts.clear();closed.clear();struck.clear();DarkCityVisuals.clear();}
    }
    public static void receive(JsonObject packet,Consumer<JsonObject> localHit){
        state();if(world==null||!ClientUi.current().ready())return;hit=localHit;
        GblAttack a=JSON.fromJson(packet,GblAttack.class);long now=ClientUi.current().serverNow();
        if(a.remove){contacts.remove(a.id);closed.put(a.id,now+10000);DarkCityVisuals.receive(a);return;}
        if(!a.instance.equals(own.get().instance)||own.get().dead||closed.containsKey(a.id)||now>=a.end()||!packet.has("boss"))return;
        DarkCityMoves move;
        try{move=DarkCityMoves.valueOf(a.kind);}catch(IllegalArgumentException invalid){return;}
        DarkCityVisuals.receive(a);
        if(move.coefficient<=0)return;
        contacts.putIfAbsent(a.id,new Contact(a,packet.get("group").getAsString(),JSON.fromJson(packet.get("boss"),Target.class),now));
    }
    private static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event){
        state();var mc=Minecraft.getInstance();long now=ClientUi.current().serverNow();
        closed.values().removeIf(until->now>until);struck.values().removeIf(until->now>until);
        if(mc.level==null||mc.player==null||!ClientUi.current().ready()||own.get().dead||mc.player.isDeadOrDying()){contacts.clear();return;}
        for(var contact:new ArrayList<>(contacts.values())){
            GblAttack a=contact.attack;DarkCityMoves move=DarkCityMoves.valueOf(a.kind);
            var raw=mc.level.getEntity(contact.source.entityId);
            // The content notice can arrive just before vanilla entity tracking, especially on a late join.
            if(raw==null){if(now>=a.end())contacts.remove(a.id);else contact.previous=Math.max(a.release(),now);continue;}
            if(!a.instance.equals(own.get().instance)||!contact.source.dimension.equals(mc.level.dimension().identifier().toString())||!(raw instanceof DungeonEnemy e)||!e.getUUID().equals(a.source)||!e.isAlive()||CombatContentTypes.controlled(e)){
                contacts.remove(a.id);continue;
            }
            if(now<a.release())continue;
            long at=Math.min(now,a.end());
            if(contact.previous>a.end()){contacts.remove(a.id);continue;}
            Vec3 from=a.position(contact.previous),to=a.position(at);boolean blocked=false;
            if(a.shape.equals("LINE")){
                var wall=mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e));
                blocked=wall.getType()!=HitResult.Type.MISS;if(blocked)to=wall.getLocation();
            }
            if(!struck.containsKey(contact.group)&&contains(contact,e,from,to,at)&&visible(contact,e)){
                struck.put(contact.group,a.end()+1000);
                JsonObject action=new JsonObject();action.addProperty("id","dark_city/"+epoch+"/"+contact.group);
                action.addProperty("clientContact",true);action.add("boss",JSON.toJsonTree(contact.source));
                action.addProperty("kind",a.kind);action.addProperty("coefficient",move.coefficient);action.addProperty("magic",move.magic);action.addProperty("element",move.element);
                action.addProperty("occurredAt",now);hit.accept(action);
            }
            contact.previous=at;
            if(blocked||now>=a.end())contacts.remove(a.id);
        }
    }
    private static boolean contains(Contact c,DungeonEnemy source,Vec3 from,Vec3 to,long at){
        var p=Minecraft.getInstance().player;GblAttack a=c.attack;
        if(a.shape.equals("LINE")){AABB box=p.getBoundingBox().inflate(a.width+.15);return box.contains(from)||box.clip(from,to).isPresent();}
        if(a.shape.equals("CHARGE"))return source.getBoundingBox().move(from.subtract(source.position())).expandTowards(to.subtract(from)).intersects(p.getBoundingBox().inflate(.15));
        Vec3 offset=p.position().subtract(a.origin());if(Math.abs(offset.y)>(a.shape.equals("WAVE")?1.05:2.6))return false;
        double distance=offset.horizontalDistance();
        if(a.shape.equals("WAVE"))return distance>=Math.max(0,a.waveRadius(c.previous)-a.width*.5-.3)&&distance<=a.waveRadius(at)+a.width*.5+.3;
        if(distance>a.radius+.3)return false;
        Vec3 direction=a.destination().subtract(a.origin()).multiply(1,0,1).normalize();
        return !a.shape.equals("FAN")||distance<.4||direction.lengthSqr()<.001||offset.multiply(1,0,1).normalize().dot(direction)>=Math.cos(a.halfAngle);
    }
    private static boolean visible(Contact c,DungeonEnemy source){
        GblAttack a=c.attack;if(a.shape.equals("LINE"))return true;
        return Minecraft.getInstance().level.clip(new ClipContext(a.origin().add(0,.5,0),Minecraft.getInstance().player.position().add(0,.65,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source)).getType()==HitResult.Type.MISS;
    }
    private DarkCityClient(){}
}
