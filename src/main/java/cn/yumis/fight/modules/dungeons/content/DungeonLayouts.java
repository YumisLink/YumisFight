package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Module-local spatial content registry. The same bounds drive construction, reuse and chunk ownership. */
public final class DungeonLayouts {
    public record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ){}
    @FunctionalInterface public interface Gate {void set(ServerLevel world,BlockPos origin,int gate,boolean open);}
    public record Layout(String version,Bounds bounds,int gates,int wavesPerGate,BiConsumer<ServerLevel,BlockPos> build,Gate gate){}
    private final Map<String,Layout> all;
    public DungeonLayouts(DungeonPorts.CampDecorations decorations){var layouts=new HashMap<>(Map.ofEntries(
        Map.entry("goblin_camp",new Layout(GoblinCamp.VERSION,GoblinCamp.BOUNDS,0,2,(world,origin)->GoblinCamp.build(world,origin,decorations),(world,origin,gate,open)->{})),
        Map.entry("dragon_tower",new Layout(DragonTower.VERSION,DragonTower.BOUNDS,0,1,(world,origin)->DragonTower.build(world,origin,decorations),(world,origin,gate,open)->{})),
        Map.entry("gbl_church",new Layout("GBL_LAYOUT_1",new Bounds(-23,-2,-6,23,49,GblChurch.MAX_Z),3,2,GblChurch::build,GblChurch::gate)),
        Map.entry(CastlePalace.ID,new Layout(CastlePalace.VERSION,CastlePalace.BOUNDS,CastlePalace.BOSS,1,CastlePalace::build,CastlePalace::gate)),
        Map.entry("behemoth_forbidden",new Layout("FORBIDDEN_LAYOUT_1",new Bounds(-23,-2,-6,23,49,BehemothForbidden.MAX_Z),3,2,BehemothForbidden::build,BehemothForbidden::gate)),
        Map.entry("skasa",new Layout("SKASA_LAYOUT_1",new Bounds(-23,-2,-6,23,49,SkasaLair.MAX_Z),3,2,SkasaLair::build,SkasaLair::gate)),
        Map.entry("dark_city",new Layout("DARK_CITY_LAYOUT_1",new Bounds(-23,-2,-6,23,49,DarkCityEntrance.MAX_Z),3,2,DarkCityEntrance::build,DarkCityEntrance::gate)),
        Map.entry(MoonlightTavern.ID,classic("TAVERN_LAYOUT_1",MoonlightTavern::build)),
        Map.entry(Hamelin.ID,classic("HAMELIN_LAYOUT_1",Hamelin::build)),
        Map.entry(PursuitAnnihilation.ID,classic("PURSUIT_LAYOUT_1",PursuitAnnihilation::build)),
        Map.entry(SeaTrain.ID,classic("TRAIN_LAYOUT_1",SeaTrain::build)),
        Map.entry(TimeGate.ID,classic("TIME_GATE_LAYOUT_1",TimeGate::build)),
        Map.entry(MysteriousAwareness.ID,classic("AWARENESS_LAYOUT_1",MysteriousAwareness::build)),
        Map.entry(Grandine.ID,classic("GRANDINE_LAYOUT_1",Grandine::build)),
        Map.entry(WitchForest.ID,classic("FOREST_LAYOUT_1",WitchForest::build))));
        PracticeLayouts.register(layouts);
        all=Map.copyOf(layouts);
    }
    private static Layout classic(String version,BiConsumer<ServerLevel,BlockPos> build){return new Layout(version,new Bounds(-23,-2,-6,23,49,ClassicDungeonRooms.MAX_Z),6,1,build,ClassicDungeonRooms::gate);}
    public Layout require(String id){Layout value=all.get(id);if(value==null)throw new IllegalArgumentException("该副本尚未配置场地："+id);return value;}
    public Bounds reuseBounds(boolean previousSkySea){
        // The shared footprint includes the treasure arena and the former flat palace.
        Bounds result=new Bounds(-44,-2,-44,44,49,258);
        for(var entry:all.entrySet()){
            if(!previousSkySea&&entry.getKey().equals(CastlePalace.ID))continue;
            Bounds b=entry.getValue().bounds;
            result=new Bounds(Math.min(result.minX,b.minX),Math.min(result.minY,b.minY),Math.min(result.minZ,b.minZ),
                Math.max(result.maxX,b.maxX),Math.max(result.maxY,b.maxY),Math.max(result.maxZ,b.maxZ));
        }
        return result;
    }
}
