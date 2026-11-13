package cn.yumis.fight.modules.dungeons.application;
import cn.yumis.fight.modules.dungeons.content.GoblinCamp;
import cn.yumis.fight.modules.dungeons.content.DragonTower;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import cn.yumis.fight.modules.dungeons.api.CageEntity;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import static cn.yumis.fight.modules.dungeons.api.EncounterScaling.expandedQ;

import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.content.*;
import cn.yumis.fight.foundation.Journal;
import net.minecraft.server.MinecraftServer;
import java.util.function.Function;
import cn.yumis.fight.*;

import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.party.api.Team;
import cn.yumis.fight.modules.dungeons.api.ReturnPoint;
import cn.yumis.fight.modules.dungeons.api.Dungeon;

import static cn.yumis.fight.modules.combat.api.LifeRules.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;

import net.minecraft.world.phys.Vec3;
import java.util.*;
public class DungeonService {
    private final DungeonLayouts layouts;
    private final GoblinCampEncounterService goblinCamp;
    public final GblEncounterService gbl;
    public final PalaceEncounterService palace;
    public final ForbiddenEncounterService forbidden;
    public final SkasaEncounterService skasa;
    public final DarkCityEncounterService darkCity;
    private final List<ClassicEncounterService> classic;
    @FunctionalInterface private interface EnemyBrain {void tick(Dungeon d,DungeonEnemy e,List<ServerPlayer> players,long now)throws Exception;}
    private final Map<String,EnemyBrain> enemyBrains=new HashMap<>();
    private final Map<String,java.util.function.Consumer<Dungeon>> waves=new HashMap<>();
    private final cn.yumis.fight.modules.character.api.CharacterPersistence characters;
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    public static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,Identifier.fromNamespaceAndPath(ItemData.NS,"dungeon"));
    private final Journal journal;
    private final MinecraftServer server;
    private final DungeonPorts.Players playerAccess;
    private final DungeonPorts.Targets targets;
    final DungeonPorts.Rewards rewards;
    public TreasureHuntService treasure;
    public WaystoneService waystones;
    private boolean stopping;
    private Set<String> activeInstances=new LinkedHashSet<>();
    private boolean instanceIndexValid;
    public void installWaystones(cn.yumis.fight.modules.inventory.api.InventoryOperations inventory,cn.yumis.fight.modules.dungeons.api.WaystonePorts rewards,Runnable recovery,Random random){waystones=new WaystoneService(this,journal,server,playerAccess,targets,meter,inventory,rewards,recovery,random);}
    public void installTreasure(cn.yumis.fight.modules.inventory.api.InventoryOperations inventory,cn.yumis.fight.modules.dungeons.api.TreasurePorts rewards,Random random){treasure=new TreasureHuntService(this,journal,server,playerAccess,targets,inventory,rewards,random);}
    private final DungeonPorts.Meter meter;
    private final Function<UUID,Team> team;
    private final Runnable refresh;
    private final Map<UUID,Cast> casts=new HashMap<>();
    private final Map<String,Long> cooldowns=new HashMap<>();
    private final Map<String,Cage> cages=new HashMap<>();
    private final Map<UUID,Long> stones=new HashMap<>();
    private record Cast(UUID id,UUID boss,JsonObject data,long active,long end) {
    }
    private static class Cage {
        String id,instance;
        UUID boss;
        long expires;
        double x,y,z;
        int hits;
        List<CageEntity> entities=new ArrayList<>();
        Set<String> operations=new HashSet<>();
    }
    public DungeonService(Journal journal,cn.yumis.fight.modules.character.api.CharacterPersistence characters,MinecraftServer server,DungeonPorts.Players players,DungeonPorts.Targets targets,DungeonPorts.Rewards rewards,DungeonPorts.Meter meter,Function<UUID,Team> team,Runnable refresh,DungeonPorts.CampDecorations decorations){
        this.characters=characters;
        this.journal=journal;this.server=server;this.playerAccess=players;this.targets=targets;this.rewards=rewards;this.meter=meter;this.team=team;this.refresh=refresh;
        this.layouts=new DungeonLayouts(decorations);
        this.goblinCamp=new GoblinCampEncounterService(this,journal,targets);
        this.gbl=new GblEncounterService(this,journal,players,targets);
        this.palace=new PalaceEncounterService(this,players,targets);
        this.forbidden=new ForbiddenEncounterService(this,journal,players,targets);
        this.skasa=new SkasaEncounterService(this,journal,players,targets);
        this.darkCity=new DarkCityEncounterService(this,journal,players,targets);
        this.classic=List.of(new TavernEncounterService(this,journal,players,targets),new HamelinEncounterService(this,journal,players,targets),
            new PursuitEncounterService(this,journal,players,targets),new SeaTrainEncounterService(this,journal,players,targets),
            new TimeGateEncounterService(this,journal,players,targets),new AwarenessEncounterService(this,journal,players,targets),
            new GrandineEncounterService(this,journal,players,targets),new ForestEncounterService(this,journal,players,targets));
        for(var kind:cn.yumis.fight.modules.dungeons.api.GblEnemyKind.values()){enemyBrains.put(kind.id,gbl::brain);}
        for(var kind:cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind.values()){enemyBrains.put(kind.id,palace::brain);}
        for(var kind:cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind.values()){enemyBrains.put(kind.id,forbidden::brain);}
        for(var kind:cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind.values()){enemyBrains.put(kind.id,skasa::brain);}
        for(var kind:cn.yumis.fight.modules.dungeons.api.DarkCityEnemyKind.values())enemyBrains.put(kind.id,darkCity::brain);
        waves.put("goblin_camp",goblinCamp::initialize);waves.put("dragon_tower",this::spawnDragonWave);
        waves.put("gbl_church",this::spawnGblWave);waves.put("castle_palace",palace::spawn);
        waves.put("behemoth_forbidden",forbidden::spawn);
        waves.put("skasa",skasa::spawn);
        waves.put("dark_city",darkCity::spawn);
        for(var encounter:classic){
            waves.put(encounter.dungeon,encounter::spawn);
            for(var kind:cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.values())if(kind.dungeon.equals(encounter.dungeon))enemyBrains.put(kind.id,encounter::brain);
        }
    }
    public Dungeon byId(String id){
        if(id==null||id.isEmpty())return null;
        return journal.get("instance/"+id,Dungeon.class,null);
    }
    private void ensureInstanceIndex(){
        if(instanceIndexValid)return;
        Set<String> active=new LinkedHashSet<>();
        for(String key:journal.keys("instance/")){
            Dungeon d=journal.get(key,Dungeon.class,null);
            if(d!=null&&!d.status.equals("CLOSED"))active.add(key.substring(9));
        }
        activeInstances=active;instanceIndexValid=true;
    }
    List<String> activeInstanceIds(){ensureInstanceIndex();return List.copyOf(activeInstances);}
    /** Call after the outer transaction has committed, never while its writes are still staged. */
    void instanceCommitted(UUID id){
        if(!instanceIndexValid)return;
        try{
            Dungeon d=byId(id.toString());
            if(d!=null&&!d.status.equals("CLOSED"))activeInstances.add(id.toString());
            else activeInstances.remove(id.toString());
        }catch(RuntimeException failure){instanceIndexValid=false;}
    }
    public Dungeon current(UUID p){
        for(String instance:activeInstanceIds()){
            Dungeon d=byId(instance);
            if(d!=null&&!d.status.equals("CLOSED")&&!(d.treasure!=null&&d.treasure.region==0&&d.treasure.cleared)&&d.roster.contains(p)&&!d.withdrawn.contains(p))return d;
        }
        return null;
    }
    public boolean participant(ServerPlayer p,String id){
        Dungeon d=byId(id);
        if(d!=null&&d.treasure!=null&&treasure!=null)return treasure.inside(d,p);
        return d!=null&&!d.status.equals("CLOSED")&&d.roster.contains(p.getUUID())&&!d.withdrawn.contains(p.getUUID());
    }
    ServerLevel level(){
        ServerLevel l=server.getLevel(DIMENSION);
        if(l==null)throw new IllegalArgumentException("地下城维度尚未加载");
        return l;
    }
    public void recover()throws Exception{
        instanceIndexValid=false;
        for(String key:journal.keys("instance/")){
            Dungeon d=journal.get(key,Dungeon.class,null);
            if(d!=null&&!d.status.equals("CLOSED")){
                d.status="CLOSED";
                journal.put(key,d);
                forceArena(d,false);
                for(var e:d.returns.entrySet())journal.put("return/"+e.getKey(),e.getValue());
            }
        }
        activeInstances=new LinkedHashSet<>();instanceIndexValid=true;
        ServerLevel l=server.getLevel(DIMENSION);
        if(l!=null){goblinCamp.recover();for(Entity e:l.getAllEntities())if(e instanceof DungeonEnemy||e instanceof CageEntity)e.discard();}
        for(ServerLevel world:server.getAllLevels())for(Entity e:world.getAllEntities())if(e instanceof DungeonEnemy mob){Dungeon d=byId(mob.instance());if(d==null||d.status.equals("CLOSED"))e.discard();}
    }
    public void request(ServerPlayer p,JsonObject j)throws Exception{
        String kind=j.get("kind").getAsString();
        if(kind.equals("leave")){
            leave(p,true);
            return;
        }
        String id=j.get("dungeon").getAsString();
        DungeonCatalog.DungeonDef def=DungeonCatalog.current().dungeon(id);
        if(!def.available())throw new IllegalArgumentException("该副本尚未开放");
        Dungeon old=current(p.getUUID());
        Difficulty difficulty=Difficulty.valueOf(j.get("difficulty").getAsString());
        String operation=j.get("operation").getAsString(),key="dungeonReceipt/"+p.getUUID()+"/"+playerAccess.session(p).id+"/"+operation;
        JsonElement previous=journal.get(key);if(!previous.isJsonNull()){if(!previous.equals(j))throw new IllegalArgumentException("入场操作号冲突");playerAccess.profile(p);return;}
        if(old!=null){if(old.dungeon.equals(id)&&old.difficulty==difficulty&&old.syncFor(p.getUUID())==j.get("sync").getAsBoolean()){playerAccess.profile(p);return;}throw new IllegalArgumentException("请先退出当前副本，再选择场次或同步");}
        Dungeon d=null;
        boolean created=false;
        for(String instance:activeInstanceIds()){
            Dungeon candidate=byId(instance);
            if(candidate!=null&&candidate.treasure==null&&candidate.waystone==null&&candidate.dungeon.equals(id)&&candidate.difficulty==difficulty&&candidate.status.equals("RUNNING")){
                d=candidate;
                break;
            }
        }
        if(d==null){
            Team team=this.team.apply(p.getUUID());
            if(team!=null&&!team.leader.equals(p.getUUID()))throw new IllegalArgumentException("仅队长可发起并选择副本设置");
            d=new Dungeon();
            created=true;
            d.dungeon=id;
            d.cell=freeCell();
            d.difficulty=difficulty;
            journal.put("instance/"+d.id,d);
            instanceCommitted(d.id);
            try{
                forceArena(d,true);ensureArena(def,d.cell);clearCell(d.cell);resetDoors(d);
                if(GoblinCampEncounterService.handles(d))goblinCamp.initialize(d);
                journal.put("instance/"+d.id,d);
            }catch(Exception failure){
                d.status="CLOSED";journal.put("instance/"+d.id,d);instanceCommitted(d.id);
                if(GoblinCampEncounterService.handles(d))goblinCamp.close(d);
                clearCell(d.cell);forceArena(d,false);throw failure;
            }
        }
        int oldSize=Math.max(1,d.roster.size());
        boolean newcomer=d.roster.add(p.getUUID());
        boolean returning=d.withdrawn.remove(p.getUUID());
        if(!d.returns.containsKey(p.getStringUUID()))d.returns.put(p.getStringUUID(),point(p));
        var nextProgress=JSON.fromJson(JSON.toJson(playerAccess.progress(p)),cn.yumis.fight.modules.character.api.Progress.class);nextProgress.dungeonSync=j.get("sync").getAsBoolean();d.synchronization.put(p.getUUID(),nextProgress.dungeonSync);
        journal.begin();
        try{
        for(var write:characters.writes(p.getUUID(),nextProgress).entrySet())journal.put(write.getKey(),write.getValue());
        journal.put(key,j);
        if(newcomer||returning)meter.join(d,p);
        journal.put("instance/"+d.id,d);
        journal.put("return/"+p.getUUID(),d.returns.get(p.getStringUUID()));
        if(newcomer&&d.roster.size()>1){
            for(Entity e:level().getAllEntities())if(e instanceof DungeonEnemy enemy&&enemy.instance().equals(d.id.toString())&&enemy.isAlive()){
                Target target=targets.target(enemy);
                target.life.q=expandedQ(target.life.q,oldSize,d.roster.size());
                target.partySize=d.roster.size();
                target.life.basis++;
                target.life.revision++;
                journal.put("target/"+e.getUUID(),target);
                for(String ailment:journal.keys("ailments/"+e.getUUID()+"/"))journal.put(ailment,null);
            }
        }
        journal.finish();
        instanceCommitted(d.id);
        }catch(Exception failure){journal.abort();if(created)close(d);throw failure;}
        playerAccess.progress(p,nextProgress);
        playerAccess.session(p).life.instance=d.id.toString();
        playerAccess.session(p).life.dimension=DIMENSION.identifier().toString();
        teleport(p,level(),d.cell*512+.5,80,2.5);
        if(d.enemies.isEmpty()&&d.wave==0)spawnWave(d);
        for(ServerPlayer player:server.getPlayerList().getPlayers())if(participant(player,d.id.toString())){
            playerAccess.profile(player);
            for(Entity e:level().getAllEntities())if(e instanceof DungeonEnemy enemy&&enemy.instance().equals(d.id.toString()))targets.context(player,enemy);
        }
        gbl.track(p);palace.track(p);forbidden.track(p);skasa.track(p);darkCity.track(p);
        for(var encounter:classic)encounter.track(p);
    }
    public void leave(ServerPlayer p,boolean teleport)throws Exception{
        Dungeon d=current(p.getUUID());
        if(d!=null&&d.treasure!=null){treasure.leave(d,p,teleport);return;}
        if(d==null){
            ReturnPoint stored=journal.get("return/"+p.getUUID(),ReturnPoint.class,null);
            if(stored!=null&&p.level().dimension()==DIMENSION)back(p,stored);
            return;
        }
        ReturnPoint point=d.returns.get(p.getStringUUID());
        d.withdrawn.add(p.getUUID());
        journal.put("instance/"+d.id,d);
        meter.leave(d,p);
        if(point!=null){
            rewards.move(p,d.id.toString(),point);
            if(teleport)back(p,point);
        }
        playerAccess.session(p).life.instance="";
        playerAccess.session(p).life.dimension=p.level().dimension().identifier().toString();
        if(waystones!=null)waystones.updateSources(p);
        playerAccess.save(p);
        rewards.publish();
        if(d.roster.stream().allMatch(d.withdrawn::contains))close(d);
        playerAccess.profile(p);
    }
    public void disconnected(ServerPlayer p)throws Exception{
        Dungeon d=current(p.getUUID());
        if(d!=null&&d.waystone!=null&&d.status.equals("RUNNING")){waystones.disconnected(p,d);return;}
        leave(p,true);
    }
    public void reconnect(ServerPlayer p)throws Exception{
        Dungeon d=current(p.getUUID());
        if(d!=null&&d.waystone!=null&&d.status.equals("RUNNING")){waystones.reconnect(p,d);return;}
        if(waystones!=null)waystones.updateSources(p);
        if(p.level().dimension()==DIMENSION&&current(p.getUUID())==null){
            ReturnPoint point=journal.get("return/"+p.getUUID(),ReturnPoint.class,null);
            if(point!=null)back(p,point);
            playerAccess.session(p).life.instance="";
            playerAccess.session(p).life.dimension=p.level().dimension().identifier().toString();
        }
    }
    public void respawn(ServerPlayer p){
        Dungeon d=current(p.getUUID());
        if(d!=null&&d.waystone!=null)try{waystones.death(d);}catch(Exception failure){throw new IllegalStateException(failure);}
        if(d!=null&&d.treasure!=null&&d.treasure.region==0){teleport(p,server.overworld(),d.treasure.mapX+.5,d.treasure.mapY,d.treasure.mapZ+.5);}
        else if(d!=null)teleport(p,level(),d.cell*512+.5,80,d.treasure==null?2.5:-23);
        else{
            ReturnPoint point=journal.get("return/"+p.getUUID(),ReturnPoint.class,null);
            if(point!=null&&p.level().dimension()==DIMENSION)back(p,point);
        }
    }
    ReturnPoint point(ServerPlayer p){
        ReturnPoint point=new ReturnPoint();
        point.dimension=p.level().dimension().identifier().toString();
        point.x=p.getX();
        point.y=p.getY();
        point.z=p.getZ();
        point.yaw=p.getYRot();
        point.pitch=p.getXRot();
        return point;
    }
    void back(ServerPlayer p,ReturnPoint point){
        ServerLevel l=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(point.dimension)));
        if(l==null)l=server.overworld();
        teleport(p,l,point.x,Math.max(l.getMinY()+1,point.y),point.z);
        p.setYRot(point.yaw);
        p.setXRot(point.pitch);
    }
    void teleport(ServerPlayer p,ServerLevel l,double x,double y,double z){
        p.teleportTo(l,x,y,z,Set.of(),p.getYRot(),p.getXRot(),true);
    }
    void forceArena(Dungeon d,boolean forced){
        int ox=d.cell*512;
        if(d.treasure!=null){if(d.treasure.region==0)return;for(int x=Math.floorDiv(ox-44,16);x<=Math.floorDiv(ox+44,16);x++)for(int z=-3;z<=2;z++){level().setChunkForced(x,z,forced);if(forced)level().getChunk(x,z);}return;}
        var bounds=layouts.require(d.dungeon).bounds();
        for(int x=Math.floorDiv(ox+bounds.minX(),16);x<=Math.floorDiv(ox+bounds.maxX(),16);x++)
        for(int z=Math.floorDiv(bounds.minZ(),16);z<=Math.floorDiv(bounds.maxZ(),16);z++){
            level().setChunkForced(x,z,forced);if(forced)level().getChunk(x,z);
        }
    }
    int freeCell(){Set<Integer> occupied=new HashSet<>();for(String instance:activeInstanceIds()){Dungeon d=byId(instance);if(d!=null&&!d.status.equals("CLOSED"))occupied.add(d.cell);}int cell=DungeonCatalog.current().dungeons.stream().mapToInt(DungeonCatalog.DungeonDef::cell).max().orElse(-1)+1;while(occupied.contains(cell))cell++;return cell;}
    void ensureArena(DungeonCatalog.DungeonDef def,int cell)throws Exception{
        var definition=layouts.require(def.id());
        String key="arenaCell/"+cell,layout=def.id()+"/"+definition.version();
        if(journal.get(key,String.class,"").equals(layout))return;
        rebuildArena(cell,layout,definition.build());
    }
    void buildTreasureArena(int cell,String theme)throws Exception{
        rebuildArena(cell,"TREASURE_LAYOUT_2/"+theme,(world,origin)->TreasureArena.build(world,origin,theme));
    }
    private void rebuildArena(int cell,String layout,java.util.function.BiConsumer<ServerLevel,BlockPos> build)throws Exception{
        String key="arenaCell/"+cell;
        journal.put(key,"BUILDING/"+layout);
        ServerLevel world=level();BlockPos origin=new BlockPos(cell*512,80,0);
        var bounds=layouts.reuseBounds();
        var temporary=new ArrayList<ChunkPos>();
        try{
            // Load the old footprint too: treasure's small footprint must not leave forest entities behind.
            for(int cx=Math.floorDiv(origin.getX()+bounds.minX(),16);cx<=Math.floorDiv(origin.getX()+bounds.maxX(),16);cx++)
                for(int cz=Math.floorDiv(bounds.minZ(),16);cz<=Math.floorDiv(bounds.maxZ(),16);cz++){
                    if(!world.getForceLoadedChunks().contains(ChunkPos.pack(cx,cz))){world.setChunkForced(cx,cz,true);temporary.add(new ChunkPos(cx,cz));}
                    world.getChunk(cx,cz);
                }
            clearCell(cell);
            var cursor=new BlockPos.MutableBlockPos();
            for(int x=bounds.minX();x<=bounds.maxX();x++)for(int z=bounds.minZ();z<=bounds.maxZ();z++){
                int wx=origin.getX()+x;var chunk=world.getChunk(Math.floorDiv(wx,16),Math.floorDiv(z,16));
                for(int y=bounds.maxY();y>=bounds.minY();y--){
                    int wy=origin.getY()+y;var section=chunk.getSection(chunk.getSectionIndex(wy));
                    if(section.hasOnlyAir()){y=Math.floorDiv(wy,16)*16-origin.getY();continue;}
                    if(!section.getBlockState(wx&15,wy&15,z&15).isAir())world.setBlock(cursor.set(wx,wy,z),Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS);
                }
            }
            build.accept(world,origin);world.getChunkSource().save(true);journal.put(key,layout);
        }finally{for(var chunk:temporary)world.setChunkForced(chunk.x(),chunk.z(),false);}
    }
    void clearCell(int cell){
        var stale=new ArrayList<Entity>();
        for(Entity e:level().getAllEntities())if(((e instanceof DungeonEnemy||e instanceof CageEntity)&&Math.abs(e.getX()-cell*512)<128)
            ||GoblinCampScenery.inCell(e,cell)
            ||e.getPersistentData().getBooleanOr("ydnf_dungeon_projectile",false)&&e.getPersistentData().getIntOr("ydnf_cell",Integer.MIN_VALUE)==cell)stale.add(e);
        for(Entity e:stale){if(e instanceof DungeonEnemy)cancelBoss(e.getUUID());e.discard();}
    }
    void resetDoors(Dungeon d){
        for(int stage=0;stage<layouts.require(d.dungeon).gates();stage++)gate(d,stage,false);
    }
    private void openDoor(Dungeon d,int stage){
        gate(d,stage,true);
    }
    private void gate(Dungeon d,int stage,boolean open){
        BlockPos origin=new BlockPos(d.cell*512,80,0);
        layouts.require(d.dungeon).gate().set(level(),origin,stage,open);
    }
    DungeonEnemy spawn(Dungeon d,String type,boolean elite,boolean boss,double x,double y,double z){
        ServerLevel world=d.treasure==null?level():treasure.world(d);
        DungeonEnemy e=new DungeonEnemy(cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.ENEMIES.get(type).get(),world);
        e.getPersistentData().putString("ydnf_instance",d.id.toString());
        e.getPersistentData().putInt("ydnf_level",d.waystone!=null?d.waystone.monsterLevel:d.treasure==null?DungeonCatalog.current().dungeon(d.dungeon).level():d.treasure.baseLevel+Math.max(0,d.treasure.region-1)*2);
        e.getPersistentData().putBoolean("ydnf_elite",elite);
        e.getPersistentData().putBoolean("ydnf_boss",boss);
        if(GoblinCampEncounterService.handles(d))e.getPersistentData().putString("ydnf_camp_region","boss");
        e.setPos(x,y,z);
        var gblKind=cn.yumis.fight.modules.dungeons.api.GblEnemyKind.find(type);
        var palaceKind=cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind.find(type);
        var forbiddenKind=cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind.find(type);
        var skasaKind=cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind.find(type);
        var darkCityKind=cn.yumis.fight.modules.dungeons.api.DarkCityEnemyKind.find(type);
        var classicKind=cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.find(type);
        e.setCustomName(cn.yumis.fight.modules.presentation.api.ModText.of(classicKind!=null?classicKind.title:darkCityKind!=null?darkCityKind.title:skasaKind!=null?skasaKind.title:forbiddenKind!=null?forbiddenKind.title:palaceKind!=null?palaceKind.title:gblKind!=null?gblKind.title:switch(type){
            case "golden_king"->"黄金之王";case "goblin_shaman"->"落雷凯诺";case "lucas"->"鲁卡斯";case "goblin_captain"->"哥布林队长";case "goblin_thrower"->"投石哥布林";case "blue_wyvern"->"蓝翼龙";case "blue_dragonman"->"蓝龙人";case "dragonman"->"龙人";case "wyvern"->"翼龙";default->"哥布林";
        }
        ));
        if(boss||gblKind!=null||palaceKind!=null||forbiddenKind!=null||skasaKind!=null||darkCityKind!=null||classicKind!=null||type.equals("goblin_thrower")||type.contains("wyvern"))e.scripted();
        if(skasaKind==cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind.ICE_DRAGON){e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);e.setYRot(180);e.yBodyRot=180;e.setYHeadRot(180);}
        if(forbiddenKind==cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind.MARCELLUS||forbiddenKind==cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind.BARRIER_CORE)e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        if(gblKind==cn.yumis.fight.modules.dungeons.api.GblEnemyKind.DRAGON_CANNON)e.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        if(type.contains("wyvern")){
            e.setNoGravity(true);
            e.getPersistentData().putDouble("ydnf_flightFloor",y);
        }
        if(palaceKind!=null){e.play("spawn",800);if(palaceKind==cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind.GOLEM){e.setNoGravity(true);e.setPos(x,y+.45,z);}}
        if(!world.addFreshEntity(e))throw new IllegalStateException("副本敌人生成失败："+type);
        d.enemies.add(e.getUUID());
        Target target=targets.target(e);
        if(d.waystone!=null){waystones.apply(target,d);target.life.basis++;target.life.revision++;try{journal.put("target/"+e.getUUID(),target);}catch(Exception failure){throw new IllegalStateException(failure);}e.setData(cn.yumis.fight.modules.combat.api.CombatContentTypes.TARGET,JSON.toJson(target));}
        return e;
    }
    void spawnWave(Dungeon d)throws Exception{
        if(d.wave>=7)return;
        var spawn=waves.get(d.dungeon);if(spawn==null)throw new IllegalArgumentException("该副本尚未配置遭遇："+d.dungeon);
        spawn.accept(d);journal.put("instance/"+d.id,d);refresh.run();
    }
    private void spawnGblWave(Dungeon d){
        int ox=d.cell*512;
            int area=GblChurch.area(d.wave);double z=area*40+14;
            if(d.wave==6)spawn(d,"gbl_asor",false,true,ox,80,140);
            else{
                String[][] waves={
                    {"gbl_cultist","gbl_cultist","gbl_cultist","gbl_female_cultist","gbl_dragon_cannon"},
                    {"gbl_cultist","gbl_cultist","gbl_female_cultist","gbl_female_cultist","gbl_yasha"},
                    {"gbl_female_cultist","gbl_dragon_cannon","gbl_yasha","gbl_female_cultist","gbl_dragon_cannon"},
                    {"gbl_cultist","gbl_yasha","gbl_cultist","gbl_yasha","gbl_dragon_cannon"},
                    {"gbl_cultist","gbl_cultist","gbl_female_cultist","gbl_yasha","gbl_fallen_priest"},
                    {"gbl_female_cultist","gbl_yasha","gbl_female_cultist","gbl_yasha","gbl_fallen_priest"}
                };
                for(int i=0;i<waves[d.wave].length;i++)spawn(d,waves[d.wave][i],waves[d.wave][i].equals("gbl_fallen_priest"),false,ox+(i-2)*4,80,z+(i%2)*5);
            }
    }
    private void spawnDragonWave(Dungeon d){
        int ox=d.cell*512;
        if(d.wave>=7)return;
            int floor=d.wave==6?3:d.wave/2;
            double y=80+floor*DragonTower.FLOOR;
            if(d.wave==6)spawn(d,"lucas",false,true,ox,y,12);
            else{
                String[][] waves={
                    {
                        "dragonman","dragonman","wyvern","blue_dragonman"
                    }
                    ,{
                        "dragonman","wyvern","wyvern","blue_dragonman"
                    }
                    ,{
                        "dragonman","wyvern","blue_wyvern","blue_dragonman"
                    }
                    ,{
                        "blue_dragonman","blue_wyvern","blue_wyvern","dragonman"
                    }
                    ,{
                        "dragonman","blue_dragonman","blue_wyvern","blue_wyvern"
                    }
                    ,{
                        "wyvern","blue_wyvern","blue_dragonman","dragonman"
                    }
                }
                ;
                for(int i=0;i<4;i++)spawn(d,waves[d.wave][i],i==3,false,ox+(i-2)*3,y,12+(i%2)*5);
            }
    }
    public void tick(){
        try{
            if(waystones!=null)waystones.tick();
            long now=System.currentTimeMillis();
            for(String instance:activeInstanceIds()){
                String key="instance/"+instance;
                Dungeon d=journal.get(key,Dungeon.class,null);
                if(d==null||d.treasure!=null)continue;
                boolean openCamp=GoblinCampEncounterService.handles(d);
                if(!d.status.equals("RUNNING")&&!(openCamp&&d.status.equals("COMPLETED")))continue;
                List<ServerPlayer> players=server.getPlayerList().getPlayers().stream().filter(p->participant(p,d.id.toString())&&p.level().dimension()==DIMENSION).toList();
                if(players.isEmpty()){
                    close(d);
                    continue;
                }
                if(openCamp)goblinCamp.tick(d,players,now);
                else{
                d.enemies.removeIf(id->{
                    Target stored=journal.get("target/"+id,Target.class,null);
                    Entity e=level().getEntity(id);
                    return stored!=null&&stored.life.dead||e!=null&&!e.isAlive();
                }
                );
                if(d.enemies.isEmpty()){
                    if(d.wave==6){
                        if(d.waystone!=null){waystones.complete(d,players);refresh.run();continue;}
                        d.status="COMPLETED";
                        meter.freeze(d);
                        journal.put(key,d);
                        for(ServerPlayer p:players)playerAccess.message(p,"地下城通关！可拾取战利品或返回。");
                        refresh.run();
                        continue;
                    }
                    var layout=layouts.require(d.dungeon);
                    if((d.wave+1)%layout.wavesPerGate()==0)openDoor(d,d.wave/layout.wavesPerGate());
                    if(d.dungeon.equals("gbl_church")&&gbl.waitsAtGate(d,players))continue;
                    if(d.dungeon.equals("behemoth_forbidden")&&forbidden.waitsAtGate(d,players))continue;
                    if(d.dungeon.equals("skasa")&&skasa.waitsAtGate(d,players))continue;
                    if(d.dungeon.equals("dark_city")&&darkCity.waitsAtGate(d,players))continue;
                    if(classic.stream().anyMatch(encounter->encounter.dungeon.equals(d.dungeon)&&encounter.waitsAtGate(d,players)))continue;
                    d.wave++;
                    spawnWave(d);
                }
                }
                for(UUID id:new ArrayList<>(d.enemies)){
                    Entity entity=level().getEntity(id);
                    if(entity instanceof DungeonEnemy enemy&&enemy.isAlive())brain(d,enemy,players,now);
                }
            }
            gbl.tick(server.getPlayerList().getPlayers(),now);palace.tick(server.getPlayerList().getPlayers(),now);forbidden.tick(server.getPlayerList().getPlayers(),now);skasa.tick(server.getPlayerList().getPlayers(),now);darkCity.tick(server.getPlayerList().getPlayers(),now);
            for(var encounter:classic)encounter.tick(server.getPlayerList().getPlayers(),now);
            for(Cast cast:new ArrayList<>(casts.values())){
                Entity raw=level().getEntity(cast.boss);
                if(raw instanceof DungeonEnemy enemy&&(cast.data.get("kind").getAsString().equals("CHARGE")||cast.data.get("kind").getAsString().equals("WYVERN_CHARGE"))&&now>=cast.active){
                    long active=cast.data.get("activeTicks").getAsLong()*50;
                    double progress=Math.clamp((now-cast.active)/(double)Math.max(1,active),0,1);
                    Vec3 origin=new Vec3(cast.data.get("x").getAsDouble(),cast.data.get("y").getAsDouble(),cast.data.get("z").getAsDouble()),direction=new Vec3(cast.data.get("dx").getAsDouble(),0,cast.data.get("dz").getAsDouble());
                    Vec3 next=origin.add(direction.scale(cast.data.get("radius").getAsDouble()*progress));
                    if(now<=cast.active+active)enemy.move(MoverType.SELF,next.subtract(enemy.position()));
                    var resistance=enemy.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
                    if(resistance!=null)resistance.setBaseValue(now<=cast.active+active?1:0);
                }
                if(now>=cast.end){
                    casts.remove(cast.boss);
                    if(raw instanceof DungeonEnemy enemy)enemy.setAggressive(false);
                    if(cast.data.get("kind").getAsString().equals("CAGE")){
                        String id=cast.id.toString();
                        Cage cage=cages.get(id);
                        if(cage!=null)discharge(cage);
                    }
                }
            }
            for(Cage cage:new ArrayList<>(cages.values()))if(cage.entities.isEmpty()&&now>=cage.expires-2000){
                for(int side=-1;side<4;side++){
                    CageEntity e=new CageEntity(cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.CAGE.get(),level());
                    e.initialize(cage.id,side);
                    e.setPos(cage.x,cage.y,cage.z);
                    level().addFreshEntity(e);
                    cage.entities.add(e);
                }
            }
            for(Cage cage:new ArrayList<>(cages.values()))if(now>=cage.expires)discharge(cage);
        }
        catch(Exception failure){
            server.sendSystemMessage(cn.yumis.fight.modules.presentation.api.ModText.of("副本处理失败："+failure.getMessage()));
        }
    }
    void brain(Dungeon d,DungeonEnemy e,List<ServerPlayer> players,long now)throws Exception{
        String id=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        EnemyBrain handler=enemyBrains.get(id);if(handler!=null){handler.tick(d,e,players,now);return;}
        ServerPlayer p=players.stream().filter(Entity::isAlive).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
        if(p==null)return;
        String type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        if(cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(e)){
            stones.remove(e.getUUID());
            return;
        }
        if(!e.boss()){
            if(type.equals("goblin_thrower")){
                e.getLookControl().setLookAt(p);
                double distance=e.distanceTo(p);
                if(distance>8)e.getNavigation().moveTo(p,1);
                else if(distance<4){
                    Vec3 away=e.position().subtract(p.position()).normalize();
                    e.getNavigation().moveTo(e.getX()+away.x*3,e.getY(),e.getZ()+away.z*3,1);
                }
                else e.getNavigation().stop();
                Long release=stones.get(e.getUUID());
                if(release!=null&&now>=release){
                    stones.remove(e.getUUID());
                    e.setAggressive(false);
                    var stone=new net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball(e.level(),e,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
                    stone.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
                    Vec3 aim=p.getEyePosition().subtract(e.getEyePosition()).normalize();
                    stone.shoot(aim.x,aim.y+.08,aim.z,1.1f,0);
                    stone.getPersistentData().putBoolean("ydnf_dungeon_projectile",true);
                    stone.getPersistentData().putInt("ydnf_cell",d.cell);
                    stone.getPersistentData().putString("ydnf_instance",d.id.toString());
                    e.level().addFreshEntity(stone);
                }
                else if(release==null&&distance<12&&ready(e,"stone",now)){
                    cooldowns.put(e.getUUID()+"/stone",now+2000);
                    stones.put(e.getUUID(),now+400);
                    e.setAggressive(true);
                }
            }
            else if(type.contains("wyvern")){
                e.getLookControl().setLookAt(p);
                double floor=e.getPersistentData().getDoubleOr("ydnf_flightFloor",e.getY());
                Vec3 direction=p.position().subtract(e.position());
                double desired=floor+1.0+Math.sin(now/500d)*.25;
                e.setDeltaMovement(direction.normalize().scale(e.distanceTo(p)>2?.14:0).add(0,Math.clamp((desired-e.getY())*.2,-.15,.15),0));
                if(type.equals("blue_wyvern")&&e.distanceTo(p)<8&&!casts.containsKey(e.getUUID())&&ready(e,"WYVERN_CHARGE",now))cast(e,p,"WYVERN_CHARGE",4.5,.15,600,450,500,1.2,false,5);
                else if(e.distanceTo(p)<2&&!casts.containsKey(e.getUUID())&&ready(e,"BASIC",now))cast(e,p,"BASIC",2,Math.PI/3,250,200,400,1,false,2);
            }
            return;
        }
        if(cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(e)||casts.containsKey(e.getUUID()))return;
        Target target=targets.target(e);
        if(!d.summoned&&target.life.q<(type.equals("lucas")?.30:.5)){
            d.summoned=true;
            if(type.equals("lucas")){
                DungeonEnemy clone=spawn(d,"lucas",false,true,e.getX()+3,e.getY(),e.getZ());
                Target cloneTarget=targets.target(clone);
                cloneTarget.speciesHealth=.15;
                cloneTarget.speciesAttack=.5;
                cloneTarget.noRewards=true;
                clone.getPersistentData().putString("ydnf_parent",e.getStringUUID());
                journal.put("target/"+clone.getUUID(),cloneTarget);
                for(ServerPlayer observer:players)targets.context(observer,clone);
            }
            else{
                spawn(d,"goblin",false,false,e.getX()-2,e.getY(),e.getZ());
                spawn(d,"goblin",false,false,e.getX()+2,e.getY(),e.getZ());
            }
            journal.put("instance/"+d.id,d);
            refresh.run();
        }
        e.getLookControl().setLookAt(p);
        double distance=e.distanceTo(p);
        if(type.equals("goblin_shaman")){
            if(distance>8)e.getNavigation().moveTo(p,1);
            else e.getNavigation().stop();
            if(distance<12&&ready(e,"LIGHTNING",now))cast(e,p,"LIGHTNING",2,Math.PI,1200,200,1000,1.4,true,10);
            else if(distance<6&&ready(e,"FAN",now))cast(e,p,"FAN",6,Math.PI/4,800,200,800,1.2,true,6);
            else if(distance<3&&ready(e,"BASIC",now))cast(e,p,"BASIC",3,Math.PI/3,300,200,400,1,false,2);
        }
        else{
            if(distance>2.5)e.getNavigation().moveTo(p,1);
            else e.getNavigation().stop();
            if(distance<12&&ready(e,"CAGE",now))cast(e,p,"CAGE",1.5,Math.PI,1000,2000,800,1.4,true,12);
            else if(distance<6&&ready(e,"CHARGE",now))cast(e,p,"CHARGE",6,Math.PI/2,800,200,800,1.8,false,7);
            else if(distance<3&&ready(e,"DISCHARGE",now))cast(e,p,"DISCHARGE",3,Math.PI,900,200,800,1.4,true,8);
            else if(distance<3&&ready(e,"BASIC",now))cast(e,p,"BASIC",3,Math.PI/6,350,150,500,1,false,2);
        }
    }
    private boolean ready(DungeonEnemy e,String kind,long now){
        return cooldowns.getOrDefault(e.getUUID()+"/"+kind,0L)<=now;
    }
    private void cast(DungeonEnemy e,ServerPlayer p,String kind,double radius,double angle,long windup,long active,long recovery,double coefficient,boolean magic,int cooldown){
        long now=System.currentTimeMillis();
        cooldowns.put(e.getUUID()+"/"+kind,now+cooldown*1000);
        JsonObject a=new JsonObject();
        UUID id=UUID.randomUUID();
        a.addProperty("id",id.toString());
        a.add("boss",JSON.toJsonTree(targets.target(e)));
        a.addProperty("kind",kind);
        a.addProperty("shape",kind.equals("CHARGE")||kind.equals("WYVERN_CHARGE")?"RECT":kind.equals("CAGE")?"SQUARE":kind.equals("DISCHARGE")||kind.equals("LIGHTNING")?"CIRCLE":"FAN");
        a.addProperty("width",kind.equals("WYVERN_CHARGE")?1.4:2);
        Vec3 dir=p.position().subtract(e.position()).normalize();
        boolean atPlayer=kind.equals("LIGHTNING")||kind.equals("CAGE");
        a.addProperty("x",atPlayer?p.getX():e.getX());
        a.addProperty("y",atPlayer?p.getY():e.getY());
        a.addProperty("z",atPlayer?p.getZ():e.getZ());
        a.addProperty("dx",dir.x);
        a.addProperty("dz",dir.z);
        a.addProperty("radius",radius);
        a.addProperty("halfAngle",angle);
        a.addProperty("windupTicks",windup/50);
        a.addProperty("activeTicks",active/50);
        a.addProperty("recoveryTicks",recovery/50);
        a.addProperty("coefficient",coefficient);
        a.addProperty("magic",magic);
        a.addProperty("element",magic?0:-1);
        a.addProperty("startedAt",now);
        e.setAggressive(true);
        casts.put(e.getUUID(),new Cast(id,e.getUUID(),a,now+windup,now+windup+active+recovery));
        broadcast(e.instance(),"bossAction",a);
        if(kind.equals("CAGE")){
            Cage cage=new Cage();
            cage.id=id.toString();
            cage.instance=e.instance();
            cage.boss=e.getUUID();
            cage.expires=now+3000;
            cage.x=p.getX();
            cage.y=p.getY();
            cage.z=p.getZ();
            cages.put(cage.id,cage);
        }
    }
    void broadcast(String instance,String action,Object value){
        for(ServerPlayer p:server.getPlayerList().getPlayers())if(participant(p,instance))cn.yumis.fight.network.Network.send(p,action,value);
    }
    private void discharge(Cage cage){
        if(!cages.containsKey(cage.id))return;
        Entity e=level().getEntity(cage.boss);
        if(e instanceof DungeonEnemy boss&&boss.isAlive()){
            JsonObject j=new JsonObject();
            j.addProperty("id",UUID.randomUUID().toString());
            j.add("boss",JSON.toJsonTree(targets.target(boss)));
            j.addProperty("kind","CAGE_DISCHARGE");
            j.addProperty("shape","SQUARE");
            j.addProperty("x",cage.x);
            j.addProperty("y",cage.y);
            j.addProperty("z",cage.z);
            j.addProperty("radius",1.5);
            j.addProperty("halfAngle",Math.PI);
            j.addProperty("dx",0);
            j.addProperty("dz",0);
            j.addProperty("windupTicks",0);
            j.addProperty("activeTicks",4);
            j.addProperty("coefficient",1.4);
            j.addProperty("magic",true);
            j.addProperty("element",0);
            j.addProperty("startedAt",System.currentTimeMillis());
            broadcast(cage.instance,"bossAction",j);
        }
        removeCage(cage);
    }
    private void removeCage(Cage cage){
        for(Entity e:cage.entities)e.discard();
        cages.remove(cage.id);
        broadcast(cage.instance,"cancelBossAction",Map.of("id",cage.id));
    }
    public void cageHit(ServerPlayer p,JsonObject j){
        Cage cage=cages.get(j.get("cage").getAsString());
        if(cage==null||!participant(p,cage.instance))return;
        String op=p.getUUID()+"/"+playerAccess.session(p).id+"/"+j.get("operation").getAsString();
        if(!cage.operations.add(op))return;
        if(++cage.hits>=3){
            removeCage(cage);
            casts.remove(cage.boss);
        }
    }
    public void died(UUID boss){
        if(treasure!=null)treasure.died(boss);
        try{forbidden.died(boss);}catch(Exception failure){throw new IllegalStateException("禁地死亡清理失败",failure);}
        skasa.died(boss);
        darkCity.died(boss);
        for(var encounter:classic)encounter.died(boss);
        cancelBoss(boss);
        for(Entity e:level().getAllEntities())if(e instanceof DungeonEnemy mob&&mob.getPersistentData().getStringOr("ydnf_parent","").equals(boss.toString())){
            cancelBoss(e.getUUID());
            try{
                Target target=targets.target(mob);
                target.life.dead=true;
                target.life.q=0;
                target.life.revision++;
                journal.put("target/"+e.getUUID(),target);
            }
            catch(Exception failure){
                throw new IllegalStateException(failure);
            }
            if(!forbidden.retainsSource(e.getUUID()))e.discard();
        }
        // The final kill is already committed. Settle now so the next AI tick cannot move a time boundary.
        Target fallen=journal.get("target/"+boss,Target.class,null);
        Dungeon completed=fallen==null?null:byId(fallen.instance);
        if(completed!=null&&GoblinCampEncounterService.handles(completed)){
            try{
                var here=server.getPlayerList().getPlayers().stream().filter(p->participant(p,completed.id.toString())&&p.level().dimension()==DIMENSION).toList();
                goblinCamp.died(completed,boss,here);
            }catch(Exception failure){throw new IllegalStateException("哥布林营地通关结算失败",failure);}
            return;
        }
        if(completed!=null&&completed.waystone!=null&&completed.status.equals("RUNNING")&&completed.wave==6&&completed.enemies.stream().allMatch(id->{var target=journal.get("target/"+id,Target.class,null);return target!=null&&target.life.dead;})){
            try{var here=server.getPlayerList().getPlayers().stream().filter(p->participant(p,completed.id.toString())&&p.level().dimension()==DIMENSION).toList();if(here.isEmpty())close(completed);else{waystones.complete(completed,here);refresh.run();}}catch(Exception failure){throw new IllegalStateException("地狱+通关结算失败",failure);}
        }
    }
    public void cancelBoss(UUID boss){
        stones.remove(boss);
        cooldowns.keySet().removeIf(key->key.startsWith(boss+"/"));
        gbl.cancel(boss);palace.cancel(boss);forbidden.cancel(boss);skasa.cancel(boss);darkCity.cancel(boss);
        for(var encounter:classic)encounter.cancel(boss);
        Cast cast=casts.remove(boss);
        if(cast!=null){
            Entity e=level().getEntity(boss);
            if(e instanceof DungeonEnemy enemy){
                enemy.setAggressive(false);
                broadcast(enemy.instance(),"cancelBossAction",Map.of("id",cast.id.toString()));
            }
            Cage cage=cages.get(cast.id.toString());
            if(cage!=null)removeCage(cage);
        }
    }
    void close(Dungeon d)throws Exception{
        if(d.treasure!=null&&treasure!=null){treasure.finish(d,"本次挖宝探索已结束。");return;}
        if(d.waystone!=null&&waystones!=null&&!stopping)waystones.fail(d);
        gbl.close(d);palace.close(d);forbidden.close(d);skasa.close(d);darkCity.close(d);
        for(var encounter:classic)encounter.close(d);
        d.status="CLOSED";
        journal.put("instance/"+d.id,d);
        instanceCommitted(d.id);
        if(GoblinCampEncounterService.handles(d))goblinCamp.close(d);
        clearCell(d.cell);
        forceArena(d,false);
        for(Cage c:new ArrayList<>(cages.values()))if(c.instance.equals(d.id.toString()))removeCage(c);
    }
    void refreshCamp(){refresh.run();}
    void completeCamp(Dungeon d,List<ServerPlayer> players)throws Exception{
        if(!d.status.equals("RUNNING"))return;
        if(players.isEmpty()){close(d);return;}
        if(d.waystone!=null)waystones.complete(d,players);
        else{
            d.status="COMPLETED";
            meter.freeze(d);
            journal.put("instance/"+d.id,d);
        }
        for(var player:players)playerAccess.message(player,"落雷凯诺已被击败！可继续探索、清理营区和拾取战利品，或离开地下城。");
        refresh.run();
    }
    public void shutdown()throws Exception{
        stopping=true;
        for(String instance:activeInstanceIds()){
            Dungeon d=byId(instance);
            if(d!=null&&!d.status.equals("CLOSED"))close(d);
        }
    }
}
