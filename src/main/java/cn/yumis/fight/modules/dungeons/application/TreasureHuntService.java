package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.OrreryTowerGilgamesh;
import cn.yumis.fight.modules.dungeons.content.TreasureThemes;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import java.util.*;

/** Map lifecycle and five-region encounters. All mutable state belongs to the dungeon module. */
public final class TreasureHuntService implements CombatPorts.SkillEffects {
    public enum PortalGuarantee {
        OFF,
        DIRECT,
        RAINBOW
    }
    private static final Gson JSON=new Gson();
    private static final int MAP_MIN_RADIUS=200;
    private static final int MAP_MAX_RADIUS=1000;
    private static final double OVERWORLD_PORTAL_CHANCE=0.75;
    private static final double RAINBOW_RESCUE_CHANCE=0.25;
    private static final long RAINBOW_RESCUE_MS=2200L;
    private static final double GOLDEN_GOBLIN_CHANCE=0.25;
    private static final long GOLDEN_GOBLIN_MS=25_000L;
    private static final int DICE_MAX_ROUNDS=6;
    private static final long DICE_ROLL_1_MS=900L;
    private static final long DICE_ROLL_2_MS=1000L;
    private static final long DICE_REVEAL_WIN_MS=1150L;
    private static final long DICE_REVEAL_LOSE_MS=1400L;
    private final DungeonService owner;
    private final Journal journal;
    private final MinecraftServer server;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final InventoryOperations inventory;
    private final TreasurePorts rewards;
    private final Random random;
    private final Map<UUID,ReturnPoint> outside=new HashMap<>();
    private final Map<UUID,GoldenWeapon> weapons=new LinkedHashMap<>();
    private final Map<String,Long> hits=new HashMap<>();
    private final Map<String,String> hitInstances=new HashMap<>();
    private PortalGuarantee portalGuarantee=PortalGuarantee.OFF;
    private int tick;
    TreasureHuntService(DungeonService owner,Journal journal,MinecraftServer server,DungeonPorts.Players players,DungeonPorts.Targets targets,InventoryOperations inventory,TreasurePorts rewards,Random random){this.owner=owner;this.journal=journal;this.server=server;this.players=players;this.targets=targets;this.inventory=inventory;this.rewards=rewards;this.random=random;}

    public void use(ServerPlayer p,InteractionHand hand){try{
        if(p.level().dimension()!=Level.OVERWORLD)throw new IllegalArgumentException("藏宝图只能在主世界使用");
        var stack=p.getItemInHand(hand);String raw=ItemData.string(stack,TreasureMapItem.DATA);
        if(raw.isEmpty()){
            int dx,dz;long d2;
            do{
                dx=random.nextInt(MAP_MAX_RADIUS*2+1)-MAP_MAX_RADIUS;
                dz=random.nextInt(MAP_MAX_RADIUS*2+1)-MAP_MAX_RADIUS;
                d2=(long)dx*dx+(long)dz*dz;
            }while(d2<(long)MAP_MIN_RADIUS*MAP_MIN_RADIUS||d2>(long)MAP_MAX_RADIUS*MAP_MAX_RADIUS);
            int x=p.getBlockX()+dx,z=p.getBlockZ()+dz;
            if(!server.overworld().getWorldBorder().isWithinBounds(new BlockPos(x,80,z)))throw new IllegalArgumentException("宝图坐标超出世界边界，请在边界内重试");
            JsonObject map=new JsonObject();map.addProperty("id",UUID.randomUUID().toString());map.addProperty("type","goblin_kingdom");map.addProperty("x",x);map.addProperty("z",z);
            JsonObject intent=new JsonObject();intent.addProperty("openMap",map.get("id").getAsString());
            if (inventory.transact(p, "map-open/" + map.get("id").getAsString(), intent,
                    () -> ItemData.string(stack, TreasureMapItem.DATA, map.toString()), Map.of())) {
                sendClue(p, x, z, true);
            }
            return;
        }
        JsonObject map=JsonParser.parseString(raw).getAsJsonObject();int x=map.get("x").getAsInt(),z=map.get("z").getAsInt();
        double dx = x + .5 - p.getX();
        double dz = z + .5 - p.getZ();
        if (dx * dx + dz * dz > TreasureMapItem.DIG_RADIUS * TreasureMapItem.DIG_RADIUS) {
            sendClue(p, x, z, false);
            return;
        }
        if(owner.current(p.getUUID())!=null)throw new IllegalArgumentException("请先结束当前战斗或离开副本");
        for(String instance:owner.activeInstanceIds()){Dungeon other=owner.byId(instance);if(other!=null&&other.treasure!=null&&other.treasure.region==0&&!other.treasure.cleared&&!other.status.equals("CLOSED")){long ox=(long)x-other.treasure.mapX,oz=(long)z-other.treasure.mapZ;if(ox*ox+oz*oz<128*128)throw new IllegalArgumentException("附近已有守宝战斗，请完成后再挖掘这张图");}}
        Vec3 portalPosition=legal(server.overworld(),x,z,p.getY());if(portalPosition==null)throw new IllegalArgumentException("藏宝坐标没有合法落脚点，请整理空间后重试");
        var theme=TreasureThemes.pickRandom(List.of(),"",random);
        var guards=theme.overworldGuards();
        List<Vec3> positions=findOverworldGuardSpots(server.overworld(),x,z,portalPosition.y,guards.size());
        if(positions.size()<guards.size())throw new IllegalArgumentException("附近没有合法守宝怪出生位置，请整理空间后重试");
        Dungeon d=new Dungeon();d.cell=owner.freeCell();d.dungeon=theme.dungeonId();d.difficulty=TreasureThemes.difficultyForRegion(0);d.treasure=new TreasureState();var t=d.treasure;
        t.baseLevel=players.level(p);t.owner=p.getUUID();t.theme=theme.id();t.visitedThemes.add(theme.id());
        t.mapX=x;t.mapZ=z;t.mapY=portalPosition.y;t.lastX=x+.5;t.lastY=t.mapY;t.lastZ=z+.5;
        d.roster.add(p.getUUID());d.synchronization.put(p.getUUID(),false);d.returns.put(p.getStringUUID(),owner.point(p));
        t.partySize=Math.max(1,present(d).size());
        JsonObject intent=new JsonObject();intent.addProperty("digMap",map.get("id").getAsString());
        boolean consumed;
        try{consumed=inventory.transact(p,"map-dig/"+map.get("id").getAsString(),intent,()->{if(!ItemData.string(p.getItemInHand(hand),TreasureMapItem.DATA).equals(raw))throw new IllegalArgumentException("手中藏宝图发生变化");p.getItemInHand(hand).shrink(1);},Map.of("instance/"+d.id,JSON.toJsonTree(d)));}
        finally{owner.instanceCommitted(d.id);}
        if(!consumed)return;
        try{
            for(int i=0;i<guards.size();i++){Vec3 pos=positions.get(i);owner.spawn(d,guards.get(i),i==guards.size()-1,false,pos.x,pos.y,pos.z);}
            journal.put("instance/"+d.id,d);
        }
        catch(Exception failure){for(UUID id:d.enemies){Entity e=server.overworld().getEntity(id);if(e!=null)e.discard();}d.status="CLOSED";try{inventory.transact(p,"map-refund/"+d.id,intent,()->{var refund=cn.yumis.fight.platform.ItemRegistry.item("goblin_treasure_map",1);ItemData.string(refund,TreasureMapItem.DATA,raw);inventory.requireSpace(p,refund);},Map.of("instance/"+d.id,JSON.toJsonTree(d)));}finally{owner.instanceCommitted(d.id);}throw failure;}
        arrived(d,p);players.message(p,"【"+theme.displayName()+"】守宝卫队现身！击败所有敌人获取宝箱。");
    }catch(Exception failure){players.message(p,failure.getMessage());}}

