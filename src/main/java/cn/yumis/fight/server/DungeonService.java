package cn.yumis.fight.server;
import cn.yumis.fight.*;
import cn.yumis.fight.game.*;
import cn.yumis.fight.rules.*;
import static cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import com.google.gson.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import java.util.*;
public final class DungeonService {
    public static final ResourceKey<Level> DIMENSION=ResourceKey.create(Registries.DIMENSION,Identifier.fromNamespaceAndPath(GameItems.NS,"dungeon"));
    private final ServerRuntime r;
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
    public DungeonService(ServerRuntime r){
        this.r=r;
    }
    public Dungeon byId(String id){
        if(id==null||id.isEmpty())return null;
        return r.journal.get("instance/"+id,Dungeon.class,null);
    }
    public Dungeon current(UUID p){
        for(String key:r.journal.keys("instance/")){
            Dungeon d=r.journal.get(key,Dungeon.class,null);
            if(d!=null&&!d.status.equals("CLOSED")&&d.roster.contains(p)&&!d.withdrawn.contains(p))return d;
        }
        return null;
    }
    public boolean participant(ServerPlayer p,String id){
        Dungeon d=byId(id);
        return d!=null&&!d.status.equals("CLOSED")&&d.roster.contains(p.getUUID())&&!d.withdrawn.contains(p.getUUID());
    }
    private ServerLevel level(){
        ServerLevel l=r.server.getLevel(DIMENSION);
        if(l==null)throw new IllegalArgumentException("地下城维度尚未加载");
        return l;
    }
    public void recover()throws Exception{
        for(String key:r.journal.keys("instance/")){
            Dungeon d=r.journal.get(key,Dungeon.class,null);
            if(d!=null&&!d.status.equals("CLOSED")){
                d.status="CLOSED";
                r.journal.put(key,d);
                forceArena(d,false);
                for(var e:d.returns.entrySet())r.journal.put("return/"+e.getKey(),e.getValue());
            }
        }
        ServerLevel l=r.server.getLevel(DIMENSION);
        if(l!=null)for(Entity e:l.getAllEntities())if(e instanceof DungeonEnemy||e instanceof CageEntity)e.discard();
    }
    public void request(ServerPlayer p,JsonObject j)throws Exception{
        String kind=j.get("kind").getAsString();
        if(kind.equals("leave")){
            leave(p,true);
            return;
        }
        String id=j.get("dungeon").getAsString();
        Catalog.DungeonDef def=Catalog.CURRENT.dungeon(id);
        if(!def.available())throw new IllegalArgumentException("该副本尚未开放");
        if(r.session(p).progress.level<def.level())throw new IllegalArgumentException("角色等级不足");
        Dungeon old=current(p.getUUID());
        if(old!=null&&!old.dungeon.equals(id)&&old.status.equals("RUNNING"))throw new IllegalArgumentException("请先退出当前副本");
        Dungeon d=null;
        for(String key:r.journal.keys("instance/")){
            Dungeon candidate=r.journal.get(key,Dungeon.class,null);
            if(candidate!=null&&candidate.dungeon.equals(id)&&candidate.status.equals("RUNNING")){
                d=candidate;
                break;
            }
        }
        if(d==null){
            Team team=r.team(p.getUUID());
            if(team!=null&&!team.leader.equals(p.getUUID()))throw new IllegalArgumentException("仅队长可发起并选择副本设置");
            for(ServerPlayer online:new ArrayList<>(r.server.getPlayerList().getPlayers())){
                Dungeon previous=current(online.getUUID());
                if(previous!=null&&previous.dungeon.equals(id))leave(online,true);
            }
            d=new Dungeon();
            d.dungeon=id;
            d.cell=def.cell();
            d.difficulty=Difficulty.valueOf(j.get("difficulty").getAsString());
            d.sync=j.get("sync").getAsBoolean();
            forceArena(d,true);
            ensureArena(def);
            clearCell(d.cell);
            r.journal.put("instance/"+d.id,d);
            resetDoors(d);
        }
        String operation=j.has("operation")?j.get("operation").getAsString():UUID.randomUUID().toString();
        String key="dungeonReceipt/"+p.getUUID()+"/"+r.session(p).id+"/"+operation;
        JsonElement previous=r.journal.get(key);
        if(!previous.isJsonNull()){
            if(!previous.equals(j))throw new IllegalArgumentException("入场操作号冲突");
            r.profile(p);
            return;
        }
        int oldSize=Math.max(1,d.roster.size());
        boolean newcomer=d.roster.add(p.getUUID());
        d.withdrawn.remove(p.getUUID());
        if(!d.returns.containsKey(p.getStringUUID()))d.returns.put(p.getStringUUID(),point(p));
        r.journal.begin();
        r.journal.put(key,j);
        r.journal.put("instance/"+d.id,d);
        r.journal.put("return/"+p.getUUID(),d.returns.get(p.getStringUUID()));
        if(newcomer&&d.roster.size()>1){
            for(Entity e:level().getAllEntities())if(e instanceof DungeonEnemy enemy&&enemy.instance().equals(d.id.toString())&&enemy.isAlive()){
                Target target=r.target(enemy);
                target.life.q=expandedQ(target.life.q,oldSize,d.roster.size());
                target.partySize=d.roster.size();
                target.life.basis++;
                target.life.revision++;
                r.journal.put("target/"+e.getUUID(),target);
                for(String ailment:r.journal.keys("ailments/"+e.getUUID()+"/"))r.journal.put(ailment,null);
            }
        }
        r.journal.finish();
        r.session(p).life.instance=d.id.toString();
        r.session(p).life.dimension=DIMENSION.identifier().toString();
        teleport(p,level(),d.cell*512+.5,80,2.5);
        if(d.enemies.isEmpty()&&d.wave==0)spawnWave(d);
        for(ServerPlayer player:r.server.getPlayerList().getPlayers())if(participant(player,d.id.toString())){
            r.profile(player);
            for(Entity e:level().getAllEntities())if(e instanceof DungeonEnemy enemy&&enemy.instance().equals(d.id.toString()))r.context(player,enemy);
        }
    }
    public void leave(ServerPlayer p,boolean teleport)throws Exception{
        Dungeon d=current(p.getUUID());
        if(d==null){
            ReturnPoint stored=r.journal.get("return/"+p.getUUID(),ReturnPoint.class,null);
            if(stored!=null&&p.level().dimension()==DIMENSION)back(p,stored);
            return;
        }
        ReturnPoint point=d.returns.get(p.getStringUUID());
        d.withdrawn.add(p.getUUID());
        r.journal.put("instance/"+d.id,d);
        if(point!=null){
            r.loot.move(p,d.id.toString(),point);
            if(teleport)back(p,point);
        }
        r.session(p).life.instance="";
        r.session(p).life.dimension=p.level().dimension().identifier().toString();
        r.savePlayer(p);
        r.loot.publish();
        r.profile(p);
    }
    public void disconnected(ServerPlayer p)throws Exception{
        leave(p,true);
    }
    public void reconnect(ServerPlayer p)throws Exception{
        if(p.level().dimension()==DIMENSION&&current(p.getUUID())==null){
            ReturnPoint point=r.journal.get("return/"+p.getUUID(),ReturnPoint.class,null);
            if(point!=null)back(p,point);
            r.session(p).life.instance="";
            r.session(p).life.dimension=p.level().dimension().identifier().toString();
        }
    }
    public void respawn(ServerPlayer p){
        Dungeon d=current(p.getUUID());
        if(d!=null)teleport(p,level(),d.cell*512+.5,80,2.5);
        else{
            ReturnPoint point=r.journal.get("return/"+p.getUUID(),ReturnPoint.class,null);
            if(point!=null&&p.level().dimension()==DIMENSION)back(p,point);
        }
    }
    private ReturnPoint point(ServerPlayer p){
        ReturnPoint point=new ReturnPoint();
        point.dimension=p.level().dimension().identifier().toString();
        point.x=p.getX();
        point.y=p.getY();
        point.z=p.getZ();
        point.yaw=p.getYRot();
        point.pitch=p.getXRot();
        return point;
    }
    private void back(ServerPlayer p,ReturnPoint point){
        ServerLevel l=r.server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(point.dimension)));
        if(l==null)l=r.server.overworld();
        teleport(p,l,point.x,Math.max(l.getMinY()+1,point.y),point.z);
        p.setYRot(point.yaw);
        p.setXRot(point.pitch);
    }
    private void teleport(ServerPlayer p,ServerLevel l,double x,double y,double z){
        p.teleportTo(l,x,y,z,Set.of(),p.getYRot(),p.getXRot(),true);
    }
    private void forceArena(Dungeon d,boolean forced){
        int ox=d.cell*512;
        int minimumZ=d.dungeon.equals("goblin_camp")?-5:-18;
        int maximumZ=d.dungeon.equals("goblin_camp")?146:36;
        for(int x=Math.floorDiv(ox-23,16);x<=Math.floorDiv(ox+23,16);x++)
        for(int z=Math.floorDiv(minimumZ,16);z<=Math.floorDiv(maximumZ,16);z++){
            level().setChunkForced(x,z,forced);
            if(forced)level().getChunk(x,z);
        }
    }
    private void ensureArena(Catalog.DungeonDef def)throws Exception{
        String key="arena/"+def.id();
        if(r.journal.get(key,String.class,"").equals("YDNF_LAYOUT_2"))return;
        r.journal.put(key,"BUILDING_YDNF_LAYOUT_2");
        ServerLevel world=level();
        BlockPos origin=new BlockPos(def.cell()*512,80,0);
        for(int x=-23;x<=23;x++)for(int z=-6;z<=146;z++)for(int y=-2;y<=49;y++){
            BlockPos pos=origin.offset(x,y,z);
            if(!world.isEmptyBlock(pos))world.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
        }
        if(def.id().equals("goblin_camp"))GoblinCamp.build(world,origin);
        else DragonTower.build(world,origin);
        world.getChunkSource().save(true);
        r.journal.put(key,"YDNF_LAYOUT_2");
    }
    private void clearCell(int cell){
        for(Entity e:level().getAllEntities())if((e instanceof DungeonEnemy||e instanceof CageEntity)&&Math.abs(e.getX()-cell*512)<128)e.discard();
    }
    private void resetDoors(Dungeon d){
        for(int stage=0;stage<3;stage++)gate(d,stage,false);
    }
    private void openDoor(Dungeon d,int stage){
        gate(d,stage,true);
    }
    private void gate(Dungeon d,int stage,boolean open){
        BlockPos origin=new BlockPos(d.cell*512,80,0);
        if(d.dungeon.equals("goblin_camp"))GoblinCamp.gate(level(),origin,stage,open);
        else DragonTower.gate(level(),origin,stage,open);
    }
    private DungeonEnemy spawn(Dungeon d,String type,boolean elite,boolean boss,double x,double y,double z){
        DungeonEnemy e=new DungeonEnemy(FightMod.ENEMIES.get(type).get(),level());
        e.getPersistentData().putString("ydnf_instance",d.id.toString());
        e.getPersistentData().putInt("ydnf_level",d.dungeon.equals("goblin_camp")?10:15);
        e.getPersistentData().putBoolean("ydnf_elite",elite);
        e.getPersistentData().putBoolean("ydnf_boss",boss);
        e.setPos(x,y,z);
        e.setCustomName(cn.yumis.fight.game.ModText.of(switch(type){
            case "goblin_shaman"->"落雷凯诺";case "lucas"->"鲁卡斯";case "goblin_captain"->"哥布林队长";case "goblin_thrower"->"投石哥布林";case "blue_wyvern"->"蓝翼龙";case "blue_dragonman"->"蓝龙人";case "dragonman"->"龙人";case "wyvern"->"翼龙";default->"哥布林";
        }
        ));
        if(boss||type.equals("goblin_thrower")||type.contains("wyvern"))e.scripted();
        if(type.contains("wyvern")){
            e.setNoGravity(true);
            e.getPersistentData().putDouble("ydnf_flightFloor",y);
        }
        if(!level().addFreshEntity(e))throw new IllegalStateException("副本敌人生成失败："+type);
        d.enemies.add(e.getUUID());
        r.target(e);
        return e;
    }
    private void spawnWave(Dungeon d)throws Exception{
        int ox=d.cell*512;
        if(d.wave>=7)return;
        if(d.dungeon.equals("goblin_camp")){
            double z=d.wave==6?126:12+(d.wave/2)*36;
            if(d.wave==6)spawn(d,"goblin_shaman",false,true,ox+.5,80,z);
            else{
                spawn(d,"goblin",false,false,ox-3,80,z);
                spawn(d,"goblin",false,false,ox+3,80,z);
                spawn(d,"goblin_thrower",false,false,ox+5,80,z+3);
                spawn(d,"goblin_captain",true,false,ox,80,z+5);
            }
        }
        else{
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
        r.journal.put("instance/"+d.id,d);
        r.refreshAll();
    }
    public void tick(){
        try{
            long now=System.currentTimeMillis();
            for(String key:r.journal.keys("instance/")){
                Dungeon d=r.journal.get(key,Dungeon.class,null);
                if(d==null||!d.status.equals("RUNNING"))continue;
                List<ServerPlayer> players=r.server.getPlayerList().getPlayers().stream().filter(p->participant(p,d.id.toString())&&p.level().dimension()==DIMENSION).toList();
                if(players.isEmpty()){
                    close(d);
                    continue;
                }
                d.enemies.removeIf(id->{
                    Target stored=r.journal.get("target/"+id,Target.class,null);
                    Entity e=level().getEntity(id);
                    return stored!=null&&stored.life.dead||e!=null&&!e.isAlive();
                }
                );
                if(d.enemies.isEmpty()){
                    if(d.wave==6){
                        d.status="COMPLETED";
                        r.journal.put(key,d);
                        for(ServerPlayer p:players)r.message(p,"地下城通关！可拾取战利品或返回。");
                        r.refreshAll();
                        continue;
                    }
                    if(d.wave%2==1)openDoor(d,d.wave/2);
                    d.wave++;
                    spawnWave(d);
                }
                for(UUID id:new ArrayList<>(d.enemies)){
                    Entity entity=level().getEntity(id);
                    if(entity instanceof DungeonEnemy enemy&&enemy.isAlive())brain(d,enemy,players,now);
                }
            }
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
                    CageEntity e=new CageEntity(FightMod.CAGE.get(),level());
                    e.initialize(cage.id,side);
                    e.setPos(cage.x,cage.y,cage.z);
                    level().addFreshEntity(e);
                    cage.entities.add(e);
                }
            }
            for(Cage cage:new ArrayList<>(cages.values()))if(now>=cage.expires)discharge(cage);
        }
        catch(Exception failure){
            r.server.sendSystemMessage(cn.yumis.fight.game.ModText.of("副本处理失败："+failure.getMessage()));
        }
    }
    private void brain(Dungeon d,DungeonEnemy e,List<ServerPlayer> players,long now)throws Exception{
        ServerPlayer p=players.stream().filter(Entity::isAlive).min(Comparator.comparingDouble(e::distanceToSqr)).orElse(null);
        if(p==null)return;
        String type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        if(e.hasEffect(FightMod.STUN)){
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
                    var stone=new net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball(level(),e,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
                    stone.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE));
                    Vec3 aim=p.getEyePosition().subtract(e.getEyePosition()).normalize();
                    stone.shoot(aim.x,aim.y+.08,aim.z,1.1f,0);
                    level().addFreshEntity(stone);
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
        if(e.hasEffect(FightMod.STUN)||casts.containsKey(e.getUUID()))return;
        Target target=r.target(e);
        if(!d.summoned&&target.life.q<(type.equals("lucas")?.30:.5)){
            d.summoned=true;
            if(type.equals("lucas")){
                DungeonEnemy clone=spawn(d,"lucas",false,true,e.getX()+3,e.getY(),e.getZ());
                Target cloneTarget=r.target(clone);
                cloneTarget.speciesHealth=.15;
                cloneTarget.speciesAttack=.5;
                cloneTarget.noRewards=true;
                clone.getPersistentData().putString("ydnf_parent",e.getStringUUID());
                r.journal.put("target/"+clone.getUUID(),cloneTarget);
                for(ServerPlayer observer:players)r.context(observer,clone);
            }
            else{
                spawn(d,"goblin",false,false,e.getX()-2,e.getY(),e.getZ());
                spawn(d,"goblin",false,false,e.getX()+2,e.getY(),e.getZ());
            }
            r.journal.put("instance/"+d.id,d);
            r.refreshAll();
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
        a.add("boss",JSON.toJsonTree(r.target(e)));
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
        a.addProperty("light",magic);
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
    private void broadcast(String instance,String action,Object value){
        for(ServerPlayer p:r.server.getPlayerList().getPlayers())if(participant(p,instance))cn.yumis.fight.network.Network.send(p,action,value);
    }
    private void discharge(Cage cage){
        if(!cages.containsKey(cage.id))return;
        Entity e=level().getEntity(cage.boss);
        if(e instanceof DungeonEnemy boss&&boss.isAlive()){
            JsonObject j=new JsonObject();
            j.addProperty("id",UUID.randomUUID().toString());
            j.add("boss",JSON.toJsonTree(r.target(boss)));
            j.addProperty("kind","CAGE_DISCHARGE");
            j.addProperty("shape","SQUARE");
            j.addProperty("light",true);
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
            j.addProperty("light",true);
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
        String op=p.getUUID()+"/"+r.session(p).id+"/"+j.get("operation").getAsString();
        if(!cage.operations.add(op))return;
        if(++cage.hits>=3){
            removeCage(cage);
            casts.remove(cage.boss);
        }
    }
    public void died(UUID boss){
        cancelBoss(boss);
        for(Entity e:level().getAllEntities())if(e instanceof DungeonEnemy mob&&mob.getPersistentData().getStringOr("ydnf_parent","").equals(boss.toString())){
            cancelBoss(e.getUUID());
            try{
                Target target=r.target(mob);
                target.life.dead=true;
                target.life.q=0;
                target.life.revision++;
                r.journal.put("target/"+e.getUUID(),target);
            }
            catch(Exception failure){
                throw new IllegalStateException(failure);
            }
            e.discard();
        }
    }
    public void cancelBoss(UUID boss){
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
    private void close(Dungeon d)throws Exception{
        d.status="CLOSED";
        r.journal.put("instance/"+d.id,d);
        clearCell(d.cell);
        forceArena(d,false);
        for(Cage c:new ArrayList<>(cages.values()))if(c.instance.equals(d.id.toString()))removeCage(c);
    }
    public void shutdown()throws Exception{
        for(String key:r.journal.keys("instance/")){
            Dungeon d=r.journal.get(key,Dungeon.class,null);
            if(d!=null&&!d.status.equals("CLOSED"))close(d);
        }
    }
}
