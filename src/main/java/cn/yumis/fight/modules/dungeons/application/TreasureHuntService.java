package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.dungeons.content.TreasureArena;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.combat.api.*;
import cn.yumis.fight.modules.presentation.api.ModText;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;
import net.minecraft.core.component.DataComponents;
import java.util.*;

/** Map lifecycle and five-region encounters. All mutable state belongs to the dungeon module. */
public final class TreasureHuntService implements CombatPorts.SkillEffects {
    private static final Gson JSON=new Gson();
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
    private int tick;
    TreasureHuntService(DungeonService owner,Journal journal,MinecraftServer server,DungeonPorts.Players players,DungeonPorts.Targets targets,InventoryOperations inventory,TreasurePorts rewards,Random random){this.owner=owner;this.journal=journal;this.server=server;this.players=players;this.targets=targets;this.inventory=inventory;this.rewards=rewards;this.random=random;}

    public void use(ServerPlayer p,InteractionHand hand){try{
        if(p.level().dimension()!=Level.OVERWORLD)throw new IllegalArgumentException("藏宝图只能在主世界使用");
        var stack=p.getItemInHand(hand);String raw=ItemData.string(stack,"treasure_map");
        if(raw.isEmpty()){
            int dx,dz;do{dx=random.nextInt(2001)-1000;dz=random.nextInt(2001)-1000;}while((long)dx*dx+(long)dz*dz>1_000_000);
            int x=p.getBlockX()+dx,z=p.getBlockZ()+dz;
            if(!server.overworld().getWorldBorder().isWithinBounds(new BlockPos(x,80,z)))throw new IllegalArgumentException("宝图坐标超出世界边界，请在边界内重试");
            JsonObject map=new JsonObject();map.addProperty("id",UUID.randomUUID().toString());map.addProperty("type","goblin_kingdom");map.addProperty("x",x);map.addProperty("z",z);
            JsonObject intent=new JsonObject();intent.addProperty("openMap",map.get("id").getAsString());
            inventory.transact(p,"map-open/"+map.get("id").getAsString(),intent,()->{ItemData.string(stack,"treasure_map",map.toString());stack.set(DataComponents.CUSTOM_NAME,ModText.of("已开启的哥布林王国藏宝图 · X:"+x+" Z:"+z));},Map.of());
            players.message(p,"藏宝地点：X="+x+"，Z="+z+"；水平10格内再次使用，不检查高度。");return;
        }
        JsonObject map=JsonParser.parseString(raw).getAsJsonObject();int x=map.get("x").getAsInt(),z=map.get("z").getAsInt();
        double dx=p.getX()-(x+.5),dz=p.getZ()-(z+.5);if(dx*dx+dz*dz>100)throw new IllegalArgumentException("请前往藏宝地点的水平10格以内：X="+x+" Z="+z);
        if(owner.current(p.getUUID())!=null)throw new IllegalArgumentException("请先结束当前战斗或离开副本");
        for(String key:journal.keys("instance/")){Dungeon other=journal.get(key,Dungeon.class,null);if(other!=null&&other.treasure!=null&&other.treasure.region==0&&!other.treasure.cleared&&!other.status.equals("CLOSED")){long ox=(long)x-other.treasure.mapX,oz=(long)z-other.treasure.mapZ;if(ox*ox+oz*oz<128*128)throw new IllegalArgumentException("附近已有守宝战斗，请完成后再挖掘这张图");}}
        List<Vec3> positions=new ArrayList<>();for(int i=0;i<4;i++){Vec3 pos=legal(server.overworld(),x+(i-2)*3,z+5,p.getY());if(pos==null)throw new IllegalArgumentException("附近没有合法守宝怪出生位置，请整理空间后重试");positions.add(pos);}
        Vec3 portalPosition=legal(server.overworld(),x,z,p.getY());if(portalPosition==null)throw new IllegalArgumentException("藏宝坐标没有合法落脚点，请整理空间后重试");
        Dungeon d=new Dungeon();d.cell=owner.freeCell();d.dungeon="goblin_camp";d.difficulty=Difficulty.HELL;d.treasure=new TreasureState();var t=d.treasure;t.baseLevel=players.level(p);t.owner=p.getUUID();t.mapX=x;t.mapZ=z;t.mapY=positions.getFirst().y;t.lastX=x+.5;t.lastY=t.mapY;t.lastZ=z+.5;
        d.roster.add(p.getUUID());d.synchronization.put(p.getUUID(),false);d.returns.put(p.getStringUUID(),owner.point(p));
        t.mapY=portalPosition.y;t.partySize=Math.max(1,present(d).size());
        JsonObject intent=new JsonObject();intent.addProperty("digMap",map.get("id").getAsString());
        boolean consumed=inventory.transact(p,"map-dig/"+map.get("id").getAsString(),intent,()->{if(!ItemData.string(p.getItemInHand(hand),"treasure_map").equals(raw))throw new IllegalArgumentException("手中藏宝图发生变化");p.getItemInHand(hand).shrink(1);},Map.of("instance/"+d.id,JSON.toJsonTree(d)));
        if(!consumed)return;
        try{String[] types={"goblin","goblin","goblin_thrower","goblin_captain"};for(int i=0;i<4;i++){Vec3 pos=positions.get(i);owner.spawn(d,types[i],i==3,false,pos.x,pos.y,pos.z);}journal.put("instance/"+d.id,d);}
        catch(Exception failure){for(UUID id:d.enemies){Entity e=server.overworld().getEntity(id);if(e!=null)e.discard();}d.status="CLOSED";inventory.transact(p,"map-refund/"+d.id,intent,()->{var refund=cn.yumis.fight.platform.ItemRegistry.item("goblin_treasure_map",1);ItemData.string(refund,"treasure_map",raw);refund.set(DataComponents.CUSTOM_NAME,ModText.of("已开启的哥布林王国藏宝图 · X:"+x+" Z:"+z));inventory.requireSpace(p,refund);},Map.of("instance/"+d.id,JSON.toJsonTree(d)));throw failure;}
        arrived(d,p);players.message(p,"守宝怪出现！击败所有敌人获得宝箱。");
    }catch(Exception failure){players.message(p,failure.getMessage());}}

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
        if(joining){int count=Math.max(1,present(d).size());for(UUID id:d.enemies){Entity e=world(d).getEntity(id);if(e instanceof DungeonEnemy mob){Target target=targets.target(mob);if(count>target.partySize){target.life.q=EncounterScaling.expandedQ(target.life.q,target.partySize,count);target.partySize=count;target.life.basis++;target.life.revision++;journal.put("target/"+id,target);}}}}
        var life=players.session(p).life;if(!life.instance.equals(d.id.toString())){life.instance=d.id.toString();life.dimension=p.level().dimension().identifier().toString();life.revision++;players.save(p);players.profile(p);}
        journal.put("instance/"+d.id,d);journal.put("return/"+p.getUUID(),d.returns.get(p.getStringUUID()));
        if(joining){for(UUID id:d.enemies){Entity e=world(d).getEntity(id);if(e instanceof LivingEntity enemy)targets.context(p,enemy);}syncWeapons(p);}
    }
    ServerLevel world(Dungeon d){return d.treasure.region==0?server.overworld():owner.level();}
    public void tick(){
        tick++;long now=System.currentTimeMillis();
        try{
            if(tick%5==0)for(String key:journal.keys("instance/")){
                Dungeon d=journal.get(key,Dungeon.class,null);if(d==null||d.treasure==null||d.status.equals("CLOSED"))continue;var t=d.treasure;
                if(d.status.equals("PREPARING")){finish(d,"地区生成未完成，本次探索已安全结束，已获得宝箱保留。");continue;}
                if(t.expires>0&&now>=t.expires){finish(d,"挖宝时间已到，探索结束。");continue;}
                List<ServerPlayer> here=present(d);
                if(t.region==0&&!t.cleared&&here.isEmpty()){finish(d,"守宝战斗已无人参与，本次挖宝结束。");continue;}
                for(ServerPlayer p:here)arrived(d,p);
                for(UUID id:new ArrayList<>(d.roster)){ServerPlayer p=server.getPlayerList().getPlayer(id);if(!d.withdrawn.contains(id)&&(p==null||!inside(d,p))){if(p!=null)leave(d,p,false);else d.withdrawn.add(id);}}
                if(t.owner==null||here.stream().noneMatch(p->p.getUUID().equals(t.owner))){for(UUID id:d.roster)if(here.stream().anyMatch(p->p.getUUID().equals(id))){t.owner=id;break;}}
                if(!t.cleared){
                    d.enemies.removeIf(id->{Target target=journal.get("target/"+id,Target.class,null);Entity e=world(d).getEntity(id);if(target!=null&&target.life.dead||e!=null&&!e.isAlive()){if(e!=null){t.lastX=e.getX();t.lastY=e.getY();t.lastZ=e.getZ();}return true;}return false;});
                    if(d.enemies.isEmpty()){if(t.region==0||t.region==5||d.wave>=(t.region>=3?3:2))cleared(d,here);else{d.wave++;spawnWave(d);}}
                    for(UUID id:new ArrayList<>(d.enemies)){Entity e=world(d).getEntity(id);if(e instanceof DungeonEnemy mob&&mob.isAlive()){if(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath().equals("golden_king"))king(d,mob,here,now);else owner.brain(d,mob,here,now);}}
                }
                if(t.region>0&&t.cleared&&t.region<5){ServerPlayer controller=server.getPlayerList().getPlayer(t.owner);if(controller!=null&&inside(d,controller)&&Math.abs(controller.getX()-d.cell*512)>=27&&Math.abs(controller.getX()-d.cell*512)<=31&&Math.abs(controller.getZ())<=2.5){if(random.nextBoolean())next(d,here);else finish(d,"这次传送没有通往更深处，探索结束。已有宝箱保留。");}}
                if(t.portal){for(ServerPlayer p:server.getPlayerList().getPlayers())if(p.level().dimension()==Level.OVERWORLD&&p.position().distanceToSqr(new Vec3(t.mapX+.5,t.mapY,t.mapZ+.5))<4){
                    if(t.region==0){next(d,List.of(p));break;}
                    else{owner.teleport(p,owner.level(),d.cell*512+.5,80,-23);arrived(d,p);}
                }}
                if(!d.status.equals("CLOSED")){journal.put(key,d);portalParticles(d);warn(d,now);}
            }
            for(ServerPlayer p:server.getPlayerList().getPlayers())if(p.level().dimension()!=DungeonService.DIMENSION)outside.put(p.getUUID(),owner.point(p));
            tickWeapons(now);
            hits.entrySet().removeIf(e->now-e.getValue()>3000);hitInstances.keySet().retainAll(hits.keySet());
        }catch(Exception failure){server.sendSystemMessage(ModText.of("挖宝处理失败："+failure.getMessage()));}
    }
    private void spawnWave(Dungeon d)throws Exception{
        var t=d.treasure;d.summoned=false;int ox=d.cell*512;
        t.partySize=Math.max(1,present(d).size());d.status="PREPARING";journal.put("instance/"+d.id,d);
        if(t.region==5||d.wave==3){String boss=t.region==5?"golden_king":t.theme.equals("tower")?"lucas":"goblin_shaman";owner.spawn(d,boss,false,true,ox+.5,80,14);t.nextVolley=System.currentTimeMillis()+5000;}
        else{String[][] tower={{"dragonman","dragonman","wyvern","blue_dragonman"},{"dragonman","wyvern","wyvern","blue_dragonman"},{"dragonman","wyvern","blue_wyvern","blue_dragonman"}};String[] types=t.theme.equals("tower")?tower[d.wave]:new String[]{"goblin","goblin","goblin_thrower","goblin_captain"};for(int i=0;i<4;i++)owner.spawn(d,types[i],i==3,false,ox+(i-2)*5,80,8+(i%2)*6);}
        d.status="RUNNING";journal.put("instance/"+d.id,d);
    }
    private void cleared(Dungeon d,List<ServerPlayer> here)throws Exception{
        var t=d.treasure;int tier=t.tier;double probability=t.failedProbability;
        if(t.region==0){double draw=random.nextDouble();tier=draw<.5?1:draw<.8?2:3;}
        else{probability=t.region==1?1:Math.min(1,probability+(t.region==5?1:.5));while(tier<10&&random.nextDouble()<probability){tier++;probability/=2;}}
        journal.begin();try{
            for(ServerPlayer p:here){String award="treasureAward/"+d.id+"/"+t.region+"/"+p.getUUID();if(!journal.get(award).isJsonNull())continue;Vec3 at=new Vec3(t.lastX,t.lastY,t.lastZ);rewards.chest(p,"treasure",tier,t.baseLevel,at,d.id.toString());if(t.region==0)rewards.chest(p,"goblin_kingdom",1,t.baseLevel,at,d.id.toString());journal.put(award,true);}
            t.cleared=true;if(t.region>0){t.tier=tier;t.failedProbability=probability;}
            else{t.portal=random.nextBoolean();if(t.portal){Vec3 legal=legal(server.overworld(),t.mapX,t.mapZ,t.mapY);if(legal!=null)t.mapY=legal.y;else t.portal=false;t.expires=System.currentTimeMillis()+30*60*1000L;}if(!t.portal)d.status="CLOSED";}
            if(t.region==5)d.status="COMPLETED";journal.put("instance/"+d.id,d);journal.finish();
        }catch(Exception e){journal.abort();rewards.abort();throw e;}
        rewards.publish();
        if(t.region==0){for(ServerPlayer p:here){players.session(p).life.instance="";players.save(p);players.profile(p);}if(t.portal)server.getPlayerList().broadcastSystemMessage(ModText.of("【挖宝】哥布林王国的秘密入口出现！主世界 X:"+t.mapX+" Y:"+(int)t.mapY+" Z:"+t.mapZ+"，所有冒险者均可进入，30分钟后关闭。"),false);}
        else for(ServerPlayer p:here)players.message(p,t.region==5?"黄金之王已被击败！最终宝箱已掉落，可拾取后返回。":"地区清场！每人一只宝箱已掉落，由图主选择左右传送门。");
        if(t.region==5)clearWeapons(d.id.toString());
    }
    private void next(Dungeon d,List<ServerPlayer> here)throws Exception{
        var t=d.treasure;int previous=t.region;
        d.status="PREPARING";
        for(ServerPlayer p:here){ReturnPoint point=d.returns.get(p.getStringUUID());if(point!=null)owner.rewards.move(p,d.id.toString(),point);}
        clearWeapons(d.id.toString());for(UUID id:d.enemies){Entity e=world(d).getEntity(id);if(e!=null)e.discard();}d.enemies.clear();
        t.region++;t.cleared=false;d.wave=0;t.theme=t.region==5?"palace":random.nextBoolean()?"forest":"tower";d.dungeon=t.theme.equals("tower")?"dragon_tower":"goblin_camp";
        owner.forceArena(d,true);journal.put("arenaCell/"+d.cell,"TREASURE_LAYOUT_1/"+t.theme);TreasureArena.build(owner.level(),new BlockPos(d.cell*512,80,0),t.theme);
        if(previous==0)d.withdrawn.addAll(d.roster);
        journal.put("instance/"+d.id,d);
        for(ServerPlayer p:here){if(previous==0&&!d.returns.containsKey(p.getStringUUID()))d.returns.put(p.getStringUUID(),owner.point(p));owner.teleport(p,owner.level(),d.cell*512+.5,80,-23);arrived(d,p);}
        spawnWave(d);owner.rewards.publish();for(ServerPlayer p:here)players.profile(p);
    }
    private void portalParticles(Dungeon d){var t=d.treasure;if(t.portal)portalRing(server.overworld(),t.mapX+.5,t.mapY,t.mapZ+.5);if(t.region>0&&t.cleared&&t.region<5)for(int side:new int[]{-1,1})portalRing(owner.level(),d.cell*512+side*29,80,0);}
    private void portalRing(ServerLevel level,double x,double y,double z){for(int i=0;i<12;i++){double a=i*Math.PI/6;level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,x,y+2+Math.sin(a)*2,z+Math.cos(a)*1.8,1,0,0,0,0);}}
    private void warn(Dungeon d,long now){var t=d.treasure;if(t.expires==0)return;long left=t.expires-now;String text=null;if(left<=60000&&!t.warningOne){t.warningOne=true;text="挖宝入口与秘境将在1分钟后关闭。";}else if(left<=300000&&!t.warningFive){t.warningFive=true;text="挖宝入口与秘境剩余5分钟。";}if(text!=null)for(ServerPlayer p:present(d))players.message(p,text);}
    void leave(Dungeon d,ServerPlayer p,boolean back)throws Exception{d.withdrawn.add(p.getUUID());ReturnPoint point=d.returns.get(p.getStringUUID());if(point!=null){owner.rewards.move(p,d.id.toString(),point);if(back)owner.back(p,point);}var life=players.session(p).life;life.instance="";life.dimension=p.level().dimension().identifier().toString();journal.put("instance/"+d.id,d);players.save(p);players.profile(p);owner.rewards.publish();}
    void finish(Dungeon d,String message)throws Exception{for(ServerPlayer p:present(d)){players.message(p,message);leave(d,p,d.treasure.region>0);}for(UUID id:d.roster){ServerPlayer p=server.getPlayerList().getPlayer(id);ReturnPoint point=d.returns.get(id.toString());if(p!=null&&point!=null)owner.rewards.move(p,d.id.toString(),point);}owner.rewards.publish();d.status="CLOSED";journal.put("instance/"+d.id,d);clearWeapons(d.id.toString());for(Entity e:world(d).getAllEntities())if(e instanceof DungeonEnemy mob&&mob.instance().equals(d.id.toString())){owner.cancelBoss(e.getUUID());e.discard();}if(d.treasure.region>0)owner.forceArena(d,false);}
    public void died(UUID boss){for(var weapon:new ArrayList<>(weapons.values()))if(weapon.boss.equals(boss))removeWeapon(weapon);}
    private void king(Dungeon d,DungeonEnemy boss,List<ServerPlayer> here,long now){
        boss.getNavigation().stop();if(boss.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN)||now<d.treasure.nextVolley)return;d.treasure.nextVolley=now+5000;
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
        JsonObject action=new JsonObject();action.addProperty("id",id);action.add("boss",JSON.toJsonTree(targets.target((LivingEntity)world(owner.byId(w.instance)).getEntity(w.boss))));action.addProperty("kind",explosion?"GOLD_EXPLOSION":"GOLD_DIRECT");action.addProperty("shape","CIRCLE");action.addProperty("x",p.getX());action.addProperty("y",p.getY());action.addProperty("z",p.getZ());action.addProperty("dx",0);action.addProperty("dz",0);action.addProperty("radius",explosion?GoldenWeapon.RADIUS:1);action.addProperty("halfAngle",Math.PI);action.addProperty("windupTicks",0);action.addProperty("activeTicks",10);action.addProperty("coefficient",explosion?2:1);action.addProperty("magic",explosion);action.addProperty("light",false);action.addProperty("startedAt",now);action.addProperty("treasureHit",true);Network.send(p,"bossAction",action);
    }
    private void sendWeapon(GoldenWeapon w,boolean remove){JsonObject packet=JSON.toJsonTree(w).getAsJsonObject();packet.addProperty("remove",remove);Dungeon d=owner.byId(w.instance);if(d!=null)for(ServerPlayer p:present(d))Network.send(p,"treasureWeapon",packet);}
    private void removeWeapon(GoldenWeapon w){weapons.remove(w.id);sendWeapon(w,true);}
    private void clearWeapons(String instance){for(var w:new ArrayList<>(weapons.values()))if(w.instance.equals(instance))removeWeapon(w);}
    private void syncWeapons(ServerPlayer p){for(var w:weapons.values())if(inside(owner.byId(w.instance),p)){JsonObject packet=JSON.toJsonTree(w).getAsJsonObject();packet.addProperty("remove",false);Network.send(p,"treasureWeapon",packet);}}
    @Override public void validate(ServerPlayer p,LivingEntity target,JsonObject report){if(!report.has("treasureAction"))return;String id=report.get("treasureAction").getAsString(),key=id+"/"+p.getUUID();Long time=hits.get(key);if(target!=p||time==null||System.currentTimeMillis()-time>3000||!players.session(p).life.instance.equals(hitInstances.get(key))||!journal.get("treasureHit/"+key).isJsonNull())throw new IllegalArgumentException("黄金之王命中凭据无效");}
    @Override public CombatPorts.Commit prepare(ServerPlayer p,LivingEntity target,JsonObject report,Receipt receipt,Life actor){if(!report.has("treasureAction"))return CombatPorts.Commit.NONE;String key="treasureHit/"+report.get("treasureAction").getAsString()+"/"+p.getUUID();return new CombatPorts.Commit(){public void persist()throws Exception{journal.put(key,true);}};}
}