    private List<Vec3> findOverworldGuardSpots(ServerLevel level,int cx,int cz,double y,int count){
        List<Vec3> out=new ArrayList<>(count);
        for(int attempt=0;attempt<count*3&&out.size()<count;attempt++){
            double ang=attempt*(Math.PI*2.0/Math.max(6,count))+0.35;
            double dist=attempt<count?5.0:attempt<count*2?3.5:7.0;
            int gx=cx+(int)Math.round(Math.cos(ang)*dist);
            int gz=cz+(int)Math.round(Math.sin(ang)*dist);
            Vec3 pos=legal(level,gx,gz,y);
            if(pos!=null)out.add(pos);
        }
        while(out.size()<count){
            Vec3 center=legal(level,cx,cz,y);
            if(center==null)break;
            out.add(center);
        }
        return out;
    }

    /** Admin open of the goblin kingdom entrance: the same facts as the natural post-fight portal, skipping the guard battle. */
    public TreasureState openPortal(ServerPlayer admin,BlockPos at)throws Exception{
        if(admin.level().dimension()!=Level.OVERWORLD)throw new IllegalArgumentException("挖宝传送门只能生成在主世界");
        if(!server.overworld().getWorldBorder().isWithinBounds(at))throw new IllegalArgumentException("坐标超出世界边界，请换一个位置");
        for(String instance:owner.activeInstanceIds()){Dungeon other=owner.byId(instance);if(other!=null&&other.treasure!=null&&other.treasure.region==0&&!other.status.equals("CLOSED")){long ox=(long)at.getX()-other.treasure.mapX,oz=(long)at.getZ()-other.treasure.mapZ;if(ox*ox+oz*oz<128*128)throw new IllegalArgumentException("附近已有挖宝活动，请完成或离开后再开门");}}
        Vec3 portalPosition=legal(server.overworld(),at.getX(),at.getZ(),at.getY());
        if(portalPosition==null)throw new IllegalArgumentException("藏宝坐标没有合法落脚点，请整理空间后重试");
        Dungeon d=new Dungeon();d.cell=owner.freeCell();d.dungeon="goblin_camp";d.difficulty=TreasureThemes.difficultyForRegion(0);
        var t=d.treasure=TreasureState.portal(admin.getUUID(),players.level(admin),at.getX(),at.getZ(),portalPosition.y,System.currentTimeMillis());
        journal.put("instance/"+d.id,d);owner.instanceCommitted(d.id);
        announceEntrance(t);
        return t;
    }

    private void announceEntrance(TreasureState t){
        server.getPlayerList().broadcastSystemMessage(ModText.of("【挖宝】秘境入口出现！主世界 X:"+t.mapX+" Y:"+(int)t.mapY+" Z:"+t.mapZ+"，所有冒险者均可进入，30分钟后关闭。"),false);
    }

    public PortalGuarantee portalGuarantee(){
        return portalGuarantee;
    }

    public PortalGuarantee setPortalGuarantee(PortalGuarantee mode){
        this.portalGuarantee=mode==null?PortalGuarantee.OFF:mode;
        for(String instance:owner.activeInstanceIds()){
            Dungeon d=owner.byId(instance);
            if(d!=null&&d.treasure!=null&&!d.status.equals("CLOSED")&&d.treasure.region>0&&d.treasure.region<5&&d.treasure.cleared&&!d.treasure.diceActive){
                broadcastPortals(d,present(d));
            }
        }
        return this.portalGuarantee;
    }

    /** Admin direct entry into any Treasure Hunt secret-realm region (1-5) with optional theme for testing. */
    public TreasureState enterForTest(ServerPlayer player,int targetRegion,String themeInput)throws Exception{
        if(targetRegion<1||targetRegion>5)throw new IllegalArgumentException("挖宝测试区域必须为 1~5");
        String theme=validateThemeForRegion(targetRegion,themeInput);
        Dungeon existing=owner.current(player.getUUID());
        if(existing!=null)owner.leave(player,false);
        ReturnPoint ret=player.level().dimension()!=DungeonService.DIMENSION
            ?owner.point(player)
            :outside.getOrDefault(player.getUUID(),overworldFallbackPoint());
        Dungeon d=new Dungeon();
        d.cell=owner.freeCell();
        d.dungeon=targetRegion==5?"dark_city":TreasureThemes.byId(theme.isEmpty()?"forest":theme).dungeonId();
        d.difficulty=TreasureThemes.difficultyForRegion(targetRegion);
        var t=d.treasure=new TreasureState();
        t.owner=player.getUUID();
        t.baseLevel=players.level(player);
        t.region=targetRegion-1;
        t.tier=Math.min(10,3+Math.max(0,targetRegion-1));
        t.mapX=(int)Math.floor(ret.x);t.mapY=ret.y;t.mapZ=(int)Math.floor(ret.z);
        t.lastX=ret.x;t.lastY=ret.y;t.lastZ=ret.z;
        t.portal=false;
        t.expires=System.currentTimeMillis()+30*60*1000L;
        d.returns.put(player.getStringUUID(),ret);
        journal.put("instance/"+d.id,d);
        owner.instanceCommitted(d.id);
        String chosen=targetRegion==5?TreasureThemes.PALACE_THEME:(theme.isEmpty()?TreasureThemes.pickRandom(List.of(),"",random).id():theme);
        next(d,List.of(player),chosen);
        return d.treasure;
    }

    /** Admin instant guaranteed advance to the next Treasure Hunt region (current region 1-4 -> 2-5). */
    public TreasureState forceNextRegion(ServerPlayer player,String themeInput)throws Exception{
        Dungeon d=owner.current(player.getUUID());
        if(d==null||d.treasure==null||d.status.equals("CLOSED"))throw new IllegalArgumentException("当前不在挖宝秘境中，可先执行 /ydnf treasure enter 进入");
        var t=d.treasure;
        if(t.region>=5)throw new IllegalArgumentException("当前已在第 5 区（黄金王宫），没有下一层");
        int nextRegion=t.region+1;
        String theme=validateThemeForRegion(nextRegion,themeInput);
        String chosen=nextRegion==5?TreasureThemes.PALACE_THEME:(!theme.isEmpty()?theme:(!t.westTheme.isEmpty()?t.westTheme:TreasureThemes.pickRandom(t.visitedThemes,t.theme,random).id()));
        List<ServerPlayer> here=present(d);
        if(here.isEmpty())here=List.of(player);
        next(d,here,chosen);
        return d.treasure;
    }

    static String validateThemeForRegion(int region,String themeInput){
        String normalized=themeInput==null?"":themeInput.trim().toLowerCase(Locale.ROOT);
        if(normalized.isEmpty())return "";
        if(region>=5){
            if(TreasureThemes.PALACE_THEME.equals(normalized))return TreasureThemes.PALACE_THEME;
            throw new IllegalArgumentException("第 5 区固定为黄金王宫（palace）");
        }
        if(!TreasureThemes.isRotationTheme(normalized)){
            throw new IllegalArgumentException("未知挖宝主题："+themeInput+"（可用：forest、tower、palace_sky、gbl、forbidden、dark_city）");
        }
        return normalized;
    }

    private ReturnPoint overworldFallbackPoint(){
        BlockPos spawn=server.overworld().getRespawnData().pos();
        ReturnPoint fallback=new ReturnPoint();
        fallback.dimension=Level.OVERWORLD.identifier().toString();
        fallback.x=spawn.getX()+.5;fallback.y=spawn.getY();fallback.z=spawn.getZ()+.5;
        return fallback;
    }

    int effectivePassPercent(int region){
        return portalGuarantee!=PortalGuarantee.OFF?100:(int)Math.round(TreasureThemes.portalPassChance(region)*100);
    }

    private static void sendClue(ServerPlayer player, int x, int z, boolean newlyOpened) {
        Network.send(player, "treasureMapClue", Map.of("x", x, "z", z, "newlyOpened", newlyOpened));
    }

