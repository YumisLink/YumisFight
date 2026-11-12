package cn.yumis.fight.modules.mapgeneration.application;

import net.minecraft.core.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.ChunkPos;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import java.util.*;
import java.util.function.Consumer;

public final class GenerationService {
    private static final Logger LOG=LogUtils.getLogger();
    private final MinecraftServer server;
    private GenerationPlan pending,last;
    private MapLighting lighting;
    private Consumer<String> completion;
    private int waitTicks,rounds,settle;
    private boolean spawn;
    public GenerationService(MinecraftServer server) {this.server=server;}
    public GenerationPlan plan(Identifier blueprint,ServerLevel level,BlockPos origin) throws Exception {return MapPlanner.plan(level,blueprint,origin);}
    public void generate(Identifier blueprint,ServerLevel level,BlockPos origin,boolean spawn,Consumer<String> reply) throws Exception {
        if(pending!=null)throw new IllegalStateException("已有地图正在完成光照检查");
        GenerationPlan plan=plan(blueprint,level,origin);
        this.pending=plan;this.last=plan;this.spawn=spawn;this.completion=reply;this.rounds=0;this.settle=0;this.waitTicks=20;
        try {MapPlacer.place(plan);}catch(RuntimeException e){pending=null;last=null;completion=null;throw e;}
        if(plan.blueprint.settings.noHostileSpawns) {
            var unwanted=new ArrayList<net.minecraft.world.entity.Entity>();for(var entity:level.getAllEntities())if(hostile(entity)&&plan.inside(entity.blockPosition().getX(),entity.blockPosition().getZ()))unwanted.add(entity);
            unwanted.forEach(net.minecraft.world.entity.Entity::discard);plan.metrics.put("initial_hostiles_removed",unwanted.size());
        }
        lighting=plan.blueprint.settings.lighting?new MapLighting(plan):null;
        LOG.info("MAP_PLACED {}",plan.summary());
        if(spawn)level.setRespawnData(LevelData.RespawnData.of(level.dimension(),plan.origin,180,0));
    }
    public void tick() {
        if(pending==null||--waitTicks>0)return;
        MapPlacer.levelLight(pending.level);
        if(pending.level.getChunkSource().getLightEngine().hasLightWork()&&settle++<200){waitTicks=5;return;}
        if(lighting!=null) {
            int remaining=lighting.dark().size();
            if(remaining>0&&rounds++<60) {
                int added=lighting.supplement(32);waitTicks=10;
                LOG.info("MAP_LIGHTING round={} dark={} added={}",rounds,remaining,added);
                if(added>0||rounds<3)return;
            }
            pending.metrics.putAll(lighting.metrics());
        }
        GenerationStore.complete(server,pending);
        MapPlacer.releaseChunks(pending);
        String result=pending.summary();LOG.info("MAP_COMPLETE {}",result);
        if(completion!=null)completion.accept(result);
        pending=null;lighting=null;completion=null;
    }
    public GenerationPlan last() {return last;}
    public static boolean hostile(net.minecraft.world.entity.Entity entity) {return entity instanceof net.minecraft.world.entity.monster.Enemy||entity.getType().getCategory()==net.minecraft.world.entity.MobCategory.MONSTER;}
    public boolean blocksHostiles(ServerLevel level,int x,int z) {return pending!=null&&pending.level==level&&pending.blueprint.settings.noHostileSpawns&&pending.inside(x,z)||GenerationStore.blocksHostiles(level,x,z);}
    public void close() {if(pending!=null)MapPlacer.releaseChunks(pending);}
    public String inspect() {
        if(last==null)return "{}";
        var root=new com.google.gson.JsonObject();root.addProperty("blueprint",last.id.toString());root.addProperty("mode",last.mode.name());
        root.add("origin",new com.google.gson.Gson().toJsonTree(new int[]{last.origin.getX(),last.origin.getY(),last.origin.getZ()}));
        root.add("bounds",new com.google.gson.Gson().toJsonTree(new int[]{last.minX,last.minZ,last.maxX,last.maxZ}));
        root.add("metrics",new com.google.gson.Gson().toJsonTree(last.metrics));
        var roads=new com.google.gson.JsonArray();for(int i:last.roadCells)roads.add(new com.google.gson.Gson().toJsonTree(new int[]{last.x(i),last.roadHeights.get(i),last.z(i)}));root.add("roads",roads);
        var facilities=new com.google.gson.JsonArray();
        for(var f:last.facilities) {
            var item=new com.google.gson.JsonObject();item.addProperty("id",f.definition().id);item.addProperty("biome",f.biome());item.addProperty("surface",f.surface());item.addProperty("island",f.definition().island);item.addProperty("lightTarget",f.definition().lightTarget);
            item.add("position",new com.google.gson.Gson().toJsonTree(new int[]{f.position().getX(),f.position().getY(),f.position().getZ()}));item.add("size",new com.google.gson.Gson().toJsonTree(new int[]{f.template().getSize().getX(),f.template().getSize().getY(),f.template().getSize().getZ()}));
            item.add("loot",new com.google.gson.Gson().toJsonTree(f.definition().loot));item.add("tags",new com.google.gson.Gson().toJsonTree(f.definition().tags));facilities.add(item);
        }
        root.add("facilities",facilities);return root.toString();
    }
    public String audit() {
        if(last==null)return "No generation in this session";
        Map<String,Object> result=new LinkedHashMap<>(last.metrics);int blocked=0,flat=0,loot=0;
        List<String> obstructions=new ArrayList<>();
        List<ChunkPos> auditChunks=new ArrayList<>();
        for(int x=last.minX>>4;x<=last.maxX>>4;x++)for(int z=last.minZ>>4;z<=last.maxZ>>4;z++){if(last.level.setChunkForced(x,z,true))auditChunks.add(new ChunkPos(x,z));last.level.getChunk(x,z);}
        for(int i:last.roadCells) {
            int y=Math.floorDiv(last.roadHeights.get(i)+1,2);BlockPos feet=new BlockPos(last.x(i),y,last.z(i));
            var box=new net.minecraft.world.phys.AABB(feet.getX()+.2,feet.getY(),feet.getZ()+.2,feet.getX()+.8,feet.getY()+2,feet.getZ()+.8);
            if(last.level.getBlockCollisions(null,box).iterator().hasNext()) {blocked++;if(obstructions.size()<30)obstructions.add(feet.toShortString()+" "+last.level.getBlockState(feet)+" / "+last.level.getBlockState(feet.above()));}
        }
        for(int[] zone:last.flatZones)for(int i:zone)if(!last.roadHeights.get(i).equals(last.roadHeights.get(zone[0])))flat++;
        for(var f:last.facilities)for(var slot:f.definition().loot) {
            var entity=last.level.getBlockEntity(f.position().offset(slot.pos[0],slot.pos[1],slot.pos[2]));
            if(entity instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity container&&container.getLootTable()!=null)loot++;
        }
        result.put("road_body_collisions",blocked);result.put("flat_zone_errors",flat);result.put("native_loot_tables",loot);
        result.put("obstructions",obstructions);
        for(var c:auditChunks)last.level.setChunkForced(c.x(),c.z(),false);
        return new com.google.gson.Gson().toJson(result);
    }
}
