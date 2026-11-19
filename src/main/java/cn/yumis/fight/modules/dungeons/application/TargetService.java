package cn.yumis.fight.modules.dungeons.application;
import cn.yumis.fight.modules.dungeons.api.Dungeon;

import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;

import cn.yumis.fight.modules.dungeons.api.Difficulty;

import cn.yumis.fight.foundation.Journal;

import cn.yumis.fight.modules.dungeons.api.Target;


import static cn.yumis.fight.modules.combat.api.LifeRules.*;
import com.google.gson.*;

import net.minecraft.server.level.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.*;

import net.minecraft.world.item.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.*;

import java.nio.file.*;
import java.util.*;
import cn.yumis.fight.modules.dungeons.api.*;
/** Enemy identity and encounter context, stored by the dungeon/enemy module. */
public final class TargetService implements WorldTargets {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    private final Journal journal;
    private final Random random;
    private final java.util.function.Supplier<Difficulty> difficulty;
    private final java.util.function.Function<String,Dungeon> dungeons;
    public TargetService(Journal journal,Random random,java.util.function.Supplier<Difficulty> difficulty,java.util.function.Function<String,Dungeon> dungeons){this.journal=journal;this.random=random;this.difficulty=difficulty;this.dungeons=dungeons;}
    public Target resolve(LivingEntity e){
        if(e instanceof WorldTargetEntity supplied){
            Target world=journal.get("target/"+e.getUUID(),Target.class,null);
            if(world==null){world=supplied.initialCombatTarget();world.id=e.getUUID();world.life.target=e.getUUID();world.dimension=e.level().dimension().identifier().toString();world.life.dimension=world.dimension;commit(Map.of(e,world));}
            world.entityId=e.getId();return world;
        }
        if(e instanceof TrainingSandbag bag){
            Target practice=journal.get("target/"+e.getUUID(),Target.class,null);
            if(practice==null){practice=new Target();practice.id=e.getUUID();practice.kind="TRAINING";practice.noRewards=true;practice.timedTraining=bag.timed();practice.originalLevel=1;practice.dimension=e.level().dimension().identifier().toString();practice.life.target=practice.id;practice.life.dimension=practice.dimension;try{journal.put("target/"+e.getUUID(),practice);}catch(Exception failure){throw new IllegalStateException(failure);}}
            practice.entityId=e.getId();return practice;
        }
        Target t=journal.get("target/"+e.getUUID(),Target.class,null);
        if(t==null){
            String raw=e.getData(cn.yumis.fight.modules.combat.api.CombatContentTypes.TARGET);
            if(!raw.isEmpty())t=JSON.fromJson(raw,Target.class);
        }
        if(t==null){
            t=new Target();
            t.id=e.getUUID();
            t.life.target=e.getUUID();
            t.life.generation=1;
            if(e instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither&&wither.getInvulnerableTicks()>0)t.life.q=1/3d;
            t.dimension=e.level().dimension().identifier().toString();
            t.life.dimension=t.dimension;
            t.originalLevel=e.level().dimension()==Level.NETHER?25+random.nextInt(11):e.level().dimension()==Level.END?45+random.nextInt(6):1+random.nextInt(10);
            t.difficulty=difficulty.get();
            t.kind=e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon?"yumis_dungeon_and_fight:dragon":e instanceof net.minecraft.world.entity.boss.wither.WitherBoss?"WITHER":"NORMAL";
            if(e instanceof cn.yumis.fight.modules.combat.api.WorldVitalEntity world){
                t.originalLevel=world.combatLevel();t.fixedLevel=true;t.difficulty=Difficulty.NORMAL;t.kind="NORMAL";t.noRewards=true;t.damageImmune=true;
                t.speciesHealth=world.combatMaximumHealth()/t.health(t.originalLevel);
            }
            else if(e instanceof DungeonEnemy mob){
                t.originalLevel=mob.dungeonLevel();
                t.instance=mob.instance();
                t.life.instance=t.instance;
                t.kind=mob.boss()?"BOSS":"NORMAL";
                t.elite=mob.elite();
                var d=dungeons.apply(t.instance);
                if(d!=null){
                    t.partySize=d.practice!=null?1:d.treasure==null?d.roster.size():d.treasure.partySize;
                    t.difficulty=d.difficulty;
                }
            }
            else if(t.kind.equals("NORMAL")){
                t.speciesHealth=e.getMaxHealth()/20d;
                var a=e.getAttribute(Attributes.ATTACK_DAMAGE);
                t.speciesAttack=a==null?1:Math.max(.1,a.getBaseValue()/3);
                t.elite=e.getRandom().nextFloat()<.05&&!e.getPersistentData().getBooleanOr("ydnf_spawner",false);
            }
            if(t.elite)t.elitePower=java.util.List.of("strong","rage","armor","elemental").get(random.nextInt(4));
            try{
                journal.put("target/"+e.getUUID(),t);
            }
            catch(Exception ex){
                throw new IllegalStateException(ex);
            }
            e.setData(cn.yumis.fight.modules.combat.api.CombatContentTypes.TARGET,JSON.toJson(t));
        }
        t.entityId=e.getId();
        return t;
    }
    @Override public Target target(LivingEntity entity){return resolve(entity);}
    @Override public cn.yumis.fight.modules.combat.api.CombatPorts.Commit prepare(Map<LivingEntity,Target> changes){
        if(changes.isEmpty())return cn.yumis.fight.modules.combat.api.CombatPorts.Commit.NONE;
        var writes=new LinkedHashMap<String,JsonElement>();
        var projections=new LinkedHashMap<LivingEntity,JsonObject>();
        for(var entry:changes.entrySet()){
            if(!(entry.getKey() instanceof WorldTargetEntity)||!entry.getKey().getUUID().equals(entry.getValue().id))
                throw new IllegalArgumentException("世界生命归属不一致");
            writes.put("target/"+entry.getKey().getUUID(),JSON.toJsonTree(entry.getValue()));
            writes.put("life/"+entry.getKey().getUUID(),JSON.toJsonTree(entry.getValue().life));
            projections.put(entry.getKey(),JSON.toJsonTree(entry.getValue()).getAsJsonObject());
        }
        return new cn.yumis.fight.modules.combat.api.CombatPorts.Commit(){
            @Override public void persist()throws Exception{for(var entry:writes.entrySet())journal.put(entry.getKey(),entry.getValue());}
            @Override public void publish(){for(var entry:projections.entrySet()){var entity=entry.getKey();var value=entry.getValue();value.addProperty("entityId",entity.getId());entity.setData(cn.yumis.fight.modules.combat.api.CombatContentTypes.TARGET,value.toString());if(entity.level() instanceof ServerLevel)net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new cn.yumis.fight.network.Network.Snapshot("context",value.toString()));}}
        };
    }
    @Override public void commit(Map<LivingEntity,Target> changes){
        var change=prepare(changes);
        try{journal.begin();change.persist();journal.finish();change.publish();}
        catch(Exception failure){journal.abort();throw new IllegalStateException("世界生命提交失败",failure);}
    }
    @Override public void release(Collection<? extends LivingEntity> entities){
        for(var entity:entities){
            if(!(entity instanceof WorldTargetEntity))throw new IllegalArgumentException("不是托管世界生命");
        }
        releaseIds(entities.stream().map(LivingEntity::getUUID).toList());
    }
    @Override public void releaseIds(Collection<UUID> actors){
        var writes=new LinkedHashMap<String,JsonElement>();
        for(UUID actor:actors){
            String target="target/"+actor,life="life/"+actor;
            if(!journal.get(target).isJsonNull())writes.put(target,JsonNull.INSTANCE);
            if(!journal.get(life).isJsonNull())writes.put(life,JsonNull.INSTANCE);
            for(String key:journal.keys("ailments/"+actor+"/"))writes.put(key,JsonNull.INSTANCE);
        }
        if(!writes.isEmpty())try{journal.transaction(writes);}catch(Exception failure){throw new IllegalStateException("世界生命清理失败",failure);}
    }
}
