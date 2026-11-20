package cn.yumis.fight.modules.arkguard.application;

import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.modules.arkguard.api.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.economy.api.RewardPorts;
import com.google.gson.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** One shared, transient war. Personal shop facts are supplied by their owners. */
final class ArkBattle {
    private static final Gson JSON=new Gson();
    static final class Seat {
        final UUID owner;final String name;final int index;
        ArkRoster roster,opening;
        final Map<String,Integer> layers=new LinkedHashMap<>();
        final List<Integer> waveExperience=new ArrayList<>();
        Map<String,Integer> counts=Map.of();
        final List<Actor> allies=new ArrayList<>();
        List<String> queue=List.of();
        int spawned;long nextSpawn,layerRevision;
        boolean ready,rallied;
        Seat(UUID owner,String name,int index,ArkRoster roster){this.owner=owner;this.name=name;this.index=index;this.roster=roster;}
        ArkWar.Plot plot(){return ArkWar.PLOTS.get(index);}
    }
    static final class Actor {
        final LivingEntity entity;
        final Seat seat;
        final ArkRoster.Card card;
        final ArkRules.Unit unit;
        final boolean enemy,core;
        final String kind;
        Target target;
        Stats stats=new Stats();
        double healthBase,attackBase,attackDivisor,physicalBase,magicalBase;
        double attackProgress=1,slow;
        long slowUntil,lastFight,stasisUntil,castUntil,meteorAt,flowerUntil,sanctuaryUntil;
        final ArkSkillClock skill=new ArkSkillClock();
        int emotion,attacks,routeIndex;
        int phase; // 0 personal field; 1 immune transfer route; 2 core field
        int element=-1;
        boolean transferred,deathProjected,enteredField,airborne;
        Vec3 meteorPoint;
        UUID victim,cloudTarget;
        final Map<String,Integer> gains=new HashMap<>();
        final Map<String,Long> cooldowns=new HashMap<>();
        Actor(LivingEntity entity,Seat seat,ArkRoster.Card card,ArkRules.Unit unit,boolean enemy,boolean core,String kind,Target target){
            this.entity=entity;this.seat=seat;this.card=card;this.unit=unit;this.enemy=enemy;this.core=core;this.kind=kind;this.target=target;
            healthBase=target.health(1);attackBase=target.attack(1);
            physicalBase=target.physicalDefenseBase;magicalBase=target.magicalDefenseBase;
            double current=target.speciesAttack;target.speciesAttack=1;attackDivisor=target.attack(1);target.speciesAttack=current;
        }
        boolean alive(){return !target.life.dead&&!entity.isRemoved();}
        boolean deployed(){return card!=null&&card.onField();}
        Vec3 position(){return entity.position();}
        int star(){return card==null?1:card.star;}
    }
    final String id;
    final ArkRules rules;
    final ServerLevel level;
    final Journal journal;
    final WorldTargets lives;
    private final RewardPorts.Experience experience;
    final List<Seat> seats;
    final List<Actor> actors=new ArrayList<>();
    final Random random=new Random();
    final ArkCombat combat;
    final ArkEffects effects;
    final Set<Actor> dirty=new LinkedHashSet<>();
    final List<Runnable> afterCommit=new ArrayList<>();
    final boolean[] dragon=new boolean[21];
    private final Set<Long> forced=new LinkedHashSet<>();
    Actor core;
    int wave=1;
    String phase="PREP",stage=UUID.randomUUID().toString(),result="";
    long deadline,started,lastTick,nextPeriodic,nextCloud,nextTowers;
    boolean ended,stateDirty=true;
    double towerMultiplier=1;

