package cn.yumis.fight.modules.industry.platform;

import cn.yumis.fight.modules.industry.api.*;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.*;
import java.util.*;

/** World-persistent deep deposits and per-player discovery. Never requests a terrain chunk. */
public final class IndustrialDeposits extends SavedData {
    private static final Gson JSON=new Gson();
    public static final SavedDataType<IndustrialDeposits> TYPE=new SavedDataType<>(IndustrialContent.id("deep_deposits_v1"),IndustrialDeposits::new,ChunkedStrings.CODEC.xmap(IndustrialDeposits::decode,IndustrialDeposits::encode));
    private final Map<String,Map<String,Map<String,Integer>>> deposits=new LinkedHashMap<>();
    private final Map<String,Survey> surveys=new LinkedHashMap<>();
    private final Map<UUID,Scan> pending=new HashMap<>();
    private static final class Survey {
        Map<String,Map<String,Integer>> known=new LinkedHashMap<>();
        long ready;int x,z;String dimension="";
    }
    private record Scan(String dimension,int x,int z,int tier,long finish){}
    public static IndustrialDeposits get(ServerLevel level){return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);}
    private static IndustrialDeposits decode(String raw){var data=JSON.fromJson(raw,IndustrialDeposits.class);return data==null?new IndustrialDeposits():data;}
    private String encode(){JsonObject j=new JsonObject();j.add("deposits",JSON.toJsonTree(deposits));j.add("surveys",JSON.toJsonTree(surveys));return j.toString();}
    private Survey survey(ServerPlayer player){return surveys.computeIfAbsent(player.getUUID().toString(),id->new Survey());}
    public Map<String,Integer> deposit(ServerLevel level,int x,int z){
        String key=Long.toString(ChunkPos.pack(x,z));var dimension=deposits.computeIfAbsent(level.dimension().identifier().toString(),id->new LinkedHashMap<>());
        var found=dimension.get(key);if(found!=null)return Collections.unmodifiableMap(found);
        var rolled=new LinkedHashMap<String,Integer>();
        for(var entry:IndustrialRules.current().minerals.entrySet())if(level.getRandom().nextInt(100)<entry.getValue().chance()){
            int richness=level.getRandom().nextInt(100);rolled.put(entry.getKey(),richness<50?100:richness<90?35:135);
        }
        dimension.put(key,rolled);setDirty();return Collections.unmodifiableMap(rolled);
    }
    public void reveal(ServerPlayer player,ServerLevel level,int x,int z,int tier){
        deposit(level,x,z);survey(player).known.computeIfAbsent(level.dimension().identifier().toString(),id->new LinkedHashMap<>()).merge(Long.toString(ChunkPos.pack(x,z)),tier,Math::max);setDirty();
    }
    public static int heldTier(ServerPlayer player){return Math.max(ProspectorItem.tier(player.getMainHandItem()),ProspectorItem.tier(player.getOffhandItem()));}
    public void start(ServerPlayer player,int tier){
        if(heldTier(player)<tier)throw new IllegalArgumentException("请手持对应等级的探矿器");
        long now=player.level().getServer().overworld().getGameTime();Survey history=survey(player);
        if(pending.containsKey(player.getUUID()))throw new IllegalArgumentException("正在扫描，可以移动或关闭地图");
        if(now<history.ready)throw new IllegalArgumentException("探矿器冷却中，还需 "+((history.ready-now+19)/20)+" 秒");
        int x=player.blockPosition().getX()>>4,z=player.blockPosition().getZ()>>4;
        long finish=now+IndustrialRules.current().scanTicks;history.ready=finish+IndustrialRules.current().scanCooldown;
        history.x=x;history.z=z;history.dimension=player.level().dimension().identifier().toString();
        pending.put(player.getUUID(),new Scan(history.dimension,x,z,tier,finish));setDirty();
    }
    public void tick(MinecraftServer server){
        long now=server.overworld().getGameTime();
        var iterator=pending.entrySet().iterator();
        while(iterator.hasNext()){
            var entry=iterator.next();Scan scan=entry.getValue();if(now<scan.finish)continue;
            var player=server.getPlayerList().getPlayer(entry.getKey());iterator.remove();if(player==null)continue;
            ServerLevel level=server.getAllLevels().iterator().next();boolean found=false;
            for(var candidate:server.getAllLevels())if(candidate.dimension().identifier().toString().equals(scan.dimension)){level=candidate;found=true;break;}
            if(!found)continue;
            int radius=scan.tier-1;
            for(int x=scan.x-radius;x<=scan.x+radius;x++)for(int z=scan.z-radius;z<=scan.z+radius;z++)reveal(player,level,x,z,scan.tier);
            IndustrialPorts.message(player,"扫描完成："+(radius*2+1)+"×"+(radius*2+1)+"区块，矿藏结果已保存");
        }
    }
    public JsonObject map(ServerPlayer player,int centerX,int centerZ,int tier){
        Survey history=survey(player);String dimension=player.level().dimension().identifier().toString();
        JsonObject j=new JsonObject();j.addProperty("tier",tier);j.addProperty("x",centerX);j.addProperty("z",centerZ);j.addProperty("dimension",dimension);
        long now=player.level().getServer().overworld().getGameTime();j.addProperty("cooldown",Math.max(0,history.ready-now));Scan scan=pending.get(player.getUUID());j.addProperty("scanning",scan!=null);j.addProperty("remaining",scan==null?0:Math.max(0,scan.finish-now));
        JsonArray cells=new JsonArray();var known=history.known.getOrDefault(dimension,Map.of());var table=deposits.getOrDefault(dimension,Map.of());
        for(int z=centerZ-4;z<=centerZ+4;z++)for(int x=centerX-4;x<=centerX+4;x++){
            String key=Long.toString(ChunkPos.pack(x,z));int grade=Math.min(tier,known.getOrDefault(key,0));JsonObject cell=new JsonObject();cell.addProperty("x",x);cell.addProperty("z",z);cell.addProperty("known",grade);JsonObject minerals=new JsonObject();
            if(grade>0)for(var entry:table.getOrDefault(key,Map.of()).entrySet()){var rule=IndustrialRules.current().minerals.get(entry.getKey());if(rule!=null&&rule.tier()<=grade)minerals.addProperty(entry.getKey(),entry.getValue());}
            cell.add("minerals",minerals);cells.add(cell);
        }
        j.add("cells",cells);return j;
    }
}
