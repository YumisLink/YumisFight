package cn.yumis.fight.server;
import cn.yumis.fight.FightMod;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.game.*;
import cn.yumis.fight.network.Network;
import cn.yumis.fight.api.Permissions;
import cn.yumis.fight.rules.*;
import cn.yumis.fight.rules.Rules.Difficulty;
import cn.yumis.fight.rules.Rules.Slot;
import cn.yumis.fight.rules.Rules.Rarity;
import static cn.yumis.fight.rules.Rules.*;
import static cn.yumis.fight.rules.Models.*;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
public final class ServerRuntime {
    private static ServerRuntime CURRENT;
    public final MinecraftServer server;
    public final Journal journal;
    public final InventoryTransactions inventory;
    public final DungeonService dungeons;
    public final LootService loot;
    public final VisualService visuals;
    public final CloudLeapService movement;
    public final NativeHits nativeHits;
    public final Map<UUID,Session> sessions=new LinkedHashMap<>();
    private final ArrayDeque<Runnable> combatQueue=new ArrayDeque<>();
    public final Random random=new Random();
    public boolean bridge;
    public Difficulty worldDifficulty=Difficulty.NORMAL;
    private int ticks;
    private final Map<UUID,Boolean> stunned=new HashMap<>();
    public static final class Session {
        public final UUID id=UUID.randomUUID();
        public Progress progress;
        public Models.Runtime resources;
        public Life life;
        public String statsKey="";
        public Session(Progress p,Models.Runtime r,Life l){
            progress=p;
            resources=r;
            life=l;
        }
    }
    private ServerRuntime(MinecraftServer server)throws Exception{
        this.server=server;
        journal=new Journal(server.getWorldPath(LevelResource.ROOT).resolve(GameItems.NS).resolve("state.wal"));
        inventory=new InventoryTransactions(this);
        loot=new LootService(this);
        visuals=new VisualService(this);
        nativeHits=new NativeHits(this);
        movement=new CloudLeapService(this);
        dungeons=new DungeonService(this);
        loadDefinitions();
        dungeons.recover();
        loot.recover();
    }
    public static void start(MinecraftServer server){
        try{
            CURRENT=new ServerRuntime(server);
        }
        catch(Exception e){
            throw new IllegalStateException("无法打开世界状态日志",e);
        }
        ExtraInventory.PROGRESS=p->p instanceof ServerPlayer sp?get().session(sp).progress:ExtraInventory.CLIENT_PROGRESS.apply(p);
    }
    public static boolean ready(){
        return CURRENT!=null;
    }
    public static ServerRuntime get(){
        if(CURRENT==null)throw new IllegalStateException("服务未就绪");
        return CURRENT;
    }
    public static void stop(){
        if(CURRENT==null)return;
        try{
            for(ServerPlayer p:CURRENT.server.getPlayerList().getPlayers())CURRENT.savePlayer(p);
            CURRENT.dungeons.shutdown();
            CURRENT.journal.close();
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
        finally{
            CURRENT=null;
        }
    }
    public static void registerEvents(){
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p)get().login(p);
        }
        );
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p)get().logout(p);
        }
        );
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p)get().respawn(p);
        }
        );
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof LivingEntity target&&get().managed(target)){
                get().context(p,target);if(target instanceof ServerPlayer caster)get().visuals.track(p,caster);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{
            if(ready())get().tick();
        }
        );
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent e)->{
            if(ready())get().incoming(e);
        }
        );
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Pre e)->{
            if(ready())get().nativeHits.pre(e);
        });
        NeoForge.EVENT_BUS.addListener(WorldRules::nativeHeal);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p){
                var runtime=get();var s=runtime.session(p);try{
                    if(!s.life.instance.isEmpty()&&p.level().dimension()!=DungeonService.DIMENSION)runtime.dungeons.leave(p,false);s.life.dimension=p.level().dimension().identifier().toString();s.life.revision++;runtime.savePlayer(p);runtime.profile(p);
                }
                catch(Exception failure){
                    runtime.message(p,failure.getMessage());
                }
            }
        }
        );
        ServerEvents.register();
    }
    public void loadDefinitions(){
        cn.yumis.fight.industry.IndustrialWorld.loadRules(server);
        try{
            Catalog nextCatalog=Catalog.CURRENT;
            Definitions nextDefinitions=Definitions.CURRENT;
            var resource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(GameItems.NS,"rules/catalog.json"));
            if(resource.isPresent())try(var reader=resource.get().openAsReader()){
                nextCatalog=Catalog.parse(JsonParser.parseReader(reader).toString());
                nextCatalog.revision=Catalog.CURRENT.revision+1;
            }
            var equipment=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(GameItems.NS,"rules/equipment.json"));
            if(equipment.isPresent())try(var reader=equipment.get().openAsReader()){
                nextDefinitions=Definitions.parse(JsonParser.parseReader(reader).toString());
            }
            EquipmentLayout nextLayout=EquipmentLayout.load(server.getResourceManager());
            Path config=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(GameItems.NS+".toml");
            if(!Files.exists(config))Files.writeString(config,"# 大世界难度：NORMAL / ADVANCED / WARRIOR / KING / HELL\nworldDifficulty=\"NORMAL\"\n");
            String text=Files.readString(config);
            var match=java.util.regex.Pattern.compile("worldDifficulty\\s*=\\s*\"(\\w+)\"").matcher(text);
            Difficulty nextDifficulty=match.find()?Difficulty.valueOf(match.group(1)):worldDifficulty;
            Catalog.CURRENT=nextCatalog;
            Definitions.CURRENT=nextDefinitions;
            EquipmentLayout.CURRENT=nextLayout;
            worldDifficulty=nextDifficulty;
            for(ServerPlayer player:server.getPlayerList().getPlayers())EquipmentService.reconcile(this,player,EquipmentService.bonus(this,player));
        }
        catch(Exception e){
            server.sendSystemMessage(cn.yumis.fight.game.ModText.of("地下城定义重载失败，保留有效定义："+e.getMessage()));
        }
    }
    public Session session(ServerPlayer p){
        return sessions.computeIfAbsent(p.getUUID(),id->{
            Progress progress=journal.get("player/"+id,Progress.class,new Progress());progress.normalizeSlots();Models.Runtime r=journal.get("resources/"+id,Models.Runtime.class,new Models.Runtime());Life l=journal.get("life/"+id,Life.class,new Life());l.target=id;l.dimension=p.level().dimension().identifier().toString();return new Session(progress,r,l);
        }
        );
    }
    public void login(ServerPlayer p){
        try{
            inventory.restore(p);
            EquipmentService.reconcile(this,p,EquipmentService.bonus(this,p));
            Session s=session(p);
            movement.reset(p);
            dungeons.reconnect(p);
            VisualService.resetGravity(p);
            var stats=stats(p);
            if(s.resources.mp<0)s.resources.mp=stats.get(Stat.MP);
            if(s.resources.shield<0)s.resources.shield=stats.get(Stat.SHIELD);
            if(!s.progress.starterGranted){
                Progress next=copy(s.progress,Progress.class);
                next.starterGranted=true;
                ItemStack starter=GameItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,1,random));
                JsonObject intent=new JsonObject();
                intent.addProperty("starter",true);
                try{
                    inventory.transact(p,"starter",intent,()->inventory.requireSpace(p,starter),Map.of("player/"+p.getUUID(),JSON.toJsonTree(next)));
                }
                catch(InventoryTransactions.NoSpace full){
                    journal.begin();
                    loot.privateDrop(p,starter,p.position(),"");
                    journal.put("player/"+p.getUUID(),next);
                    journal.finish();
                    loot.publish();
                }
                s.progress=next;
            }
            if(s.life.dead&&!p.isDeadOrDying()){
                bridge=true;
                try{
                    p.hurtServer(p.level(),p.damageSources().genericKill(),Float.MAX_VALUE);
                }
                finally{
                    bridge=false;
                }
            }
            savePlayer(p);
            profile(p);
        }
        catch(Exception e){
            p.connection.disconnect(cn.yumis.fight.game.ModText.of("状态恢复失败："+e.getMessage()));
        }
    }
    public void logout(ServerPlayer p){
        try{
            inventory.closeCursor(p);
            savePlayer(p);
            loot.endSession(p);
            dungeons.disconnected(p);
            sessions.remove(p.getUUID());
            movement.logout(p);
        }
        catch(Exception e){
            server.sendSystemMessage(cn.yumis.fight.game.ModText.of("保存玩家失败："+e.getMessage()));
        }
    }
    public void respawn(ServerPlayer p){
        Session s=session(p);
        inventory.restore(p);
        s.life.generation++;
        movement.reset(p);
        s.life.revision=0;
        s.life.q=1;
        s.life.dead=false;
        s.life.dimension=p.level().dimension().identifier().toString();
        Stats stats=stats(p);
        s.resources.mp=stats.get(Stat.MP);
        if(s.resources.training)s.resources.trainingBoundary=s.resources.mp;
        VisualService.resetGravity(p);
        s.resources.shield=stats.get(Stat.SHIELD);
        s.resources.combatUntil=0;
        s.resources.shieldRecoveryAt=0;
        s.resources.buffs.clear();
        s.resources.absorption.clear();
        s.resources.absorptionUntil.clear();
        p.getFoodData().setFoodLevel(20);
        dungeons.respawn(p);
        savePlayer(p);
        profile(p);
    }
    public void savePlayer(ServerPlayer p){
        try{
            Session s=session(p);
            journal.begin();
            journal.put("player/"+p.getUUID(),s.progress);
            journal.put("resources/"+p.getUUID(),s.resources);
            journal.put("life/"+p.getUUID(),s.life);
            journal.finish();
        }
        catch(Exception e){
            journal.abort();
            throw new IllegalStateException(e);
        }
    }
    public Stats stats(ServerPlayer p){
        return EquipmentStats.build(p,session(p).progress);
    }
    public void profile(ServerPlayer p){
        Session s=session(p);
        Stats stats=stats(p);
        s.statsKey=JSON.toJson(stats);
        var attribute=p.getAttribute(Attributes.MAX_HEALTH);
        if(attribute!=null)attribute.setBaseValue(Math.max(1,stats.get(Stat.HEALTH)));
        bridge=true;
        try{
            if(!s.life.dead)p.setHealth((float)Math.max(.001,s.life.q*p.getMaxHealth()));
        }
        finally{
            bridge=false;
        }
        JsonObject j=new JsonObject();
        j.addProperty("protocol",PROTOCOL);
        j.addProperty("rules",VERSION);
        j.addProperty("session",s.id.toString());
        j.addProperty("serverNow",System.currentTimeMillis());
        j.add("progress",JSON.toJsonTree(s.progress));
        j.add("resources",JSON.toJsonTree(s.resources));
        j.add("life",JSON.toJsonTree(s.life));
        j.add("stats",JSON.toJsonTree(stats));
        j.add("movement",movement.snapshot(p));
        var sources=cn.yumis.fight.api.CommonServices.attributes(p.getUUID());
        j.addProperty("attributeRevision",sources.revision());
        j.add("attributeSources",JSON.toJsonTree(sources.snapshot()));
        j.add("party",JSON.toJsonTree(team(p.getUUID())));
        j.add("dungeon",JSON.toJsonTree(dungeons.current(p.getUUID())));
        j.add("dungeonCatalog",JSON.toJsonTree(Catalog.CURRENT.dungeons));
        j.add("materials",JSON.toJsonTree(Catalog.CURRENT.materials));
        j.addProperty("definitionsRevision",Catalog.CURRENT.revision);
        j.addProperty("equipmentState",inventory.clientStorageState(p));
        j.add("industrialRules",JSON.toJsonTree(cn.yumis.fight.industry.IndustrialRules.CURRENT));
        j.add("catalog",JSON.toJsonTree(Catalog.CURRENT));
        j.add("equipmentDefinitions",JSON.toJsonTree(Definitions.CURRENT));
        j.add("equipmentLayout",JSON.toJsonTree(EquipmentLayout.CURRENT));
        j.add("equipmentGroups",JSON.toJsonTree(MenuHooks.extra(p).groups));
        Network.send(p,"profile",j);
    }
    public void message(ServerPlayer p,String message){
        Network.send(p,"message",Map.of("text",message==null?"操作失败":message));
    }
    public boolean managed(Entity e){
        return e instanceof Player||e instanceof Monster||e instanceof Enemy||e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon;
    }
    public Target target(LivingEntity e){
        Target t=journal.get("target/"+e.getUUID(),Target.class,null);
        if(t==null){
            String raw=e.getData(FightMod.TARGET);
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
            t.difficulty=worldDifficulty;
            t.kind=e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon?"DRAGON":e instanceof net.minecraft.world.entity.boss.wither.WitherBoss?"WITHER":"NORMAL";
            if(e instanceof DungeonEnemy mob){
                t.originalLevel=mob.dungeonLevel();
                t.instance=mob.instance();
                t.life.instance=t.instance;
                t.kind=mob.boss()?"BOSS":"NORMAL";
                t.elite=mob.elite();
                var d=dungeons.byId(t.instance);
                if(d!=null){
                    t.partySize=d.roster.size();
                    t.difficulty=d.difficulty;
                }
            }
            else if(t.kind.equals("NORMAL")){
                t.speciesHealth=e.getMaxHealth()/20d;
                var a=e.getAttribute(Attributes.ATTACK_DAMAGE);
                t.speciesAttack=a==null?1:Math.max(.1,a.getBaseValue()/3);
                t.elite=e.getRandom().nextFloat()<.05&&!e.getPersistentData().getBooleanOr("ydnf_spawner",false);
            }
            try{
                journal.put("target/"+e.getUUID(),t);
            }
            catch(Exception ex){
                throw new IllegalStateException(ex);
            }
            e.setData(FightMod.TARGET,JSON.toJson(t));
        }
        t.entityId=e.getId();
        return t;
    }
    public void context(ServerPlayer p,LivingEntity e){
        Target t=target(e);
        Network.send(p,"context",t);
        for(String key:journal.keys("ailments/"+t.id+"/")){
            JsonElement ailments=journal.get(key);
            if(!ailments.isJsonNull())Network.send(p,"ailments",ailments);
        }
    }
    public void handle(ServerPlayer p,String action,JsonObject j){
        try{
            Session s=session(p);
            switch(action){
                case "inventoryClick"->{
                    if(!j.get("session").getAsString().equals(s.id.toString())||p.containerMenu!=p.inventoryMenu)throw new IllegalArgumentException("背包界面已失效");
                    inventory.click(p,p.inventoryMenu,j.get("slot").getAsInt(),j.get("button").getAsInt(),ContainerInput.valueOf(j.get("input").getAsString()));
                }
                case "hello"->{
                    if(j.get("protocol").getAsInt()!=PROTOCOL||j.has("rules")&&j.get("rules").getAsInt()!=VERSION){
                        p.connection.disconnect(cn.yumis.fight.game.ModText.of("模组协议或规则版本不一致"));
                        return;
                    }
                    profile(p);
                }
                case "profile"->profile(p);
                case "context"->{
                    Entity e=p.level().getEntity(j.get("entity").getAsInt());
                    if(e instanceof LivingEntity l&&managed(l))context(p,l);
                }
                case "runtime"->runtime(p,j);
                case "damage","heal","nativeOutcome"->{
                    JsonObject report=j.deepCopy();
                    combatQueue.add(()->report(p,action,report));
                }
                case "progress"->progress(p,j);
                case "professions"->professions(p);
                case "party"->party(p,j);
                case "dungeon"->dungeons.request(p,j);
                case "craft"->craft(p,j);
                case "salvage"->salvage(p,j);
                case "bag"->openBag(p);
                case "currency"->currencyRequest(p,j);
                case "claim"->claimWaiting(p);
                case "visual"->visual(p,j);
                case "cageHit"->dungeons.cageHit(p,j);
                case "cloudLeap"->movement.request(p,j);
                case "movementUi"->movement.ui(p,j);
                default->message(p,"未知操作："+action);
            }
        }
        catch(Exception e){
            message(p,e.getMessage());
        }
    }
    public void runtime(ServerPlayer p,JsonObject j){
        Session s=session(p);
        if(j.has("session")&&!s.id.toString().equals(j.get("session").getAsString()))return;
        long generation=j.has("generation")?j.get("generation").getAsLong():s.life.generation;
        if(generation!=s.life.generation)return;
        Models.Runtime r=JSON.fromJson(j.has("state")?j.get("state"):j,Models.Runtime.class);
        r.validate();
        long issued=j.has("serverIssuedAt")?j.get("serverIssuedAt").getAsLong():r.recordedAt;
        if(r.controlEpoch!=s.resources.controlEpoch||issued<s.resources.recordedAt)return;
        long local=r.recordedAt;
        // These fields belong to the administrator command, never to combat prediction uploads.
        r.training=s.resources.training;
        r.trainingBoundary=s.resources.trainingBoundary;
        // Accepted damage events own these clocks; prediction uploads cannot extend them.
        r.combatUntil=s.resources.combatUntil;
        r.shieldRecoveryAt=s.resources.shieldRecoveryAt;
        r.cooldowns.replaceAll((k,v)->issued+Math.max(0,v-local));
        r.buffs.replaceAll((k,v)->issued+Math.max(0,v-local));
        r.absorptionUntil.replaceAll((k,v)->v==Long.MAX_VALUE?v:issued+Math.max(0,v-local));
        r.recordedAt=issued;
        s.resources=r;
        try{
            journal.put("resources/"+p.getUUID(),r);
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
    private void report(ServerPlayer p,String action,JsonObject j){
        Session s=session(p);
        String key="receipt/"+p.getUUID()+"/"+s.id+"/"+j.get("operation").getAsLong();
        Models.Runtime previousResources=copy(s.resources,Models.Runtime.class);
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
            boolean heal=action.equals("heal"),outcomeOnly=action.equals("nativeOutcome");
            if(j.has("heal")&&j.get("heal").getAsBoolean()!=heal)throw new IllegalArgumentException("伤害类型不一致");
            UUID id=UUID.fromString(j.get("target").getAsString());
            LivingEntity entity=id.equals(p.getUUID())?p:(LivingEntity)p.level().getEntity(id);
            if(entity==null||!managed(entity))throw new IllegalArgumentException("目标不存在");
            if(entity instanceof Player&&entity!=p)throw new IllegalArgumentException("PVP已关闭");
            if(heal&&entity!=p)throw new IllegalArgumentException("仅支持本人恢复");
            Target target=entity==p?null:target(entity);
            Life life=entity==p?copy(s.life,Life.class):target.life;
            if(!j.has("dimension")||!j.get("dimension").getAsString().equals(p.level().dimension().identifier().toString()))throw new IllegalArgumentException("维度已改变");
            if(j.get("generation").getAsLong()!=life.generation||j.get("basis").getAsLong()!=life.basis)throw new IllegalArgumentException("生命或扩血基准已失效");
            String instance=j.has("instance")?j.get("instance").getAsString():"";
            if(!instance.equals(life.instance)||!instance.isEmpty()&&!dungeons.participant(p,instance))throw new IllegalArgumentException("副本轮次已失效");
            boolean nativeHit=nativeHits.validate(p,entity,j);
            if(heal&&nativeHit)throw new IllegalArgumentException("原生命中不能用于治疗");
            JsonObject dot=null;
            if(j.has("effect")){
                dot=validateDot(p,j,target);
            }
            double fraction=j.get("fraction").getAsDouble();
            String outcome=j.has("outcome")?j.get("outcome").getAsString():"HIT";
            if(!Set.of("HIT","DODGE","IMMUNE").contains(outcome)||!outcome.equals("HIT")&&fraction!=0)throw new IllegalArgumentException("命中结果无效");
            double taken=j.has("damageTaken")?j.get("damageTaken").getAsDouble():0;
            finite(taken);
            if(taken<0||!outcome.equals("HIT")&&taken!=0)throw new IllegalArgumentException("承伤结果无效");
            if(outcomeOnly){
                boolean bossHit=entity==p&&outcome.equals("HIT")&&taken>0&&j.has("feedbackEntity")&&p.level().getEntity(UUID.fromString(j.get("feedbackEntity").getAsString())) instanceof LivingEntity origin&&managed(origin);
                if(heal||entity!=p||fraction!=0||!nativeHit&&!bossHit)throw new IllegalArgumentException("零伤害命中来源无效");
            }
            Receipt receipt=outcomeOnly?Models.noDamageReceipt(life):Models.apply(life,fraction,heal);
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
            if(j.has("runtime"))runtime(p,j.getAsJsonObject("runtime"));
            if(!heal&&(receipt.actual()>0||entity==p&&outcome.equals("HIT")&&taken>0)){
                long eventTime=nativeHit?nativeHits.occurredAt(j):j.has("occurredAt")?Math.min(System.currentTimeMillis(),j.get("occurredAt").getAsLong()):System.currentTimeMillis();
                if(entity==p)CombatTiming.taken(s.resources,eventTime);else CombatTiming.dealt(s.resources,eventTime);
                journal.put("resources/"+p.getUUID(),s.resources);
            }
            ack.addProperty("combatUntil",s.resources.combatUntil);
            ack.addProperty("shieldRecoveryAt",s.resources.shieldRecoveryAt);
            if(dot!=null){
                dot.addProperty("nextIndex",dot.get("nextIndex").getAsInt()+1);
                dot.addProperty("nextAt",System.currentTimeMillis()+1000);
                journal.put("ailments/"+id+"/"+p.getUUID()+"/BLEED",dot);
            }
            journal.put(key,stored);
            if(nativeHit)nativeHits.commit(p,j);
            journal.put("life/"+id,life);
            if(target!=null){
                target.life=life;
                if(receipt.actual()>0&&!heal)target.contributions.merge(p.getStringUUID(),receipt.actual(),Double::sum);
                journal.put("target/"+id,target);
                if(receipt.accepted()&&!receipt.deathId().isEmpty()&&journal.get("death/"+receipt.deathId()).isJsonNull()){
                    journal.put("death/"+receipt.deathId(),p.getUUID());
                    loot.death(p,entity,target);
                }
            }
            JsonObject ailment=null;
            if(j.has("ailment")&&receipt.actual()>0)ailment=applyAilment(p,entity,j.getAsJsonObject("ailment"),target);
            journal.finish();
            durable=true;
            if(ailment!=null){
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("ailments",JSON.toJson(ailment)));
                if(ailment.get("kind").getAsString().equals("STUN")){
                    entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(FightMod.STUN,(int)Math.max(1,(ailment.get("expires").getAsLong()-System.currentTimeMillis()+49)/50)));
                    dungeons.cancelBoss(entity.getUUID());
                }
            }
            if(life.dead&&entity instanceof DungeonEnemy)dungeons.died(entity.getUUID());
            if(entity==p)s.life=life;
            else entity.setData(FightMod.TARGET,JSON.toJson(target));
            nativeHits.suppressProjectionFeedback=nativeHit&&life.dead;
            bridge=true;
            try{
                if(life.dead&&!entity.isDeadOrDying())entity.hurtServer(p.level(),entity==p?entity.damageSources().genericKill():nativeHit?nativeHits.source(j):entity.damageSources().playerAttack(p),Float.MAX_VALUE);
                else if(!life.dead)entity.setHealth((float)Math.max(.001,entity.getMaxHealth()*life.q));
            }
            finally{
                bridge=false;
                nativeHits.suppressProjectionFeedback=false;
            }
            if(nativeHit)nativeHits.complete(j);
            Network.send(p,"ack",ack);
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("health",JSON.toJson(life)));
            if(!heal&&!nativeHit&&(receipt.actual()>0||entity==p&&taken>0)){
                entity.hurtTime=10;
                entity.hurtDuration=10;
                LivingEntity feedbackAttacker=p;
                if(entity==p&&j.has("feedbackEntity")&&p.level().getEntity(UUID.fromString(j.get("feedbackEntity").getAsString())) instanceof LivingEntity origin&&managed(origin))feedbackAttacker=origin;
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
                p.connection.disconnect(cn.yumis.fight.game.ModText.of("战斗结果已保存，投影应用失败，请重连："+error.getMessage()));
                return;
            }
            message(p,error.getMessage());
            JsonObject ack=new JsonObject();
            ack.addProperty("session",s.id.toString());
            ack.add("operation",j.get("operation"));
            ack.add("receipt",JSON.toJsonTree(new Receipt(false,error.getMessage(),0,0,0,"")));
            Network.send(p,"ack",ack);
        }
    }
    private JsonObject validateDot(ServerPlayer p,JsonObject report,Target target){
        if(target==null)throw new IllegalArgumentException("持续伤害目标无效");
        String key="ailments/"+target.id+"/"+p.getUUID()+"/BLEED";
        JsonObject effect=journal.get(key).isJsonObject()?journal.get(key).getAsJsonObject():null;
        Session owner=session(p);
        if(effect==null||owner.life.dead||!effect.get("session").getAsString().equals(owner.id.toString())||effect.get("ownerGeneration").getAsLong()!=owner.life.generation||effect.get("generation").getAsLong()!=target.life.generation||effect.get("basis").getAsLong()!=target.life.basis||effect.get("expires").getAsLong()<System.currentTimeMillis()||!effect.getAsJsonObject("change").get("effect").equals(report.get("effect"))||effect.get("nextIndex").getAsInt()!=report.get("index").getAsInt())throw new IllegalArgumentException("持续伤害生命周期或顺序已失效");
        return effect;
    }
    private JsonObject applyAilment(ServerPlayer p,LivingEntity entity,JsonObject change,Target target)throws Exception{
        if(target==null||target.life.dead)return null;
        String kind=change.get("kind").getAsString();
        if(!Set.of("BLEED","SHOCK","STUN").contains(kind))throw new IllegalArgumentException("未知异常类型");
        long ticks=change.get("durationTicks").getAsLong();
        if(ticks<=0)throw new IllegalArgumentException("异常持续时间无效");
        String key="ailments/"+entity.getUUID()+"/"+p.getUUID()+"/"+kind;
        JsonElement previous=journal.get(key);
        JsonObject state=new JsonObject();
        state.addProperty("target",entity.getStringUUID());
        state.addProperty("generation",target.life.generation);
        state.addProperty("basis",target.life.basis);
        state.addProperty("owner",p.getStringUUID());
        state.addProperty("ownerGeneration",session(p).life.generation);
        state.addProperty("session",session(p).id.toString());
        state.addProperty("kind",kind);
        state.addProperty("dimension",entity.level().dimension().identifier().toString());
        state.addProperty("instance",target.life.instance);
        long expires=Math.addExact(System.currentTimeMillis(),Math.multiplyExact(ticks,50));
        if(kind.equals("STUN")&&previous.isJsonObject())expires=Math.max(expires,previous.getAsJsonObject().get("expires").getAsLong());
        state.addProperty("expires",expires);
        state.add("change",change);
        state.addProperty("nextIndex",0);
        state.addProperty("nextAt",kind.equals("BLEED")&&previous.isJsonObject()?previous.getAsJsonObject().get("nextAt").getAsLong():System.currentTimeMillis()+1000);
        journal.put(key,state);
        return state;
    }
    private void expireAilments(){
        long now=System.currentTimeMillis();
        for(String key:journal.keys("ailments/")){
            JsonElement raw=journal.get(key);
            if(!raw.isJsonObject())continue;
            JsonObject j=raw.getAsJsonObject();
            ServerPlayer owner=server.getPlayerList().getPlayer(UUID.fromString(j.get("owner").getAsString()));
            LivingEntity entity=null;
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(j.get("dimension").getAsString())));
            if(level!=null&&level.getEntity(UUID.fromString(j.get("target").getAsString())) instanceof LivingEntity living)entity=living;
            boolean valid=owner!=null&&entity!=null&&entity.isAlive()&&j.get("expires").getAsLong()>now;
            if(valid){
                Session source=session(owner);
                Target target=target(entity);
                valid=!source.life.dead&&source.id.toString().equals(j.get("session").getAsString())&&source.life.generation==j.get("ownerGeneration").getAsLong()&&owner.level()==entity.level()&&target.life.generation==j.get("generation").getAsLong()&&target.life.basis==j.get("basis").getAsLong()&&source.life.instance.equals(j.get("instance").getAsString());
            }
            if(!valid)try{
                journal.put(key,null);
                if(entity!=null)net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity,new Network.Snapshot("ailmentsClear",JSON.toJson(j)));
            }
            catch(Exception e){
                throw new IllegalStateException(e);
            }
        }
    }
    private void incoming(LivingIncomingDamageEvent e){
        nativeHits.incoming(e);
    }
    public void craft(ServerPlayer p,JsonObject j){
        Session s=session(p);
        if(!(p.containerMenu instanceof FabricatorMenu m)||!m.stillValid(p))throw new IllegalArgumentException("请使用装备制作器");
        if(!Permissions.allowed(p.getUUID(),"craft"))throw new IllegalArgumentException("没有制作权限");
        Slot slot=Slot.valueOf(j.get("slot").getAsString());
        int level=j.get("level").getAsInt();
        if(level>s.progress.level)throw new IllegalArgumentException("制作等级超过角色等级");
        Weapon weapon=Weapon.valueOf(j.get("weapon").getAsString());
        ArmorType armorType=j.has("armorType")?ArmorType.valueOf(j.get("armorType").getAsString()):ArmorType.LEGACY;
        JewelryType jewelryType=j.has("jewelryType")?JewelryType.valueOf(j.get("jewelryType").getAsString()):JewelryType.LEGACY;
        if(slot.armor()&&(armorType==ArmorType.LEGACY||jewelryType!=JewelryType.LEGACY)||slot.jewelry()&&(jewelryType==JewelryType.LEGACY||armorType!=ArmorType.LEGACY)||!slot.armor()&&!slot.jewelry()&&(armorType!=ArmorType.LEGACY||jewelryType!=JewelryType.LEGACY))throw new IllegalArgumentException("请选择有效装备类型");
        List<Catalog.Need> needs=Catalog.CURRENT.recipe(slot,level);
        String op=j.get("operation").getAsString();
        Profession prof=profession(p,"craftsman");
        prof.award(60,false);
        Map<String,JsonElement> edits=Map.of("profession/"+p.getUUID()+"/craftsman",JSON.toJsonTree(prof));
        boolean applied=inventory.transact(p,op,j,()->{
            for(var need:needs){
                int total=0;for(int i=0;i<36;i++){
                    ItemStack stack=p.getInventory().getItem(i);if(matches(stack,need))total+=stack.getCount();
                }
                if(total<need.count())throw new IllegalArgumentException("材料不足："+(need.regional()?Catalog.CURRENT.materials.get(need.id()).name():need.id()));
            }
            for(var need:needs){
                int remaining=need.count();for(int i=0;i<36&&remaining>0;i++){
                    ItemStack stack=p.getInventory().getItem(i);if(matches(stack,need)){
                        int n=Math.min(remaining,stack.getCount());stack.shrink(n);remaining-=n;
                    }
                }
            }
            inventory.requireSpace(p,GameItems.gear(EquipmentRules.create(slot,weapon,Rarity.COMMON,level,armorType,jewelryType,random)));
        }
        ,edits);
        if(applied)message(p,"制作成功，工匠经验 +60");
        profile(p);
    }
    private boolean matches(ItemStack stack,Catalog.Need need){
        return !stack.isEmpty()&&(need.regional()?GameItems.material(stack).equals(need.id()):BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(need.id()));
    }
    public Profession profession(ServerPlayer p,String id){
        return journal.get("profession/"+p.getUUID()+"/"+id,Profession.class,new Profession());
    }
    public void awardProfession(ServerPlayer p,String id,long xp,String operation){
        awardProfession(p,id,xp,operation,false);
    }
    public void awardProfession(ServerPlayer p,String id,long xp,String operation,boolean administrator){
        if(!Set.of("craftsman","enchanter","farmer","fisherman").contains(id)||xp<=0||operation==null||operation.isBlank())throw new IllegalArgumentException("无效副职业奖励");
        try{
            String key="professionReceipt/"+p.getUUID()+"/"+operation;
            JsonElement intent=JSON.toJsonTree(Map.of("profession",id,"xp",xp,"administrator",administrator));
            JsonElement previous=journal.get(key);
            if(!previous.isJsonNull()){
                if(!previous.equals(intent))throw new IllegalArgumentException("副职业奖励操作号冲突");
                return;
            }
            Profession prof=profession(p,id);
            prof.award(xp,administrator);
            journal.begin();
            journal.put("profession/"+p.getUUID()+"/"+id,prof);
            journal.put(key,intent);
            journal.finish();
        }
        catch(Exception e){
            journal.abort();
            throw new IllegalStateException(e);
        }
    }
    public void professions(ServerPlayer p){
        Map<String,Object> value=new LinkedHashMap<>();
        Map<String,Object> all=new LinkedHashMap<>();
        for(String id:List.of("craftsman","enchanter","farmer","fisherman")){
            Profession pr=profession(p,id);
            all.put(id,Map.of("level",pr.level(),"experience",pr.experience.toString(),"coins",pr.coins.toString()));
        }
        value.put("professions",all);
        value.put("gold",journal.get("gold/"+p.getUUID(),Long.class,0L));
        Network.send(p,"professions",value);
    }
    private void progress(ServerPlayer p,JsonObject j){
        Session s=session(p);
        Progress next=copy(s.progress,Progress.class);
        String kind=j.get("kind").getAsString();
        if(!kind.equals("bind")&&System.currentTimeMillis()<s.resources.combatUntil)throw new IllegalArgumentException("请在连续3秒未造成或受到伤害后操作");
        String op=j.get("operation").getAsString(),key="progressReceipt/"+p.getUUID()+"/"+op;
        JsonElement previous=journal.get(key);
        if(!previous.isJsonNull()){
            if(!previous.equals(j))throw new IllegalArgumentException("操作号冲突");
            profile(p);
            return;
        }
        switch(kind){
            case "learn"->next.learn(Skill.valueOf(j.get("skill").getAsString()),j.has("count")?j.get("count").getAsInt():1);
            case "allocate"->next.allocate(j.get("stat").getAsString(),j.has("count")?j.get("count").getAsInt():1);
            case "resetAttributes"->{
                next.strength=next.intelligence=next.spirit=0;
            }
            case "resetSkills"->next.resetSkills();
            case "sync"->next.sync=j.get("enabled").getAsBoolean();
            case "bind"->{
                int slot=j.get("slot").getAsInt();
                if(slot<0||slot>=SLOT_COUNT)throw new IllegalArgumentException("快捷槽无效");
                String skill=j.get("skill").getAsString();
                if(!skill.isEmpty()&&(Skill.valueOf(skill).passive()||next.learned(Skill.valueOf(skill))<=0))throw new IllegalArgumentException("该技能尚未学习或为被动");
                next.slots[slot]=skill;
            }
            case "quest"->{
                if(next.level<20||next.swordmaster)throw new IllegalArgumentException("转职条件未满足");
                next.questAccepted=true;
            }
            case "job"->{
                if(next.swordmaster||next.level<20||!next.questAccepted||next.questKills<50||next.questElites<3)throw new IllegalArgumentException("转职任务尚未完成");
                next.swordmaster=true;
                inventory.transact(p,op,j,()->{
                    consume(p,Items.IRON_INGOT,32);consume(p,Items.LAPIS_LAZULI,16);
                }
                ,Map.of("player/"+p.getUUID(),JSON.toJsonTree(next),key,j));
                s.progress=next;
                profile(p);
                return;
            }
            default->throw new IllegalArgumentException("未知进度操作");
        }
        try{
            journal.begin();
            journal.put("player/"+p.getUUID(),next);
            journal.put(key,j);
            journal.finish();
            s.progress=next;
            profile(p);
        }
        catch(Exception e){
            journal.abort();
            throw new IllegalStateException(e);
        }
    }
    public void consume(ServerPlayer p,Item item,int count){
        int total=0;
        for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(item))total+=p.getInventory().getItem(i).getCount();
        if(total<count)throw new IllegalArgumentException("材料不足");
        for(int i=0;i<36&&count>0;i++){
            var stack=p.getInventory().getItem(i);
            if(stack.is(item)){
                int take=Math.min(count,stack.getCount());
                stack.shrink(take);
                count-=take;
            }
        }
    }
    public boolean currencyClick(ServerPlayer p,AbstractContainerMenu menu,int slot,int button,ContainerInput input){
        return currencyClick(p,menu,slot,button,input,-1);
    }
    public boolean currencyClick(ServerPlayer p,AbstractContainerMenu menu,int slot,int button,ContainerInput input,int socket){
        if(button!=1||input!=ContainerInput.PICKUP||slot<0||slot>=menu.slots.size())return false;
        ItemStack cursor=menu.getCarried(),stack=menu.slots.get(slot).getItem();
        Gear gear=GameItems.gear(stack);
        if(gear==null||cursor.isEmpty())return false;
        String id=GameItems.id(cursor);
        if(!CurrencySlots.isCurrency(cursor))return false;
        CurrencySlots.requireWritable(p,menu,slot);
        if(id.equals("set_extraction_stone")){
            if(gear.set.isEmpty())throw new IllegalArgumentException("该装备没有套装");
            ItemStack stone=GameItems.payload("set_stone",Map.of("set",gear.set,"level",gear.level,"group",gear.slot.armor()?"armor":"jewelry"),"套装石 · "+Definitions.CURRENT.sets.get(gear.set).name());
            menu.slots.get(slot).set(ItemStack.EMPTY);
            cursor.shrink(1);
            inventory.requireSpace(p,stone);
            return true;
        }
        if(id.equals("set_stone")){
            JsonObject payload=JsonParser.parseString(GameItems.payload(cursor)).getAsJsonObject();
            boolean armor=payload.get("group").getAsString().equals("armor");
            if(gear.level<payload.get("level").getAsInt()||armor&&!gear.slot.armor()||!armor&&!gear.slot.jewelry())throw new IllegalArgumentException("套装石部位或来源等级不符");
            String setId=payload.get("set").getAsString();
            var definition=Definitions.CURRENT.sets.get(setId);
            if(definition==null||!definition.group().equals(payload.get("group").getAsString()))throw new IllegalArgumentException("套装定义或分类不符");
            gear.set=setId;
            gear.revision++;
        }
        else if(id.equals("affix_gem")){
            Gem gem=JSON.fromJson(GameItems.payload(cursor),Gem.class);
            gear=EquipmentRules.socket(gear,gem,socket>=0?socket:gear.gems.size()<gear.sockets?gear.gems.size():0);
        }
        else if(id.equals("affix_extraction_stone"))throw new IllegalArgumentException("特殊装备词条拆卸暂未开放");
        else gear=EquipmentRules.process(gear,id,random);
        ItemStack updated=stack.copy();
        GameItems.write(updated,gear);
        menu.slots.get(slot).set(updated);
        cursor.shrink(1);
        return true;
    }
    public void currencyRequest(ServerPlayer p,JsonObject j){
        JsonElement prior=journal.get("inventoryReceipt/"+p.getUUID()+"/"+j.get("operation").getAsString());
        if(!prior.isJsonNull()){
            if(!prior.getAsJsonObject().get("intent").equals(j))throw new IllegalArgumentException("操作号冲突");
            inventory.restore(p);
            profile(p);
            return;
        }
        if(!j.get("session").getAsString().equals(session(p).id.toString())||j.get("menu").getAsInt()!=p.containerMenu.containerId)throw new IllegalArgumentException("加工界面或会话已失效");
        int index=j.get("slot").getAsInt();
        var targetSlot=CurrencySlots.requireWritable(p,p.containerMenu,index);
        Gear g=GameItems.gear(targetSlot.getItem());
        if(g==null||!g.instance.toString().equals(j.get("equipment").getAsString())||g.revision!=j.get("revision").getAsInt()||!j.has("cursor")||!inventory.encode(p,p.containerMenu.getCarried()).equals(j.get("cursor")))throw new IllegalArgumentException("装备或通货已发生变化");
        inventory.transact(p,j.get("operation").getAsString(),j,()->{
            if(!currencyClick(p,p.containerMenu,index,1,ContainerInput.PICKUP,j.has("socket")?j.get("socket").getAsInt():-1))throw new IllegalArgumentException("无法加工");
        }
        ,Map.of());
        profile(p);
    }
    public void salvage(ServerPlayer p,JsonObject j){
        if(!(p.containerMenu instanceof FabricatorMenu m)||!m.stillValid(p))throw new IllegalArgumentException("请使用装备制作器");
        int index=j.get("inventorySlot").getAsInt();
        if(index<0||index>=36)throw new IllegalArgumentException("背包槽无效");
        inventory.transact(p,j.get("operation").getAsString(),j,()->{
            Gear gear=GameItems.gear(p.getInventory().getItem(index));
            if(gear==null)throw new IllegalArgumentException("请选择装备");
            String upgrade=CurrencyRules.salvageCurrency(gear.rarity);
            p.getInventory().setItem(index,ItemStack.EMPTY);inventory.requireSpace(p,GameItems.item(upgrade,1));
        }
        ,Map.of());
        message(p,"分解完成，获得1枚升级石");
        profile(p);
    }
    private void openBag(ServerPlayer p){
        inventory.closeCursor(p);
        cn.yumis.fight.industry.DimensionalMenu.open(p);
    }
    private void claimWaiting(ServerPlayer p){
        JsonElement waiting=journal.get("equipmentWaiting/"+p.getUUID());
        if(waiting.isJsonNull())return;
        Map<String,JsonElement> changes=new HashMap<>();
        JsonObject intent=new JsonObject();
        intent.addProperty("claimWaiting",true);
        inventory.transact(p,UUID.randomUUID().toString(),intent,()->{
            JsonArray remaining=new JsonArray();for(JsonElement entry:waiting.getAsJsonArray()){
                ItemStack stack=inventory.decode(p,entry);InventoryPacking.insert(p,stack);if(!stack.isEmpty())remaining.add(inventory.encode(p,stack));
            }
            changes.put("equipmentWaiting/"+p.getUUID(),remaining);
        }
        ,changes);
        message(p,"已领取可放入背包的暂存装备");
    }
    public Team team(UUID player){
        for(String key:journal.keys("team/")){
            Team team=journal.get(key,Team.class,null);
            if(team!=null&&team.members.contains(player))return team;
        }
        return null;
    }
    private void party(ServerPlayer p,JsonObject j)throws Exception{
        String kind=j.get("kind").getAsString();
        Team t=team(p.getUUID());
        ServerPlayer target=j.has("name")?server.getPlayerList().getPlayerByName(j.get("name").getAsString()):null;
        long now=System.currentTimeMillis();
        switch(kind){
            case "create"->{
                if(t!=null)throw new IllegalArgumentException("你已有队伍");
                t=new Team();
                t.leader=p.getUUID();
                t.members.add(p.getUUID());
            }
            case "invite","approve","kick","transfer","disband"->{
                if(t==null||!t.leader.equals(p.getUUID()))throw new IllegalArgumentException("仅队长可以操作");
                if(kind.equals("disband")){
                    journal.put("team/"+t.id,null);
                    refreshAll();
                    return;
                }
                if(target==null)throw new IllegalArgumentException("目标玩家不在线");
                if(kind.equals("invite")){
                    if(team(target.getUUID())!=null)throw new IllegalArgumentException("目标已有队伍");
                    t.invitations.put(target.getStringUUID(),now+300000);
                }
                if(kind.equals("approve")){
                    if(t.applications.getOrDefault(target.getStringUUID(),0L)<now||team(target.getUUID())!=null)throw new IllegalArgumentException("申请已失效");
                    t.members.add(target.getUUID());
                    t.applications.remove(target.getStringUUID());
                }
                if(kind.equals("kick")){
                    if(target==p)throw new IllegalArgumentException("队长请移交或解散");
                    t.members.remove(target.getUUID());
                }
                if(kind.equals("transfer")){
                    if(!t.members.contains(target.getUUID()))throw new IllegalArgumentException("目标不在队伍");
                    t.leader=target.getUUID();
                }
            }
            case "apply","accept"->{
                if(t!=null||target==null)throw new IllegalArgumentException("已有队伍或目标不在线");
                t=team(target.getUUID());
                if(t==null)throw new IllegalArgumentException("目标没有队伍");
                if(kind.equals("apply"))t.applications.put(p.getStringUUID(),now+300000);
                else{
                    if(t.invitations.getOrDefault(p.getStringUUID(),0L)<now)throw new IllegalArgumentException("邀请已失效");
                    t.members.add(p.getUUID());
                    t.invitations.remove(p.getStringUUID());
                }
            }
            case "leave"->{
                if(t==null)return;
                if(t.leader.equals(p.getUUID())&&t.members.size()>1)throw new IllegalArgumentException("请先手动移交队长");
                t.members.remove(p.getUUID());
                if(t.members.isEmpty()){
                    journal.put("team/"+t.id,null);
                    refreshAll();
                    return;
                }
            }
            default->throw new IllegalArgumentException("未知队伍操作");
        }
        journal.put("team/"+t.id,t);
        refreshAll();
    }
    public void refreshAll(){
        for(ServerPlayer p:server.getPlayerList().getPlayers())profile(p);
    }
    private void visual(ServerPlayer p,JsonObject j){
        visuals.receive(p,j);
    }
    private void tick(){
        ticks++;
        cn.yumis.fight.industry.IndustrialWorld.tick(this);
        movement.tick();
        int size=combatQueue.size();
        for(int i=0;i<size;i++)combatQueue.removeFirst().run();
        if(ticks%5==0){
            dungeons.tick();
            loot.tick();
            expireAilments();
            WorldRules.tick(this,ticks);
            visuals.tick();
        }
        if(ticks%20==0)for(ServerPlayer p:server.getPlayerList().getPlayers()){
            Session s=session(p);
            for(int i=0;i<p.getInventory().getContainerSize();i++){
                ItemStack stack=p.getInventory().getItem(i);
                if(!stack.isEmpty()&&GameItems.own(stack)&&GameItems.GEAR_IDS.contains(GameItems.id(stack))&&GameItems.gear(stack)==null){
                    String id=GameItems.id(stack);
                    Slot slot=Arrays.stream(Slot.values()).filter(x->x.item().equals(id)).findFirst().orElse(Slot.WEAPON);
                    Weapon w=Arrays.stream(Weapon.values()).filter(x->x.name().equalsIgnoreCase(id)).findFirst().orElse(Weapon.KATANA);
                    if(s.progress.level>=slot.minimum())GameItems.write(stack,EquipmentTemplates.createStandard(slot,w,Rarity.COMMON,s.progress.level,random));
                }
            }
            if(inventory.isBag(p)&&!(p.containerMenu instanceof cn.yumis.fight.industry.DimensionalMenu))inventory.bag(p,false);
            Stats st=stats(p);
            var max=p.getAttribute(Attributes.MAX_HEALTH);
            if(!JSON.toJson(st).equals(s.statsKey))profile(p);
        }
        for(ServerLevel level:server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof Mob mob){
            boolean stun=mob.hasEffect(FightMod.STUN);
            if(stun&&!stunned.containsKey(e.getUUID())){
                stunned.put(e.getUUID(),mob.isNoAi());
                mob.setNoAi(true);
                mob.getNavigation().stop();
            }
            else if(!stun&&stunned.containsKey(e.getUUID()))mob.setNoAi(stunned.remove(e.getUUID()));
        }
    }
}
