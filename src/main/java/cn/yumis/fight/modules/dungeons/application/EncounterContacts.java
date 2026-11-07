package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;

/** Signed contacts owned only by the dungeon module; health arithmetic stays on the receiving client. */
public final class EncounterContacts implements CombatPorts.SkillEffects {
    private record Credit(String action,UUID source,String instance,UUID session,long generation,long basis,String dimension,long at,long expires){}
    private static final Gson JSON=new Gson();
    private final DungeonService owner;
    private final Journal journal;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final Set<String> signedTypes=new HashSet<>();
    private final Map<String,Credit> credits=new HashMap<>();
    EncounterContacts(DungeonService owner,Journal journal,DungeonPorts.Players players,DungeonPorts.Targets targets){this.owner=owner;this.journal=journal;this.players=players;this.targets=targets;}
    void register(String type){signedTypes.add(type);}
    void issue(String id,DungeonEnemy source,ServerPlayer player,String kind,double coefficient,boolean magic,int element,long now){
        var session=players.session(player);String key=id+"/"+player.getUUID();
        Credit credit=new Credit(id,source.getUUID(),source.instance(),session.id,session.life.generation,session.life.basis,player.level().dimension().identifier().toString(),now,now+3000);
        if(credits.containsKey(key)||!journal.get(receipt(credit,player)).isJsonNull())return;
        credits.put(key,credit);
        JsonObject hit=new JsonObject();hit.addProperty("id",id);hit.add("boss",JSON.toJsonTree(targets.target(source)));
        hit.addProperty("kind",kind);hit.addProperty("shape","CIRCLE");hit.addProperty("x",player.getX());hit.addProperty("y",player.getY());hit.addProperty("z",player.getZ());
        hit.addProperty("dx",0);hit.addProperty("dz",0);hit.addProperty("radius",1.5);hit.addProperty("halfAngle",Math.PI);hit.addProperty("windupTicks",0);hit.addProperty("activeTicks",8);
        hit.addProperty("coefficient",coefficient);hit.addProperty("magic",magic);hit.addProperty("element",element);hit.addProperty("startedAt",now);hit.addProperty("encounterHit",true);
        Network.send(player,"bossAction",hit);
    }
    void tick(long now){credits.values().removeIf(c->c.expires<now);}
    boolean pending(UUID source){return credits.values().stream().anyMatch(c->c.source.equals(source));}
    void close(String instance){credits.values().removeIf(c->c.instance.equals(instance));}
    private String receipt(Credit c,ServerPlayer p){return "dungeonContact/"+c.action+"/"+p.getUUID()+"/"+c.session+"/"+c.generation;}
    private Credit credit(ServerPlayer p,JsonObject report){return credits.get(report.get("encounterAction").getAsString()+"/"+p.getUUID());}
    @Override public long occurredAt(ServerPlayer p,JsonObject report){if(!report.has("encounterAction"))return -1;Credit c=credit(p,report);return c==null?-1:c.at;}
    @Override public void validate(ServerPlayer p,LivingEntity target,JsonObject report){
        if(!report.has("encounterAction")){
            if(target==p&&report.has("feedbackEntity")&&p.level().getEntity(UUID.fromString(report.get("feedbackEntity").getAsString())) instanceof DungeonEnemy e&&signedTypes.contains(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())&&!report.has("nativeRoot"))throw new IllegalArgumentException("副本攻击缺少命中凭据");
            return;
        }
        Credit c=credit(p,report);var s=players.session(p);
        if(target!=p||report.get("heal").getAsBoolean()||c==null||c.expires<System.currentTimeMillis()||!s.id.equals(c.session)||s.life.generation!=c.generation||s.life.basis!=c.basis||!s.life.instance.equals(c.instance)||!p.level().dimension().identifier().toString().equals(c.dimension)||!owner.participant(p,c.instance)||!report.has("feedbackEntity")||!report.get("feedbackEntity").getAsString().equals(c.source.toString())||!journal.get(receipt(c,p)).isJsonNull())throw new IllegalArgumentException("副本命中凭据已失效或重复");
    }
    @Override public CombatPorts.Commit prepare(ServerPlayer p,LivingEntity target,JsonObject report,Receipt receipt,Life actor){
        if(!report.has("encounterAction"))return CombatPorts.Commit.NONE;
        String key=report.get("encounterAction").getAsString()+"/"+p.getUUID();Credit credit=Objects.requireNonNull(credits.get(key));
        return new CombatPorts.Commit(){public void persist()throws Exception{journal.put(receipt(credit,p),true);}public void publish(){credits.remove(key,credit);}};
    }
}