    ArkBattle(String id,ServerLevel level,Journal journal,WorldTargets lives,List<Seat> seats,ArkRules rules,long now,java.util.function.Consumer<JsonObject> presentation,RewardPorts.Experience experience){
        this.id=id;this.level=level;this.journal=journal;this.lives=lives;this.seats=seats;this.experience=experience;
        this.rules=ArkRules.parse(JSON.toJson(rules));combat=new ArkCombat(this);effects=new ArkEffects(this,presentation);
        holdChunks();
        try{
        for(int w=1;w<=20;w++)dragon[w]=random.nextBoolean();
        deadline=preparationDeadline(now);lastTick=now;
        var entity=ArkContentTypes.ROLE.get().create(level,EntitySpawnReason.COMMAND);
        if(entity==null)throw new IllegalStateException("无法创建方舟生命锚点");
        entity.setPos(ArkWar.CORE.getX()+.5,ArkWar.CORE.getY(),ArkWar.CORE.getZ()+.5);
        var target=target(entity,200000,0,0,0,new double[4],false,false,true);
        entity.core(target);core=new Actor(entity,null,null,null,false,true,"core",target);spawn(core);
        for(var seat:seats)syncRoster(seat,false);
        flush();
        }catch(RuntimeException failure){try{close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    long preparationMillis(){return (wave*10L+30)*1000;}
    private long preparationDeadline(long now){return seats.size()==1?0:now+preparationMillis();}
    boolean boss(){return wave%5==0;}
    Seat seat(UUID owner){return seats.stream().filter(s->s.owner.equals(owner)).findFirst().orElse(null);}
    int presentCount(Set<UUID> present){return (int)seats.stream().filter(s->present.contains(s.owner)).count();}
    int readyCount(Set<UUID> present){return (int)seats.stream().filter(s->present.contains(s.owner)&&s.ready).count();}
    boolean editing(){return !ended&&phase.equals("PREP");}

    void accept(Seat seat,ArkRoster roster){
        seat.roster=roster;
        if(editing())seat.ready=false;
        syncRoster(seat,!editing());stateDirty=true;flush();
    }
    void ready(Seat seat,boolean ready){seat.ready=ready;stateDirty=true;}
    void tick(long now,Set<UUID> present){
        if(ended)return;
        if(presentCount(present)==0){finish("所有参战者已离场，本局结束");return;}
        double dt=Math.max(0,(now-lastTick)/1000d);lastTick=now;
        loadLives();
        if(editing()){
            heldModels();
            if((deadline>0&&now>=deadline)||readyCount(present)==presentCount(present))begin(now);
            flush();return;
        }
        if(!phase.equals("BATTLE"))return;
        for(var seat:seats)while(seat.spawned<seat.queue.size()&&now>=seat.nextSpawn){
            spawnEnemy(seat,seat.queue.get(seat.spawned++));seat.nextSpawn+=1000;
        }
        combat.tick(now,dt);
        transferClearedLines();
        flush();
        for(var actor:actors)if(actor.target.life.dead&&!actor.deathProjected){
            actor.deathProjected=true;actor.entity.discard();
        }
        if(core.target.life.dead){finish("方舟被摧毁，本局失败");return;}
        boolean spawned=seats.stream().allMatch(s->s.spawned==s.queue.size());
        boolean cleared=actors.stream().noneMatch(a->a.enemy&&a.alive());
        if(spawned&&cleared||!boss()&&now>=deadline)endWave(now,present);
    }
    private void begin(long now){
        phase="BATTLE";stage=UUID.randomUUID().toString();started=now;deadline=boss()?0:now+240000;
        nextPeriodic=now+500;nextCloud=now+1000;nextTowers=now+1000;
        for(var seat:seats){
            seat.ready=false;seat.rallied=false;seat.opening=seat.roster.copy();seat.opening.future.clear();
            seat.counts=ArkTraits.counts(seat.opening,rules);seat.queue=army(wave,dragon[wave]);seat.spawned=0;seat.nextSpawn=now+1000;
            for(var actor:seat.allies){
                actor.attacks=0;actor.emotion=0;actor.attackProgress=1;actor.gains.clear();actor.skill.reset(now);
                actor.stasisUntil=actor.castUntil=actor.meteorAt=actor.flowerUntil=actor.sanctuaryUntil=0;
                actor.victim=null;actor.meteorPoint=null;actor.airborne=false;
                actor.target.life.elemental=new ElementAccumulation.State();actor.target.life.elemental.generation=actor.target.life.generation;
                actor.target.life.q=1;actor.target.life.dead=false;actor.target.life.revision++;
                if(actor.entity instanceof Mob mob){mob.setNoAi(!actor.deployed());mob.getNavigation().stop();}
                actor.entity.setInvisible(actor.card.held);actor.entity.setNoGravity(!actor.deployed());
                var scale=actor.entity.getAttribute(Attributes.SCALE);if(scale!=null)scale.setBaseValue(1);
                dirty.add(actor);
            }
        }
        double boost=seats.stream().mapToDouble(s->ArkTraits.longevity(s.counts,rules)).sum();
        towerMultiplier=1+boost;maximum(core,200000*towerMultiplier);
        combat.start(now);stateDirty=true;
    }
    private void endWave(long now,Set<UUID> present){
        combat.end();
        for(var seat:seats)for(var actor:seat.allies)if(actor.deployed()){
            if(actor.unit.id().equals("ark_letiya_sloth")){
                long count=seat.opening.cards.stream().filter(c->c.onField()&&rules.unit(c.unit).has("white_church")).count();
                gain(seat,"white_church",(int)(count*actor.unit.s("churchPerMember",actor.star())));
            }
            if(actor.unit.id().equals(ArkCombat.JOSEPHINE)){
                var original=seat.opening.card(actor.card.token);
                int amount=(int)actor.unit.p("endLayers");
                gainOpeningTraits(seat,original,amount);
                var front=seat.opening.inFrontOf(original);
                if(front!=null)gainOpeningTraits(seat,front,amount);
            }
        }
        for(var seat:seats)if(present.contains(seat.owner))seat.waveExperience.add(10+5*wave);
        if(wave==20){finish("第20波全部清场，方舟战争胜利");return;}
        clearActors(false);
        wave++;phase="PREP";stage=UUID.randomUUID().toString();deadline=preparationDeadline(now);
        for(var seat:seats){
            seat.roster.reach(wave);seat.ready=false;seat.rallied=false;seat.allies.clear();
            journalPut("arkWar/owners/"+id+"/"+seat.owner,seat.roster);
            syncRoster(seat,false);
        }
        stateDirty=true;flush();
    }
    private void gainOpeningTraits(Seat seat,ArkRoster.Card card,int amount){
        for(String trait:rules.unit(card.unit).traits())if(ArkTraits.active(trait,seat.counts,rules))gain(seat,trait,amount);
    }
    private void loadLives(){
        for(var actor:actors)if(!actor.entity.isRemoved()){
            actor.target=lives.target(actor.entity);
            if(actor.target.life.dead&&actor.core)stateDirty=true;
        }
    }
    void gain(Seat seat,String trait,int amount){
        if(amount<=0)return;int before=seat.layers.getOrDefault(trait,0),after=Math.min(rules.maximumTraitLayers,before+amount);
        if(before!=after){seat.layers.put(trait,after);seat.layerRevision++;stateDirty=true;}
    }
    void maximum(Actor actor,double maximum){
        double before=actor.target.health(1);if(Math.abs(before-maximum)<=Math.ulp(maximum)*4)return;
        actor.target.speciesHealth*=maximum/before;actor.target.life.basis++;actor.target.life.revision++;dirty.add(actor);
        if(actor.entity instanceof ArkRoleEntity role)role.maximum(maximum);
    }
    void changed(Actor actor){dirty.add(actor);}
    void flush(){flush(null);}
    private void flush(RewardPorts.ExperienceCommit reward){
        if(dirty.isEmpty()&&!stateDirty){afterCommit.forEach(Runnable::run);afterCommit.clear();effects.publish();return;}
        var changed=new LinkedHashMap<LivingEntity,Target>();
        for(var actor:dirty)changed.put(actor.entity,actor.target);
        var commit=lives.prepare(changed);
        try{
            journal.begin();commit.persist();journal.put("arkWar/matches/"+id,metadata());
            if(reward!=null)journal.transaction(reward.writes());
            journal.finish();
        }catch(Exception failure){journal.abort();throw new IllegalStateException("方舟事实提交失败",failure);}
        if(reward!=null)reward.publish().run();
        commit.publish();
        for(var actor:dirty)if(actor.alive())actor.entity.setHealth((float)Math.max(.001,actor.entity.getMaxHealth()*actor.target.life.q));
        dirty.clear();stateDirty=false;afterCommit.forEach(Runnable::run);afterCommit.clear();effects.publish();
    }
    private JsonObject metadata(){
        var data=new JsonObject();data.addProperty("id",id);data.addProperty("wave",wave);data.addProperty("phase",phase);data.addProperty("stage",stage);
        var layers=new JsonObject();for(var seat:seats)layers.add(seat.owner.toString(),JSON.toJsonTree(seat.layers));data.add("layers",layers);return data;
    }
    void finish(String result){
        var percentages=new LinkedHashMap<UUID,List<Integer>>();
        for(var seat:seats)if(!seat.waveExperience.isEmpty())percentages.put(seat.owner,List.copyOf(seat.waveExperience));
        var reward=percentages.isEmpty()?null:experience.prepare("arkWar/"+id,percentages);
        this.result=result;ended=true;phase="FINISHED";stateDirty=true;flush(reward);
    }
    void close(){
        effects.clear();
        try{
            combat.end();clearActors(true);afterCommit.clear();dirty.clear();
            try{journal.begin();journal.put("arkWar/matches/"+id,JsonNull.INSTANCE);for(var seat:seats)journal.put("arkWar/owners/"+id+"/"+seat.owner,JsonNull.INSTANCE);journal.finish();}
            catch(Exception failure){journal.abort();throw new IllegalStateException("方舟局内事实清理失败",failure);}
        }finally{releaseChunks();}
    }
    private void holdChunks(){
        for(int x=Math.floorDiv(ArkWar.ORIGIN.getX()-64,16);x<=Math.floorDiv(ArkWar.ORIGIN.getX()+64,16);x++)
            for(int z=Math.floorDiv(ArkWar.ORIGIN.getZ()-80,16);z<=Math.floorDiv(ArkWar.ORIGIN.getZ()+112,16);z++){
                long key=ChunkPos.pack(x,z);if(!level.getForceLoadedChunks().contains(key))forced.add(key);
            }
        journalPut("arkWar/forced-v1",forced);
        for(long key:forced)level.setChunkForced(ChunkPos.getX(key),ChunkPos.getZ(key),true);
    }
    private void releaseChunks(){for(long key:forced)level.setChunkForced(ChunkPos.getX(key),ChunkPos.getZ(key),false);forced.clear();journalPut("arkWar/forced-v1",JsonNull.INSTANCE);}
    static void recoverChunks(ServerLevel level,Journal journal){
        var value=journal.get("arkWar/forced-v1");if(!value.isJsonArray())return;
        for(var item:value.getAsJsonArray()){long key=item.getAsLong();level.setChunkForced(ChunkPos.getX(key),ChunkPos.getZ(key),false);}
        try{journal.put("arkWar/forced-v1",JsonNull.INSTANCE);}catch(Exception failure){throw new IllegalStateException("方舟场景驻留清理失败",failure);}
    }
    private void clearActors(boolean includeCore){
        var removed=actors.stream().filter(a->includeCore||!a.core).toList();
        for(var actor:removed){actor.entity.discard();dirty.remove(actor);}
        lives.release(removed.stream().map(a->a.entity).toList());actors.removeAll(removed);
    }
    private void heldModels(){
        for(var seat:seats)for(var actor:seat.allies)if(actor.card.held&&actor.alive()){
            var owner=level.getServer().getPlayerList().getPlayer(seat.owner);
            boolean visible=owner!=null&&ArkWar.contains(owner.level(),owner.blockPosition());
            actor.entity.setInvisible(!visible);
            if(visible){Vec3 point=owner.position().add(owner.getLookAngle().scale(1.1)).add(0,.7,0);actor.entity.setPos(point.x,point.y,point.z);actor.entity.setYRot(owner.getYRot());}
        }
    }
    private Vec3 cardPosition(Seat seat,ArkRoster.Card card){
        if(card.bench>=0){var benches=seat.plot().benches(level);if(card.bench<benches.size())return ArkWar.standing(level,benches.get(card.bench));}
        if(card.deployed)return ArkWar.standing(level,new net.minecraft.core.BlockPos(card.x,card.y,card.z));
        return seat.plot().shop();
    }
    private void syncRoster(Seat seat,boolean reservesOnly){
        // Shopping never replaces a fighting actor, even when the purchased copy merges with it.
        var replaced=seat.allies.stream().filter(actor->!reservesOnly||!actor.deployed()).toList();
        for(var actor:replaced){actor.entity.discard();actors.remove(actor);dirty.remove(actor);seat.allies.remove(actor);}
        if(!replaced.isEmpty())lives.release(replaced.stream().map(actor->actor.entity).toList());
        for(var card:seat.roster.cards)if(!reservesOnly||!card.onField()){
            var definition=rules.unit(card.unit);
            var entity=ArkContentTypes.ROLE.get().create(level,EntitySpawnReason.COMMAND);if(entity==null)throw new IllegalStateException("无法创建角色");
            var target=target(entity,definition.health()*definition.starScale(card.star),definition.attack()*definition.starScale(card.star),
                definition.physicalDefense(),definition.magicalDefense(),new double[4],false,false,true);
            entity.configure(seat.owner.toString(),card,definition,target);entity.setNoAi(true);entity.setNoGravity(card.held);
            entity.setInvisible(reservesOnly&&card.held);
            var scale=entity.getAttribute(Attributes.SCALE);if(scale!=null)scale.setBaseValue(card.held?.55:1);
            Vec3 point=cardPosition(seat,card);entity.setPos(point.x,point.y,point.z);
            float yaw=card.yaw(seat.plot().facing());
            entity.setYRot(yaw);entity.setYBodyRot(yaw);entity.setYHeadRot(yaw);
            var actor=new Actor(entity,seat,card,definition,false,false,"",target);seat.allies.add(actor);spawn(actor);
        }
    }
    private void spawn(Actor actor){
        lives.commit(Map.of(actor.entity,actor.target));
        if(!cn.yumis.fight.modules.mapgeneration.api.MapGeneration.spawnSceneActor(level,actor.entity)){lives.release(List.of(actor.entity));throw new IllegalStateException("方舟实体投放失败");}
        actors.add(actor);
    }
    private void spawnEnemy(Seat seat,String kind){
        var type=ArkContentTypes.ENEMIES.get(kind);if(type==null)throw new IllegalArgumentException("未知军团单位");
        var entity=type.get().create(level,EntitySpawnReason.COMMAND);if(entity==null)throw new IllegalStateException("无法创建军团单位");
        boolean boss=kind.equals("goblin_shaman")||kind.equals("lucas"),elite=kind.equals("goblin_captain");
        double hp=boss?rules.enemies.bossHealth(wave):rules.enemies.health(wave,dragon[wave]);
        double atk=boss?rules.enemies.bossAttack(wave):rules.enemies.attack(wave,dragon[wave]);
        double[] profile=profile(kind);hp*=profile[0];atk*=profile[1];
        var target=target(entity,hp,atk,rules.enemies.physicalDefense(wave,dragon[wave]),rules.enemies.magicalDefense(wave),
            rules.enemies.resistances(dragon[wave]),boss,elite,false);
        entity.configure(target);entity.setCustomName(Component.literal(name(kind)));entity.setCustomNameVisible(true);
        Vec3 point=seat.plot().spawn();entity.setPos(point.x,point.y,point.z);
        var actor=new Actor(entity,seat,null,null,true,false,kind,target);spawn(actor);
    }
    static double[] profile(String kind){return switch(kind){
        case "goblin_thrower"->new double[]{.8,1.2};case "goblin_captain"->new double[]{1.6,1.4};
        case "wyvern"->new double[]{.8,1.2};case "blue_dragonman"->new double[]{1.2,1.1};case "blue_wyvern"->new double[]{.9,1.3};
        default->new double[]{1,1};
    };}
    static String name(String kind){return switch(kind){case "goblin"->"哥布林";case "goblin_thrower"->"投石哥布林";case "goblin_captain"->"哥布林队长";case "goblin_shaman"->"落雷凯诺";case "dragonman"->"龙人";case "wyvern"->"翼龙";case "blue_dragonman"->"蓝龙人";case "blue_wyvern"->"蓝翼龙";case "lucas"->"鲁卡斯";default->kind;};}
    List<String> army(int wave,boolean dragon){
        var result=new ArrayList<String>();
        if(wave%5==0){for(int i=0;i<(dragon?rules.enemies.dragonGuards:rules.enemies.goblinGuards);i++)result.add(dragon?"dragonman":"goblin");result.add(dragon?"lucas":"goblin_shaman");return result;}
        int count=rules.enemies.count(wave,dragon);
        if(dragon){int wing=(int)Math.round(count*.2),blue=(int)Math.round(count*.2),blueWing=(int)Math.round(count*.1);for(int i=0;i<wing;i++)result.add("wyvern");for(int i=0;i<blue;i++)result.add("blue_dragonman");for(int i=0;i<blueWing;i++)result.add("blue_wyvern");while(result.size()<count)result.add("dragonman");}
        else{int thrown=(int)Math.round(count*.3),captains=(int)Math.round(count*.1);for(int i=0;i<thrown;i++)result.add("goblin_thrower");for(int i=0;i<captains;i++)result.add("goblin_captain");while(result.size()<count)result.add("goblin");}
        Collections.shuffle(result,random);return List.copyOf(result);
    }
    static Target target(LivingEntity entity,double hp,double attack,double physicalDefense,double magicalDefense,double[] resists,boolean boss,boolean elite,boolean playerImmune){
        var t=new Target();t.id=entity.getUUID();t.entityId=entity.getId();t.kind=boss?"BOSS":"NORMAL";t.elite=elite;t.noRewards=true;t.fixedLevel=true;t.originalLevel=1;
        t.dimension=entity.level().dimension().identifier().toString();t.life.target=t.id;t.life.dimension=t.dimension;t.life.basis=1;
        t.speciesHealth=hp/t.health(1);t.speciesAttack=attack/t.attack(1);t.physicalDefenseBase=physicalDefense;t.magicalDefenseBase=magicalDefense;
        t.elementalResists=resists.clone();t.damageImmune=playerImmune;return t;
    }
    private void transferClearedLines(){
        for(var seat:seats)if(!seat.rallied&&seat.spawned==seat.queue.size()
            &&actors.stream().noneMatch(a->a.enemy&&a.seat==seat&&a.phase==0&&a.alive())){
            seat.rallied=true;int index=0;
            for(var actor:seat.allies)if(actor.deployed()&&actor.alive()){
                var point=seat.plot().rally(index++);
                if(actor.airborne)point=point.add(0,actor.unit.p("flightHeight"),0);
                actor.entity.setPos(point.x,point.y,point.z);actor.transferred=true;
                if(actor.entity instanceof Mob mob)mob.getNavigation().stop();
            }
        }
    }
    Actor actor(UUID id){return actors.stream().filter(a->a.entity.getUUID().equals(id)).findFirst().orElse(null);}
    List<Actor> enemies(){return actors.stream().filter(a->a.enemy&&a.alive()&&a.phase!=1).toList();}
    List<Actor> friends(){return actors.stream().filter(a->!a.enemy&&!a.core&&a.deployed()&&a.alive()).toList();}
    List<Actor> friends(Seat seat){return seat.allies.stream().filter(a->a.deployed()&&a.alive()).toList();}
    void journalPut(String key,Object value){try{journal.put(key,value);}catch(Exception failure){throw new IllegalStateException(failure);}}
    JsonObject snapshot(Seat personal,Set<UUID> present){
        long now=System.currentTimeMillis();
        var j=new JsonObject();j.addProperty("round",id);j.addProperty("phase",phase);j.addProperty("stage",stage);j.addProperty("wave",wave);j.addProperty("deadline",deadline);
        j.addProperty("at",now);
        j.addProperty("boss",boss());j.addProperty("dragon",dragon[wave]);j.addProperty("nextDragon",wave<20&&dragon[wave+1]);j.addProperty("coreHealth",core.target.health(1)*core.target.life.q);j.addProperty("coreMaximum",core.target.health(1));
        j.addProperty("ready",readyCount(present));j.addProperty("present",presentCount(present));j.addProperty("participants",seats.size());j.addProperty("result",result);j.add("rules",JSON.toJsonTree(rules));
        j.addProperty("remaining",actors.stream().filter(a->a.enemy&&a.alive()).count());j.addProperty("unspawned",seats.stream().mapToInt(s->s.queue.size()-s.spawned).sum());
        var summaries=new JsonArray();
        for(var seat:seats){var s=new JsonObject();s.addProperty("owner",seat.owner.toString());s.addProperty("name",seat.name);s.addProperty("index",seat.index);s.addProperty("ready",seat.ready);s.addProperty("present",present.contains(seat.owner));summaries.add(s);}
        j.add("seats",summaries);
        var displayed=new JsonArray();
        for(var actor:actors)if(!actor.core&&actor.alive()){
            var view=new JsonObject();view.addProperty("entity",actor.entity.getId());view.addProperty("id",actor.entity.getStringUUID());
            view.addProperty("maximum",actor.target.health(1));view.addProperty("health",actor.target.health(1)*actor.target.life.q);view.addProperty("enemy",actor.enemy);
            if(actor.unit!=null)combat.skillState(actor,view,now,editing());
            displayed.add(view);
        }
        j.add("actors",displayed);
        if(personal!=null){
            var counts=editing()?ArkTraits.counts(personal.roster,rules):personal.counts;
            j.addProperty("seat",personal.index);j.addProperty("personalReady",personal.ready);j.add("roster",JSON.toJsonTree(personal.roster));j.add("layers",JSON.toJsonTree(personal.layers));j.add("counts",JSON.toJsonTree(counts));
            var units=new JsonObject();
            for(var a:personal.allies){
                var s=new JsonObject();
                s.addProperty("health",a.target.health(1)*a.target.life.q);
                s.addProperty("maximum",a.target.health(1));
                s.addProperty("attack",editing()?a.attackBase:a.stats.panel(a.unit.magic()));
                s.addProperty("physicalDefense",a.target.defense(1,false));
                s.addProperty("magicalDefense",a.target.defense(1,true));
                s.addProperty("attackRate",combat.attackRate(a));
                s.addProperty("range",editing()?ArkTraits.range(a.unit,counts,personal.layers,rules):combat.range(a));
                s.addProperty("emotion",a.emotion);
                s.addProperty("cooldown",a.skill.cooldownRemaining(now));
                s.addProperty("active",Math.max(0,a.skill.end()-now));
                units.add(a.card.token,s);
            }
            j.add("units",units);
        }
        else j.addProperty("seat",-1);
        return j;
    }
}
