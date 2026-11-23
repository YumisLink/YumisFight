package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.AwakeningTower;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** Module-local spatial content registry. The same bounds drive construction, reuse and chunk ownership. */
public final class DungeonLayouts {
    public record Bounds(int minX,int minY,int minZ,int maxX,int maxY,int maxZ){}
    @FunctionalInterface public interface Gate {void set(ServerLevel world,BlockPos origin,int gate,boolean open);}
    public record Layout(String version,Bounds bounds,int gates,int wavesPerGate,BiConsumer<ServerLevel,BlockPos> build,Gate gate,Bounds activeBounds){
        public Layout(String version,Bounds bounds,int gates,int wavesPerGate,BiConsumer<ServerLevel,BlockPos> build,Gate gate){
            this(version,bounds,gates,wavesPerGate,build,gate,bounds);
        }
    }
    private final Map<String,Layout> all;
    public DungeonLayouts(DungeonPorts.CampDecorations decorations){var layouts=new HashMap<>(Map.ofEntries(
        Map.entry("goblin_camp",new Layout(GoblinCamp.VERSION,GoblinCamp.BOUNDS,0,2,
            withFloorLighting("goblin_camp",GoblinCamp.BOUNDS,(world,origin)->GoblinCamp.build(world,origin,decorations)),(world,origin,gate,open)->{})),
        Map.entry(GoblinKingdomLayout.ID,GoblinKingdomLayout.layout()),
        Map.entry("dragon_tower",new Layout(DragonTower.VERSION,DragonTower.BOUNDS,0,1,
            withFloorLighting("dragon_tower",DragonTower.BOUNDS,(world,origin)->DragonTower.build(world,origin,decorations)),(world,origin,gate,open)->{})),
        Map.entry(GblChurch.ID,new Layout(GblChurch.VERSION,GblChurch.BOUNDS,0,1,
            withFloorLighting(GblChurch.ID,GblChurch.BOUNDS,GblChurch::build),(world,origin,gate,open)->{},GblChurch.ACTIVE_BOUNDS)),
        Map.entry(CastlePalace.ID,new Layout(CastlePalace.VERSION,CastlePalace.BOUNDS,0,1,
            withFloorLighting(CastlePalace.ID,CastlePalace.BOUNDS,CastlePalace::build),(world,origin,gate,open)->{})),
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
        for(var tower:AwakeningTower.values())layouts.put(tower.dungeon().id(),AwakeningTowerLayout.layout(tower));
        all=Map.copyOf(layouts);
    }
    private static Layout classic(String version,BiConsumer<ServerLevel,BlockPos> build){return new Layout(version,new Bounds(-23,-2,-6,23,49,ClassicDungeonRooms.MAX_Z),6,1,build,ClassicDungeonRooms::gate);}
    private static BiConsumer<ServerLevel,BlockPos> withFloorLighting(String dungeon,Bounds bounds,BiConsumer<ServerLevel,BlockPos> build){
        var id=Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","dungeons/"+dungeon+"/floor_lighting");
        return (world,origin)->{
            var template=world.getStructureManager().get(id)
                .orElseThrow(()->new IllegalStateException("缺少副本地面光源结构："+id));
            var size=template.getSize();
            if(size.getX()>bounds.maxX()-bounds.minX()+1||size.getY()>bounds.maxY()-bounds.minY()+1||size.getZ()>bounds.maxZ()-bounds.minZ()+1)
                throw new IllegalStateException("副本地面光源超出场地范围："+id);
            build.accept(world,origin);
            BlockPos at=origin.offset(bounds.minX(),bounds.minY(),bounds.minZ());
            var settings=new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(true);
            if(!template.placeInWorld(world,at,at,settings,world.getRandom(),Block.UPDATE_CLIENTS|Block.UPDATE_SUPPRESS_DROPS))
                throw new IllegalStateException("无法放置副本地面光源："+id);
        };
    }
    public Layout require(String id){Layout value=all.get(id);if(value==null)throw new IllegalArgumentException("该副本尚未配置场地："+id);return value;}
    public Bounds reuseBounds(boolean previousSkySea,boolean includesWhale,boolean includesAwakeningTower){
        // The shared footprint includes the treasure arena and the former flat palace.
        Bounds result=new Bounds(-44,-2,-44,44,49,258);
        for(var entry:all.entrySet()){
            if(!previousSkySea&&entry.getKey().equals(CastlePalace.ID))continue;
            if(!includesWhale&&entry.getKey().equals(GblChurch.ID))continue;
            if(!includesAwakeningTower&&AwakeningTower.find(entry.getKey())!=null)continue;
            Bounds b=entry.getValue().bounds;
            result=new Bounds(Math.min(result.minX,b.minX),Math.min(result.minY,b.minY),Math.min(result.minZ,b.minZ),
                Math.max(result.maxX,b.maxX),Math.max(result.maxY,b.maxY),Math.max(result.maxZ,b.maxZ));
        }
        return result;
    }
}