    private Vec3 legal(ServerLevel level,int x,int z,double preferredY){
        int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        for(int base:new int[]{(int)Math.floor(preferredY),top})for(int off=0;off<=16;off++)for(int sign:new int[]{1,-1}){
            BlockPos pos=new BlockPos(x,base+off*sign,z);if(pos.getY()<=level.getMinY()||pos.getY()>=level.getMaxY()-2)continue;
            if(!level.getWorldBorder().isWithinBounds(pos)||!level.getFluidState(pos).isEmpty()||!level.getFluidState(pos.below()).isEmpty())continue;
            if(level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP)&&level.noCollision(new AABB(x+.05,pos.getY(),z+.05,x+.95,pos.getY()+2.4,z+.95)))return Vec3.atBottomCenterOf(pos);
        }
        return null;
    }
    boolean inside(Dungeon d,ServerPlayer p){
        if(d==null||d.treasure==null||d.status.equals("CLOSED"))return false;
        var t=d.treasure;if(t.region==0)return !t.cleared&&p.level().dimension()==Level.OVERWORLD&&p.position().distanceToSqr(new Vec3(t.mapX+.5,t.mapY,t.mapZ+.5))<=4096;
        return p.level().dimension()==DungeonService.DIMENSION&&Math.abs(p.getX()-d.cell*512)<=43&&Math.abs(p.getZ())<=43&&p.getY()>=77&&p.getY()<=104;
    }
    private List<ServerPlayer> present(Dungeon d){return server.getPlayerList().getPlayers().stream().filter(p->inside(d,p)).toList();}
    private void arrived(Dungeon d,ServerPlayer p)throws Exception{
        boolean joining=!d.roster.contains(p.getUUID())||d.withdrawn.contains(p.getUUID());
        if(joining){Dungeon old=owner.current(p.getUUID());if(old!=null&&!old.id.equals(d.id))owner.leave(p,false);}
        if(!d.returns.containsKey(p.getStringUUID())){ReturnPoint fallback=new ReturnPoint();fallback.dimension=Level.OVERWORLD.identifier().toString();fallback.x=d.treasure.mapX+.5;fallback.y=d.treasure.mapY;fallback.z=d.treasure.mapZ+.5;d.returns.put(p.getStringUUID(),outside.getOrDefault(p.getUUID(),fallback));}
        d.roster.add(p.getUUID());d.withdrawn.remove(p.getUUID());d.synchronization.put(p.getUUID(),false);
        List<ServerPlayer> here=joining?present(d):List.of();
        if(joining)for(UUID id:d.enemies){
            Entity e=world(d).getEntity(id);
            if(e instanceof DungeonEnemy mob&&mob.isAlive()){
                Target target=targets.target(mob);
                if(target.expandParty(here.size()))journal.put("target/"+id,target);
            }
        }
        var life=players.session(p).life;if(!life.instance.equals(d.id.toString())){life.instance=d.id.toString();life.dimension=p.level().dimension().identifier().toString();life.revision++;players.save(p);players.profile(p);}
        journal.put("instance/"+d.id,d);journal.put("return/"+p.getUUID(),d.returns.get(p.getStringUUID()));
        if(joining){
            for(UUID id:d.enemies){
                Entity e=world(d).getEntity(id);
                if(e instanceof LivingEntity enemy)for(ServerPlayer observer:here)targets.context(observer,enemy);
            }
            if(d.treasure.goldenGoblin!=null){
                Entity g=world(d).getEntity(d.treasure.goldenGoblin);
                if(g instanceof LivingEntity goblin&&goblin.isAlive())targets.context(p,goblin);
            }
            syncWeapons(p);
            sendPortals(p,d);
        }
    }
    ServerLevel world(Dungeon d){return d.treasure.region==0?server.overworld():owner.level();}
    public void tick(){
        tick++;long now=System.currentTimeMillis();
        try{
            // 每 tick 推进逃跑的黄金哥布林移动、彩虹破门演出倒计时与命运双骰开奖
            for(String instance:owner.activeInstanceIds()){
                Dungeon d=owner.byId(instance);
                if(d==null||d.treasure==null||d.status.equals("CLOSED")||d.status.equals("PREPARING"))continue;
                var t=d.treasure;
                if(t.goldenGoblin!=null||t.portalRescuing||t.diceActive){
                    List<ServerPlayer> here=present(d);
                    boolean dirty=false;
                    if(t.goldenGoblin!=null)dirty|=tickGoldenGoblin(d,here,now);
                    if(t.portalRescuing&&now>=t.portalRescueAt){
                        String targetTheme=t.pendingPortalTheme;
                        t.portalRescuing=false;t.pendingPortalSide="";t.pendingPortalTheme="";t.portalRescueAt=0L;
                        next(d,here,targetTheme);
                        continue;
                    }
                    if(t.diceActive)dirty|=tickDice(d,here,now);
                    if(dirty&&!d.status.equals("CLOSED"))journal.put("instance/"+instance,d);
                }
            }
            if(tick%5==0)for(String instance:owner.activeInstanceIds()){
                String key="instance/"+instance;
                Dungeon d=journal.get(key,Dungeon.class,null);if(d==null||d.treasure==null||d.status.equals("CLOSED"))continue;var t=d.treasure;
                if(d.status.equals("PREPARING")){finish(d,"地区生成未完成，本次探索已安全结束，已获得宝箱保留。");continue;}
                if(t.expires>0&&now>=t.expires){finish(d,"挖宝时间已到，探索结束。");continue;}
                List<ServerPlayer> here=present(d);
                if(t.region==0&&!t.cleared&&here.isEmpty()){finish(d,"守宝战斗已无人参与，本次挖宝结束。");continue;}
                for(ServerPlayer p:here)arrived(d,p);
                for(UUID id:new ArrayList<>(d.roster)){ServerPlayer p=server.getPlayerList().getPlayer(id);if(!d.withdrawn.contains(id)&&(p==null||!inside(d,p))){if(p!=null)leave(d,p,false);else d.withdrawn.add(id);}}
                UUID prevOwner=t.owner;
                if(t.owner==null||here.stream().noneMatch(p->p.getUUID().equals(t.owner))){for(UUID id:d.roster)if(here.stream().anyMatch(p->p.getUUID().equals(id))){t.owner=id;break;}}
                if(t.diceActive&&!Objects.equals(prevOwner,t.owner))syncDiceEntities(d);
                if(!t.cleared){
                    d.enemies.removeIf(id->{Target target=journal.get("target/"+id,Target.class,null);Entity e=world(d).getEntity(id);if(target!=null&&target.life.dead||e!=null&&!e.isAlive()){if(e!=null){t.lastX=e.getX();t.lastY=e.getY();t.lastZ=e.getZ();}return true;}return false;});
                    if(d.enemies.isEmpty()){if(t.region==0||t.region==5||d.wave>=3)cleared(d,here);else{d.wave++;spawnWave(d);}}
                    for(UUID id:new ArrayList<>(d.enemies)){Entity e=world(d).getEntity(id);if(e instanceof DungeonEnemy mob&&mob.isAlive()){if(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().equals("golden_king"))king(d,mob,here,now);else owner.brain(d,mob,here,now);}}
                }
                if(t.region>0&&t.cleared&&t.region<5&&!t.diceActive&&!t.portalRescuing){
                    ServerPlayer controller=server.getPlayerList().getPlayer(t.owner);
                    if(controller!=null&&inside(d,controller)&&Math.abs(controller.getZ())<=2.8){
                        double relX=controller.getX()-(d.cell*512+.5);
                        if(Math.abs(relX)>=26.5&&Math.abs(relX)<=31.5){
                            String side=relX<0?"west":"east";
                            String chosen="west".equals(side)?t.westTheme:t.eastTheme;
                            if(chosen==null||chosen.isEmpty())chosen=t.region+1>=5?TreasureThemes.PALACE_THEME:TreasureThemes.pickRandom(t.visitedThemes,t.theme,random).id();
                            boolean directPass=portalGuarantee==PortalGuarantee.DIRECT
                                ||(portalGuarantee==PortalGuarantee.OFF&&random.nextDouble()<TreasureThemes.portalPassChance(t.region));
                            if(directPass){
                                next(d,here,chosen);
                                continue;
                            }
                            if(portalGuarantee==PortalGuarantee.RAINBOW||random.nextDouble()<RAINBOW_RESCUE_CHANCE){
                                t.portalRescuing=true;t.pendingPortalSide=side;t.pendingPortalTheme=chosen;t.portalRescueAt=now+RAINBOW_RESCUE_MS;
                                double px=d.cell*512+.5+("west".equals(side)?-29:29);
                                JsonObject fx=new JsonObject();
                                fx.addProperty("kind","RAINBOW_RESCUE");
                                fx.addProperty("x",px);fx.addProperty("y",80.0);fx.addProperty("z",0.5);
                                fx.addProperty("theme",chosen);fx.addProperty("title",TreasureThemes.displayName(chosen));
                                fx.addProperty("startAt",now);fx.addProperty("openAt",t.portalRescueAt);
                                for(ServerPlayer p:here){
                                    Network.send(p,"treasurePortalFx",fx);
                                    players.message(p,"……传送门突然封闭？等等——【彩虹破门】强行撕开了通往【"+TreasureThemes.displayName(chosen)+"】的通道！");
                                }
                            }else{
                                finish(d,"这次传送没有通往更深处，探索结束。已有宝箱保留。");
                                continue;
                            }
                        }
                    }
                }
                if(t.portal){for(ServerPlayer p:server.getPlayerList().getPlayers())if(p.level().dimension()==Level.OVERWORLD&&p.position().distanceToSqr(new Vec3(t.mapX+.5,t.mapY,t.mapZ+.5))<4){
                    if(t.region==0){
                        String firstTheme=TreasureThemes.pickRandom(t.visitedThemes,t.theme,random).id();
                        next(d,List.of(p),firstTheme);break;
                    }
                    else{owner.teleport(p,owner.level(),d.cell*512+.5,80,-23);arrived(d,p);}
                }}
                if(!d.status.equals("CLOSED")){journal.put(key,d);portalParticles(d);warn(d,now);}
            }
            for(ServerPlayer p:server.getPlayerList().getPlayers())if(p.level().dimension()!=DungeonService.DIMENSION)outside.put(p.getUUID(),owner.point(p));
            tickWeapons(now);
            hits.entrySet().removeIf(e->now-e.getValue()>3000);hitInstances.keySet().retainAll(hits.keySet());
        }catch(Exception failure){server.sendSystemMessage(ModText.of("挖宝处理失败："+failure.getMessage()));}
    }

    /** Called by DungeonService.died(UUID) for non-Golden-King enemy kills inside Treasure Hunt (e.g. Escape Golden Goblin). */
    void onEnemyDied(Dungeon room,UUID id){
        if(room==null||room.treasure==null||id==null)return;
        var t=room.treasure;
        if(!id.equals(t.goldenGoblin))return;
        try{
            Vec3 at=new Vec3(t.lastX,t.lastY,t.lastZ);
            Entity raw=world(room).getEntity(id);
            if(raw!=null)at=raw.position();
            t.goldenGoblin=null;t.goldenGoblinExpires=0L;t.goldenGoblinBonus=true;
            List<ServerPlayer> here=present(room);
            journal.begin();
            try{
                for(ServerPlayer p:here){
                    String key="treasureGoblin/"+room.id+"/"+t.region+"/"+p.getUUID();
                    if(!journal.get(key).isJsonNull())continue;
                    rewards.goblinLoot(p,t.baseLevel,at,room.id.toString());
                    journal.put(key,true);
                }
                journal.put("instance/"+room.id,room);
                journal.finish();
            }catch(Exception failure){journal.abort();rewards.abort();throw failure;}
            rewards.publish();
            for(ServerPlayer p:here)players.message(p,"【奇遇大胜】成功截杀逃跑的黄金哥布林！全队获得丰厚金币、通货与本区宝箱额外 +1 升阶！");
        }catch(Exception failure){server.sendSystemMessage(ModText.of("黄金哥布林奖励发放失败："+failure.getMessage()));}
    }

    private boolean tickGoldenGoblin(Dungeon d,List<ServerPlayer> here,long now){
        var t=d.treasure;
        if(t.goldenGoblin==null)return false;
        ServerLevel level=world(d);
        Entity raw=level.getEntity(t.goldenGoblin);
        Target stored=journal.get("target/"+t.goldenGoblin,Target.class,null);
        if(!(raw instanceof DungeonEnemy goblin)||!goblin.isAlive()||(stored!=null&&stored.life.dead)){
            t.goldenGoblin=null;t.goldenGoblinExpires=0L;
            return true;
        }
        if(now>=t.goldenGoblinExpires){
            level.sendParticles(ParticleTypes.POOF,goblin.getX(),goblin.getY()+0.8,goblin.getZ(),14,0.4,0.4,0.4,0.04);
            goblin.discard();
            t.goldenGoblin=null;t.goldenGoblinExpires=0L;
            for(ServerPlayer p:here)players.message(p,"逃跑的黄金哥布林钻入空间裂隙溜走了……");
            return true;
        }
        if(!CombatContentTypes.controlled(goblin)){
            double cx=d.cell*512+.5,cz=0.5;
            ServerPlayer nearest=here.stream().min(Comparator.comparingDouble(goblin::distanceToSqr)).orElse(null);
            double fx=0,fz=0;
            if(nearest!=null){
                double dx=goblin.getX()-nearest.getX(),dz=goblin.getZ()-nearest.getZ();
                double len=Math.hypot(dx,dz);
                if(len>1e-4){fx=dx/len;fz=dz/len;}else{fx=1;fz=0;}
            }else{
                double ang=now*0.002;fx=Math.cos(ang);fz=Math.sin(ang);
            }
            // 靠近竞技场边缘时顺切线转向内侧绕圈，避免卡墙被白嫖
            double rx=goblin.getX()-cx,rz=goblin.getZ()-cz;
            double rDist=Math.hypot(rx,rz);
            if(rDist>21.0){
                double nx=rx/rDist,nz=rz/rDist;
                double tx=-nz,tz=nx;
                if(tx*fx+tz*fz<0){tx=-tx;tz=-tz;}
                fx=tx*0.75-nx*0.65;fz=tz*0.75-nz*0.65;
            }
            double norm=Math.hypot(fx,fz);
            if(norm>1e-4){fx/=norm;fz/=norm;}
            double speed=0.33;
            goblin.setDeltaMovement(fx*speed,goblin.getDeltaMovement().y,fz*speed);
            float yaw=(float)(Math.toDegrees(Math.atan2(-fx,fz)));
            goblin.setYRot(yaw);goblin.yBodyRot=yaw;goblin.setYHeadRot(yaw);
        }
        if(tick%4==0){
            level.sendParticles(new DustParticleOptions(0xFFD54F,1.25f),goblin.getX(),goblin.getY()+0.9,goblin.getZ(),3,0.28,0.3,0.28,0.01);
        }
        return false;
    }

    private void spawnWave(Dungeon d)throws Exception{
        var t=d.treasure;d.summoned=false;int ox=d.cell*512;
        t.partySize=Math.max(1,present(d).size());d.status="PREPARING";journal.put("instance/"+d.id,d);
        if(t.region==5){
            owner.spawn(d,OrreryTowerGilgamesh.ID,false,true,ox+.5,80,14);
            t.nextVolley=System.currentTimeMillis()+5000;
        }else if(d.wave==3){
            var theme=TreasureThemes.byId(t.theme);
            if(t.region>=3){
                owner.spawn(d,theme.bossId(),false,true,ox+.5,80,14);
            }else{
                var elites=theme.eliteWave();
                for(int i=0;i<elites.size();i++){
                    double ang=(i-(elites.size()-1)/2.0)*0.42;
                    double r=11.0+(i%2)*3.5;
                    owner.spawn(d,elites.get(i),i<2,false,ox+.5+Math.sin(ang)*r,80,4+Math.cos(ang)*r);
                }
            }
        }else{
            var theme=TreasureThemes.byId(t.theme);
            var wave=TreasureThemes.ordinaryWave(theme,d.wave,random);
            for(int i=0;i<wave.size();i++){
                boolean outer=i>=6;
                int slot=outer?i-6:i;
                double ang=(slot-2.5)*0.48+(outer?0.24:0.0);
                double r=outer?14.5:8.5;
                double sx=ox+.5+Math.sin(ang)*r;
                double sz=2.5+Math.cos(ang)*r;
                boolean elite=(i==5||i==11);
                owner.spawn(d,wave.get(i),elite,false,sx,80,sz);
            }
            if((d.wave==1||d.wave==2)&&!t.goldenGoblinSpawned&&random.nextDouble()<GOLDEN_GOBLIN_CHANCE){
                spawnGoldenGoblin(d);
            }
        }
        d.status="RUNNING";journal.put("instance/"+d.id,d);
    }

    private void spawnGoldenGoblin(Dungeon d){
        var t=d.treasure;int ox=d.cell*512;
        double sx=ox+.5+(random.nextDouble()-0.5)*12.0;
        double sz=(random.nextDouble()-0.5)*10.0;
        DungeonEnemy goblin=owner.spawn(d,"treasure_golden_goblin",true,false,sx,80,sz,pre->{
            pre.noRewards=true;
            pre.speciesHealth=1.65;
        });
        d.enemies.remove(goblin.getUUID());
        t.goldenGoblin=goblin.getUUID();
        t.goldenGoblinExpires=System.currentTimeMillis()+GOLDEN_GOBLIN_MS;
        t.goldenGoblinSpawned=true;
        for(ServerPlayer p:present(d)){
            targets.context(p,goblin);
            players.message(p,"【奇遇】逃跑的黄金哥布林出现！25秒内将其截杀可获得丰厚金币、通货与额外宝箱升阶！");
        }
    }

    private void cleared(Dungeon d,List<ServerPlayer> here)throws Exception{
        var t=d.treasure;int tier=t.tier;double probability=t.failedProbability;
        if(t.region==0){
            tier=random.nextBoolean()?3:4;
            journal.begin();try{
                for(ServerPlayer p:here){
                    String award="treasureAward/"+d.id+"/"+t.region+"/"+p.getUUID();
                    if(!journal.get(award).isJsonNull())continue;
                    Vec3 at=new Vec3(t.lastX,t.lastY,t.lastZ);
                    rewards.chest(p,"treasure",tier,t.baseLevel,1,d.dungeon,at,d.id.toString());
                    journal.put(award,true);
                }
                t.cleared=true;
                t.portal=portalGuarantee!=PortalGuarantee.OFF||random.nextDouble()<OVERWORLD_PORTAL_CHANCE;
                if(t.portal){
                    Vec3 legal=legal(server.overworld(),t.mapX,t.mapZ,t.mapY);
                    if(legal!=null)t.mapY=legal.y;else t.portal=false;
                    t.expires=System.currentTimeMillis()+30*60*1000L;
                }
                if(!t.portal)d.status="CLOSED";
                journal.put("instance/"+d.id,d);journal.finish();owner.instanceCommitted(d.id);
            }catch(Exception e){journal.abort();rewards.abort();throw e;}
            rewards.publish();
            for(ServerPlayer p:here){players.session(p).life.instance="";players.save(p);players.profile(p);}
            if(t.portal)announceEntrance(t);
            else for(ServerPlayer p:here)players.message(p,"守宝卫队已被击败！黄金/钻石宝箱已掉落。");
            return;
        }
        probability=t.region==1?1:Math.min(1,probability+(t.region==5?1:.5));
        while(tier<10&&random.nextDouble()<probability){tier++;probability/=2;}
        if(t.goldenGoblinBonus&&tier<10)tier++;
        boolean streakOffer=t.region>=3&&t.diceStreakReady;
        boolean triggerDice=streakOffer||(t.region>=3&&random.nextDouble()<TreasureThemes.diceTriggerChance(t.region));
        if(triggerDice){
            t.cleared=true;t.tier=tier;t.failedProbability=probability;
            t.diceActive=true;t.dicePendingTier=tier;t.dicePendingTheme=d.dungeon;
            t.diceFirst=0;t.diceSecond=0;t.diceRounds=0;t.diceLastResult=0;t.diceAnimUntil=0L;
            t.diceMultiplier=1;t.diceMustWinOne=false;
            t.dicePhase=streakOffer?"CHOOSE_START":"READY_DIE_1";
            spawnDiceEntities(d);
            journal.put("instance/"+d.id,d);
            for(ServerPlayer p:here){
                if(streakOffer)players.message(p,"【跨区连庄 · 命运双骰】降临！由图主准星对准【骰子 1】滚轮选择并右键确认：稳健领 1×、常规 1× 起步，或连庄豪赌 64× 起步（最高 4096×）！");
                else players.message(p,"【命运双骰】降临！本区通关宝箱将作为赌注——由图主准星对准【骰子 1】右键交互开掷，猜对大小最高翻倍至 64×，猜错宝箱清零！");
            }
            if(t.region==5)clearWeapons(d.id.toString());
            return;
        }
        journal.begin();try{
            for(ServerPlayer p:here){
                String award="treasureAward/"+d.id+"/"+t.region+"/"+p.getUUID();
                if(!journal.get(award).isJsonNull())continue;
                Vec3 at=new Vec3(t.lastX,t.lastY,t.lastZ);
                rewards.chest(p,"treasure",tier,t.baseLevel,1,d.dungeon,at,d.id.toString());
                journal.put(award,true);
            }
            t.cleared=true;t.tier=tier;t.failedProbability=probability;
            if(t.region<5)preparePortals(d);
            else d.status="COMPLETED";
            journal.put("instance/"+d.id,d);journal.finish();owner.instanceCommitted(d.id);
        }catch(Exception e){journal.abort();rewards.abort();throw e;}
        rewards.publish();
        if(t.region<5){
            broadcastPortals(d,here);
            String hint="地区清场！每人一只宝箱已掉落。左门【"+TreasureThemes.displayName(t.westTheme)+"】/ 右门【"+TreasureThemes.displayName(t.eastTheme)+"】（通过率 "+effectivePassPercent(t.region)+"%），由图主选择！";
            for(ServerPlayer p:here)players.message(p,hint);
        }else{
            for(ServerPlayer p:here)players.message(p,"英雄王·吉尔伽美什已被击败！最终宝箱已掉落，可拾取后返回。");
        }
        if(t.region==5)clearWeapons(d.id.toString());
    }

    private void preparePortals(Dungeon d){
        var t=d.treasure;
        String[] pair=TreasureThemes.pickPortalPair(t.region+1,t.visitedThemes,t.theme,random);
        t.westTheme=pair[0];
        t.eastTheme=pair[1];
    }

    private void broadcastPortals(Dungeon d,List<ServerPlayer> targetsList){
        for(ServerPlayer p:targetsList)sendPortals(p,d);
    }

    private void sendPortals(ServerPlayer p,Dungeon d){
        if(d==null||d.treasure==null||d.treasure.region<=0||d.treasure.region>=5||!d.treasure.cleared||d.treasure.diceActive||d.treasure.westTheme.isEmpty()){
            Network.send(p,"treasurePortals",Map.of("active",false));
            return;
        }
        var t=d.treasure;
        JsonObject pkt=new JsonObject();
        pkt.addProperty("active",true);
        pkt.addProperty("cell",d.cell);
        pkt.addProperty("westTheme",t.westTheme);
        pkt.addProperty("westTitle",TreasureThemes.displayName(t.westTheme));
        pkt.addProperty("westColor",TreasureThemes.accentColor(t.westTheme));
        pkt.addProperty("eastTheme",t.eastTheme);
        pkt.addProperty("eastTitle",TreasureThemes.displayName(t.eastTheme));
        pkt.addProperty("eastColor",TreasureThemes.accentColor(t.eastTheme));
        pkt.addProperty("passPercent",effectivePassPercent(t.region));
        Network.send(p,"treasurePortals",pkt);
    }

    // ---- 命运双骰（1-9 不重复猜大小翻倍，含跨区连庄 64x 起步最高 4096x） ----
    private void spawnDiceEntities(Dungeon d){
        discardDiceEntities(d);
        var t=d.treasure;
        ServerLevel level=owner.level();
        int ox=d.cell*512;
        var type=DungeonContentTypes.TREASURE_DICE.get();
        TreasureDiceEntity d1=new TreasureDiceEntity(type,level);
        d1.setPos(ox+.5-3.2,80.9,0.5);
        TreasureDiceEntity d2=new TreasureDiceEntity(type,level);
        d2.setPos(ox+.5+3.2,80.9,0.5);
        level.addFreshEntity(d1);
        level.addFreshEntity(d2);
        t.diceEntity1=d1.getUUID();
        t.diceEntity2=d2.getUUID();
        syncDiceEntities(d);
    }

    private void syncDiceEntities(Dungeon d){
        var t=d.treasure;
        ServerLevel level=owner.level();
        String ownerStr=t.owner==null?"":t.owner.toString();
        String inst=d.id.toString();
        long roll1="ROLLING_1".equals(t.dicePhase)?t.diceAnimUntil:0L;
        long roll2="ROLLING_2".equals(t.dicePhase)?t.diceAnimUntil:0L;
        int face1="ROLLING_1".equals(t.dicePhase)?0:t.diceFirst;
        int face2="ROLLING_2".equals(t.dicePhase)?0:t.diceSecond;
        if(t.diceEntity1!=null&&level.getEntity(t.diceEntity1) instanceof TreasureDiceEntity e1){
            e1.syncState(1,face1,t.diceFirst,t.diceMultiplier,t.diceRounds,t.diceLastResult,t.dicePhase,ownerStr,inst,t.diceStreakReady,t.diceMustWinOne,roll1);
        }
        if(t.diceEntity2!=null&&level.getEntity(t.diceEntity2) instanceof TreasureDiceEntity e2){
            e2.syncState(2,face2,t.diceFirst,t.diceMultiplier,t.diceRounds,t.diceLastResult,t.dicePhase,ownerStr,inst,t.diceStreakReady,t.diceMustWinOne,roll2);
        }
    }

    private void discardDiceEntities(Dungeon d){
        var t=d.treasure;
        ServerLevel level=owner.level();
        if(t.diceEntity1!=null){Entity e=level.getEntity(t.diceEntity1);if(e!=null)e.discard();t.diceEntity1=null;}
        if(t.diceEntity2!=null){Entity e=level.getEntity(t.diceEntity2);if(e!=null)e.discard();t.diceEntity2=null;}
    }

    public void diceAction(ServerPlayer p,JsonObject req)throws Exception{
        Dungeon d=owner.current(p.getUUID());
        if(d==null||d.treasure==null||!d.treasure.diceActive)throw new IllegalArgumentException("当前没有进行中的命运双骰");
        var t=d.treasure;
        if(!inside(d,p))throw new IllegalArgumentException("请回到秘境场地内操作骰子");
        if(t.owner==null||!t.owner.equals(p.getUUID()))throw new IllegalArgumentException("仅图主可操作命运双骰");
        long now=System.currentTimeMillis();
        if(now<t.diceAnimUntil)return;
        String act=req.has("action")?req.get("action").getAsString():"";
        List<ServerPlayer> here=present(d);
        switch(t.dicePhase){
            case "CHOOSE_START" -> {
                switch(act){
                    case "cashout_1x" -> {
                        t.diceStreakReady=false;
                        settleDice(d,here,1,"图主选择稳健收手，全队领取 1× 通关宝箱！");
                        return;
                    }
                    case "start_1x" -> {
                        t.diceStreakReady=false;t.diceMultiplier=1;t.diceMustWinOne=false;
                        beginRollFirst(d,here,now);
                    }
                    case "start_64x" -> {
                        t.diceStreakReady=false;t.diceMultiplier=64;t.diceMustWinOne=true;
                        for(ServerPlayer member:here)players.message(member,"【连庄豪赌开启！】以 64× 起步押注，首轮猜中即达 128×（最高 4096×）！");
                        beginRollFirst(d,here,now);
                    }
                    default -> throw new IllegalArgumentException("无效的开局选择");
                }
            }
            case "READY_DIE_1" -> {
                if("cashout".equals(act)){
                    if(t.diceMustWinOne)throw new IllegalArgumentException("连庄豪赌至少需赢得首轮才可收手");
                    settleDice(d,here,t.diceMultiplier,"图主见好就收，全队领取 ×"+t.diceMultiplier+" 通关宝箱！");
                    return;
                }
                if("roll_1".equals(act)){
                    beginRollFirst(d,here,now);
                }else throw new IllegalArgumentException("请先对准【骰子 1】掷出第一颗骰子");
            }
            case "READY_DIE_2" -> {
                if(!"guess_higher".equals(act)&&!"guess_lower".equals(act))throw new IllegalArgumentException("请对准【骰子 2】猜测更大或更小");
                t.diceGuessHigher="guess_higher".equals(act);
                int pick=1+random.nextInt(8);
                if(pick>=t.diceFirst)pick++;
                t.diceSecond=pick;
                t.dicePhase="ROLLING_2";
                t.diceAnimUntil=now+DICE_ROLL_2_MS;
                syncDiceEntities(d);
                journal.put("instance/"+d.id,d);
                for(ServerPlayer member:here)players.message(member,"图主押注第二颗骰子【"+(t.diceGuessHigher?"更大 (>"+t.diceFirst+")":"更小 (<"+t.diceFirst+")")+"】——骰子旋转中……");
            }
            default -> {}
        }
    }

    private void beginRollFirst(Dungeon d,List<ServerPlayer> here,long now)throws Exception{
        var t=d.treasure;
        t.diceFirst=1+random.nextInt(9);
        t.diceSecond=0;t.diceLastResult=0;
        t.dicePhase="ROLLING_1";
        t.diceAnimUntil=now+DICE_ROLL_1_MS;
        syncDiceEntities(d);
        journal.put("instance/"+d.id,d);
    }

    private boolean tickDice(Dungeon d,List<ServerPlayer> here,long now)throws Exception{
        var t=d.treasure;
        if(!t.diceActive||t.diceAnimUntil<=0||now<t.diceAnimUntil)return false;
        t.diceAnimUntil=0L;
        switch(t.dicePhase){
            case "ROLLING_1" -> {
                t.dicePhase="READY_DIE_2";
                syncDiceEntities(d);
                for(ServerPlayer p:here)players.message(p,"【骰子 1】定格为【"+t.diceFirst+"】！请图主对准【骰子 2】猜大小。");
                return true;
            }
            case "ROLLING_2" -> {
                boolean won=t.diceGuessHigher?(t.diceSecond>t.diceFirst):(t.diceSecond<t.diceFirst);
                t.diceLastResult=won?1:-1;
                t.dicePhase="REVEAL";
                if(won){
                    t.diceMultiplier=Math.min(4096,t.diceMultiplier*2);
                    t.diceRounds++;
                    t.diceMustWinOne=false;
                    t.diceAnimUntil=now+DICE_REVEAL_WIN_MS;
                    syncDiceEntities(d);
                    for(ServerPlayer p:here)players.message(p,"【猜中！】"+t.diceFirst+" → "+t.diceSecond+"，宝箱倍率翻倍至 ×"+t.diceMultiplier+"（第 "+t.diceRounds+"/"+DICE_MAX_ROUNDS+" 轮）！");
                }else{
                    t.diceAnimUntil=now+DICE_REVEAL_LOSE_MS;
                    syncDiceEntities(d);
                    for(ServerPlayer p:here)players.message(p,"【猜错了】"+t.diceFirst+" → "+t.diceSecond+"，本区通关宝箱归零！");
                }
                return true;
            }
            case "REVEAL" -> {
                if(t.diceLastResult<0){
                    t.diceStreakReady=false;
                    settleDice(d,here,0,"命运双骰失败，本区通关宝箱已丢失。");
                }else if(t.diceRounds>=DICE_MAX_ROUNDS){
                    if(t.diceMultiplier>=64)t.diceStreakReady=true;
                    String extra=t.region<5?"，且下一区域必出【跨区连庄命运双骰】！":"！";
                    settleDice(d,here,t.diceMultiplier,"【6 轮封顶全胜！】全队自动领取 ×"+t.diceMultiplier+" 超级宝箱"+extra);
                }else{
                    t.dicePhase="READY_DIE_1";
                    t.diceFirst=0;t.diceSecond=0;t.diceLastResult=0;
                    syncDiceEntities(d);
                    for(ServerPlayer p:here)players.message(p,"当前倍率 ×"+t.diceMultiplier+"！图主可对准【骰子 1】继续冲刺，或选择见好就收。");
                }
                return true;
            }
            default -> {return false;}
        }
    }

    private void settleDice(Dungeon d,List<ServerPlayer> here,int finalMultiplier,String announcement)throws Exception{
        var t=d.treasure;
        discardDiceEntities(d);
        t.diceActive=false;t.dicePhase="";t.diceAnimUntil=0L;
        t.diceFirst=0;t.diceSecond=0;t.diceLastResult=0;t.diceMustWinOne=false;
        journal.begin();try{
            if(finalMultiplier>0){
                Vec3 at=new Vec3(d.cell*512+.5,80.2,0.5);
                for(ServerPlayer p:here){
                    String award="treasureAward/"+d.id+"/"+t.region+"/"+p.getUUID();
                    if(!journal.get(award).isJsonNull())continue;
                    rewards.chest(p,"treasure",t.dicePendingTier,t.baseLevel,finalMultiplier,t.dicePendingTheme,at,d.id.toString());
                    journal.put(award,true);
                }
            }
            if(t.region<5)preparePortals(d);
            else d.status="COMPLETED";
            journal.put("instance/"+d.id,d);journal.finish();owner.instanceCommitted(d.id);
        }catch(Exception e){journal.abort();rewards.abort();throw e;}
        rewards.publish();
        for(ServerPlayer p:here)players.message(p,announcement);
        if(t.region<5){
            broadcastPortals(d,here);
            String hint="传送门已开启：左门【"+TreasureThemes.displayName(t.westTheme)+"】/ 右门【"+TreasureThemes.displayName(t.eastTheme)+"】（通过率 "+effectivePassPercent(t.region)+"%），由图主选择！";
            for(ServerPlayer p:here)players.message(p,hint);
        }else{
            for(ServerPlayer p:here)players.message(p,"黄金王宫挑战全部完成！可拾取战利品后返回。");
        }
    }

    private void next(Dungeon d,List<ServerPlayer> here,String chosenTheme)throws Exception{
        var t=d.treasure;int previous=t.region;
        d.status="PREPARING";
        for(ServerPlayer p:here){ReturnPoint point=d.returns.get(p.getStringUUID());if(point!=null)owner.rewards.move(p,d.id.toString(),point);}
        clearWeapons(d.id.toString());
        owner.gilgamesh.close(d);
        discardDiceEntities(d);
        if(t.goldenGoblin!=null){Entity g=world(d).getEntity(t.goldenGoblin);if(g!=null)g.discard();t.goldenGoblin=null;}
        for(UUID id:d.enemies){Entity e=world(d).getEntity(id);if(e!=null)e.discard();}d.enemies.clear();
        t.region++;t.cleared=false;d.wave=0;
        t.westTheme="";t.eastTheme="";t.pendingPortalSide="";t.pendingPortalTheme="";t.portalRescuing=false;t.portalRescueAt=0L;
        t.goldenGoblinExpires=0L;t.goldenGoblinSpawned=false;t.goldenGoblinBonus=false;
        t.diceActive=false;t.dicePhase="";t.diceFirst=0;t.diceSecond=0;t.diceMultiplier=1;t.diceRounds=0;t.diceLastResult=0;t.diceAnimUntil=0L;t.diceMustWinOne=false;
        if(t.region>=5){
            t.theme=TreasureThemes.PALACE_THEME;
            if(d.dungeon==null||d.dungeon.isEmpty())d.dungeon="dark_city";
        }else{
            String nextTheme=(chosenTheme!=null&&TreasureThemes.isRotationTheme(chosenTheme))
                ?chosenTheme:TreasureThemes.pickRandom(t.visitedThemes,t.theme,random).id();
            t.theme=nextTheme;
            if(!t.visitedThemes.contains(nextTheme))t.visitedThemes.add(nextTheme);
            d.dungeon=TreasureThemes.byId(nextTheme).dungeonId();
        }
        d.difficulty=TreasureThemes.difficultyForRegion(t.region);
        owner.forceArena(d,true);owner.buildTreasureArena(d.cell,t.theme);
        if(previous==0)d.withdrawn.addAll(d.roster);
        journal.put("instance/"+d.id,d);
        for(ServerPlayer p:here){if(previous==0&&!d.returns.containsKey(p.getStringUUID()))d.returns.put(p.getStringUUID(),owner.point(p));owner.teleport(p,owner.level(),d.cell*512+.5,80,-23);arrived(d,p);}
        broadcastPortals(d,here);
        spawnWave(d);owner.rewards.publish();
        int hellPlus=TreasureThemes.hellPlusTier(t.region);
        String diffLabel=hellPlus>0?"地狱+"+hellPlus:switch(d.difficulty){case WARRIOR->"勇士";case KING->"王者";case HELL->"地狱";default->"普通";};
        String enterMsg=t.region==5
            ?"进入第 5 区域【黄金王宫 · "+diffLabel+"】——英雄王·吉尔伽美什降临！"
            :"进入第 "+t.region+" 区域【"+TreasureThemes.displayName(t.theme)+" · "+diffLabel+"】！";
        for(ServerPlayer p:here){players.profile(p);players.message(p,enterMsg);}
    }

    private void portalParticles(Dungeon d){
        var t=d.treasure;
        if(t.portal)portalRing(server.overworld(),t.mapX+.5,t.mapY,t.mapZ+.5,0xFFD858);
        if(t.region>0&&t.cleared&&t.region<5&&!t.diceActive){
            portalRing(owner.level(),d.cell*512+.5-29,80,0.5,TreasureThemes.accentColor(t.westTheme));
            portalRing(owner.level(),d.cell*512+.5+29,80,0.5,TreasureThemes.accentColor(t.eastTheme));
        }
    }
    private void portalRing(ServerLevel level,double x,double y,double z,int rgb){
        var dust=new DustParticleOptions(rgb,1.35f);
        for(int i=0;i<12;i++){double a=i*Math.PI/6;level.sendParticles(dust,x,y+2+Math.sin(a)*2,z+Math.cos(a)*1.8,1,0,0,0,0);}
    }
    private void warn(Dungeon d,long now){var t=d.treasure;if(t.expires==0)return;long left=t.expires-now;String text=null;if(left<=60000&&!t.warningOne){t.warningOne=true;text="挖宝入口与秘境将在1分钟后关闭。";}else if(left<=300000&&!t.warningFive){t.warningFive=true;text="挖宝入口与秘境剩余5分钟。";}if(text!=null)for(ServerPlayer p:present(d))players.message(p,text);}
    void leave(Dungeon d,ServerPlayer p,boolean back)throws Exception{
        d.withdrawn.add(p.getUUID());
        owner.gilgamesh.playerLeft(p,d);
        Network.send(p,"treasurePortals",Map.of("active",false));
        ReturnPoint point=d.returns.get(p.getStringUUID());
        if(point!=null){owner.rewards.move(p,d.id.toString(),point);if(back)owner.back(p,point);}
        var life=players.session(p).life;life.instance="";life.dimension=p.level().dimension().identifier().toString();
        journal.put("instance/"+d.id,d);players.save(p);players.profile(p);owner.rewards.publish();
    }
    void finish(Dungeon d,String message)throws Exception{
        for(ServerPlayer p:present(d)){players.message(p,message);leave(d,p,d.treasure.region>0);}
        for(UUID id:d.roster){ServerPlayer p=server.getPlayerList().getPlayer(id);ReturnPoint point=d.returns.get(id.toString());if(p!=null&&point!=null)owner.rewards.move(p,d.id.toString(),point);}
        owner.rewards.publish();
        owner.gilgamesh.close(d);
        discardDiceEntities(d);
        if(d.treasure.goldenGoblin!=null){Entity g=world(d).getEntity(d.treasure.goldenGoblin);if(g!=null)g.discard();d.treasure.goldenGoblin=null;}
        d.status="CLOSED";journal.put("instance/"+d.id,d);owner.instanceCommitted(d.id);
        clearWeapons(d.id.toString());
        for(Entity e:world(d).getAllEntities())if(e instanceof DungeonEnemy mob&&mob.instance().equals(d.id.toString())){owner.cancelBoss(e.getUUID());e.discard();}
        if(d.treasure.region>0)owner.forceArena(d,false);
    }
    public void died(UUID boss){for(var weapon:new ArrayList<>(weapons.values()))if(weapon.boss.equals(boss))removeWeapon(weapon);}
    private void king(Dungeon d,DungeonEnemy boss,List<ServerPlayer> here,long now){
        boss.getNavigation().stop();if(cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(boss)||now<d.treasure.nextVolley)return;d.treasure.nextVolley=now+5000;
        List<ServerPlayer> alive=here.stream().filter(Entity::isAlive).toList();if(alive.isEmpty())return;
        boss.getLookControl().setLookAt(alive.getFirst());Vec3 forward=boss.getLookAngle();Vec3 side=new Vec3(forward.z,0,-forward.x).normalize();
        for(int i=0;i<alive.size();i++){GoldenWeapon weapon=new GoldenWeapon();weapon.boss=boss.getUUID();weapon.target=alive.get(i).getUUID();weapon.instance=d.id.toString();weapon.created=now;weapon.model=GoldenWeapon.MODELS.get(random.nextInt(GoldenWeapon.MODELS.size()));weapon.position(boss.position().subtract(forward.scale(2)).add(side.scale((i-(alive.size()-1)/2d)*1.2)).add(0,2.7,0));weapon.aim(alive.get(i).position().add(0,.8,0));weapons.put(weapon.id,weapon);sendWeapon(weapon,false);}
    }
    private void tickWeapons(long now){
        for(var w:new ArrayList<>(weapons.values())){
            Dungeon d=owner.byId(w.instance);Entity raw=d==null?null:world(d).getEntity(w.boss);Target target=journal.get("target/"+w.boss,Target.class,null);
            if(d==null||d.status.equals("CLOSED")||raw==null||!raw.isAlive()||target!=null&&target.life.dead){removeWeapon(w);continue;}
            if(w.phase.equals("AIM")){ServerPlayer p=server.getPlayerList().getPlayer(w.target);if(p!=null&&inside(d,p))w.aim(p.position().add(0,.8,0));if(now-w.created>=GoldenWeapon.WINDUP){w.phase="FLY";w.launched=now;}sendWeapon(w,false);continue;}
            if(w.phase.equals("FLY")){
                Vec3 from=w.position();double step=Math.min(GoldenWeapon.RANGE-w.distance,GoldenWeapon.SPEED/20);Vec3 to=from.add(w.direction().scale(step));
                BlockHitResult block=world(d).clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,raw));boolean landed=block.getType()!=HitResult.Type.MISS;if(landed)to=block.getLocation();
                for(ServerPlayer p:present(d))if(p.isAlive()&&!w.struck.contains(p.getUUID())&&(p.getBoundingBox().inflate(.2).contains(from)||p.getBoundingBox().inflate(.2).clip(from,to).isPresent())){w.struck.add(p.getUUID());issueHit(w,p,from,to,false,now);}
                w.distance+=from.distanceTo(to);w.position(to);if(landed){w.phase="LAND";w.landed=now;}else if(w.distance>=GoldenWeapon.RANGE){removeWeapon(w);continue;}sendWeapon(w,false);
            }else if(now-w.landed>=GoldenWeapon.FUSE){for(ServerPlayer p:present(d))issueHit(w,p,w.position(),w.position(),true,now);w.phase="EXPLODE";removeWeapon(w);}
        }
    }
    private void issueHit(GoldenWeapon w,ServerPlayer p,Vec3 from,Vec3 to,boolean explosion,long now){
        String id=w.id+(explosion?"/explosion":"/direct");String key="treasureHit/"+id+"/"+p.getUUID();if(!journal.get(key).isJsonNull()||hits.containsKey(id+"/"+p.getUUID()))return;
        if(explosion&&p.position().distanceToSqr(to)>GoldenWeapon.RADIUS*GoldenWeapon.RADIUS)return;
        hits.put(id+"/"+p.getUUID(),now);hitInstances.put(id+"/"+p.getUUID(),w.instance);
        JsonObject action=new JsonObject();action.addProperty("id",id);action.add("boss",JSON.toJsonTree(targets.target((LivingEntity)world(owner.byId(w.instance)).getEntity(w.boss))));action.addProperty("kind",explosion?"GOLD_EXPLOSION":"GOLD_DIRECT");action.addProperty("shape","CIRCLE");action.addProperty("x",p.getX());action.addProperty("y",p.getY());action.addProperty("z",p.getZ());action.addProperty("dx",0);action.addProperty("dz",0);action.addProperty("radius",explosion?GoldenWeapon.RADIUS:1);action.addProperty("halfAngle",Math.PI);action.addProperty("windupTicks",0);action.addProperty("activeTicks",10);action.addProperty("coefficient",explosion?2:1);action.addProperty("magic",explosion);action.addProperty("element",-1);action.addProperty("startedAt",now);action.addProperty("treasureHit",true);Network.send(p,"bossAction",action);
    }
    private void sendWeapon(GoldenWeapon w,boolean remove){JsonObject packet=JSON.toJsonTree(w).getAsJsonObject();packet.addProperty("remove",remove);Dungeon d=owner.byId(w.instance);if(d!=null)for(ServerPlayer p:present(d))Network.send(p,"treasureWeapon",packet);}
    private void removeWeapon(GoldenWeapon w){weapons.remove(w.id);sendWeapon(w,true);}
    private void clearWeapons(String instance){for(var w:new ArrayList<>(weapons.values()))if(w.instance.equals(instance))removeWeapon(w);}
    private void syncWeapons(ServerPlayer p){for(var w:weapons.values())if(inside(owner.byId(w.instance),p)){JsonObject packet=JSON.toJsonTree(w).getAsJsonObject();packet.addProperty("remove",false);Network.send(p,"treasureWeapon",packet);}}
    @Override public CombatPorts.Commit prepare(ServerPlayer p,LivingEntity target,JsonObject report,Receipt receipt,Life actor){if(!report.has("treasureAction"))return CombatPorts.Commit.NONE;String key="treasureHit/"+report.get("treasureAction").getAsString()+"/"+p.getUUID();return new CombatPorts.Commit(){public void persist()throws Exception{journal.put(key,true);}};}
}
